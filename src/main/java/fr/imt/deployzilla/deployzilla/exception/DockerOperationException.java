package fr.imt.deployzilla.deployzilla.exception;

/**
 * Exception thrown when Docker client operations fail.
 */
public class DockerOperationException extends DeployzillaException {

    private static final String ERROR_CODE = "DOCKER_ERR";

    public DockerOperationException(String operation, Throwable cause) {
        super(ERROR_CODE, "Docker operation failed: " + operation, cause);
    }

    public DockerOperationException(String message) {
        super(ERROR_CODE, message);
    }
}
