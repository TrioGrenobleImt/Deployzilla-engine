package fr.imt.deployzilla.deployzilla.business.service.jobs;

import fr.imt.deployzilla.deployzilla.business.model.ProcessResult;
import fr.imt.deployzilla.deployzilla.business.service.ContainerExecutor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

@Component
@RequiredArgsConstructor
@Slf4j
public class AppRunService {

    private final ContainerExecutor containerExecutor;

    public CompletableFuture<ProcessResult> execute(String pipelineId, Map<String, String> envVars) {
        String imageName = "deployzilla-app-" + pipelineId + ":latest";

        log.info("Running application {} for pipeline {}", imageName, pipelineId);

        try {
            String containerId = containerExecutor.startContainer(pipelineId, imageName, envVars);
            // We consider the job successful if the container started successfully.
            // In a real scenario we might wait for a health check.
            return CompletableFuture.completedFuture(new ProcessResult(0, "Container ID: " + containerId));
        } catch (Exception e) {
            log.error("Run application failed", e);
            return CompletableFuture.completedFuture(new ProcessResult(1, "ERROR"));
        }
    }
}
