package fr.imt.deployzilla.deployzilla.business.service;

import com.github.dockerjava.api.DockerClient;

import com.github.dockerjava.api.command.CreateContainerResponse;
import com.github.dockerjava.api.command.WaitContainerResultCallback;
import com.github.dockerjava.api.model.*;
import com.github.dockerjava.core.DefaultDockerClientConfig;
import com.github.dockerjava.core.DockerClientBuilder;
import com.github.dockerjava.httpclient5.ApacheDockerHttpClient;
import fr.imt.deployzilla.deployzilla.business.model.ProcessResult;

import fr.imt.deployzilla.deployzilla.exception.ContainerExecutionException;
import fr.imt.deployzilla.deployzilla.exception.ImagePullException;
import fr.imt.deployzilla.deployzilla.infrastructure.docker.ContainerLogStreamer;
import fr.imt.deployzilla.deployzilla.infrastructure.ssh.SshTunnel;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;


import java.time.Duration;
import java.util.List;
import java.util.Map;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;


import static fr.imt.deployzilla.deployzilla.business.utils.Constants.*;

/**
 * Executes pipeline steps in isolated Docker containers.
 * Uses label-based strategy to track and isolate managed containers.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ContainerExecutor {


    private final SshTunnel sshTunnel;
    private final ContainerLogStreamer containerLogStreamer;

    @Value("${docker.host:unix:///var/run/docker.sock}")
    private String localDockerHost;

    @Value("${deployzilla.remote.enabled:false}")
    private boolean remoteEnabled;

    @Value("${deployzilla.docker.registry.username:}")
    private String registryUsername;

    @Value("${deployzilla.docker.registry.password:}")
    private String registryPassword;

    @Value("${docker.timeout.seconds:600}")
    private int timeoutSeconds;

    @Value("${docker.memory.limit:2147483648}")
    private long memoryLimit;

    private DockerClient dockerClient; // Remote (or local if remote invalid/disabled)
    private DockerClient localDockerClient; // Always Local

    @PostConstruct
    public void init() {
        // 1. Initialize Local Client (Always needed for Build & Push)
        DefaultDockerClientConfig localConfig = DefaultDockerClientConfig.createDefaultConfigBuilder()
                .withDockerHost(localDockerHost)
                .build();

        ApacheDockerHttpClient localHttpClient = new ApacheDockerHttpClient.Builder()
                .dockerHost(localConfig.getDockerHost())
                .connectionTimeout(Duration.ofSeconds(30))
                .responseTimeout(Duration.ofSeconds(timeoutSeconds))
                .build();

        this.localDockerClient = DockerClientBuilder.getInstance(localConfig)
                .withDockerHttpClient(localHttpClient)
                .build();
        
        log.info("Local Docker client initialized connected to: {}", localDockerHost);

        // 2. Initialize Remote Client (for Run)
        String finalRemoteDockerHost = localDockerHost;

        if (remoteEnabled) {
            sshTunnel.connect();
            // Point Docker Client to the Local Tunnel
            finalRemoteDockerHost = "tcp://127.0.0.1:" + sshTunnel.getLocalPort();
        }

        DefaultDockerClientConfig remoteConfig = DefaultDockerClientConfig.createDefaultConfigBuilder()
                .withDockerHost(finalRemoteDockerHost)
                .build();

        // Use standard Apache client (Works with TCP)
        ApacheDockerHttpClient remoteHttpClient = new ApacheDockerHttpClient.Builder()
                .dockerHost(remoteConfig.getDockerHost())
                .connectionTimeout(Duration.ofSeconds(30))
                .responseTimeout(Duration.ofSeconds(timeoutSeconds))
                .build();

        this.dockerClient = DockerClientBuilder.getInstance(remoteConfig)
                .withDockerHttpClient(remoteHttpClient)
                .build();

        log.info("Remote Docker client initialized connected to: {}", finalRemoteDockerHost);
    }

    @PreDestroy
    public void cleanup() {
        // Disconnect SSH tunnel first
        sshTunnel.disconnect();
        
        if (dockerClient != null) {
            try {
                dockerClient.close();
            } catch (Exception e) {
                log.warn("Error closing Docker client", e);
            }
        }
        if (localDockerClient != null) {
            try {
                localDockerClient.close();
            } catch (Exception e) {
                log.warn("Error closing Local Docker client", e);
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
            containerLogStreamer.publishLog(pipelineId, String.format("--- Step [%s] Starting (Local) ---", stepId));
            containerLogStreamer.publishLog(pipelineId, String.format("Image: %s", image));

            // Pull image from registry if not present (LOCALLY)
            pullImageIfNeeded(localDockerClient, pipelineId, image);

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
                    .withNetworkMode("bridge") // Use bridge for local instead of deployzilla? Or assume Deployzilla network exists locally too.
                    // Assuming host has the network, or fallback to bridge for generic steps. 
                    // Let's keep deployzilla if user set it up, but usually bridge is safer for generic steps unless Inter-Container comm fits.
                    // Safest for Git Clone is default.
                    .withBinds(binds)
                    .withAutoRemove(false);

            // Create container LOCALLY
            var containerCmd = localDockerClient.createContainerCmd(image)
                    .withLabels(labels)
                    .withEnv(env)
                    .withHostConfig(hostConfig);

            if (command != null && !command.isEmpty()) {
                containerCmd.withCmd(command);
            }

            CreateContainerResponse container = containerCmd.exec();

            containerId = container.getId();
            containerLogStreamer.publishLog(pipelineId, String.format("Container created: %s", containerId.substring(0, 12)));

            // Start container
            localDockerClient.startContainerCmd(containerId).exec();

            // Stream logs
            StringBuilder capturedOutput = new StringBuilder();
            containerLogStreamer.streamLogs(localDockerClient, pipelineId, containerId, capturedOutput);

            // Wait for completion
            Integer exitCode = localDockerClient.waitContainerCmd(containerId)
                    .exec(new WaitContainerResultCallback())
                    .awaitStatusCode(timeoutSeconds, TimeUnit.SECONDS);

            containerLogStreamer.publishLog(pipelineId, String.format("--- Step [%s] Finished (Exit: %d) ---", stepId, exitCode));

            return CompletableFuture.completedFuture(new ProcessResult(exitCode, capturedOutput.toString()));

        } catch (Exception e) {
            log.error("Container execution failed for step {}", stepId, e);
            containerLogStreamer.publishLog(pipelineId, String.format("ERROR: %s", e.getMessage()));
            return CompletableFuture.completedFuture(new ProcessResult(1, "ERROR"));

        } finally {
            // Cleanup container
            if (containerId != null) {
                try {
                    localDockerClient.removeContainerCmd(containerId)
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
     * Pull image if not already present.
     * Uses configured registry credentials if provided.
     */
    private void pullImageIfNeeded(DockerClient client, String pipelineId, String image) {
        String imageToCheck = image.contains(":") ? image : image + ":latest";

        try {
            // Check if exact image exists
            String repoName = imageToCheck.split(":")[0];
            List<Image> images = client.listImagesCmd()
                    .withImageNameFilter(repoName)
                    .exec();

            boolean exists = images.stream()
                    .anyMatch(img -> img.getRepoTags() != null &&
                            java.util.Arrays.asList(img.getRepoTags()).contains(imageToCheck));

            if (!exists) {
                containerLogStreamer.publishLog(pipelineId, String.format("Pulling image: %s", imageToCheck));
                log.info("Pulling Docker image: {}", imageToCheck);

                var pullCommand = client.pullImageCmd(imageToCheck);

                // Inject Registry Credentials
                if (registryUsername != null && !registryUsername.isBlank()) {
                    log.info("Pulling Docker image with registry credentials: {}", imageToCheck);
                    AuthConfig authConfig = new AuthConfig()
                            .withUsername(registryUsername)
                            .withPassword(registryPassword);
                    pullCommand.withAuthConfig(authConfig);
                }

                pullCommand.start().awaitCompletion(5, TimeUnit.MINUTES);

                containerLogStreamer.publishLog(pipelineId, "Image pulled successfully");
                log.info("Successfully pulled image: {}", imageToCheck);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ImagePullException(imageToCheck, "pull interrupted");
        } catch (Exception e) {
            log.error("Failed to pull image: {}", imageToCheck, e);
            containerLogStreamer.publishLog(pipelineId, String.format("ERROR: Failed to pull image %s: %s", imageToCheck, e.getMessage()));
            throw new ImagePullException(imageToCheck, e);
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
     * Start a container and return the container ID (without waiting for completion).
     * Binds to Traefik reverse proxy
     */
    public String startContainer(
            String pipelineId,
            String imageName, Map<String, String> envVars,
            Map<String, String> labels) {
        String containerId;
        try {
            containerLogStreamer.publishLog(pipelineId, "Starting application container: " + imageName);

            // Pull image on the remote server (using remote dockerClient)
            pullImageIfNeeded(dockerClient, pipelineId, imageName);

            // Prepare environment variables
            String[] env = envVars != null
                    ? envVars.entrySet().stream()
                    .map(e -> e.getKey() + "=" + e.getValue())
                    .toArray(String[]::new)
                    : new String[0];

            log.info("Starting container with environment variables: {}", envVars);

             // Create host config
            HostConfig hostConfig = HostConfig.newHostConfig()
                    .withMemory(memoryLimit)
                    .withMemorySwap(memoryLimit)
                    .withCpuQuota(50000L)
                    .withPublishAllPorts(true)
                    .withExtraHosts("host.docker.internal:host-gateway")
                    .withAutoRemove(false);

            CreateContainerResponse container = dockerClient.createContainerCmd(imageName)
                    .withLabels(labels)
                    .withEnv(env)
                    .withHostConfig(hostConfig)
                    .exec();

            containerId = container.getId();
            dockerClient.startContainerCmd(containerId).exec();
            
            log.info("Application container started: " + containerId.substring(0, 12));

            containerLogStreamer.publishLog(pipelineId, "Application container started: " + containerId.substring(0, 12));
            
            // Monitor logs in background
            containerLogStreamer.monitorAsync(dockerClient, pipelineId, containerId);
            
            return containerId;

        } catch (Exception e) {
            log.error("Failed to start application container", e);
            containerLogStreamer.publishLog(pipelineId, "Failed to start container: " + e.getMessage());
            throw new ContainerExecutionException(imageName, "start", e);
        }
    }





}
