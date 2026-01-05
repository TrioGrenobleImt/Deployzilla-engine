package fr.imt.deployzilla.deployzilla.business.service;

import fr.imt.deployzilla.deployzilla.configuration.RedisConfiguration;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.file.Path;

@Service
@RequiredArgsConstructor
public class BashExecutor {

    private final StringRedisTemplate redisTemplate;

    @Async
    public void executeScript(String pipelineId, String scriptName) {
        try {
            // We publish a JSON structure or a formatted string so the subscriber knows
            // WHICH pipeline this log belongs to.
            // Format: "pipelineId|logMessage"
            publishLog(pipelineId, "--- Pipeline Started ---");

            Path scriptPath = new ClassPathResource("scripts/" + scriptName).getFile().toPath();
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

        } catch (Exception e) {
            publishLog(pipelineId, "ERROR: " + e.getMessage());
        }
    }

    private void publishLog(String pipelineId, String message) {
        // Simple protocol: "ID|MESSAGE"
        String payload = pipelineId + "|" + message;
        redisTemplate.convertAndSend(RedisConfiguration.LOGS_TOPIC, payload);
    }
}