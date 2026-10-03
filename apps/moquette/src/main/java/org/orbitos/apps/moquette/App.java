package org.orbitos.apps.moquette;

import org.orbitos.apps.moquette.admin.AdminAuth;
import org.orbitos.apps.moquette.admin.AdminServer;
import org.orbitos.apps.moquette.broker.BrokerRuntime;
import org.orbitos.apps.moquette.broker.MqttUserStore;
import org.orbitos.sdk.Client;
import org.orbitos.sdk.logger.Logger;
import org.orbitos.sdk.services.AppHubManager;
import org.orbitos.sdk.services.SystemManager;

import java.nio.file.Path;

public class App {

    static final String TAG = "main";
    static final String ADMIN_HOST = "127.0.0.1";
    static final int ADMIN_PORT = 50099;
    static final String ADMIN_ROUTE = "/moquette";

    public static void main(String[] args) {
        Logger.Init("moquette", "INFO", true);

        String host = args.length > 0 ? args[0] : "192.168.1.229";
        Path workDir = Path.of(System.getProperty("user.dir"));

        try (Client client = Client.connect(host, "moquette")) {
            Logger.Info(TAG, "=== moquette ===");

            SystemManager sys = client.systemManager();
            Logger.Infof(TAG, "[System] board=%s%n", sys.getBoardModel());

            MqttUserStore mqttUsers = new MqttUserStore(workDir.resolve(MqttUserStore.DEFAULT_RELATIVE_PATH));
            BrokerRuntime runtime = new BrokerRuntime(workDir, workDir.resolve("config/broker.properties"), mqttUsers);
            AdminAuth auth = new AdminAuth(workDir.resolve("config/admin.properties"));

            AdminServer admin = new AdminServer(runtime, auth, mqttUsers);
            admin.start(ADMIN_HOST, ADMIN_PORT);

            // Off by default — the broker only starts if a previous admin session
            // enabled it (persisted in config/broker.properties). A boot failure
            // (e.g. port already taken) is logged but never crashes the app, since
            // the admin UI must stay reachable to fix it.
            runtime.startIfEnabled();

            AppHubManager appHub = client.appHubManager();
            boolean webUiRegistered = false;
            try {
                appHub.registerWebUI(ADMIN_HOST, ADMIN_PORT, ADMIN_ROUTE);
                webUiRegistered = true;
                Logger.Infof(TAG, "[admin] registered with portal at route %s%n", ADMIN_ROUTE);
            } catch (Exception e) {
                Logger.Warn(TAG, "[admin] could not register with portal, admin UI stays local-only: " + e.getMessage());
            }
            boolean webUiWasRegistered = webUiRegistered;

            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                if (webUiWasRegistered) {
                    try {
                        appHub.unregisterService();
                    } catch (Exception ignored) {
                        // best-effort cleanup
                    }
                }
                admin.stop();
                runtime.stop();
            }, "moquette-shutdown"));

            // Netty's own event-loop threads (and the admin HTTP server's) keep the
            // JVM alive; just park the main thread until the process is signalled to stop.
            Thread.currentThread().join();

        } catch (Exception e) {
            Logger.Error(TAG, "moquette: " + e.getMessage());
            System.exit(1);
        }
    }
}
