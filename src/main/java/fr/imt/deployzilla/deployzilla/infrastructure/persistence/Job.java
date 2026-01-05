package fr.imt.deployzilla.deployzilla.infrastructure.persistence;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Data
public class Job {

    private String id;
    private String scriptName;
    private String status;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private List<String> bashArguments;

    private String logId;

    public Job(String scriptName, List<String> arguments) {
        this.id = UUID.randomUUID().toString();
        this.scriptName = scriptName;
        this.bashArguments = arguments != null ? arguments : new ArrayList<>();
        this.status = "PENDING";
    }

}
