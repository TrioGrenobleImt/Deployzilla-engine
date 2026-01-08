package fr.imt.deployzilla.deployzilla.infrastructure.redis;

import fr.imt.deployzilla.deployzilla.business.port.PipelineStatusPublisherPort;
import fr.imt.deployzilla.deployzilla.configuration.RedisConfiguration;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

@Component
@Slf4j
@RequiredArgsConstructor
public class RedisPipelineStatusPublisherAdapter implements PipelineStatusPublisherPort {

    private final StringRedisTemplate redisTemplate;

    @Override
    public void publish(String pipelineId, String status, String currentStep) {
        try {
            String message = String.format("%s|%s|%s",
                    pipelineId,
                    status,
                    currentStep != null ? currentStep : "");
            redisTemplate.convertAndSend(RedisConfiguration.PIPELINE_STATUS_TOPIC, message);
        } catch (Exception e) {
            log.error("Failed to publish pipeline status for pipeline {}", pipelineId, e);
        }
    }
}
