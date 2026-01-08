package fr.imt.deployzilla.deployzilla;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.data.mongodb.repository.config.EnableMongoRepositories;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@ConfigurationPropertiesScan
@EnableFeignClients(basePackages = "fr.imt.deployzilla.deployzilla")
@EnableAsync
public class DeployzillaApplication {

    public static void main(String[] args) {
        SpringApplication.run(DeployzillaApplication.class, args);
    }

}
