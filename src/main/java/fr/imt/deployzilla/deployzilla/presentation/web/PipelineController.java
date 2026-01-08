package fr.imt.deployzilla.deployzilla.presentation.web;

import fr.imt.deployzilla.deployzilla.business.service.PipelineService;
import fr.imt.deployzilla.deployzilla.infrastructure.persistence.Pipeline;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.nio.channels.Pipe;

@RestController
@RequestMapping("/api/v1/pipelines")
@RequiredArgsConstructor
public class PipelineController {

    private final PipelineService pipelineService;

    @PostMapping("/start")
    @ResponseStatus(HttpStatus.OK)
    public ResponseEntity<Pipeline> startPipeline(@RequestBody PipelineCreationRequest request) {
        Pipeline pipeline = pipelineService.createPipeline(request.getProjectId(), request.getCommitHash(), request.getAuthor());

        pipelineService.runPipeline(pipeline.getId());
        return ResponseEntity.ok(pipeline);
    }

}