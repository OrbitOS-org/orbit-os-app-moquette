package org.orbitos.apps.moquette.broker;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/** Loads/saves {@link BrokerConfig} as a flat .properties file so edits survive restarts. */
public final class BrokerConfigStore {

    private BrokerConfigStore() {}

    public static BrokerConfig load(Path file) throws IOException {
        BrokerConfig cfg = new BrokerConfig();
        if (!Files.exists(file)) {
            return cfg;
        }
        Properties p = new Properties();
        try (InputStream in = Files.newInputStream(file)) {
            p.load(in);
        }
        cfg.enabled = Boolean.parseBoolean(p.getProperty("enabled", Boolean.toString(cfg.enabled)));
        cfg.host = p.getProperty("host", cfg.host);
        cfg.mqttPort = Integer.parseInt(p.getProperty("mqttPort", Integer.toString(cfg.mqttPort)));
        cfg.websocketEnabled = Boolean.parseBoolean(p.getProperty("websocketEnabled", Boolean.toString(cfg.websocketEnabled)));
        cfg.websocketPort = Integer.parseInt(p.getProperty("websocketPort", Integer.toString(cfg.websocketPort)));
        cfg.persistenceEnabled = Boolean.parseBoolean(p.getProperty("persistenceEnabled", Boolean.toString(cfg.persistenceEnabled)));
        cfg.dataPath = p.getProperty("dataPath", cfg.dataPath);
        cfg.allowAnonymous = Boolean.parseBoolean(p.getProperty("allowAnonymous", Boolean.toString(cfg.allowAnonymous)));
        cfg.aclFile = p.getProperty("aclFile", cfg.aclFile);
        cfg.sessionQueueSize = Integer.parseInt(p.getProperty("sessionQueueSize", Integer.toString(cfg.sessionQueueSize)));
        cfg.persistentClientExpiration = p.getProperty("persistentClientExpiration", cfg.persistentClientExpiration);
        return cfg;
    }

    public static void save(Path file, BrokerConfig cfg) throws IOException {
        Properties p = new Properties();
        p.setProperty("enabled", Boolean.toString(cfg.enabled));
        p.setProperty("host", cfg.host);
        p.setProperty("mqttPort", Integer.toString(cfg.mqttPort));
        p.setProperty("websocketEnabled", Boolean.toString(cfg.websocketEnabled));
        p.setProperty("websocketPort", Integer.toString(cfg.websocketPort));
        p.setProperty("persistenceEnabled", Boolean.toString(cfg.persistenceEnabled));
        p.setProperty("dataPath", cfg.dataPath);
        p.setProperty("allowAnonymous", Boolean.toString(cfg.allowAnonymous));
        p.setProperty("aclFile", cfg.aclFile == null ? "" : cfg.aclFile);
        p.setProperty("sessionQueueSize", Integer.toString(cfg.sessionQueueSize));
        p.setProperty("persistentClientExpiration", cfg.persistentClientExpiration == null ? "" : cfg.persistentClientExpiration);

        Path parent = file.toAbsolutePath().getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        try (OutputStream out = Files.newOutputStream(file)) {
            p.store(out, "moquette broker config - edited via the admin web UI");
        }
    }
}
