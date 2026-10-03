package org.orbitos.apps.moquette.broker;

import io.moquette.broker.ClientDescriptor;
import io.moquette.broker.Server;
import io.moquette.broker.config.FluentConfig;
import io.moquette.broker.config.IConfig;
import org.orbitos.sdk.logger.Logger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Owns the embedded Moquette {@link Server} instance and the config that
 * produced it. Config changes are applied by stopping the current server and
 * starting a fresh one — Moquette binds its listeners at start time, so
 * network/persistence settings can't be hot-swapped in place.
 */
public final class BrokerRuntime {

    private static final String TAG = "main";

    private final Path configFile;
    private final Path baseDir;
    private final MqttUserStore mqttUsers;
    private final ActivityLog activityLog = new ActivityLog(200);

    private BrokerConfig config;
    private Server server;
    private OrbitBridgeListener listener;
    private Instant startedAt;

    public BrokerRuntime(Path baseDir, Path configFile, MqttUserStore mqttUsers) throws IOException {
        this.baseDir = baseDir;
        this.configFile = configFile;
        this.mqttUsers = mqttUsers;
        this.config = BrokerConfigStore.load(configFile);
    }

    /**
     * Note: Moquette's own {@code Server.startServer} can throw an <em>unchecked</em>
     * {@code RuntimeException} on failure (e.g. "Cannot bind to port: 1883") rather
     * than the {@code IOException} its signature declares — normalize everything to
     * {@code IOException} here so every caller has one consistent failure mode.
     */
    public synchronized void start() throws IOException {
        if (server != null) {
            throw new IllegalStateException("broker already running");
        }
        config.validate();

        OrbitBridgeListener l = new OrbitBridgeListener(activityLog);
        IConfig ic = buildConfig(config);
        Server s = new Server();
        try {
            s.startServer(ic, List.of(l));
        } catch (Exception e) {
            // startServer can fail partway through (e.g. after it has already opened
            // the H2 persistence store, before it gets to binding the TCP listener).
            // Without this, that half-initialized Server leaks its file lock on
            // data/moquette_store.h2 for the rest of the JVM's life, so *every*
            // later start attempt fails with "The file is locked" even though the
            // original port conflict is long gone. Release whatever it grabbed.
            try {
                s.stopServer();
            } catch (Exception cleanupFailure) {
                Logger.Warnf(TAG, "[MQTT] cleanup after failed start also failed: %s%n", cleanupFailure.getMessage());
            }
            if (e instanceof IOException io) {
                throw io;
            }
            throw new IOException(e.getMessage(), e);
        }
        server = s;
        listener = l;
        startedAt = Instant.now();

        Logger.Infof(TAG, "[MQTT] broker listening on %s:%d (websocket %s)%n",
                config.host, config.mqttPort,
                config.websocketEnabled ? ":" + config.websocketPort : "disabled");
    }

    public synchronized void stop() {
        if (server == null) {
            return;
        }
        Logger.Info(TAG, "[MQTT] stopping broker");
        server.stopServer();
        server = null;
        listener = null;
        startedAt = null;
    }

    /**
     * Called once at app boot. Never throws — a bad persisted config (or a port
     * already taken on the device) must not prevent the admin UI itself from
     * coming up, since that's the only way to fix it.
     */
    public synchronized void startIfEnabled() {
        if (!config.enabled) {
            Logger.Info(TAG, "[MQTT] broker disabled — enable it from the admin UI to start it");
            return;
        }
        try {
            start();
        } catch (Exception e) {
            Logger.Errorf(TAG, "[MQTT] failed to start broker on boot: %s%n", e.getMessage());
        }
    }

    /** Starts the broker with the current config (if not already running) and marks it enabled. */
    public synchronized void enable() throws IOException {
        if (server == null) {
            config.validate();
            checkStartPreconditions(config);
            start();
        }
        if (!config.enabled) {
            config.enabled = true;
            BrokerConfigStore.save(configFile, config);
        }
    }

    /** Stops the broker (if running) and marks it disabled. */
    public synchronized void disable() throws IOException {
        stop();
        if (config.enabled) {
            config.enabled = false;
            BrokerConfigStore.save(configFile, config);
        }
    }

    /**
     * Persists {@code newConfig} and, if it's marked enabled, (re)starts the broker
     * with it. Config is only written to disk once it's confirmed to work (or, if
     * disabled, immediately — there's no bind risk to check). Rolls back to the
     * previous config on failure, restarting it only if the broker was running before.
     */
    public synchronized void applyConfig(BrokerConfig newConfig) throws IOException {
        newConfig.validate();
        if (newConfig.enabled) {
            checkStartPreconditions(newConfig);
        }
        BrokerConfig previous = config;
        boolean wasRunning = server != null;
        stop();

        if (!newConfig.enabled) {
            config = newConfig;
            BrokerConfigStore.save(configFile, newConfig);
            return;
        }

        try {
            config = newConfig;
            start();
            BrokerConfigStore.save(configFile, newConfig);
        } catch (Exception e) {
            Logger.Errorf(TAG, "[MQTT] failed to apply new config, rolling back: %s%n", e.getMessage());
            config = previous;
            if (wasRunning) {
                try {
                    start();
                } catch (Exception rollbackFailure) {
                    Logger.Errorf(TAG, "[MQTT] rollback also failed: %s%n", rollbackFailure.getMessage());
                }
            }
            if (e instanceof IOException io) {
                throw io;
            }
            throw new IOException(e.getMessage(), e);
        }
    }

    public synchronized BrokerConfig config() {
        return config.copy();
    }

    public synchronized boolean isRunning() {
        return server != null;
    }

    public synchronized Instant startedAt() {
        return startedAt;
    }

    public synchronized List<ClientInfo> connectedClients() {
        List<ClientInfo> out = new ArrayList<>();
        if (server == null) {
            return out;
        }
        for (ClientDescriptor d : server.listConnectedClients()) {
            Instant since = listener != null ? listener.connectedAt(d.getClientID()) : null;
            out.add(new ClientInfo(d.getClientID(), d.getAddress(), d.getPort(), since));
        }
        return out;
    }

    public synchronized boolean kickClient(String clientId) {
        if (server == null) {
            return false;
        }
        return server.disconnectClient(clientId);
    }

    public List<ActivityLog.Entry> recentActivity() {
        return activityLog.snapshot();
    }

    private IConfig buildConfig(BrokerConfig c) throws IOException {
        FluentConfig fc = new FluentConfig()
                .host(c.host)
                .port(c.mqttPort)
                .dataPath(resolve(c.dataPath))
                .sessionQueueSize(c.sessionQueueSize);

        if (c.websocketEnabled) {
            fc.websocketPort(c.websocketPort);
        }
        if (c.persistenceEnabled) {
            fc.enablePersistence();
        } else {
            fc.disablePersistence();
        }
        if (!c.allowAnonymous) {
            fc.disallowAnonymous();
        }
        // Automatic, not user-editable: whichever MQTT users have been added in
        // the admin UI become the broker's password file, once there's at least one.
        if (!mqttUsers.listUsernames().isEmpty()) {
            fc.passwordFile(resolve(MqttUserStore.DEFAULT_RELATIVE_PATH).toString());
        }
        if (c.aclFile != null && !c.aclFile.isBlank()) {
            fc.aclFile(resolve(c.aclFile).toString());
        }
        if (c.persistentClientExpiration != null && !c.persistentClientExpiration.isBlank()) {
            fc.persistentClientExpiration(c.persistentClientExpiration);
        }
        return fc.build();
    }

    /**
     * Checks that can't live in {@link BrokerConfig#validate()} because they need
     * data it doesn't have (the ACL file's existence, and whether any MQTT user
     * has been added). Locking out anonymous clients with zero MQTT users means
     * nobody — including you — could ever connect; fail loudly instead of
     * producing a broker nobody can reach.
     */
    private void checkStartPreconditions(BrokerConfig c) throws IOException {
        if (c.aclFile != null && !c.aclFile.isBlank() && !Files.exists(resolve(c.aclFile))) {
            throw new IOException("ACL file does not exist: " + c.aclFile);
        }
        if (!c.allowAnonymous && mqttUsers.listUsernames().isEmpty()) {
            throw new IOException(
                    "Allow anonymous is off but no MQTT users are set — no client would be able to " +
                    "connect. Add a user below, or turn anonymous connections back on.");
        }
    }

    private Path resolve(String p) {
        Path path = Path.of(p);
        return path.isAbsolute() ? path : baseDir.resolve(path);
    }

    public record ClientInfo(String clientId, String address, int port, Instant connectedAt) {}
}
