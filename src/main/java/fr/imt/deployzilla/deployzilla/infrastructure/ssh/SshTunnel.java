package fr.imt.deployzilla.deployzilla.infrastructure.ssh;

/**
 * Interface defining SSH tunnel operations.
 * Provides abstraction layer for SSH tunnel management,
 * enabling independent testing and future library replacement.
 */
public interface SshTunnel {

    /**
     * Establishes the SSH tunnel connection.
     *
     * @throws SshException if the tunnel cannot be established
     */
    void connect() throws SshException;

    /**
     * Closes the SSH tunnel connection gracefully.
     */
    void disconnect();

    /**
     * Checks if the SSH tunnel is currently connected and alive.
     *
     * @return true if the tunnel is connected, false otherwise
     */
    boolean isConnected();

    /**
     * Gets the local port used for the tunnel.
     *
     * @return the local port number
     */
    int getLocalPort();
}
