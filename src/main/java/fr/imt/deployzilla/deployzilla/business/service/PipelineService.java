package fr.imt.deployzilla.deployzilla.business.service;

import fr.imt.deployzilla.deployzilla.business.command.Command;
import fr.imt.deployzilla.deployzilla.business.command.CommandFactory;
import fr.imt.deployzilla.deployzilla.business.model.JobType;
import fr.imt.deployzilla.deployzilla.business.model.ProcessResult;
import fr.imt.deployzilla.deployzilla.business.port.PipelineRepositoryPort;
import fr.imt.deployzilla.deployzilla.business.port.PipelineStatusPublisherPort;
import fr.imt.deployzilla.deployzilla.infrastructure.persistence.Job;
import fr.imt.deployzilla.deployzilla.infrastructure.persistence.Pipeline;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Iterator;

@Service
@Slf4j
@RequiredArgsConstructor
public class PipelineService {

    private final PipelineRepositoryPort pipelineRepositoryPort;
    private final PipelineStatusPublisherPort pipelineStatusPublisherPort;
    private final CommandFactory commandFactory;

    private void publishStatus(String pipelineId, String status, String currentStep) {
        pipelineStatusPublisherPort.publish(pipelineId, status, currentStep);
    }

    /**
     * Create the pipeline structure
     */
    public Pipeline createPipeline(String projectId, String commitHash, String author) {
        Pipeline pipeline = new Pipeline();
        // Clone project job
        pipeline.addJob(new Job(JobType.CLONE));
        pipeline.addJob(new Job(JobType.NPM_INSTALL));
        pipeline.addJob(new Job(JobType.NPM_LINT));
        pipeline.addJob(new Job(JobType.NPM_TEST));
        pipeline.addJob(new Job(JobType.SONAR));
        pipeline.setProjectId(projectId);
        pipeline.setCommitHash(commitHash);
        pipeline.setAuthor(author);

        if (commitHash == null || commitHash.isEmpty()) {
            pipeline.setTrigger("manual");
        }

        Pipeline saved = pipelineRepositoryPort.save(pipeline);
        publishStatus(saved.getId(), "CREATED", null);
        return saved;
    }

    /**
     * Execute the chain
     */
    @Async
    public void runPipeline(String pipelineId) {
        Pipeline pipeline = pipelineRepositoryPort.findById(pipelineId)
                .orElseThrow(() -> new RuntimeException("Pipeline not found: " + pipelineId));

        initializePipelineRun(pipeline);

        boolean success = executeJobs(pipeline);

        finalizePipelineRun(pipeline, success);
    }

    private void initializePipelineRun(Pipeline pipeline) {
        pipeline.setStatus("RUNNING");
        pipelineRepositoryPort.save(pipeline);
        log.info("Pipeline {} started", pipeline.getId());
        publishStatus(pipeline.getId(), "RUNNING", null);
    }

    private boolean executeJobs(Pipeline pipeline) {
        for (Job job : pipeline.getJobs()) {
            ProcessResult result = executeJob(job, pipeline);
            if (result.getExitCode() != 0) {
                handleFailedJob(job, result, pipeline);
                return false; // Stop execution
            }
            handleSuccessfulJob(job, result, pipeline);
        }
        return true;
    }

    private ProcessResult executeJob(Job job, Pipeline pipeline) {
        log.info("Job {} of type {} running.", job.getId(), job.getJobType());
        job.setStartTime(LocalDateTime.now());
        job.setStatus("RUNNING");
        pipelineRepositoryPort.save(pipeline);
        publishStatus(pipeline.getId(), "RUNNING", job.getJobType().getCommandName());

        Command command = commandFactory.create(job.getJobType().getCommandName(), pipeline.getProjectId(), pipeline.getId());
        ProcessResult result = command.execute();

        job.setEndTime(LocalDateTime.now());
        return result;
    }

    private void handleSuccessfulJob(Job job, ProcessResult result, Pipeline pipeline) {
        log.info("Job {} succeeded.", job.getId());
        job.setStatus("SUCCESS");

        if (isCloneJobWithMissingHash(job, pipeline)) {
            updateCommitHashFromClone(result.getOutput(), pipeline);
        }

        pipelineRepositoryPort.save(pipeline);
    }

    private void handleFailedJob(Job job, ProcessResult result, Pipeline pipeline) {
        log.warn("Job {} failed. Exit code: {}", job.getId(), result.getExitCode());
        job.setStatus("FAILED");
        pipelineRepositoryPort.save(pipeline);
    }

    private void finalizePipelineRun(Pipeline pipeline, boolean success) {
        if (success) {
            pipeline.setStatus("SUCCESS");
            log.info("Pipeline {} succeeded", pipeline.getId());
            publishStatus(pipeline.getId(), "SUCCESS", null);
        } else {
            pipeline.setStatus("FAILED");
            log.warn("Pipeline {} failed", pipeline.getId());
            publishStatus(pipeline.getId(), "FAILED", null);
        }
        pipelineRepositoryPort.save(pipeline);
    }

    private boolean isCloneJobWithMissingHash(Job job, Pipeline pipeline) {
        return JobType.CLONE.equals(job.getJobType()) &&
                (pipeline.getCommitHash() == null || pipeline.getCommitHash().isEmpty());
    }

    private void updateCommitHashFromClone(String output, Pipeline pipeline) {
        // Basic validation: Hash is typically 40 chars hex
        if (output != null && output.length() == 40 && !output.contains(" ")) {
            log.info("Updating pipeline {} commit hash to {}", pipeline.getId(), output);
            pipelineRepositoryPort.updateCommitHash(pipeline.getId(), output);
            pipeline.setCommitHash(output); // Update in-memory object
        }
    }

}
