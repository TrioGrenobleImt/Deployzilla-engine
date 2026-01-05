package fr.imt.deployzilla.deployzilla.presentation.web;

import fr.imt.deployzilla.deployzilla.business.service.BashExecutor;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/pipelines")
@RequiredArgsConstructor
public class PipelineController {

    private final BashExecutor bashExecutor;

    @PostMapping("/{id}/start")
    public ResponseEntity<String> startPipeline(@PathVariable String id) {
        bashExecutor.executeScript("scripts/test.sh", id);
        return ResponseEntity.ok("Pipeline " + id + " started.");
    }

}