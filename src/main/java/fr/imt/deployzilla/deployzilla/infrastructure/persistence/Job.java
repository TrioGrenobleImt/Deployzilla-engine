package fr.imt.deployzilla.deployzilla.infrastructure.persistence;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

import fr.imt.deployzilla.deployzilla.business.model.JobType;

@Data
@NoArgsConstructor
public class Job {

    private String id;
    private String status;
    private LocalDateTime startTime;
    private LocalDateTime endTime;

    private JobType jobType;

    public Job(JobType jobType) {
        this.id = UUID.randomUUID().toString();
        this.status = "PENDING";
        this.jobType = jobType;
    }

}
