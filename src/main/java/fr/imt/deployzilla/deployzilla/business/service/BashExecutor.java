package fr.imt.deployzilla.deployzilla.business.service;

import fr.imt.deployzilla.deployzilla.configuration.RedisConfiguration;
import fr.imt.deployzilla.deployzilla.infrastructure.ProcessResult;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;

@Service
@RequiredArgsConstructor
public class BashExecutor {

    private final StringRedisTemplate redisTemplate;

    @Async
    public CompletableFuture<ProcessResult> executeScript(String pipelineId, String scriptName) {
        try {
            publishLog(pipelineId, "--- Pipeline Started ---");

            Path scriptPath;
            Path externalPath = Path.of("/app/scripts", scriptName);
            if (externalPath.toFile().exists()) {
                scriptPath = externalPath;
            } else {
                scriptPath = new ClassPathResource("scripts/" + scriptName).getFile().toPath();
            }
            
            ProcessBuilder pb = new ProcessBuilder("bash", scriptPath.toString());
            pb.redirectErrorStream(true);

            Process process = pb.start();

            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    publishLog(pipelineId, line);
                }
            }

            int exitCode = process.waitFor();
            publishLog(pipelineId, "--- Finished (Exit: " + exitCode + ") ---");
            return CompletableFuture.completedFuture(new ProcessResult(0, "SUCCESS"));


        } catch (Exception e) {
            publishLog(pipelineId, "ERROR: " + e.getMessage());
            return CompletableFuture.completedFuture(new ProcessResult(1, "ERROR"));
        }
    }

    private void publishLog(String pipelineId, String message) {
        String payload = pipelineId + "|" + message;
        redisTemplate.convertAndSend(RedisConfiguration.LOGS_TOPIC, payload);
    }
}