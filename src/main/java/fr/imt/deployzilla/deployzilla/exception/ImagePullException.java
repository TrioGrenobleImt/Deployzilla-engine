package fr.imt.deployzilla.deployzilla.exception;

/**
 * Exception thrown when Docker image pull operations fail.
 */
public class ImagePullException extends DeployzillaException {

    private static final String ERROR_CODE = "PULL_ERR";

    public ImagePullException(String imageName, Throwable cause) {
        super(ERROR_CODE, "Failed to pull image: " + imageName, cause);
    }

    public ImagePullException(String imageName, String reason) {
        super(ERROR_CODE, "Failed to pull image: " + imageName + " - " + reason);
    }
}
