package fr.imt.deployzilla.deployzilla.presentation.web.dto;

import fr.imt.deployzilla.deployzilla.business.model.JobType;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class JobResponse {
    private String id;
    private String status;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private JobType jobType;
}
