package fr.imt.deployzilla.deployzilla.presentation.web;

import fr.imt.deployzilla.deployzilla.business.service.PipelineService;
import fr.imt.deployzilla.deployzilla.infrastructure.persistence.Pipeline;
import fr.imt.deployzilla.deployzilla.presentation.web.dto.PipelineResponse;
import fr.imt.deployzilla.deployzilla.presentation.web.dto.mappers.PipelineMapper;
import jakarta.ws.rs.core.Response;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/pipelines")
@RequiredArgsConstructor
public class PipelineController {

    private final PipelineService pipelineService;
    private final PipelineMapper pipelineMapper;

    @PostMapping("/start")
    @ResponseStatus(HttpStatus.OK)
    public ResponseEntity<PipelineResponse> startPipeline(@RequestBody PipelineCreationRequest request) {
        Pipeline pipeline = pipelineService.createPipeline(request.getProjectId(), request.getCommitHash(), request.getAuthor());

        pipelineService.runPipeline(pipeline.getId());
        return ResponseEntity.ok(pipelineMapper.toResponse(pipeline));
    }

}