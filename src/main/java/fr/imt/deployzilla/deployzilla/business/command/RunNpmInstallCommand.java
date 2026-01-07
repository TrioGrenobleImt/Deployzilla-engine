package fr.imt.deployzilla.deployzilla.business.command;

import fr.imt.deployzilla.deployzilla.business.model.ProcessResult;
import fr.imt.deployzilla.deployzilla.business.service.JobService;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class RunNpmInstallCommand implements Command {

    private final JobService jobService;

    private final String projectId;

    private final String pipelineId;

    @Override
    public ProcessResult execute() {
        return jobService.runNpmInstall(projectId, pipelineId);
    }

}
