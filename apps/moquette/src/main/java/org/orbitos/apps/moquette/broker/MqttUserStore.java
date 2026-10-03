package org.orbitos.apps.moquette.broker;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Reads/writes a Moquette-compatible password file: one
 * {@code username:sha256hex(password)} line per user. This is exactly the
 * format Moquette's own {@code ResourceAuthenticator} expects (plain,
 * unsalted SHA-256) — not a choice made here, just matched to it.
 *
 * <p>Moquette's parser treats any {@code #} not at column 0 as a malformed
 * comment (parse error), so usernames/passwords containing {@code #} or
 * {@code :} are rejected up front rather than silently corrupting the file.
 */
public final class MqttUserStore {

    // Fixed, well-known location — shown in the admin UI, and used
    // automatically as the broker's password file once it has any users.
    public static final String DEFAULT_RELATIVE_PATH = "config/mqtt_password_file.conf";

    private final Path file;

    public MqttUserStore(Path file) {
        this.file = file;
    }

    public synchronized List<String> listUsernames() throws IOException {
        return new ArrayList<>(readEntries().keySet());
    }

    public synchronized void setUser(String username, String plaintextPassword) throws IOException {
        validate(username, "Username");
        if (plaintextPassword == null || plaintextPassword.isEmpty()) {
            throw new IllegalArgumentException("Password must not be blank");
        }
        validate(plaintextPassword, "Password");
        Map<String, String> entries = readEntries();
        entries.put(username, sha256Hex(plaintextPassword));
        write(entries);
    }

    public synchronized boolean removeUser(String username) throws IOException {
        Map<String, String> entries = readEntries();
        boolean removed = entries.remove(username) != null;
        if (removed) {
            write(entries);
        }
        return removed;
    }

    private static void validate(String s, String field) {
        if (s == null || s.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        if (s.contains(":") || s.contains("#")) {
            throw new IllegalArgumentException(field + " must not contain ':' or '#'");
        }
    }

    private Map<String, String> readEntries() throws IOException {
        Map<String, String> entries = new LinkedHashMap<>();
        if (!Files.exists(file)) {
            return entries;
        }
        for (String line : Files.readAllLines(file, StandardCharsets.UTF_8)) {
            String trimmed = line.trim();
            if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                continue;
            }
            int idx = trimmed.indexOf(':');
            if (idx > 0) {
                entries.put(trimmed.substring(0, idx), trimmed.substring(idx + 1));
            }
        }
        return entries;
    }

    private void write(Map<String, String> entries) throws IOException {
        Path parent = file.toAbsolutePath().getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        StringBuilder sb = new StringBuilder("# managed by the moquette admin UI — do not edit by hand\n");
        for (Map.Entry<String, String> e : entries.entrySet()) {
            sb.append(e.getKey()).append(':').append(e.getValue()).append('\n');
        }
        Files.writeString(file, sb.toString(), StandardCharsets.UTF_8);
    }

    private static String sha256Hex(String s) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(s.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(digest.length * 2);
            for (byte b : digest) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
