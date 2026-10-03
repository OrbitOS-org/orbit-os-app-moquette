package org.orbitos.apps.moquette.broker;

/**
 * Editable Moquette broker settings, mirrored 1:1 by the admin web UI.
 * Plain mutable fields — serialized directly to/from JSON (Gson) and to the
 * on-disk properties file, no getters/setters needed for either.
 */
public class BrokerConfig {

    // Off by default: a fresh install/upgrade never auto-starts the broker —
    // it must be turned on (and reviewed) from the admin UI first.
    public boolean enabled = false;

    public String host = "0.0.0.0";
    public int mqttPort = 1883;

    // Off by default: most setups only need plain MQTT, and a listener nobody
    // asked for is just another port that can collide with something else.
    public boolean websocketEnabled = false;
    // 8080/8090 are common defaults for other services (portals, proxies, dev
    // servers) and collide constantly on real devices — 8095 is picked to avoid that.
    public int websocketPort = 8095;

    public boolean persistenceEnabled = true;
    // Relative paths are resolved against the app's working directory.
    public String dataPath = "data";

    // Not user-editable directly: automatically backed by the MQTT users
    // managed in the admin UI (MqttUserStore) once there's at least one.
    public boolean allowAnonymous = true;
    // Blank = disabled. Path to a mosquitto-style ACL file.
    public String aclFile = "";

    public int sessionQueueSize = 1024;
    // Blank = sessions never expire.
    public String persistentClientExpiration = "";

    public BrokerConfig copy() {
        BrokerConfig c = new BrokerConfig();
        c.enabled = enabled;
        c.host = host;
        c.mqttPort = mqttPort;
        c.websocketEnabled = websocketEnabled;
        c.websocketPort = websocketPort;
        c.persistenceEnabled = persistenceEnabled;
        c.dataPath = dataPath;
        c.allowAnonymous = allowAnonymous;
        c.aclFile = aclFile;
        c.sessionQueueSize = sessionQueueSize;
        c.persistentClientExpiration = persistentClientExpiration;
        return c;
    }

    /**
     * Throws IllegalArgumentException with a human-readable message (matching the
     * admin UI's field labels, not the Java field names) if invalid.
     */
    public void validate() {
        requirePort(mqttPort, "MQTT port");
        if (websocketEnabled) {
            requirePort(websocketPort, "WebSocket port");
            if (websocketPort == mqttPort) {
                throw new IllegalArgumentException("WebSocket port must differ from the MQTT port");
            }
        }
        if (sessionQueueSize < 1) {
            throw new IllegalArgumentException("Session queue size must be at least 1");
        }
        if (host == null || host.isBlank()) {
            throw new IllegalArgumentException("Bind host must not be blank");
        }
        // The "no anonymous + no MQTT users" lockout check lives in BrokerRuntime
        // now, since it needs to look at MqttUserStore — data this class doesn't have.
    }

    private static void requirePort(int port, String field) {
        if (port < 1 || port > 65535) {
            throw new IllegalArgumentException(field + " must be in range 1-65535");
        }
    }
}
