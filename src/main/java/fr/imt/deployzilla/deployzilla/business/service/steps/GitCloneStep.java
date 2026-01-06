package fr.imt.deployzilla.deployzilla.business.service.steps;

import fr.imt.deployzilla.deployzilla.business.service.ContainerExecutor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

/**
 * Pipeline step that clones a Git repository into a shared workspace.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class GitCloneStep {

    private static final String GIT_IMAGE = "alpine/git:v2.52.0";

    // Container mount paths
    private static final String CONTAINER_WORKSPACE_PATH = "/workspace";
    private static final String CONTAINER_KEYS_PATH = "/keys";
    private static final String CONTAINER_DEPLOY_KEY_FILE = CONTAINER_KEYS_PATH + "/deploy_key";
    private static final String CONTAINER_KNOWN_HOSTS_FILE = CONTAINER_KEYS_PATH + "/known_hosts";

    // Default branch
    private static final String DEFAULT_BRANCH = "main";

    // GitHub SSH host keys for secure host verification (avoids MITM attacks)
    // Source: https://docs.github.com/en/authentication/keeping-your-account-and-data-secure/githubs-ssh-key-fingerprints
    private static final String GITHUB_KNOWN_HOSTS = """
            github.com ssh-ed25519 AAAAC3NzaC1lZDI1NTE5AAAAIOMqqnkVzrm0SdG6UOoqKLsabgH5C9okWi0dh2l9GKJl
            github.com ecdsa-sha2-nistp256 AAAAE2VjZHNhLXNoYTItbmlzdHAyNTYAAAAIbmlzdHAyNTYAAABBBEmKSENjQEezOmxkZMy7opKgwFB9nkt5YRrYMjNuG5N87uRgg6CLrbo5wAdT/y6v0mKV0U2w0WZ2YB/++Tpockg=
            github.com ssh-rsa AAAAB3NzaC1yc2EAAAADAQABAAABgQCj7ndNxQowgcQnjshcLrqPEiiphnt+VTTvDP6mHBL9j1aNUkY4Ue1gvwnGLVlOhGeYrnZaMgRK6+PKCUXaDbC7qtbW8gIkhL7aGCsOr/C56SJMy/BCZfxd1nWzAOxSDPgVsmerOBYfNqltV9/hWCqBywINIR+5dIg6JTJ72pcEpEjcYgXkE2YEFXV1JHnsKgbLWNlhScqb2UmyRkQyytRLtL+38TGxkxCflmO+5Z8CSSNY7GidjMIZ7Q4zMjA2n1nGrlTDkzwDCsw+wqFPGQA179cnfGWOWRVruj16z6XyvxvjJwbz0wQZ75XK5tKSb7FNyeIEs4TT4jk+S4dhPeAUC5y+bDYirYgM4GC7uEnztnZyaVWQ7B381AK4Qdrwt51ZqExKbQpTUNn+EjqoTwvqNj4kqx5QUCI0ThS/YkOxJCXmPUWZbhjpCg56i+2aB6CmK2JGhn57K5mj0MNdBXA4/WnwH6XoPWJzK5Nyu2zB3nAZp+S5hpQs+p1vN1/wsjk=
            """;

    private final ContainerExecutor containerExecutor;

    @Value("${deployzilla.workspace.path:/workspaces}")
    private String workspacePath;

    @Value("${deployzilla.keys.path:/tmp/deployzilla-keys}")
    private String secureKeysPath;

    /**
     * Clone a repository from GitHub.
     *
     * @param pipelineId Unique pipeline identifier
     * @param repoUrl    Git repository URL (e.g., "https://github.com/user/repo.git")
     * @param branch     Branch to clone (default: main)
     * @param targetDir  Directory name within workspace
     * @return CompletableFuture with exit code (0 = success)
     */
    public CompletableFuture<Integer> execute(
            String pipelineId,
            String repoUrl,
            String branch,
            String targetDir) {

        String stepId = "git-clone";

        // Validate inputs
        if (repoUrl == null || repoUrl.isBlank()) {
            throw new IllegalArgumentException("Repository URL cannot be empty");
        }
        if (branch == null || branch.isBlank()) {
            branch = DEFAULT_BRANCH;
        }
        if (targetDir == null || targetDir.isBlank()) {
            targetDir = extractRepoName(repoUrl);
        }

        // Sanitize target directory to prevent path traversal
        targetDir = sanitizeDirectoryName(targetDir);

        // Build git clone command
        List<String> command = List.of(
                "clone",
                "--depth", "1",           // Shallow clone for speed
                "--branch", branch,
                repoUrl,
                CONTAINER_WORKSPACE_PATH + "/" + targetDir
        );

        // Mount shared workspace volume
        String pipelineWorkspace = workspacePath + "/" + pipelineId;
        List<String> volumes = List.of(
                pipelineWorkspace + ":" + CONTAINER_WORKSPACE_PATH
        );

        log.info("Cloning {} (branch: {}) to {}", repoUrl, branch, pipelineWorkspace + "/" + targetDir);

        return containerExecutor.executeStep(
                pipelineId,
                stepId,
                GIT_IMAGE,
                command,
                volumes,
                Map.of()  // No extra env vars needed for public repos
        );
    }

    /**
     * Clone a private repository using an SSH deploy key content.
     * 
     * The deploy key content is written to a temporary file in the shared workspace,
     * used for the clone operation, and then securely deleted.
     * 
     * Deploy keys are more secure than tokens because:
     * - They are repository-specific (least privilege)
     * - They can be read-only
     * - They don't expose personal access tokens
     *
     * @param pipelineId       Unique pipeline identifier
     * @param sshRepoUrl       SSH repository URL (e.g., "git@github.com:user/repo.git")
     * @param branch           Branch to clone (default: main)
     * @param targetDir        Directory name within workspace
     * @param deployKeyContent The SSH private key content (e.g., from MongoDB)
     * @return CompletableFuture with exit code (0 = success)
     */
    public CompletableFuture<Integer> executeWithDeployKey(
            String pipelineId,
            String sshRepoUrl,
            String branch,
            String targetDir,
            String deployKeyContent) {

        String stepId = "git-clone-ssh";

        // Validate inputs
        if (sshRepoUrl == null || !sshRepoUrl.startsWith("git@")) {
            throw new IllegalArgumentException("SSH URL must start with 'git@' (e.g., git@github.com:user/repo.git)");
        }
        if (deployKeyContent == null || deployKeyContent.isBlank()) {
            throw new IllegalArgumentException("Deploy key content is required");
        }

        if (branch == null || branch.isBlank()) {
            branch = DEFAULT_BRANCH;
        }
        if (targetDir == null || targetDir.isBlank()) {
            targetDir = extractRepoName(sshRepoUrl);
        }

        targetDir = sanitizeDirectoryName(targetDir);

        // Create temp files in secure location (NOT in shared workspace to prevent exfiltration)
        String pipelineWorkspace = workspacePath + "/" + pipelineId;
        Path secureKeyDir = Path.of(secureKeysPath, pipelineId);
        Path tempKeyFile = null;
        Path knownHostsFile = null;

        try {
            // Ensure secure key directory exists
            Files.createDirectories(secureKeyDir);
            
            // Create temp key file with unique name
            tempKeyFile = Files.createTempFile(secureKeyDir, "deploy_key_", ".pem");
            tempKeyFile.toFile().deleteOnExit();  // Backup cleanup
            
            // Create known_hosts file with GitHub's SSH host keys
            knownHostsFile = Files.createTempFile(secureKeyDir, "known_hosts_", ".txt");
            knownHostsFile.toFile().deleteOnExit();
            Files.writeString(knownHostsFile, GITHUB_KNOWN_HOSTS);
            
            // Write key content
            Files.writeString(tempKeyFile, deployKeyContent);
            
            // Set secure permissions (600 - owner read/write only)
            Set<PosixFilePermission> permissions = Set.of(
                    PosixFilePermission.OWNER_READ,
                    PosixFilePermission.OWNER_WRITE
            );
            try {
                Files.setPosixFilePermissions(tempKeyFile, permissions);
                Files.setPosixFilePermissions(knownHostsFile, permissions);
            } catch (UnsupportedOperationException e) {
                // Windows doesn't support POSIX permissions, skip
                log.warn("Could not set POSIX permissions on key file (non-POSIX filesystem)");
            }

            // GIT_SSH_COMMAND with proper host verification using known_hosts
            String gitSshCommand = "ssh -i " + CONTAINER_DEPLOY_KEY_FILE +
                    " -o UserKnownHostsFile=" + CONTAINER_KNOWN_HOSTS_FILE +
                    " -o StrictHostKeyChecking=yes";

            List<String> command = List.of(
                    "clone",
                    "--depth", "1",
                    "--branch", branch,
                    sshRepoUrl,
                    CONTAINER_WORKSPACE_PATH + "/" + targetDir
            );

            // Mount workspace AND secure keys directory separately
            // Keys are mounted read-only and in a different path than workspace
            List<String> volumes = List.of(
                    pipelineWorkspace + ":" + CONTAINER_WORKSPACE_PATH,
                    tempKeyFile.toString() + ":" + CONTAINER_DEPLOY_KEY_FILE + ":ro",
                    knownHostsFile.toString() + ":" + CONTAINER_KNOWN_HOSTS_FILE + ":ro"
            );

            Map<String, String> envVars = Map.of(
                    "GIT_SSH_COMMAND", gitSshCommand
            );

            log.info("Cloning via SSH {} (branch: {}) to {}", sshRepoUrl, branch, pipelineWorkspace + "/" + targetDir);

            // Execute and clean up key files after completion
            final Path keyFileToDelete = tempKeyFile;
            final Path knownHostsToDelete = knownHostsFile;
            return containerExecutor.executeStep(
                    pipelineId,
                    stepId,
                    GIT_IMAGE,
                    command,
                    volumes,
                    envVars
            ).whenComplete((exitCode, throwable) -> {
                // Always delete key files after execution (defense in depth)
                try {
                    Files.deleteIfExists(keyFileToDelete);
                    Files.deleteIfExists(knownHostsToDelete);
                    log.debug("Cleaned up temp key files for pipeline: {}", pipelineId);
                } catch (IOException e) {
                    log.warn("Failed to delete temp key files for pipeline: {}", pipelineId, e);
                }
            });

        } catch (IOException e) {
            // Clean up on error
            if (tempKeyFile != null) {
                try {
                    Files.deleteIfExists(tempKeyFile);
                } catch (IOException ignored) {}
            }
            if (knownHostsFile != null) {
                try {
                    Files.deleteIfExists(knownHostsFile);
                } catch (IOException ignored) {}
            }
            log.error("Failed to create temp key files for pipeline {}", pipelineId, e);
            return CompletableFuture.completedFuture(1);
        }
    }

    /**
     * Clone a private repository using HTTPS URL format with a deploy key.
     * 
     * This is a convenience method that automatically converts the HTTPS URL to SSH format
     * and delegates to executeWithDeployKey.
     *
     * @param pipelineId       Unique pipeline identifier
     * @param httpsRepoUrl     HTTPS repository URL (e.g., "https://github.com/user/repo.git")
     * @param branch           Branch to clone (default: main)
     * @param targetDir        Directory name within workspace
     * @param deployKeyContent The SSH private key content (e.g., from MongoDB)
     * @return CompletableFuture with exit code (0 = success)
     */
    public CompletableFuture<Integer> executePrivateRepo(
            String pipelineId,
            String httpsRepoUrl,
            String branch,
            String targetDir,
            String deployKeyContent) {

        String sshUrl = convertToSshUrl(httpsRepoUrl);
        return executeWithDeployKey(pipelineId, sshUrl, branch, targetDir, deployKeyContent);
    }

    /**
     * Extract repository name from URL.
     * Example: "https://github.com/user/my-repo.git" -> "my-repo"
     */
    private String extractRepoName(String repoUrl) {
        String name = repoUrl.substring(repoUrl.lastIndexOf('/') + 1);
        if (name.endsWith(".git")) {
            name = name.substring(0, name.length() - 4);
        }
        return name;
    }

    /**
     * Sanitize directory name to prevent path traversal attacks.
     */
    private String sanitizeDirectoryName(String dirName) {
        return dirName
                .replaceAll("[^a-zA-Z0-9._-]", "_")
                .replaceAll("\\.\\.", "_");
    }


    /**
     * Convert HTTPS URL to SSH URL format.
     * Example: "https://github.com/user/repo.git" -> "git@github.com:user/repo.git"
     */
    public String convertToSshUrl(String httpsUrl) {
        if (httpsUrl == null || !httpsUrl.startsWith("https://")) {
            throw new IllegalArgumentException("Expected HTTPS URL");
        }
        // https://github.com/user/repo.git -> git@github.com:user/repo.git
        String withoutProtocol = httpsUrl.replace("https://", "");
        int firstSlash = withoutProtocol.indexOf('/');
        if (firstSlash == -1) {
            throw new IllegalArgumentException("Invalid repository URL format");
        }
        String host = withoutProtocol.substring(0, firstSlash);
        String path = withoutProtocol.substring(firstSlash + 1);
        return "git@" + host + ":" + path;
    }
}

