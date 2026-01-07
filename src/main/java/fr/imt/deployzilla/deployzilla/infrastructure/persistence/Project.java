package fr.imt.deployzilla.deployzilla.infrastructure.persistence;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.bson.types.ObjectId;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "projects")
public class Project {

    @Id
    private ObjectId projectId;

    private String name;

    @Builder.Default
    private String branch = "main";

    private String repoUrl;

    @Builder.Default
    private boolean autoDeploy = true;

    @Builder.Default
    private List<EnvVar> envVars = new ArrayList<>();

    @Builder.Default
    private List<ObjectId> allowedUsers = new ArrayList<>();

    @Builder.Default
    private boolean isPrivate = false;

    private String publicKey;

    private String privateKey;

    @CreatedDate
    private LocalDateTime createdAt;

    @LastModifiedDate
    private LocalDateTime updatedAt;

}
