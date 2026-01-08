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
import fr.imt.deployzilla.deployzilla.configuration.RedisConfiguration;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

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

    private final StringRedisTemplate redisTemplate;

    @Value("${docker.host:unix:///var/run/docker.sock}")
    private String dockerHost;

    @Value("${docker.timeout.seconds:600}")
    private int timeoutSeconds;

    @Value("${docker.memory.limit:2147483648}") // 2GB default
    private long memoryLimit;

    private DockerClient dockerClient;

    @PostConstruct
    public void init() {
        DefaultDockerClientConfig config = DefaultDockerClientConfig.createDefaultConfigBuilder()
                .withDockerHost(dockerHost)
                .build();

        ApacheDockerHttpClient httpClient = new ApacheDockerHttpClient.Builder()
                .dockerHost(config.getDockerHost())
                .connectionTimeout(Duration.ofSeconds(30))
                .responseTimeout(Duration.ofSeconds(timeoutSeconds))
                .build();

        this.dockerClient = DockerClientBuilder.getInstance(config)
                .withDockerHttpClient(httpClient)
                .build();

        log.info("Docker client initialized with host: {}", dockerHost);
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
     * @param command    Command to execute
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

            // Pull image if not present
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
                    .withBinds(binds)
                    .withAutoRemove(false);      // We'll remove manually after logs

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

            // Stream logs to Redis and capture output
            StringBuilder capturedOutput = new StringBuilder();
            streamLogs(pipelineId, containerId, capturedOutput);

            // Wait for completion with timeout
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
     * Always pulls if the image uses a specific tag to ensure we have the correct version.
     */
    /**
     * Pull image if not already present locally.
     * Always pulls if the image uses a specific tag to ensure we have the correct version.
     */
    private void pullImageIfNeeded(String pipelineId, String image) {
        // Normalize image name to always have a tag for comparison
        String imageToCheck = image.contains(":") ? image : image + ":latest";

        try {
            // Check if exact image exists locally
            // We strip the tag for the filter, then check exact tag match in results
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
                
                dockerClient.pullImageCmd(imageToCheck)
                        .start()
                        .awaitCompletion(5, TimeUnit.MINUTES);
                
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

    private void publishLog(String pipelineId, String message) {
        String payload = pipelineId + "|" + message;
        redisTemplate.convertAndSend(RedisConfiguration.LOGS_TOPIC, payload);
    }
}
