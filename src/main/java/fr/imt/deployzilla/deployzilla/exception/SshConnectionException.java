package fr.imt.deployzilla.deployzilla.exception;

/**
 * Exception thrown when SSH tunnel operations fail.
 */
public class SshConnectionException extends DeployzillaException {

    private static final String ERROR_CODE = "SSH_ERR";

    public SshConnectionException(String message) {
        super(ERROR_CODE, message);
    }

    public SshConnectionException(String message, Throwable cause) {
        super(ERROR_CODE, message, cause);
    }

    public SshConnectionException(String host, int port, Throwable cause) {
        super(ERROR_CODE, "Failed to connect to " + host + ":" + port, cause);
    }
}
