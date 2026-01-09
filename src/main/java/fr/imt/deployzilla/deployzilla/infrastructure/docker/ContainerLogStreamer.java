package fr.imt.deployzilla.deployzilla.infrastructure.docker;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.async.ResultCallback;
import com.github.dockerjava.api.model.Frame;
import com.github.dockerjava.api.model.StreamType;
import fr.imt.deployzilla.deployzilla.business.port.ProcessLogPublisherPort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

@Component
@RequiredArgsConstructor
@Slf4j
public class ContainerLogStreamer {

    private final ProcessLogPublisherPort logPublisher;

    @Value("${docker.timeout.seconds:600}")
    private int timeoutSeconds;

    /**
     * Stream container logs synchronously, capturing output.
     */
    public void streamLogs(DockerClient client, String pipelineId,
                           String containerId, StringBuilder outputBuffer) {
        try {
            client.logContainerCmd(containerId)
                    .withStdOut(true)
                    .withStdErr(true)
                    .withFollowStream(true)
                    .exec(new ResultCallback.Adapter<Frame>() {
                        @Override
                        public void onNext(Frame frame) {
                            String logLine = new String(frame.getPayload()).trim();
                            if (!logLine.isEmpty()) {
                                logPublisher.publish(pipelineId, logLine);
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
     * Monitor container logs asynchronously (non-blocking).
     */
    public void monitorAsync(DockerClient client, String pipelineId, String containerId) {
        CompletableFuture.runAsync(() -> {
            try {
                client.logContainerCmd(containerId)
                        .withStdOut(true)
                        .withStdErr(true)
                        .withFollowStream(true)
                        .exec(new ResultCallback.Adapter<Frame>() {
                            @Override
                            public void onNext(Frame frame) {
                                String logLine = new String(frame.getPayload()).trim();
                                if (!logLine.isEmpty()) {
                                    logPublisher.publish(pipelineId,
                                            "[" + containerId.substring(0, 8) + "] " + logLine);
                                }
                            }
                        })
                        .awaitCompletion();
            } catch (Exception e) {
                log.warn("Stopped monitoring logs for container {}", containerId);
            }
        });
    }
    public void publishLog(String pipelineId, String message) {
        logPublisher.publish(pipelineId, message);
    }
}
