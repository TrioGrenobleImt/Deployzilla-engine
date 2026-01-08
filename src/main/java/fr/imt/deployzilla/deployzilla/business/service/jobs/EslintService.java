package fr.imt.deployzilla.deployzilla.business.service.jobs;

import fr.imt.deployzilla.deployzilla.business.model.ProcessResult;
import fr.imt.deployzilla.deployzilla.business.service.ContainerExecutor;
import fr.imt.deployzilla.deployzilla.business.utils.DirectorySanitizer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

@Component
@RequiredArgsConstructor
@Slf4j
public class EslintService {

    private static final String NODE_IMAGE = "deployzilla/step:npm-lint";
    private static final String CONTAINER_WORKSPACE_PATH = "/workspace";

    private final ContainerExecutor containerExecutor;

    @Value("${deployzilla.workspace.path:/workspaces}")
    private String workspacePath;

    public CompletableFuture<ProcessResult> execute(String pipelineId, String projectDir) {
        String stepId = "eslint";
        String sanitizedDir = DirectorySanitizer.sanitizeDirectoryName(projectDir);

        String hostProjectPath = workspacePath + "/" + pipelineId + "/" + sanitizedDir;
        List<String> volumes = List.of(
                hostProjectPath + ":" + CONTAINER_WORKSPACE_PATH
        );

        log.info("Running ESLint for pipeline {} in {}", pipelineId, sanitizedDir);

        return containerExecutor.executeStep(
                pipelineId,
                stepId,
                NODE_IMAGE,
                volumes,
                Map.of("NODE_OPTIONS", "--max-old-space-size=4096")
        );
    }
}
