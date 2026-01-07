package fr.imt.deployzilla.deployzilla.business.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PipelineStatusUpdate {
    private String pipelineId;
    private String status;
    private String currentStep;
}
