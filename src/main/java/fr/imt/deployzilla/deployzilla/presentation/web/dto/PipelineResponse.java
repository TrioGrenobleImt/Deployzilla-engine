package fr.imt.deployzilla.deployzilla.presentation.web.dto;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
public class PipelineResponse {
    private String id;
    private String projectId;
    private String status;
    private String commitHash;
    private String author;
    private List<JobResponse> jobs;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
