package fr.imt.deployzilla.deployzilla.business.service;

import fr.imt.deployzilla.deployzilla.exception.ProjectNotFoundException;
import fr.imt.deployzilla.deployzilla.business.model.ProcessResult;
import fr.imt.deployzilla.deployzilla.infrastructure.persistence.Project;
import fr.imt.deployzilla.deployzilla.infrastructure.persistence.repository.ProjectRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bson.types.ObjectId;
import org.springframework.stereotype.Service;

import java.util.concurrent.ExecutionException;


@Service
@RequiredArgsConstructor
@Slf4j
public class JobService {

    private final ProjectRepository projectRepository;

    private final GitCloneService gitCloneService;
    private final EslintService eslintService;
    private final UnitTestService unitTestService;
    private final NpmInstallService npmInstallService;

    private static final String PROJECT_DIR = "/tmp/deployzilla";

    public ProcessResult cloneGitRepository(String projectId, String pipelineId) {
        Project project = projectRepository.findById(new ObjectId(projectId))
                .orElseThrow(() -> new ProjectNotFoundException(projectId));

        try {
            return gitCloneService.execute(
                    pipelineId, project, PROJECT_DIR
            ).get();
        } catch (InterruptedException | ExecutionException e) {
            log.error("Error {}", e.getMessage());
            return new ProcessResult(1, "ERROR");
        }
    }

    public ProcessResult runNpmInstall(String projectId, String pipelineId) {
        log.info("Running NPM install for pipeline: {}", pipelineId);
        try {
            return npmInstallService.execute(
                    pipelineId,
                    PROJECT_DIR
            ).get();
        } catch (InterruptedException | ExecutionException e) {
            log.error("Error {}", e.getMessage());
            return new ProcessResult(1, "ERROR");
        }
    }

    public ProcessResult runEslint(String projectId, String pipelineId) {
        // We might not strictly need projectId if the path is fixed,
        // but keeping the signature consistent with other methods if validation is needed later.
        try {
            return eslintService.execute(pipelineId, PROJECT_DIR).get();
        } catch (InterruptedException | ExecutionException e) {
            log.error("Error running eslint {}", e.getMessage());
            return new ProcessResult(1, "ERROR");
        }
    }

    public ProcessResult runUnitTests(String projectId, String pipelineId) {
        try {
            return unitTestService.execute(pipelineId, PROJECT_DIR).get();
        } catch (InterruptedException | ExecutionException e) {
            log.error("Error running unit tests {}", e.getMessage());
            return new ProcessResult(1, "ERROR");
        }
    }

}
