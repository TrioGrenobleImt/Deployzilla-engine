package fr.imt.deployzilla.deployzilla.exception;

/**
 * Exception thrown when a requested project is not found.
 */
public class ProjectNotFoundException extends DeployzillaException {

    private static final String ERROR_CODE = "NOT_FOUND";

    public ProjectNotFoundException(String message) {
        super(ERROR_CODE, message);
    }

    public ProjectNotFoundException(String projectId, Throwable cause) {
        super(ERROR_CODE, "Project not found: " + projectId, cause);
    }
}
