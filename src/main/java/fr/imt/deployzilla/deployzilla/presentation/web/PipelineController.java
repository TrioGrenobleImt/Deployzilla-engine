package fr.imt.deployzilla.deployzilla.presentation.web;

import fr.imt.deployzilla.deployzilla.infrastructure.service.MongoPipelineOrchestrator;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.concurrent.ExecutionException;

@RestController
@RequestMapping("/api/v1/pipelines")
@RequiredArgsConstructor
public class PipelineController {

    private final MongoPipelineOrchestrator mongoPipelineOrchestrator;

    @PostMapping("/start")
    public ResponseEntity<String> startPipeline() throws ExecutionException, InterruptedException {
        String pipelineId = mongoPipelineOrchestrator.createPipeline(List.of("sayhi.sh", "test.sh"))
                .getId();
        mongoPipelineOrchestrator.runPipeline(pipelineId);
        return ResponseEntity.ok("Pipeline started.");
    }

}