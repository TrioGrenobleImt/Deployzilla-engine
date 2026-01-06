package fr.imt.deployzilla.deployzilla.infrastructure.persistence;

import lombok.Value;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Value
@Document(collection = "projects")
public class Project {

    @Id
    String projectId;

    String name;

    String branch;

    String repoUrl;

}
