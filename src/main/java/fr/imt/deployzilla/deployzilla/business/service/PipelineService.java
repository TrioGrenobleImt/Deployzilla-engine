package fr.imt.deployzilla.deployzilla.business.service;

import fr.imt.deployzilla.deployzilla.business.command.Command;
import fr.imt.deployzilla.deployzilla.business.command.CommandFactory;
import fr.imt.deployzilla.deployzilla.business.model.ProcessResult;
import fr.imt.deployzilla.deployzilla.infrastructure.persistence.Job;
import fr.imt.deployzilla.deployzilla.infrastructure.persistence.Pipeline;
import fr.imt.deployzilla.deployzilla.infrastructure.persistence.repository.PipelineRepository;
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

    private final PipelineRepository pipelineRepository;
    private final CommandFactory commandFactory;

    /**
     * Create the pipeline structure
     */
    public Pipeline createPipeline(String projectId, String commitHash, String author) {
        Pipeline pipeline = new Pipeline();
        // Clone project job
        pipeline.addJob(new Job("CLONE"));
        pipeline.setProjectId(projectId);
        pipeline.setCommitHash(commitHash);
        pipeline.setAuthor(author);
        return pipelineRepository.save(pipeline);
    }

    /**
     * Execute the chain
     */
    @Async
    public void runPipeline(String pipelineId) {
        Pipeline pipeline = pipelineRepository.findById(pipelineId)
                .orElseThrow(() -> new RuntimeException("Not Found"));

        pipeline.setStatus("RUNNING");
        log.info("Pipeline {} started", pipeline.getId());
        pipelineRepository.save(pipeline);

        boolean chainBroken = false;

        Iterator<Job> iterator = pipeline.getJobs().iterator();
        while (iterator.hasNext() && !chainBroken) {
            Job job = iterator.next();

            log.info("Job {} running.", job.getId());

            job.setStartTime(LocalDateTime.now());
            job.setStatus("RUNNING");

            pipelineRepository.save(pipeline);

            Command command = commandFactory.create(job.getCommandName(), pipeline.getProjectId(), pipelineId);

            ProcessResult result = command.execute();

            job.setEndTime(LocalDateTime.now());

            if (result.getExitCode() == 0) {
                log.info("Job {} succeeded.", job.getId());
                job.setStatus("SUCCESS");
            } else {
                log.info("Job {} failed.", job.getId());
                job.setStatus("FAILED");
                chainBroken = true;
            }

            pipelineRepository.save(pipeline);
        }

        if (!chainBroken) {
            log.info("Pipeline {} succeeded", pipeline.getId());
            pipeline.setStatus("SUCCESS");
            pipelineRepository.save(pipeline);
        }
        log.warn("Pipeline {} failed", pipeline.getId());
        pipeline.setStatus("FAILED");
    }

}
