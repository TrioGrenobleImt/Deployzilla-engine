package fr.imt.deployzilla.deployzilla.business.service;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.async.ResultCallback;
import com.github.dockerjava.api.command.CreateContainerResponse;
import com.github.dockerjava.api.command.WaitContainerResultCallback;
import com.github.dockerjava.api.model.*;
import com.github.dockerjava.core.DefaultDockerClientConfig;
import com.github.dockerjava.core.DockerClientBuilder;
import com.github.dockerjava.httpclient5.ApacheDockerHttpClient;
import fr.imt.deployzilla.deployzilla.business.model.ProcessResult;
import fr.imt.deployzilla.deployzilla.business.port.ProcessLogPublisherPort;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.File;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import com.github.dockerjava.api.command.BuildImageResultCallback;

/**
 * Executes pipeline steps in isolated Docker containers.
 * Uses label-based strategy to track and isolate managed containers.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ContainerExecutor {

    private static final String MANAGED_LABEL = "deployzilla.managed";
    private static final String PIPELINE_LABEL = "deployzilla.pipeline-id";
    private static final String STEP_LABEL = "deployzilla.step-id";
    private static final int TUNNEL_PORT = 2375;

    private final ProcessLogPublisherPort processLogPublisherPort;

    // --- Configuration: Docker Host (Local vs Remote) ---

    @Value("${docker.host:unix:///var/run/docker.sock}")
    private String localDockerHost;

    @Value("${deployzilla.remote.enabled:false}")
    private boolean remoteEnabled;

    @Value("${deployzilla.remote.host:localhost}")
    private String remoteHost;

    @Value("${deployzilla.remote.user:root}")
    private String remoteUser;

    @Value("${deployzilla.remote.password:}")
    private String remotePassword; // Inject Password

    @Value("${deployzilla.remote.port:22}")
    private int remotePort;

    // --- Configuration: Private Registry Credentials ---

    @Value("${deployzilla.docker.registry.username:}")
    private String registryUsername;

    @Value("${deployzilla.docker.registry.password:}")
    private String registryPassword;

    // --- Configuration: Resources ---

    @Value("${docker.timeout.seconds:600}")
    private int timeoutSeconds;

    @Value("${docker.memory.limit:2147483648}") // 2GB default
    private long memoryLimit;

    private DockerClient dockerClient;
    private Process sshTunnelProcess;

    @PostConstruct
    public void init() {
        String finalDockerHost = localDockerHost;

        if (remoteEnabled) {
            log.info("Starting SSH Tunnel to {}:{}", remoteHost, remotePort);
            try {
                startSshTunnel();
                // Point Docker Client to the Local Tunnel
                finalDockerHost = "tcp://127.0.0.1:" + TUNNEL_PORT;
            } catch (Exception e) {
                throw new RuntimeException("Failed to establish SSH tunnel", e);
            }
        }

        DefaultDockerClientConfig config = DefaultDockerClientConfig.createDefaultConfigBuilder()
                .withDockerHost(finalDockerHost)
                .build();

        // Use standard Apache client (Works with TCP)
        ApacheDockerHttpClient httpClient = new ApacheDockerHttpClient.Builder()
                .dockerHost(config.getDockerHost())
                .connectionTimeout(Duration.ofSeconds(30))
                .responseTimeout(Duration.ofSeconds(timeoutSeconds))
                .build();

        this.dockerClient = DockerClientBuilder.getInstance(config)
                .withDockerHttpClient(httpClient)
                .build();

        log.info("Docker client initialized connected to: {}", finalDockerHost);
    }

    private void startSshTunnel() throws IOException, InterruptedException {
        log.info("Starting SSH Tunnel with Password Auth...");

        ProcessBuilder pb = new ProcessBuilder(
                "sshpass", "-e",
                "ssh",
                "-4",
                "-N", // No remote command (just forwarding)
                "-L", String.format("%d:/var/run/docker.sock", TUNNEL_PORT),
                "-p", String.valueOf(remotePort),
                "-o", "StrictHostKeyChecking=no",     // Don't ask for confirmation
                "-o", "UserKnownHostsFile=/dev/null", // Don't save host keys
                "-o", "LogLevel=VERBOSE",             // <--- Enable Verbose Logs
                String.format("%s@%s", remoteUser, remoteHost)
        );

        if (remotePassword != null && !remotePassword.isBlank()) {
            pb.environment().put("SSHPASS", remotePassword);
        } else {
            throw new RuntimeException("Remote Password is required!");
        }

        // Redirect stderr to stdout so we can read both in one stream
        pb.redirectErrorStream(true);

        this.sshTunnelProcess = pb.start();

        // --- NEW: Read SSH Output in a background thread ---
        new Thread(() -> {
            try (java.io.BufferedReader reader = new java.io.BufferedReader(
                    new java.io.InputStreamReader(sshTunnelProcess.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    // Log every line from SSH so you can see auth errors
                    log.warn("[SSH-TUNNEL] {}", line);
                }
            } catch (IOException e) {
                // Ignore stream close errors
            }
        }).start();
        // --------------------------------------------------

        // Wait a bit longer to ensure connection is stable
        Thread.sleep(3000);

        if (!sshTunnelProcess.isAlive()) {
            throw new RuntimeException("SSH Tunnel process died immediately. Check logs above for [SSH-TUNNEL] errors.");
        }

        log.info("SSH Tunnel started successfully on port {}", TUNNEL_PORT);
    }

    @PreDestroy
    public void cleanup() {
        if (dockerClient != null) {
            try {
                dockerClient.close();
            } catch (Exception e) {
                log.warn("Error closing Docker client", e);
            }
        }
    }

    /**
     * Execute a command in a container with the specified image.
     *
     * @param pipelineId Unique pipeline identifier
     * @param stepId     Unique step identifier
     * @param image      Docker image to use (e.g., "alpine/git")
     * @param volumes    Optional volume bindings (host:container)
     * @param envVars    Optional environment variables
     * @return CompletableFuture with exit code
     */
    @Async
    public CompletableFuture<ProcessResult> executeStep(
            String pipelineId,
            String stepId,
            String image,
            List<String> volumes,
            Map<String, String> envVars) {
        return executeStep(pipelineId, stepId, image, volumes, envVars, null);
    }

    @Async
    public CompletableFuture<ProcessResult> executeStep(
            String pipelineId,
            String stepId,
            String image,
            List<String> volumes,
            Map<String, String> envVars,
            List<String> command) {

        String containerId = null;

        try {
            publishLog(pipelineId, String.format("--- Step [%s] Starting ---", stepId));
            publishLog(pipelineId, String.format("Image: %s", image));

            // Pull image from registry if not present
            pullImageIfNeeded(pipelineId, image);

            // Prepare labels for ownership tracking
            Map<String, String> labels = Map.of(
                    MANAGED_LABEL, "true",
                    PIPELINE_LABEL, pipelineId,
                    STEP_LABEL, stepId
            );

            // Prepare environment variables
            String[] env = envVars != null
                    ? envVars.entrySet().stream()
                    .map(e -> e.getKey() + "=" + e.getValue())
                    .toArray(String[]::new)
                    : new String[0];

            // Prepare volume binds
            Bind[] binds = volumes != null
                    ? volumes.stream()
                    .map(Bind::parse)
                    .toArray(Bind[]::new)
                    : new Bind[0];

            // Create host config with resource limits
            HostConfig hostConfig = HostConfig.newHostConfig()
                    .withMemory(memoryLimit)
                    .withMemorySwap(memoryLimit) // Disable swap
                    .withCpuQuota(50000L)        // 50% CPU limit
                    .withCpuPeriod(100000L)
                    .withNetworkMode("deployzilla") // Ensure this network exists on remote host!
                    .withBinds(binds)
                    .withAutoRemove(false);

            // Create container
            var containerCmd = dockerClient.createContainerCmd(image)
                    .withLabels(labels)
                    .withEnv(env)
                    .withHostConfig(hostConfig);

            if (command != null && !command.isEmpty()) {
                containerCmd.withCmd(command);
            }

            CreateContainerResponse container = containerCmd.exec();

            containerId = container.getId();
            publishLog(pipelineId, String.format("Container created: %s", containerId.substring(0, 12)));

            // Start container
            dockerClient.startContainerCmd(containerId).exec();

            // Stream logs
            StringBuilder capturedOutput = new StringBuilder();
            streamLogs(pipelineId, containerId, capturedOutput);

            // Wait for completion
            Integer exitCode = dockerClient.waitContainerCmd(containerId)
                    .exec(new WaitContainerResultCallback())
                    .awaitStatusCode(timeoutSeconds, TimeUnit.SECONDS);

            publishLog(pipelineId, String.format("--- Step [%s] Finished (Exit: %d) ---", stepId, exitCode));

            return CompletableFuture.completedFuture(new ProcessResult(exitCode, capturedOutput.toString()));

        } catch (Exception e) {
            log.error("Container execution failed for step {}", stepId, e);
            publishLog(pipelineId, String.format("ERROR: %s", e.getMessage()));
            return CompletableFuture.completedFuture(new ProcessResult(1, "ERROR"));

        } finally {
            // Cleanup container
            if (containerId != null) {
                try {
                    dockerClient.removeContainerCmd(containerId)
                            .withForce(true)
                            .exec();
                    log.debug("Container {} removed", containerId);
                } catch (Exception e) {
                    log.warn("Failed to remove container {}", containerId, e);
                }
            }
        }
    }

    /**
     * Pull image if not already present locally.
     * Uses configured registry credentials if provided.
     */
    private void pullImageIfNeeded(String pipelineId, String image) {
        String imageToCheck = image.contains(":") ? image : image + ":latest";

        try {
            // Check if exact image exists
            String repoName = imageToCheck.split(":")[0];
            List<Image> images = dockerClient.listImagesCmd()
                    .withImageNameFilter(repoName)
                    .exec();

            boolean exists = images.stream()
                    .anyMatch(img -> img.getRepoTags() != null &&
                            java.util.Arrays.asList(img.getRepoTags()).contains(imageToCheck));

            if (!exists) {
                publishLog(pipelineId, String.format("Pulling image: %s", imageToCheck));
                log.info("Pulling Docker image: {}", imageToCheck);

                var pullCommand = dockerClient.pullImageCmd(imageToCheck);

                // Inject Registry Credentials
                if (registryUsername != null && !registryUsername.isBlank()) {
                    AuthConfig authConfig = new AuthConfig()
                            .withUsername(registryUsername)
                            .withPassword(registryPassword);
                    pullCommand.withAuthConfig(authConfig);
                }

                pullCommand.start().awaitCompletion(5, TimeUnit.MINUTES);

                publishLog(pipelineId, "Image pulled successfully");
                log.info("Successfully pulled image: {}", imageToCheck);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Image pull interrupted", e);
        } catch (Exception e) {
            log.error("Failed to pull image: {}", imageToCheck, e);
            publishLog(pipelineId, String.format("ERROR: Failed to pull image %s: %s", imageToCheck, e.getMessage()));
            throw new RuntimeException("Failed to pull image: " + imageToCheck, e);
        }
    }

    /**
     * Stream container logs to Redis.
     */
    private void streamLogs(String pipelineId, String containerId, StringBuilder outputBuffer) {
        try {
            dockerClient.logContainerCmd(containerId)
                    .withStdOut(true)
                    .withStdErr(true)
                    .withFollowStream(true)
                    .exec(new ResultCallback.Adapter<Frame>() {
                        @Override
                        public void onNext(Frame frame) {
                            String logLine = new String(frame.getPayload()).trim();
                            if (!logLine.isEmpty()) {
                                publishLog(pipelineId, logLine);
                                if (StreamType.STDOUT.equals(frame.getStreamType())) {
                                    outputBuffer.append(logLine).append("\n");
                                }
                            }
                        }
                    })
                    .awaitCompletion(timeoutSeconds, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("Log streaming interrupted for container {}", containerId);
        }
    }

    /**
     * List all containers managed by Deployzilla.
     */
    public List<Container> listManagedContainers() {
        return dockerClient.listContainersCmd()
                .withShowAll(true)
                .withLabelFilter(Map.of(MANAGED_LABEL, "true"))
                .exec();
    }

    /**
     * List containers for a specific pipeline.
     */
    public List<Container> listPipelineContainers(String pipelineId) {
        return dockerClient.listContainersCmd()
                .withShowAll(true)
                .withLabelFilter(Map.of(
                        MANAGED_LABEL, "true",
                        PIPELINE_LABEL, pipelineId
                ))
                .exec();
    }

    /**
     * Cleanup all containers for a specific pipeline.
     */
    public void cleanupPipeline(String pipelineId) {
        List<Container> containers = listPipelineContainers(pipelineId);
        for (Container container : containers) {
            try {
                dockerClient.removeContainerCmd(container.getId())
                        .withForce(true)
                        .exec();
                log.info("Cleaned up container {} for pipeline {}", container.getId(), pipelineId);
            } catch (Exception e) {
                log.warn("Failed to cleanup container {}", container.getId(), e);
            }
        }
    }

    /**
     * Build a Docker image from a directory.
     */
    public String buildImage(String pipelineId, String buildContextPath, String dockerfileName, String imageName, String tag) {
        String fullImageName = imageName + ":" + tag;
        publishLog(pipelineId, "Starting image build: " + fullImageName + " using " + dockerfileName);

        try {
            return dockerClient.buildImageCmd(new File(buildContextPath))
                    .withDockerfile(new File(buildContextPath, dockerfileName))
                    .withTags(Set.of(fullImageName))
                    .exec(new BuildImageResultCallback() {
                        @Override
                        public void onNext(BuildResponseItem item) {
                            if (item.getStream() != null) {
                                publishLog(pipelineId, item.getStream().trim());
                            }
                            super.onNext(item);
                        }
                    })
                    .awaitImageId();
        } catch (Exception e) {
            log.error("Image build failed", e);
            publishLog(pipelineId, "Image build failed: " + e.getMessage());
            throw new RuntimeException("Image build failed", e);
        }
    }

    /**
     * Start a container and return the container ID (without waiting for completion).
     */
    public String startContainer(String pipelineId, String imageName, Map<String, String> envVars) {
        String containerId = null;
        try {
            publishLog(pipelineId, "Starting application container: " + imageName);

            // Prepare labels for ownership tracking
            Map<String, String> labels = Map.of(
                    MANAGED_LABEL, "true",
                    PIPELINE_LABEL, pipelineId,
                    "deployzilla.type", "app"
            );

            // Prepare environment variables
            String[] env = envVars != null
                    ? envVars.entrySet().stream()
                    .map(e -> e.getKey() + "=" + e.getValue())
                    .toArray(String[]::new)
                    : new String[0];

             // Create host config
            HostConfig hostConfig = HostConfig.newHostConfig()
                    .withMemory(memoryLimit)
                    .withMemorySwap(memoryLimit)
                    .withCpuQuota(50000L)
                    .withPublishAllPorts(true) // Publish all exposed ports
                    .withAutoRemove(false);

            CreateContainerResponse container = dockerClient.createContainerCmd(imageName)
                    .withLabels(labels)
                    .withEnv(env)
                    .withHostConfig(hostConfig)
                    .exec();

            containerId = container.getId();
            dockerClient.startContainerCmd(containerId).exec();

            publishLog(pipelineId, "Application container started: " + containerId.substring(0, 12));
            return containerId;

        } catch (Exception e) {
            log.error("Failed to start application container", e);
            publishLog(pipelineId, "Failed to start container: " + e.getMessage());
            throw new RuntimeException("Failed to start container", e);
        }
    }

    public void publishLog(String pipelineId, String message) {
        processLogPublisherPort.publish(pipelineId, message);
    }
}
