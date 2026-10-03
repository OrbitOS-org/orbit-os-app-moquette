package org.orbitos.apps.moquette.broker;

import io.moquette.interception.AbstractInterceptHandler;
import io.moquette.interception.messages.InterceptConnectMessage;
import io.moquette.interception.messages.InterceptDisconnectMessage;
import io.moquette.interception.messages.InterceptPublishMessage;
import org.orbitos.sdk.logger.Logger;

import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Forwards broker lifecycle events into the OrbitOS log stream and into an
 * {@link ActivityLog} that the admin web UI polls, and tracks per-client
 * connect times (not exposed by Moquette's own {@code ClientDescriptor}).
 */
public final class OrbitBridgeListener extends AbstractInterceptHandler {

    private static final String TAG = "main";

    private final ActivityLog activityLog;
    private final ConcurrentMap<String, Instant> connectedAt = new ConcurrentHashMap<>();

    public OrbitBridgeListener(ActivityLog activityLog) {
        this.activityLog = activityLog;
    }

    public Instant connectedAt(String clientId) {
        return connectedAt.get(clientId);
    }

    @Override
    public String getID() {
        return "orbitos-moquette-bridge";
    }

    @Override
    public void onConnect(InterceptConnectMessage msg) {
        connectedAt.put(msg.getClientID(), Instant.now());
        Logger.Infof(TAG, "[MQTT] connected clientId=%s%n", msg.getClientID());
        activityLog.add("CONNECT", msg.getClientID(), "");
    }

    @Override
    public void onDisconnect(InterceptDisconnectMessage msg) {
        connectedAt.remove(msg.getClientID());
        Logger.Infof(TAG, "[MQTT] disconnected clientId=%s%n", msg.getClientID());
        activityLog.add("DISCONNECT", msg.getClientID(), "");
    }

    @Override
    public void onPublish(InterceptPublishMessage msg) {
        Logger.Infof(TAG, "[MQTT] publish topic=%s clientId=%s%n", msg.getTopicName(), msg.getClientID());
        activityLog.add("PUBLISH", msg.getClientID(), msg.getTopicName());
        super.onPublish(msg);
    }

    @Override
    public void onSessionLoopError(Throwable error) {
        Logger.Errorf(TAG, "[MQTT] session loop error: %s%n", error.getMessage());
        activityLog.add("ERROR", "", String.valueOf(error.getMessage()));
    }
}
