package org.orbitos.apps.moquette.admin;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonPrimitive;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import io.moquette.broker.Server;
import org.orbitos.apps.moquette.broker.ActivityLog;
import org.orbitos.apps.moquette.broker.BrokerConfig;
import org.orbitos.apps.moquette.broker.BrokerRuntime;
import org.orbitos.apps.moquette.broker.MqttUserStore;
import org.orbitos.sdk.logger.Logger;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Minimal embedded HTTP admin UI for the broker — status/clients/activity and
 * a full config editor. Deliberately built on the JDK's own {@link HttpServer}
 * (no extra web-framework dependency) and bound to loopback only; it is meant
 * to be reached through the OrbitOS portal's reverse proxy
 * ({@code AppHubManager.registerWebUI}), not exposed directly on the LAN.
 *
 * <p>Everything under {@code /api} except {@code /api/login} and
 * {@code /api/session} requires a valid session cookie, obtained by logging
 * in with {@link AdminAuth} (default {@code admin/admin} on first run).
 */
public final class AdminServer {

    private static final String TAG = "admin";

    private final BrokerRuntime runtime;
    private final AdminAuth auth;
    private final MqttUserStore mqttUsers;
    // Gson has no built-in java.time support: without this adapter, Instant fields
    // (e.g. ActivityLog.Entry.time) serialize as {"seconds":...,"nanos":...} instead
    // of a string, and the dashboard's `new Date(e.time)` renders "Invalid Date".
    private final Gson gson = new GsonBuilder()
            .registerTypeAdapter(Instant.class, (com.google.gson.JsonSerializer<Instant>)
                    (src, type, ctx) -> new JsonPrimitive(src.toString()))
            .create();
    private final String appVersion;
    private HttpServer http;
    private ExecutorService executor;

    public AdminServer(BrokerRuntime runtime, AdminAuth auth, MqttUserStore mqttUsers) {
        this.runtime = runtime;
        this.auth = auth;
        this.mqttUsers = mqttUsers;
        this.appVersion = readAppVersion();
    }

    /** Reads the "version" field out of the app's own bundled metadata.json. */
    private String readAppVersion() {
        try (InputStream in = getClass().getResourceAsStream("/metadata.json")) {
            if (in == null) {
                return null;
            }
            Map<?, ?> metadata = gson.fromJson(new String(in.readAllBytes(), StandardCharsets.UTF_8), Map.class);
            Object version = metadata != null ? metadata.get("version") : null;
            return version != null ? String.valueOf(version) : null;
        } catch (Exception e) {
            Logger.Warn(TAG, "[admin] could not read app version from metadata.json: " + e.getMessage());
            return null;
        }
    }

    public void start(String bindHost, int port) throws IOException {
        http = HttpServer.create(new InetSocketAddress(bindHost, port), 0);
        executor = Executors.newCachedThreadPool(r -> {
            Thread t = new Thread(r, "moquette-admin");
            t.setDaemon(true);
            return t;
        });
        http.setExecutor(executor);
        http.createContext("/", this::serveDashboard);
        http.createContext("/favicon.svg", ex -> serveResource(ex, "/admin/favicon.svg", "image/svg+xml"));
        http.createContext("/api/login", this::handleLogin);
        http.createContext("/api/logout", this::handleLogout);
        http.createContext("/api/session", this::handleSession);
        http.createContext("/api/account", protect(this::handleAccount));
        http.createContext("/api/status", protect(this::handleStatus));
        http.createContext("/api/clients", protect(this::handleClients));
        http.createContext("/api/activity", protect(this::handleActivity));
        http.createContext("/api/config", protect(this::handleConfig));
        http.createContext("/api/broker/start", protect(ex -> handleBrokerToggle(ex, true)));
        http.createContext("/api/broker/stop", protect(ex -> handleBrokerToggle(ex, false)));
        http.createContext("/api/mqtt-users", protect(this::handleMqttUsers));
        http.start();
        Logger.Infof(TAG, "[admin] listening on %s:%d%n", bindHost, port);
    }

    public void stop() {
        if (http != null) {
            http.stop(0);
        }
        if (executor != null) {
            executor.shutdownNow();
        }
    }

    // -- auth ---------------------------------------------------------

    /** Wraps a handler so it 401s instead of running when there's no valid session. */
    private HttpHandler protect(HttpHandler handler) {
        return ex -> {
            if (!isAuthenticated(ex)) {
                sendJson(ex, 401, Map.of("error", "unauthorized"));
                return;
            }
            handler.handle(ex);
        };
    }

    private boolean isAuthenticated(HttpExchange ex) {
        return auth.isValidSession(sessionCookie(ex));
    }

    private String sessionCookie(HttpExchange ex) {
        List<String> headers = ex.getRequestHeaders().get("Cookie");
        if (headers == null) {
            return null;
        }
        for (String header : headers) {
            for (String part : header.split(";")) {
                String[] kv = part.trim().split("=", 2);
                if (kv.length == 2 && kv[0].equals(AdminAuth.COOKIE_NAME)) {
                    return kv[1];
                }
            }
        }
        return null;
    }

    private void handleLogin(HttpExchange ex) throws IOException {
        if (!"POST".equals(ex.getRequestMethod())) {
            sendEmpty(ex, 405);
            return;
        }
        Map<?, ?> body;
        try {
            body = readJsonBody(ex, Map.class);
        } catch (Exception e) {
            sendJson(ex, 400, Map.of("error", "invalid JSON body"));
            return;
        }
        String username = body != null ? String.valueOf(body.get("username")) : "";
        String password = body != null ? String.valueOf(body.get("password")) : "";
        if (auth.checkUsername(username) && auth.checkPassword(password)) {
            String token = auth.createSession();
            ex.getResponseHeaders().add("Set-Cookie",
                    AdminAuth.COOKIE_NAME + "=" + token + "; Path=/; HttpOnly; SameSite=Strict");
            sendJson(ex, 200, Map.of("ok", true, "username", auth.username()));
        } else {
            sendJson(ex, 401, Map.of("error", "invalid username or password"));
        }
    }

    private void handleLogout(HttpExchange ex) throws IOException {
        if (!"POST".equals(ex.getRequestMethod())) {
            sendEmpty(ex, 405);
            return;
        }
        auth.invalidateSession(sessionCookie(ex));
        ex.getResponseHeaders().add("Set-Cookie", AdminAuth.COOKIE_NAME + "=; Path=/; HttpOnly; Max-Age=0");
        sendJson(ex, 200, Map.of("ok", true));
    }

    private void handleSession(HttpExchange ex) throws IOException {
        if (!"GET".equals(ex.getRequestMethod())) {
            sendEmpty(ex, 405);
            return;
        }
        boolean authenticated = isAuthenticated(ex);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("authenticated", authenticated);
        body.put("username", authenticated ? auth.username() : null);
        sendJson(ex, 200, body);
    }

    private void handleAccount(HttpExchange ex) throws IOException {
        if (!"POST".equals(ex.getRequestMethod())) {
            sendEmpty(ex, 405);
            return;
        }
        try {
            Map<?, ?> body = readJsonBody(ex, Map.class);
            if (body == null) {
                sendJson(ex, 400, Map.of("error", "empty body"));
                return;
            }
            if (!auth.checkPassword(String.valueOf(body.get("currentPassword")))) {
                sendJson(ex, 400, Map.of("error", "current password is incorrect"));
                return;
            }
            Object newUsername = body.get("username");
            if (newUsername != null && !String.valueOf(newUsername).isBlank()) {
                auth.setUsername(String.valueOf(newUsername));
            }
            Object newPassword = body.get("newPassword");
            if (newPassword != null && !String.valueOf(newPassword).isBlank()) {
                auth.setPassword(String.valueOf(newPassword));
            }
            sendJson(ex, 200, Map.of("ok", true, "username", auth.username()));
        } catch (Exception e) {
            sendJson(ex, 400, Map.of("error", String.valueOf(e.getMessage())));
        }
    }

    private <T> T readJsonBody(HttpExchange ex, Class<T> type) throws IOException {
        try (InputStream in = ex.getRequestBody()) {
            return gson.fromJson(new String(in.readAllBytes(), StandardCharsets.UTF_8), type);
        }
    }

    // -- routes ---------------------------------------------------------

    private void serveDashboard(HttpExchange ex) throws IOException {
        serveResource(ex, "/admin/index.html", "text/html; charset=utf-8");
    }

    private void serveResource(HttpExchange ex, String resourcePath, String contentType) throws IOException {
        if (!"GET".equals(ex.getRequestMethod())) {
            sendEmpty(ex, 405);
            return;
        }
        byte[] body;
        try (InputStream in = getClass().getResourceAsStream(resourcePath)) {
            if (in == null) {
                sendJson(ex, 500, Map.of("error", "resource missing: " + resourcePath));
                return;
            }
            body = in.readAllBytes();
        }
        ex.getResponseHeaders().set("Content-Type", contentType);
        ex.sendResponseHeaders(200, body.length);
        try (OutputStream os = ex.getResponseBody()) {
            os.write(body);
        }
    }

    private void handleStatus(HttpExchange ex) throws IOException {
        if (!"GET".equals(ex.getRequestMethod())) {
            sendEmpty(ex, 405);
            return;
        }
        sendJson(ex, 200, statusPayload());
    }

    private void handleBrokerToggle(HttpExchange ex, boolean start) throws IOException {
        if (!"POST".equals(ex.getRequestMethod())) {
            sendEmpty(ex, 405);
            return;
        }
        try {
            if (start) {
                runtime.enable();
            } else {
                runtime.disable();
            }
            sendJson(ex, 200, statusPayload());
        } catch (Exception e) {
            sendJson(ex, 400, Map.of("error", String.valueOf(e.getMessage())));
        }
    }

    private Map<String, Object> statusPayload() {
        Map<String, Object> status = new LinkedHashMap<>();
        status.put("running", runtime.isRunning());
        Instant startedAt = runtime.startedAt();
        status.put("startedAt", startedAt == null ? null : startedAt.toString());
        status.put("uptimeSeconds", startedAt == null ? 0 : Duration.between(startedAt, Instant.now()).getSeconds());
        status.put("clientCount", runtime.connectedClients().size());
        status.put("moquetteVersion", Server.MOQUETTE_VERSION);
        status.put("appVersion", appVersion);
        status.put("config", runtime.config());
        return status;
    }

    private void handleClients(HttpExchange ex) throws IOException {
        String method = ex.getRequestMethod();
        String path = ex.getRequestURI().getPath();

        // POST /api/clients/{clientId}/kick
        if ("POST".equals(method) && path.endsWith("/kick")) {
            String rest = path.substring("/api/clients/".length());
            String clientId = java.net.URLDecoder.decode(rest.substring(0, rest.length() - "/kick".length()), StandardCharsets.UTF_8);
            boolean ok = runtime.kickClient(clientId);
            sendJson(ex, ok ? 200 : 404, Map.of("kicked", ok, "clientId", clientId));
            return;
        }
        if ("GET".equals(method)) {
            sendJson(ex, 200, runtime.connectedClients());
            return;
        }
        sendEmpty(ex, 405);
    }

    private void handleActivity(HttpExchange ex) throws IOException {
        if (!"GET".equals(ex.getRequestMethod())) {
            sendEmpty(ex, 405);
            return;
        }
        List<ActivityLog.Entry> entries = runtime.recentActivity();
        sendJson(ex, 200, entries);
    }

    private void handleMqttUsers(HttpExchange ex) throws IOException {
        String method = ex.getRequestMethod();
        String path = ex.getRequestURI().getPath();

        // POST /api/mqtt-users/{username}/delete
        if ("POST".equals(method) && path.endsWith("/delete")) {
            String rest = path.substring("/api/mqtt-users/".length());
            String username = java.net.URLDecoder.decode(rest.substring(0, rest.length() - "/delete".length()), StandardCharsets.UTF_8);
            try {
                boolean removed = mqttUsers.removeUser(username);
                sendJson(ex, removed ? 200 : 404, mqttUsersPayload());
            } catch (Exception e) {
                sendJson(ex, 400, Map.of("error", String.valueOf(e.getMessage())));
            }
            return;
        }

        switch (method) {
            case "GET" -> sendJson(ex, 200, mqttUsersPayload());
            case "POST" -> {
                try {
                    Map<?, ?> body = readJsonBody(ex, Map.class);
                    if (body == null) {
                        sendJson(ex, 400, Map.of("error", "empty or invalid JSON body"));
                        return;
                    }
                    mqttUsers.setUser(String.valueOf(body.get("username")), String.valueOf(body.get("password")));
                    sendJson(ex, 200, mqttUsersPayload());
                } catch (Exception e) {
                    sendJson(ex, 400, Map.of("error", String.valueOf(e.getMessage())));
                }
            }
            default -> sendEmpty(ex, 405);
        }
    }

    private Map<String, Object> mqttUsersPayload() throws IOException {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("path", MqttUserStore.DEFAULT_RELATIVE_PATH);
        out.put("usernames", mqttUsers.listUsernames());
        return out;
    }

    private void handleConfig(HttpExchange ex) throws IOException {
        switch (ex.getRequestMethod()) {
            case "GET" -> sendJson(ex, 200, runtime.config());
            case "POST" -> {
                try {
                    BrokerConfig newConfig;
                    try (InputStream in = ex.getRequestBody()) {
                        newConfig = gson.fromJson(new String(in.readAllBytes(), StandardCharsets.UTF_8), BrokerConfig.class);
                    }
                    if (newConfig == null) {
                        sendJson(ex, 400, Map.of("error", "empty or invalid JSON body"));
                        return;
                    }
                    runtime.applyConfig(newConfig);
                    sendJson(ex, 200, runtime.config());
                } catch (Exception e) {
                    // Covers malformed JSON (Gson throws unchecked), bad field values, and
                    // any failure while (re)starting the broker with the new config —
                    // Moquette itself can throw unchecked RuntimeExceptions on bind failure.
                    sendJson(ex, 400, Map.of("error", String.valueOf(e.getMessage())));
                }
            }
            default -> sendEmpty(ex, 405);
        }
    }

    // -- plumbing ---------------------------------------------------------

    private void sendJson(HttpExchange ex, int status, Object body) throws IOException {
        byte[] json = gson.toJson(body).getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        ex.sendResponseHeaders(status, json.length);
        try (OutputStream os = ex.getResponseBody()) {
            os.write(json);
        }
    }

    private void sendEmpty(HttpExchange ex, int status) throws IOException {
        ex.sendResponseHeaders(status, -1);
        ex.close();
    }
}
