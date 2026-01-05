package fr.imt.deployzilla.deployzilla.infrastructure.persistence;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
public class Job {

    private String id;
    private String scriptName;
    private String status;
    private LocalDateTime startTime;
    private LocalDateTime endTime;

    private String logId;

    public Job(String scriptName) {
        this.id = UUID.randomUUID().toString();
        this.scriptName = scriptName;
        this.status = "PENDING";
    }

}
