package fr.imt.deployzilla.deployzilla.configuration;

import fr.imt.deployzilla.deployzilla.infrastructure.websocket.RedisPipelineStatusSubscriber;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.data.redis.listener.adapter.MessageListenerAdapter;

@Configuration
public class RedisConfiguration {

    public static final String LOGS_TOPIC = "pipeline-logs";
    public static final String PIPELINE_STATUS_TOPIC = "pipeline-status";

    @Bean
    RedisMessageListenerContainer container(RedisConnectionFactory connectionFactory,
                                            MessageListenerAdapter logListenerAdapter,
                                            MessageListenerAdapter statusListenerAdapter) {
        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(connectionFactory);
        // Subscribe to topics
        container.addMessageListener(logListenerAdapter, new ChannelTopic(LOGS_TOPIC));
        container.addMessageListener(statusListenerAdapter, new ChannelTopic(PIPELINE_STATUS_TOPIC));
        return container;
    }

    @Bean
    MessageListenerAdapter statusListenerAdapter(RedisPipelineStatusSubscriber subscriber) {
        return new MessageListenerAdapter(subscriber, "onMessage");
    }

    @Bean
    StringRedisTemplate stringRedisTemplate(RedisConnectionFactory connectionFactory) {
        return new StringRedisTemplate(connectionFactory);
    }
}