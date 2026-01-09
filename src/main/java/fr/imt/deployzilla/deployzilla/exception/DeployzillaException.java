package fr.imt.deployzilla.deployzilla.exception;

/**
 * Base exception class for all Deployzilla domain exceptions.
 * Provides structured error handling with error codes for debugging and API responses.
 */
public class DeployzillaException extends RuntimeException {

    private final String errorCode;

    public DeployzillaException(String errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public DeployzillaException(String errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
    }

    public String getErrorCode() {
        return errorCode;
    }
}
