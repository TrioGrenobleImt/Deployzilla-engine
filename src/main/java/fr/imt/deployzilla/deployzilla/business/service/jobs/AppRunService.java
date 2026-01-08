package fr.imt.deployzilla.deployzilla.business.service.jobs;

import fr.imt.deployzilla.deployzilla.business.model.ProcessResult;
import fr.imt.deployzilla.deployzilla.business.port.PipelineRepositoryPort;
import fr.imt.deployzilla.deployzilla.business.port.ProjectRepositoryPort;
import fr.imt.deployzilla.deployzilla.business.service.ContainerExecutor;
import fr.imt.deployzilla.deployzilla.infrastructure.persistence.Pipeline;
import fr.imt.deployzilla.deployzilla.infrastructure.persistence.Project;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static fr.imt.deployzilla.deployzilla.business.utils.Constants.*;

@Component
@RequiredArgsConstructor
@Slf4j
public class AppRunService {

    private final ContainerExecutor containerExecutor;
    private final PipelineRepositoryPort pipelineRepositoryPort;
    private final ProjectRepositoryPort projectRepositoryPort;

    public CompletableFuture<ProcessResult> execute(String pipelineId, Map<String, String> envVars) {
        String imageName = "deployzilla-app-" + pipelineId + ":latest";

        log.info("Running application {} for pipeline {}", imageName, pipelineId);

        try {
            String containerId = containerExecutor.startContainer(
                    pipelineId,
                    imageName,
                    envVars,
                    getLabelsMap(pipelineId, extractProjectNameFromPipelineId(pipelineId)));

            return CompletableFuture.completedFuture(new ProcessResult(0, "Container ID: " + containerId));
        } catch (Exception e) {
            log.error("Run application failed", e);
            return CompletableFuture.completedFuture(new ProcessResult(1, "ERROR"));
        }
    }

    private static Map<String, String> getLabelsMap(String pipelineId, String projectName) {
        String appUrl = String.format("%s.%s", projectName, BASE_DOMAIN);
        // Prepare labels for ownership tracking
        // Router Rule (Host)
        // Connect to the HTTPS EntryPoint we defined in Traefik
        // Enable TLS
        // Use the Cert Resolver defined in Traefik command (--certificatesresolvers.myresolver)
        // Point to the container port
        return Map.of(
                MANAGED_LABEL, "true",
                PIPELINE_LABEL, pipelineId,
                "deployzilla.type", "app",
                "traefik.enable", "true",

                // Router Rule (Host)
                "traefik.http.routers." + projectName + ".rule", "Host(`" + appUrl + "`)",
                // Connect to the HTTPS EntryPoint we defined in Traefik
                "traefik.http.routers." + projectName + ".entrypoints", "websecure",
                // Enable TLS
                "traefik.http.routers." + projectName + ".tls", "true",
                // Use the Cert Resolver defined in Traefik command (--certificatesresolvers.myresolver)
                "traefik.http.routers." + projectName + ".tls.certresolver", "myresolver",
                // Point to the container port
                "traefik.http.services." + projectName + ".loadbalancer.server.port", "8080"
        );
    }

    public String extractProjectNameFromPipelineId(String pipelineId) {
        // Extract project name from pipelineId
        Pipeline pipeline = pipelineRepositoryPort.findById(pipelineId)
                .orElseThrow(() -> new RuntimeException("Pipeline not found"));
        Project project = projectRepositoryPort.findById(pipeline.getProjectId())
                .orElseThrow(() -> new RuntimeException("Project not found"));
        return project.getName().replaceAll(" ", "").toLowerCase();
    }

}
