package fr.imt.deployzilla.deployzilla.infrastructure.persistence;

import lombok.Data;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;
import java.util.UUID;
import java.util.List;
import java.util.ArrayList;

@Document(collection = "pipelines")
@Data
public class Pipeline {

    @Id
    private String id;

    private String projectId;

    private String status;

    private String commitHash;

    private String author;

    private List<Job> jobs = new ArrayList<>();

    @CreatedDate
    private LocalDateTime createdAt;

    @LastModifiedDate
    private LocalDateTime updatedAt;

    public Pipeline() {
        this.id = UUID.randomUUID().toString();
        this.status = "CREATED";
        this.createdAt = LocalDateTime.now();
    }

    public void addJob(Job job) {
        this.jobs.add(job);
    }

}
