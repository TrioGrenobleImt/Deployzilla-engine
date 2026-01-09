package fr.imt.deployzilla.deployzilla.infrastructure.ssh;

import fr.imt.deployzilla.deployzilla.exception.SshConnectionException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;

import static fr.imt.deployzilla.deployzilla.business.utils.Constants.TUNNEL_PORT;

/**
 * SSH tunnel implementation using sshpass for password authentication.
 * Manages the lifecycle of an SSH tunnel to a remote Docker host.
 */
@Service
@Slf4j
public class SshpassTunnelManager implements SshTunnel {

    @Value("${deployzilla.remote.enabled:false}")
    private boolean enabled;

    @Value("${deployzilla.remote.host:localhost}")
    private String host;

    @Value("${deployzilla.remote.user:root}")
    private String user;

    @Value("${deployzilla.remote.password:}")
    private String password;

    @Value("${deployzilla.remote.port:22}")
    private int port;

    private Process sshTunnelProcess;

    @Override
    public void connect() throws SshConnectionException {
        if (!enabled) {
            log.info("[SSH-TUNNEL] Remote mode disabled, skipping tunnel setup");
            return;
        }

        log.info("[SSH-TUNNEL] Starting SSH Tunnel with Password Auth to {}:{}", host, port);

        if (password == null || password.isBlank()) {
            throw new SshConnectionException("Remote password is required for SSH tunnel");
        }

        try {
            ProcessBuilder pb = new ProcessBuilder(
                    "sshpass", "-e",
                    "ssh",
                    "-4",
                    "-N", // No remote command (just forwarding)
                    "-L", String.format("%d:/var/run/docker.sock", TUNNEL_PORT),
                    "-p", String.valueOf(port),
                    "-o", "StrictHostKeyChecking=no",     // Don't ask for confirmation
                    "-o", "UserKnownHostsFile=/dev/null", // Don't save host keys
                    "-o", "LogLevel=VERBOSE",             // Enable Verbose Logs
                    String.format("%s@%s", user, host)
            );

            pb.environment().put("SSHPASS", password);
            pb.redirectErrorStream(true);

            this.sshTunnelProcess = pb.start();

            // Read SSH output in a background thread for debugging
            startLogReader();

            // Wait for connection to stabilize
            Thread.sleep(3000);

            if (!isConnected()) {
                throw new SshConnectionException("SSH Tunnel process died immediately. Check logs for [SSH-TUNNEL] errors.");
            }

            log.info("[SSH-TUNNEL] SSH Tunnel started successfully on port {}", TUNNEL_PORT);

        } catch (IOException e) {
            throw new SshConnectionException("Failed to start SSH tunnel process", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new SshConnectionException("SSH tunnel connection was interrupted", e);
        }
    }

    @Override
    public void disconnect() {
        if (sshTunnelProcess != null && sshTunnelProcess.isAlive()) {
            log.info("[SSH-TUNNEL] Disconnecting SSH tunnel");
            sshTunnelProcess.destroy();
            try {
                // Give it a moment to terminate gracefully
                if (!sshTunnelProcess.waitFor(5, java.util.concurrent.TimeUnit.SECONDS)) {
                    sshTunnelProcess.destroyForcibly();
                    log.warn("[SSH-TUNNEL] SSH tunnel process forcibly terminated");
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                sshTunnelProcess.destroyForcibly();
            }
            log.info("[SSH-TUNNEL] SSH tunnel disconnected");
        }
    }

    @Override
    public boolean isConnected() {
        return sshTunnelProcess != null && sshTunnelProcess.isAlive();
    }

    @Override
    public int getLocalPort() {
        return TUNNEL_PORT;
    }

    /**
     * Starts a background thread to read and log SSH process output.
     */
    private void startLogReader() {
        new Thread(() -> {
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(sshTunnelProcess.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    log.warn("[SSH-TUNNEL] {}", line);
                }
            } catch (IOException e) {
                // Stream closed, tunnel likely disconnected
                log.debug("[SSH-TUNNEL] Log reader stream closed");
            }
        }, "ssh-tunnel-log-reader").start();
    }

    /**
     * Checks if remote mode is enabled.
     *
     * @return true if remote mode is enabled
     */
    public boolean isEnabled() {
        return enabled;
    }
}
