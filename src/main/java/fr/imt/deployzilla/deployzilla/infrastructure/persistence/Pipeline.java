package fr.imt.deployzilla.deployzilla.infrastructure.persistence;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
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

    private List<Job> jobs = new ArrayList<>();

    public Pipeline() {
        this.id = UUID.randomUUID().toString();
        this.status = "PENDING";
    }

    public void addJob(Job job) {
        this.jobs.add(job);
    }

}
