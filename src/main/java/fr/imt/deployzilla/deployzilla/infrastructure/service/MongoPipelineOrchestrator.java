package fr.imt.deployzilla.deployzilla.infrastructure.service;

import fr.imt.deployzilla.deployzilla.business.service.BashExecutor;
import fr.imt.deployzilla.deployzilla.infrastructure.ProcessResult;
import fr.imt.deployzilla.deployzilla.infrastructure.persistence.Job;
import fr.imt.deployzilla.deployzilla.infrastructure.persistence.Pipeline;
import fr.imt.deployzilla.deployzilla.infrastructure.persistence.repository.PipelineRepository;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

@Service
public class MongoPipelineOrchestrator {

    private final PipelineRepository pipelineRepository;
    private final BashExecutor bashExecutor; // Reusing your engine

    public MongoPipelineOrchestrator(PipelineRepository pipelineRepository,
                                     BashExecutor bashExecutor) {
        this.pipelineRepository = pipelineRepository;
        this.bashExecutor = bashExecutor;
    }

    /**
     * Create the pipeline structure
     */
    public Pipeline createPipeline(List<String> scripts) {
        Pipeline pipeline = new Pipeline();
        for (String script : scripts) {
            pipeline.addJob(new Job(script));
        }
        return pipelineRepository.save(pipeline);
    }

    /**
     * Execute the chain
     */
    @Async
    public void runPipeline(String pipelineId) throws ExecutionException, InterruptedException {
        // 1. Fetch the WHOLE pipeline
        Pipeline pipeline = pipelineRepository.findById(pipelineId)
                .orElseThrow(() -> new RuntimeException("Not Found"));

        pipeline.setStatus("RUNNING");
        pipelineRepository.save(pipeline);

        boolean chainBroken = false;

        // 2. Iterate through the embedded jobs
        for (Job job : pipeline.getJobs()) {
            if (chainBroken) {
                job.setStatus("SKIPPED");
                continue;
            }

            // A. Update Status to RUNNING
            job.setStartTime(LocalDateTime.now());
            job.setStatus("RUNNING");
            // We save the pipeline to persist the "RUNNING" state of this specific job
            pipelineRepository.save(pipeline);

            // B. Execute Script
            CompletableFuture<ProcessResult> result = bashExecutor.executeScript(job.getScriptName(), job.getId());

            // D. Update Job Completion Status
            job.setEndTime(LocalDateTime.now());

            if (result.get().exitCode() == 0) {
                job.setStatus("SUCCESS");
            } else {
                job.setStatus("FAILED");
                chainBroken = true;
                pipeline.setStatus("FAILED");
            }

            // Save the state after every job
            pipelineRepository.save(pipeline);
        }

        if (!chainBroken) {
            pipeline.setStatus("SUCCESS");
            pipelineRepository.save(pipeline);
        }
    }
}