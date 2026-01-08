package fr.imt.deployzilla.deployzilla.business.service;

import fr.imt.deployzilla.deployzilla.business.port.ProjectRepositoryPort;
import fr.imt.deployzilla.deployzilla.exception.ProjectNotFoundException;
import fr.imt.deployzilla.deployzilla.business.model.ProcessResult;
import fr.imt.deployzilla.deployzilla.infrastructure.persistence.Project;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

@Service
@RequiredArgsConstructor
@Slf4j
public class JobService {

    private final ProjectRepositoryPort projectRepositoryPort;

    private final GitCloneService gitCloneService;
    private final EslintService eslintService;
    private final UnitTestService unitTestService;
    private final NpmInstallService npmInstallService;
    private final SonarqubeService sonarTokenService;

    @Value("${deployzilla.workspace.path}")
    private String projectDir;

    public ProcessResult cloneGitRepository(String projectId, String pipelineId) {
        Project project = projectRepositoryPort.findById(projectId)
                .orElseThrow(() -> new ProjectNotFoundException(projectId));

        ProcessResult cloneResult = executeCompletableFuture(
                gitCloneService.execute(pipelineId, project, projectDir),
                "git clone"
        );

        try {
            if (cloneResult.getExitCode() == 0) {
                // If clone successful, try to retrieve commit hash
                String hash = gitCloneService.retrieveCommitHash(pipelineId, projectDir).get();
                if (hash != null && !hash.isEmpty()) {
                    // Return the hash as output
                    return new ProcessResult(0, hash);
                }
            }
            return cloneResult;

        } catch (InterruptedException | ExecutionException e) {
            log.error("Error running step git clone {}", e.getMessage());
            return new ProcessResult(1, "ERROR");
        }

    }

    public ProcessResult runNpmInstall(String pipelineId) {
        return executeCompletableFuture(
                npmInstallService.execute(pipelineId, projectDir),
                "npm install"
        );
    }

    public ProcessResult runEslint(String pipelineId) {
        return executeCompletableFuture(
                eslintService.execute(pipelineId, projectDir),
                "eslint"
        );
    }

    public ProcessResult runUnitTests(String pipelineId) {
        return executeCompletableFuture(
                unitTestService.execute(pipelineId, projectDir),
                "unit tests");
    }

    public ProcessResult runSonarAnalysis(String pipelineId) {
        String token = sonarTokenService.getSonarToken();
        return executeCompletableFuture(
                sonarTokenService.runAnalysis(pipelineId, projectDir, token),
                "sonarQube"
        );
    }

    private ProcessResult executeCompletableFuture(CompletableFuture<ProcessResult> completableFuture, String stepName) {
        try {
            return completableFuture.get();
        } catch (InterruptedException | ExecutionException e) {
            log.error("Error running step {} {}", stepName, e.getMessage());
            return new ProcessResult(1, "ERROR");
        }
    }

}
