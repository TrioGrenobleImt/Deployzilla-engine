package fr.imt.deployzilla.deployzilla.business.service.jobs;

import fr.imt.deployzilla.deployzilla.business.model.ProcessResult;
import fr.imt.deployzilla.deployzilla.business.service.ContainerExecutor;
import fr.imt.deployzilla.deployzilla.business.utils.DirectorySanitizer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.FileSystemUtils;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;

@Component
@RequiredArgsConstructor
@Slf4j
public class ImageBuildService {

    private final ContainerExecutor containerExecutor;

    @Value("${deployzilla.workspace.path:/workspaces}")
    private String workspacePath;

    @Value("${deployzilla.workspace.local.path:/workspaces}")
    private String workspaceLocalPath;

    public CompletableFuture<ProcessResult> execute(String pipelineId, String projectDir, String gitUrl) {
        String sanitizedDir = DirectorySanitizer.sanitizeDirectoryName(projectDir);
        String hostProjectPath = workspacePath + "/" + pipelineId + "/" + sanitizedDir;
        String localProjectPath = workspaceLocalPath + "/" + pipelineId + "/" + sanitizedDir;
        
        // We use pipelineId as the image name (or part of it)
        String imageName = "deployzilla-app-" + pipelineId;
        String tag = "latest";
        String dockerfileName = "Dockerfile";

        log.info("Building image {} for pipeline {}", imageName, pipelineId);

        try {
            // 1. Detect Package Manager
            // 1. Detect Package Manager (still check local files for decision)
            Path projectPath = Path.of(localProjectPath);
            String runCommand = detectRunCommand(projectPath);
            log.info("Detected run command: {}", runCommand);

            // 2. Create a temporary directory for the build context
            // This ensures a clean context without symlinks (node_modules)
            Path buildContextPath = Files.createTempDirectory("deployzilla-build-" + pipelineId);
            log.info("Created temporary build context at {}", buildContextPath);
            containerExecutor.publishLog(pipelineId, "Created clean build context at " + buildContextPath);

            // 3. Generate Dockerfile Content
            String dockerfileContent = generateDockerfileContent(gitUrl, runCommand);
            log.info("Generated Dockerfile content: {}", dockerfileContent);
            containerExecutor.publishLog(pipelineId, "Generated Dockerfile content:\n" + dockerfileContent);

            // 4. Write Dockerfile to the temporary context
            Path dockerfilePath = buildContextPath.resolve("Dockerfile");
            log.info("Writing Dockerfile to {}", dockerfilePath);
            containerExecutor.publishLog(pipelineId, "Writing Dockerfile to " + dockerfilePath);
            Files.writeString(dockerfilePath, dockerfileContent);
            
            // 5. Build Image
            containerExecutor.buildImage(pipelineId, buildContextPath.toString(), "Dockerfile", imageName, tag);
            
            try {
                FileSystemUtils.deleteRecursively(buildContextPath);
                log.info("Cleaned up temp dir {}", buildContextPath);
            } catch (Exception e) {
                log.warn("Failed to cleanup temp dir", e);
            }
            
            return CompletableFuture.completedFuture(new ProcessResult(0, "SUCCESS"));
        } catch (Exception e) {
            log.error("Build image failed", e);
            return CompletableFuture.completedFuture(new ProcessResult(1, "ERROR"));
        }
    }

    private String detectRunCommand(Path projectPath) {
        if (Files.exists(projectPath.resolve("yarn.lock"))) {
            return "yarn";
        } else if (Files.exists(projectPath.resolve("pnpm-lock.yaml"))) {
            return "pnpm";
        } else {
            return "npm";
        }
    }

    private String generateDockerfileContent(String gitUrl, String packageManager) {
        String installCommand;
        if ("yarn".equals(packageManager)) {
            installCommand = "RUN corepack enable && yarn install --frozen-lockfile";
        } else if ("pnpm".equals(packageManager)) {
            installCommand = "RUN corepack enable && pnpm install --frozen-lockfile";
        } else {
            installCommand = "RUN npm ci";
        }

        return """
                # Stage 1: Clone
                FROM alpine/git AS source
                WORKDIR /src
                RUN git clone %s .

                # Stage 2: Build
                FROM node:22-alpine
                WORKDIR /app
                COPY --from=source /src .
                
                %s
                RUN %s build
                EXPOSE 3000
                CMD ["%s", "start"]
                """.formatted(gitUrl, installCommand, packageManager, packageManager);
    }
}
