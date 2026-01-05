package fr.imt.deployzilla.deployzilla.infrastructure.persistence;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
public class Job {

    private String id; // UUID generated at creation
    private String scriptName;
    private String status; // PENDING, RUNNING, SUCCESS, FAILED
    private LocalDateTime startTime;
    private LocalDateTime endTime;

    // We store a reference to the log document, not the log itself
    private String logId;

    public Job(String scriptName) {
        this.id = UUID.randomUUID().toString();
        this.scriptName = scriptName;
        this.status = "PENDING";
    }

}
