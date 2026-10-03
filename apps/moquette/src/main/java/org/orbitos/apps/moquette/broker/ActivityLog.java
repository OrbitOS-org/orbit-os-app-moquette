package org.orbitos.apps.moquette.broker;

import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/** Fixed-size ring buffer of recent broker events, for the admin UI's activity panel. */
public final class ActivityLog {

    public record Entry(Instant time, String type, String clientId, String detail) {}

    private final int capacity;
    private final Deque<Entry> entries = new ArrayDeque<>();

    public ActivityLog(int capacity) {
        this.capacity = capacity;
    }

    public synchronized void add(String type, String clientId, String detail) {
        entries.addFirst(new Entry(Instant.now(), type, clientId, detail));
        while (entries.size() > capacity) {
            entries.removeLast();
        }
    }

    public synchronized List<Entry> snapshot() {
        return new ArrayList<>(entries);
    }
}
