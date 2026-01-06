package fr.imt.deployzilla.deployzilla.presentation.web;

import fr.imt.deployzilla.deployzilla.business.service.steps.GitCloneStep;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/tests")
@RequiredArgsConstructor
public class TestController {

    private final GitCloneStep gitCloneStep;

    /**
     * Test endpoint for GitCloneStep with containerized execution.
     * Uses a public repo so no deploy key is needed.
     */
    @PostMapping("/git-clone")
    public ResponseEntity<String> testGitClone() {
        // Generate a unique pipeline ID for this test
        String pipelineId = "test-" + UUID.randomUUID().toString().substring(0, 8);
        
        // Test with a public repository
        String repoUrl = "https://github.com/cedric-champeix/software-architecture-car-lease-project.git";
        String branch = "main";
        String targetDir = "test-clone";

        gitCloneStep.execute(pipelineId, repoUrl, branch, targetDir)
                .thenAccept(exitCode -> {
                    if (exitCode == 0) {
                        System.out.println("Git clone successful for pipeline: " + pipelineId);
                    } else {
                        System.out.println("Git clone failed with exit code: " + exitCode);
                    }
                });

        return ResponseEntity.ok("Git clone started. Pipeline ID: " + pipelineId + 
                ". Check Redis logs for output.");
    }
}
