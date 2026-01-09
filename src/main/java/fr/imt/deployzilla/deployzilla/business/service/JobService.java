package fr.imt.deployzilla.deployzilla.business.service;

import fr.imt.deployzilla.deployzilla.business.port.ProjectRepositoryPort;
import fr.imt.deployzilla.deployzilla.exception.ProjectNotFoundException;
import fr.imt.deployzilla.deployzilla.business.model.ProcessResult;
import fr.imt.deployzilla.deployzilla.business.service.jobs.NpmInstallService;
import fr.imt.deployzilla.deployzilla.business.service.jobs.UnitTestService;
import fr.imt.deployzilla.deployzilla.business.service.jobs.EslintService;
import fr.imt.deployzilla.deployzilla.business.service.jobs.GitCloneService;
import fr.imt.deployzilla.deployzilla.business.service.jobs.NpmBuildService;
import fr.imt.deployzilla.deployzilla.business.service.jobs.ImageBuildService;
import fr.imt.deployzilla.deployzilla.business.service.jobs.AppRunService;
import fr.imt.deployzilla.deployzilla.infrastructure.persistence.EnvVar;
import fr.imt.deployzilla.deployzilla.infrastructure.persistence.Project;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.Map;
import java.util.stream.Collectors;

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
    private final NpmBuildService npmBuildService;
    private final ImageBuildService imageBuildService;
    private final AppRunService appRunService;

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

    public ProcessResult runNpmBuild(String pipelineId) {
        log.info("Running NPM build for pipeline: {}", pipelineId);
        try {
            return npmBuildService.execute(
                    pipelineId,
                    projectDir
            ).get();
        } catch (InterruptedException | ExecutionException e) {
            log.error("Error {}", e.getMessage());
            return new ProcessResult(1, "ERROR");
        }
    }

    public ProcessResult createImage(String projectId, String pipelineId) {
        log.info("Creating image for pipeline: {}", pipelineId);
        Project project = projectRepositoryPort.findById(projectId)
                .orElseThrow(() -> new ProjectNotFoundException(projectId));
        
        try {
            return imageBuildService.execute(
                    pipelineId,
                    projectDir,
                    project.getRepoUrl()
            ).get();
        } catch (InterruptedException | ExecutionException e) {
            log.error("Error {}", e.getMessage());
            return new ProcessResult(1, "ERROR");
        }
    }

    public ProcessResult runApp(String projectId, String pipelineId) {
        log.info("Running app for pipeline: {}", pipelineId);
        Project project = projectRepositoryPort.findById(projectId)
                .orElseThrow(() -> new ProjectNotFoundException(projectId));

        Map<String, String> envVars = project.getEnvVars().stream()
                .collect(Collectors.toMap(EnvVar::getKey, EnvVar::getValue));

        log.info("Running app with environment variables: {}", envVars.toString());

        try {
            return appRunService.execute(pipelineId, envVars).get();
        } catch (InterruptedException | ExecutionException e) {
            log.error("Error {}", e.getMessage());
            return new ProcessResult(1, "ERROR");
        }
    }

}
