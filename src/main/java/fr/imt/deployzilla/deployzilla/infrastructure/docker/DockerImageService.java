package fr.imt.deployzilla.deployzilla.infrastructure.docker;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.command.BuildImageResultCallback;
import com.github.dockerjava.api.model.AuthConfig;
import com.github.dockerjava.api.model.BuildResponseItem;
import com.github.dockerjava.core.DefaultDockerClientConfig;
import com.github.dockerjava.core.DockerClientBuilder;
import com.github.dockerjava.httpclient5.ApacheDockerHttpClient;

import fr.imt.deployzilla.deployzilla.exception.ImageBuildException;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import com.github.dockerjava.api.exception.DockerException;

import java.io.File;
import java.time.Duration;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * Service responsible for Docker image build and push operations.
 * Operates exclusively on the local Docker daemon.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DockerImageService {

    private final ContainerLogStreamer containerLogStreamer;

    @Value("${docker.host:unix:///var/run/docker.sock}")
    private String dockerHost;

    @Value("${deployzilla.docker.registry.username:}")
    private String registryUsername;

    @Value("${deployzilla.docker.registry.password:}")
    private String registryPassword;

    @Value("${deployzilla.docker.registry.url:}")
    private String registryUrl;

    @Value("${docker.timeout.seconds:600}")
    private int timeoutSeconds;

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

        log.info("[DockerImageService] Initialized with Docker host: {}", dockerHost);
    }

    @PreDestroy
    public void cleanup() {
        if (dockerClient != null) {
            try {
                dockerClient.close();
            } catch (Exception e) {
                log.warn("[DockerImageService] Error closing Docker client", e);
            }
        }
    }

    /**
     * Build a Docker image from a directory.
     *
     * @param pipelineId       Pipeline identifier for logging
     * @param buildContextPath Path to the build context directory
     * @param dockerfileName   Name of the Dockerfile
     * @param imageName        Image name (e.g., "myuser/myapp")
     * @param tag              Image tag (e.g., "latest")
     * @return The built image ID
     */
    @Retryable(
        retryFor = {DockerException.class, ImageBuildException.class},
        maxAttempts = 3,
        backoff = @Backoff(delay = 2000, multiplier = 2)
    )
    public String buildImage(String pipelineId, String buildContextPath, String dockerfileName, String imageName, String tag) {
        String fullImageName = imageName + ":" + tag;
        containerLogStreamer.publishLog(pipelineId, "Starting LOCAL image build: " + fullImageName);

        try {
            var buildCmd = dockerClient.buildImageCmd(new File(buildContextPath))
                    .withDockerfile(new File(buildContextPath, dockerfileName))
                    .withTags(Set.of(fullImageName))
                    .withPlatform("linux/amd64");

            return buildCmd.exec(new BuildImageResultCallback() {
                        @Override
                        public void onNext(BuildResponseItem item) {
                            if (item.getStream() != null) {
                                containerLogStreamer.publishLog(pipelineId, item.getStream().trim());
                            }
                            super.onNext(item);
                        }
                    })
                    .awaitImageId();
        } catch (Exception e) {
            log.error("[DockerImageService] Image build failed", e);
            containerLogStreamer.publishLog(pipelineId, "Image build failed: " + e.getMessage());
            throw new ImageBuildException(fullImageName, "build", e);
        }
    }

    /**
     * Push an image to the configured registry.
     *
     * @param pipelineId Pipeline identifier for logging
     * @param imageName  Image name (e.g., "myuser/myapp")
     * @param tag        Image tag (e.g., "latest")
     */
    @Retryable(
        retryFor = {DockerException.class, ImageBuildException.class},
        maxAttempts = 3,
        backoff = @Backoff(delay = 2000, multiplier = 2)
    )
    public void pushImage(String pipelineId, String imageName, String tag) {
        String fullImageName = imageName + ":" + tag;
        containerLogStreamer.publishLog(pipelineId, "Pushing image to registry: " + fullImageName);

        try {
            var pushCmd = dockerClient.pushImageCmd(fullImageName);

            if (registryUsername != null && !registryUsername.isBlank()) {
                AuthConfig authConfig = new AuthConfig()
                        .withUsername(registryUsername)
                        .withPassword(registryPassword)
                        .withRegistryAddress(registryUrl);
                pushCmd.withAuthConfig(authConfig);
            }

            pushCmd.start().awaitCompletion(timeoutSeconds, TimeUnit.SECONDS);
            containerLogStreamer.publishLog(pipelineId, "Image pushed successfully");

        } catch (Exception e) {
            log.error("[DockerImageService] Image push failed", e);
            containerLogStreamer.publishLog(pipelineId, "Image push failed: " + e.getMessage());
            throw new ImageBuildException(fullImageName, "push", e);
        }
    }


}
