package fr.imt.deployzilla.deployzilla.infrastructure.redis;

import fr.imt.deployzilla.deployzilla.business.port.ProcessLogPublisherPort;
import fr.imt.deployzilla.deployzilla.configuration.RedisConfiguration;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class RedisProcessLogPublisherAdapter implements ProcessLogPublisherPort {

    private final StringRedisTemplate redisTemplate;

    @Override
    public void publish(String pipelineId, String message) {
        String payload = pipelineId + "|" + message;
        redisTemplate.convertAndSend(RedisConfiguration.LOGS_TOPIC, payload);
    }
}
