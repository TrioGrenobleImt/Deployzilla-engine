package fr.imt.deployzilla.deployzilla.exception;

/**
 * Exception thrown when container execution fails.
 */
public class ContainerExecutionException extends DeployzillaException {

    private static final String ERROR_CODE = "CONTAINER_ERR";

    public ContainerExecutionException(String imageName, String operation, Throwable cause) {
        super(ERROR_CODE, "Container " + operation + " failed for image: " + imageName, cause);
    }

    public ContainerExecutionException(String message, Throwable cause) {
        super(ERROR_CODE, message, cause);
    }
}
