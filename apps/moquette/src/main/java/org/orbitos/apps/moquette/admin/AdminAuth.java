package org.orbitos.apps.moquette.admin;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.EnumSet;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Session-cookie auth for the admin UI. Credentials are salted-hashed and
 * persisted to {@code config/admin.properties}; on first run (file missing)
 * they default to {@code admin/admin} — change this from the admin UI's
 * Account section before exposing the app beyond your own machine.
 */
public final class AdminAuth {

    public static final String COOKIE_NAME = "moquette_admin_session";

    private static final SecureRandom RANDOM = new SecureRandom();

    private final Path credentialsFile;
    private final Set<String> sessions = ConcurrentHashMap.newKeySet();

    private String username;
    private String passwordHash; // "<base64 salt>:<base64 hash>"

    public AdminAuth(Path credentialsFile) throws IOException {
        this.credentialsFile = credentialsFile;
        load();
    }

    private synchronized void load() throws IOException {
        if (Files.exists(credentialsFile)) {
            Properties p = new Properties();
            try (InputStream in = Files.newInputStream(credentialsFile)) {
                p.load(in);
            }
            username = p.getProperty("username", "admin");
            passwordHash = p.getProperty("passwordHash");
            if (passwordHash == null) {
                setPassword("admin");
            }
        } else {
            username = "admin";
            setPassword("admin");
        }
    }

    private synchronized void save() throws IOException {
        Properties p = new Properties();
        p.setProperty("username", username);
        p.setProperty("passwordHash", passwordHash);
        Path parent = credentialsFile.toAbsolutePath().getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        try (OutputStream out = Files.newOutputStream(credentialsFile)) {
            p.store(out, "moquette admin credentials - change the default password from the admin UI");
        }
        tryRestrictPermissions();
    }

    private void tryRestrictPermissions() {
        try {
            Files.setPosixFilePermissions(credentialsFile,
                    EnumSet.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE));
        } catch (UnsupportedOperationException | IOException ignored) {
            // Non-POSIX filesystem (e.g. Windows dev machine) — best effort only.
        }
    }

    public synchronized String username() {
        return username;
    }

    public synchronized boolean checkUsername(String candidate) {
        return username.equals(candidate);
    }

    public synchronized void setUsername(String newUsername) throws IOException {
        this.username = newUsername;
        save();
    }

    public synchronized void setPassword(String newPassword) throws IOException {
        byte[] salt = new byte[16];
        RANDOM.nextBytes(salt);
        this.passwordHash = Base64.getEncoder().encodeToString(salt) + ":" + hash(newPassword, salt);
        save();
    }

    public synchronized boolean checkPassword(String candidate) {
        if (passwordHash == null || candidate == null) {
            return false;
        }
        String[] parts = passwordHash.split(":", 2);
        if (parts.length != 2) {
            return false;
        }
        byte[] salt = Base64.getDecoder().decode(parts[0]);
        return constantTimeEquals(hash(candidate, salt), parts[1]);
    }

    private static String hash(String password, byte[] salt) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            md.update(salt);
            return Base64.getEncoder().encodeToString(md.digest(password.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private static boolean constantTimeEquals(String a, String b) {
        if (a.length() != b.length()) {
            return false;
        }
        int diff = 0;
        for (int i = 0; i < a.length(); i++) {
            diff |= a.charAt(i) ^ b.charAt(i);
        }
        return diff == 0;
    }

    // -- sessions ---------------------------------------------------------

    public String createSession() {
        byte[] tokenBytes = new byte[32];
        RANDOM.nextBytes(tokenBytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(tokenBytes);
        sessions.add(token);
        return token;
    }

    public boolean isValidSession(String token) {
        return token != null && sessions.contains(token);
    }

    public void invalidateSession(String token) {
        if (token != null) {
            sessions.remove(token);
        }
    }
}
