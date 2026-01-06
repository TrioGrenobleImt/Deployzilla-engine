package fr.imt.deployzilla.deployzilla.business.service;

import fr.imt.deployzilla.deployzilla.business.exception.ProjectNotFoundException;
import fr.imt.deployzilla.deployzilla.business.model.ProcessResult;
import fr.imt.deployzilla.deployzilla.infrastructure.persistence.Project;
import fr.imt.deployzilla.deployzilla.infrastructure.persistence.repository.ProjectRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.concurrent.ExecutionException;


@Service
@RequiredArgsConstructor
@Slf4j
public class JobService {

    private final ProjectRepository projectRepository;

    private final GitCloneService gitCloneService;

    public ProcessResult cloneGitRepository(String projectId, String pipelineId) {
        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new ProjectNotFoundException(String.format("No project found for id %s", projectId)));

        try {
            return gitCloneService.execute(
                    pipelineId, project, "/tmp/deployzilla"
            ).get();
        } catch (InterruptedException | ExecutionException e) {
            log.error("Error {}", e.getMessage());
            return new ProcessResult(1, "ERROR");
        }

    }

}
