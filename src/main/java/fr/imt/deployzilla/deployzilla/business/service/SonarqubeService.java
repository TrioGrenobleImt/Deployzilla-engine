package fr.imt.deployzilla.deployzilla.business.service;

import fr.imt.deployzilla.deployzilla.business.model.ProcessResult;
import fr.imt.deployzilla.deployzilla.business.utils.DirectorySanitizer;
import fr.imt.deployzilla.deployzilla.infrastructure.client.SonarQubeClient;
import fr.imt.deployzilla.deployzilla.business.model.SonarTokenResponse;
import fr.imt.deployzilla.deployzilla.infrastructure.persistence.Pipeline;
import fr.imt.deployzilla.deployzilla.infrastructure.persistence.repository.PipelineRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

@Service
@RequiredArgsConstructor
@Slf4j
public class SonarqubeService {

    private static final String NODE_IMAGE = "deployzilla/step:sonarqube";
    private static final String CONTAINER_WORKSPACE_PATH = "/workspace";

    private final SonarQubeClient sonarQubeClient;

    private final ContainerExecutor containerExecutor;

    @Value("${deployzilla.workspace.path:/workspaces}")
    private String workspacePath;

    @Value("${sonar.web.username}")
    private String sonarUsername;

    @Value("${sonar.web.password}")
    private String sonarPassword;

    @Value("${sonar.url}")
    private String sonarUrl;

    private final PipelineRepository pipelineRepository;

    public String getSonarToken() {
        String auth = sonarUsername + ":" + sonarPassword;
        String encodedAuth = Base64.getEncoder().encodeToString(auth.getBytes(StandardCharsets.UTF_8));
        String headerValue = "Basic " + encodedAuth;

        SonarTokenResponse response = sonarQubeClient.fetchSonarTokengenerateToken(
                headerValue,
                "CI-Auto-Token-" + System.currentTimeMillis(),
                "GLOBAL_ANALYSIS_TOKEN"
        );
        log.info("Generating token for sonar user: {}. Token is {}", sonarUsername, response.getToken());
        return response.getToken();
    }

    public CompletableFuture<ProcessResult> runAnalysis(String pipelineId, String projectDir, String token) {
        String stepId = "sonar";
        String sanitizedDir = DirectorySanitizer.sanitizeDirectoryName(projectDir);

        String hostProjectPath = workspacePath + "/" + pipelineId + "/" + sanitizedDir;
        List<String> volumes = List.of(
                hostProjectPath + ":" + CONTAINER_WORKSPACE_PATH
        );

        // Using
        Pipeline pipeline = pipelineRepository.findById(pipelineId)
                .orElseThrow(() -> new RuntimeException("Pipeline not found"));
        String projectId = pipeline.getProjectId();

        log.info("Running ESLint for pipeline {} in {}", pipelineId, sanitizedDir);

        return containerExecutor.executeStep(
                pipelineId,
                stepId,
                NODE_IMAGE,
                volumes,
                Map.of("SONAR_TOKEN", token,
                        "SONAR_PROJECT_KEY", projectId,
                        "SONAR_HOST_URL", sonarUrl,
                        "SONAE_ARGS", "-Dsonar.javascript.lcov.reportPaths=coverage/lcov.info"
                )
        );
    }

}
