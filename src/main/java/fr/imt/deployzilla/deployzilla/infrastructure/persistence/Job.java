package fr.imt.deployzilla.deployzilla.infrastructure.persistence;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@NoArgsConstructor
public class Job {

    private String id;
    private String status;
    private LocalDateTime startTime;
    private LocalDateTime endTime;

    private String commandName;

    public Job(String commandName) {
        this.id = UUID.randomUUID().toString();
        this.status = "PENDING";
        this.commandName = commandName;
    }

}
