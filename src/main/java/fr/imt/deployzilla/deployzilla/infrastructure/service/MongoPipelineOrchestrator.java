package fr.imt.deployzilla.deployzilla.infrastructure.service;

import fr.imt.deployzilla.deployzilla.business.service.BashExecutor;
import fr.imt.deployzilla.deployzilla.infrastructure.ProcessResult;
import fr.imt.deployzilla.deployzilla.infrastructure.persistence.Job;
import fr.imt.deployzilla.deployzilla.infrastructure.persistence.Pipeline;
import fr.imt.deployzilla.deployzilla.infrastructure.persistence.repository.PipelineRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

@Service
@RequiredArgsConstructor
public class MongoPipelineOrchestrator {

    private final PipelineRepository pipelineRepository;
    private final BashExecutor bashExecutor;

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
        Pipeline pipeline = pipelineRepository.findById(pipelineId)
                .orElseThrow(() -> new RuntimeException("Not Found"));

        pipeline.setStatus("RUNNING");
        pipelineRepository.save(pipeline);

        boolean chainBroken = false;

        for (Job job : pipeline.getJobs()) {
            if (chainBroken) {
                job.setStatus("SKIPPED");
                continue;
            }

            job.setStartTime(LocalDateTime.now());
            job.setStatus("RUNNING");

            pipelineRepository.save(pipeline);

            CompletableFuture<ProcessResult> result = bashExecutor.executeScript(job.getId(), job.getScriptName());

            job.setEndTime(LocalDateTime.now());

            if (result.get().exitCode() == 0) {
                job.setStatus("SUCCESS");
            } else {
                job.setStatus("FAILED");
                chainBroken = true;
                pipeline.setStatus("FAILED");
            }

            pipelineRepository.save(pipeline);
        }

        if (!chainBroken) {
            pipeline.setStatus("SUCCESS");
            pipelineRepository.save(pipeline);
        }
    }
}