package fr.imt.deployzilla.deployzilla.presentation.web;

import fr.imt.deployzilla.deployzilla.business.service.PipelineService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/pipelines")
@RequiredArgsConstructor
public class PipelineController {

    private final PipelineService pipelineService;

    @PostMapping("/start")
    public ResponseEntity<String> startPipeline(@RequestBody PipelineCreationRequest request) {
        String pipelineId = pipelineService.createPipeline(request.getProjectId(), request.getCommitHash(), request.getAuthor())
                .getId();

        pipelineService.runPipeline(pipelineId);
        return ResponseEntity.ok("Pipeline started.");
    }

}