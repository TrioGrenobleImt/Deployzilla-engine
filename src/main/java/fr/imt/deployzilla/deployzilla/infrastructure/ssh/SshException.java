package fr.imt.deployzilla.deployzilla.infrastructure.ssh;

/**
 * Exception thrown when SSH tunnel operations fail.
 */
public class SshException extends RuntimeException {

    public SshException(String message) {
        super(message);
    }

    public SshException(String message, Throwable cause) {
        super(message, cause);
    }
}
