package fr.imt.deployzilla.deployzilla.infrastructure.service;

import fr.imt.deployzilla.deployzilla.business.model.ProcessResult;
import fr.imt.deployzilla.deployzilla.business.service.ContainerExecutor;
import fr.imt.deployzilla.deployzilla.business.utils.DirectorySanitizer;
import fr.imt.deployzilla.deployzilla.infrastructure.persistence.Project;
import fr.imt.deployzilla.deployzilla.infrastructure.persistence.client.SonarQubeClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

@Service
@RequiredArgsConstructor
@Slf4j
public class SonarTokenService {

    private static final String NODE_IMAGE = "deployzilla/step:npm-lint";
    private static final String CONTAINER_WORKSPACE_PATH = "/workspace";

    private final SonarQubeClient sonarQubeClient;

    private final ContainerExecutor containerExecutor;

    @Value("${deployzilla.workspace.path:/workspaces}")
    private String workspacePath;

    @Value("${sonar.web.username}")
    private String sonarUsername;

    @Value("${sonar.web.password}")
    private String sonarPassword;

    public String getSonarToken() {
        String auth = sonarUsername + ":" + sonarPassword;
        String encodedAuth = Base64.getEncoder().encodeToString(auth.getBytes(StandardCharsets.UTF_8));
        String headerValue = "Basic " + encodedAuth;

        SonarTokenResponse response = sonarQubeClient.fetchSonarTokengenerateToken(
                headerValue,
                "CI-Auto-Token-" + System.currentTimeMillis(),
                "GLOBAL_ANALYSIS_TOKEN"
        );

        return response.getToken();
    }

    public CompletableFuture<ProcessResult> runAnalysis(String pipelineId, String projectDir, String token) {
        String stepId = "sonar";
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
                Map.of("SONAR_PROJECT_KEY", token)
        );
    }

}
