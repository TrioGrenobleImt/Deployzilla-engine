package fr.imt.deployzilla.deployzilla.business.service;

import fr.imt.deployzilla.deployzilla.business.command.Command;
import fr.imt.deployzilla.deployzilla.business.command.CommandFactory;
import fr.imt.deployzilla.deployzilla.business.model.ProcessResult;
import fr.imt.deployzilla.deployzilla.infrastructure.persistence.Job;
import fr.imt.deployzilla.deployzilla.infrastructure.persistence.Pipeline;
import fr.imt.deployzilla.deployzilla.infrastructure.persistence.repository.PipelineRepository;
import fr.imt.deployzilla.deployzilla.configuration.RedisConfiguration;
import fr.imt.deployzilla.deployzilla.business.model.JobType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.data.redis.core.StringRedisTemplate;
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
    private final StringRedisTemplate redisTemplate;
    // Inject MongoTemplate for partial updates
    private final org.springframework.data.mongodb.core.MongoTemplate mongoTemplate;

    private void publishStatus(String pipelineId, String status, String currentStep) {
        try {
            // Simple protocol: pipelineId|status|currentStep
            String message = String.format("%s|%s|%s", 
                pipelineId, 
                status, 
                currentStep != null ? currentStep : "");
            redisTemplate.convertAndSend(RedisConfiguration.PIPELINE_STATUS_TOPIC, message);
        } catch (Exception e) {
            log.error("Failed to publish pipeline status", e);
        }
    }

    /**
     * Create the pipeline structure
     */
    public Pipeline createPipeline(String projectId, String commitHash, String author, String trigger) {
        Pipeline pipeline = new Pipeline();
        // Clone project job
        // pipeline.addJob(new Job(JobType.CLONE));
        // pipeline.addJob(new Job(JobType.NPM_INSTALL));
        // pipeline.addJob(new Job(JobType.NPM_LINT));
        // pipeline.addJob(new Job(JobType.NPM_TEST));
        // pipeline.addJob(new Job(JobType.SONAR));
        // pipeline.addJob(new Job(JobType.NPM_BUILD));
        pipeline.addJob(new Job(JobType.IMAGE_BUILD));
        pipeline.addJob(new Job(JobType.APP_RUN));

        pipeline.setProjectId(projectId);
        pipeline.setCommitHash(commitHash);
        pipeline.setAuthor(author);
        pipeline.setTrigger(trigger);
        
        Pipeline saved = pipelineRepository.save(pipeline);
        publishStatus(saved.getId(), "CREATED", null);
        return saved;
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
            publishStatus(pipelineId, "RUNNING", job.getJobType().getCommandName());

            Command command = commandFactory.create(job.getJobType().getCommandName(), pipeline.getProjectId(), pipelineId);

            ProcessResult result = command.execute();

            job.setEndTime(LocalDateTime.now());

            if (result.getExitCode() == 0) {
                log.info("Job {} succeeded.", job.getId());
                job.setStatus("SUCCESS");

                // Update commit hash if missing and this was the clone step
                if (JobType.CLONE.equals(job.getJobType()) &&
                        (pipeline.getCommitHash() == null || pipeline.getCommitHash().isEmpty())) {
                    String output = result.getOutput();
                    // Basic validation: Hash is typically 40 chars hex
                    if (output != null && output.length() == 40 && !output.contains(" ")) {
                        log.info("Updating pipeline {} commit hash to {}", pipelineId, output);
                        
                        // Use MongoTemplate for partial update to avoid overwriting other fields
                        mongoTemplate.updateFirst(
                            org.springframework.data.mongodb.core.query.Query.query(
                                org.springframework.data.mongodb.core.query.Criteria.where("_id").is(pipelineId)
                            ),
                            org.springframework.data.mongodb.core.query.Update.update("commitHash", output),
                            Pipeline.class
                        );
                        
                        // Update in-memory object too so subsequent saves don't revert it (though save overwrites anyway)
                        // But strictly speaking, if we just want to persist hash without touching others, 
                        // we've done it in DB. For the current flow, we keep the object in sync.
                        pipeline.setCommitHash(output);
                    }
                }
            } else {
                log.warn("Job {} failed. Exit code: {}", job.getId(), result.getExitCode());
                job.setStatus("FAILED");
                chainBroken = true;
            }

            pipelineRepository.save(pipeline);
        }

        if (!chainBroken) {
            log.info("Pipeline {} succeeded", pipeline.getId());
            pipeline.setStatus("SUCCESS");
            pipelineRepository.save(pipeline);
            publishStatus(pipelineId, "SUCCESS", null);
        } else {
            log.warn("Pipeline {} failed", pipeline.getId());
            pipeline.setStatus("FAILED");
            publishStatus(pipelineId, "FAILED", null);
        }
        pipelineRepository.save(pipeline);
    }

}
