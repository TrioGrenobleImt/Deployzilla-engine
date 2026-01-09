package fr.imt.deployzilla.deployzilla.configuration;

import fr.imt.deployzilla.deployzilla.exception.DockerOperationException;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.retry.support.RetryTemplate;

@Configuration
public class RetryConfiguration {
    
    @Bean
    public RetryTemplate dockerRetryTemplate() {
        return RetryTemplate.builder()
            .maxAttempts(3)
            .exponentialBackoff(1000, 2, 10000)
            .retryOn(DockerOperationException.class)
            .build();
    }
}
