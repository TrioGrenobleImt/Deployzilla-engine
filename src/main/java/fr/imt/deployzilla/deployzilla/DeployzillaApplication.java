package fr.imt.deployzilla.deployzilla;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.retry.annotation.EnableRetry;

@SpringBootApplication
@ConfigurationPropertiesScan
@EnableFeignClients(basePackages = "fr.imt.deployzilla.deployzilla")
@EnableAsync
@EnableRetry
public class DeployzillaApplication {

    public static void main(String[] args) {
        SpringApplication.run(DeployzillaApplication.class, args);
    }

}
