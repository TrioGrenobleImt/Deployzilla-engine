package fr.imt.deployzilla.deployzilla.exception;

/**
 * Exception thrown when Docker image build or push operations fail.
 */
public class ImageBuildException extends DeployzillaException {

    private static final String ERROR_CODE = "BUILD_ERR";

    public ImageBuildException(String imageName, String operation, Throwable cause) {
        super(ERROR_CODE, "Failed to " + operation + " image: " + imageName, cause);
    }

    public ImageBuildException(String message, Throwable cause) {
        super(ERROR_CODE, message, cause);
    }
}
