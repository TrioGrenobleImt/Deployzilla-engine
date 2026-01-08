package fr.imt.deployzilla.deployzilla.business.command;

import fr.imt.deployzilla.deployzilla.business.model.ProcessResult;
import fr.imt.deployzilla.deployzilla.business.service.JobService;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class CreateImageCommand implements Command {

    private final JobService jobService;

    private final String pipelineId;
    private final String projectId;

    @Override
    public ProcessResult execute() {
        return jobService.createImage(projectId, pipelineId);
    }

}
