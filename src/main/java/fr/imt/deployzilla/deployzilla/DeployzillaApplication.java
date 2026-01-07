package fr.imt.deployzilla.deployzilla;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.data.mongodb.config.EnableMongoAuditing;
import org.springframework.data.mongodb.repository.config.EnableMongoRepositories;
import lombok.Data;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.mongodb.core.mapping.Document;

@SpringBootApplication
@ConfigurationPropertiesScan
@EnableMongoRepositories
@EnableMongoAuditing
public class DeployzillaApplication {

    public static void main(String[] args) {
        SpringApplication.run(DeployzillaApplication.class, args);
    }

}
