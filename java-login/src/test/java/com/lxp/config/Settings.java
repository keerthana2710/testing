package com.lxp.config;

import io.github.cdimascio.dotenv.Dotenv;
import io.github.cdimascio.dotenv.DotenvEntry;
import io.github.cdimascio.dotenv.DotenvException;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * Settings, read from .env.
 *
 * Looks for .env in the working folder and every folder above it, then above
 * the compiled test classes, so it is found however the run was started.
 *
 * Order: a -D flag (one run, e.g. ./run.sh -DBASE_URL=...), then .env, then a
 * terminal environment variable. .env beats the terminal because VS Code
 * terminals can hold a stale copy of an older .env.
 */
public final class Settings {

    private static final Path DOTENV_FILE = findDotenv();
    private static final Dotenv DOTENV = loadDotenv();

    public static final String BASE_URL = origin(get("BASE_URL", "https://lxp-stg.campusinsights.ai"));

    public static final String EMAIL = get("LXP_EMAIL", "");

    static {
        // say which site and account this run uses, and where each came from
        System.out.println("[settings] BASE_URL = " + BASE_URL + " (from " + sourceOf("BASE_URL") + ")");
        System.out.println("[settings] LXP_EMAIL = " + EMAIL + " (from " + sourceOf("LXP_EMAIL") + ")");
    }
    public static final String BROWSER = get("BROWSER", "chrome").trim().toLowerCase();
    public static final boolean HEADLESS = isTruthy(get("HEADLESS", "true"));
    public static final Duration TIMEOUT = Duration.ofSeconds(Long.parseLong(get("TIMEOUT", "20").trim()));

    // not final: the profile scenario changes it mid-run
    private static volatile String password = get("LXP_PASSWORD", "");

    /** Failure screenshots land here. */
    public static final Path SCREENSHOT_DIR = Paths.get("target", "screenshots");

    /** Files the browser downloads (e.g. a course exported as .imscc) land here. */
    public static final Path DOWNLOAD_DIR = Paths.get("target", "downloads").toAbsolutePath();

    private Settings() {
    }

    public static boolean hasCredentials() {
        return !EMAIL.isBlank() && !password.isBlank();
    }

    public static String password() {
        return password;
    }

    /** One role's sign-in, from LXP_<ROLE>_EMAIL and LXP_<ROLE>_PASSWORD in .env. */
    public record Account(String role, String email, String password) {
        public boolean isConfigured() {
            return !email.isBlank() && !password.isBlank();
        }
    }

    /** "admin", "teacher" or "student". */
    public static Account account(String role) {
        String key = "LXP_" + role.strip().toUpperCase() + "_";
        return new Account(role.strip().toLowerCase(), get(key + "EMAIL", "").strip(), get(key + "PASSWORD", ""));
    }
    /**
     * After the account's password changes: use the new one for the rest of
     * the run, and write it to LXP_PASSWORD in .env so the next run signs in.
     */
    public static void changePassword(String newPassword) {
        password = newPassword;
        Path file = DOTENV_FILE == null ? Paths.get(".env") : DOTENV_FILE;
        try {
            List<String> lines = Files.exists(file) ? new ArrayList<>(Files.readAllLines(file)) : new ArrayList<>();
            boolean replaced = false;
            for (int i = 0; i < lines.size(); i++) {
                if (lines.get(i).startsWith("LXP_PASSWORD=")) {
                    lines.set(i, "LXP_PASSWORD=" + newPassword);
                    replaced = true;
                }
            }
            if (!replaced) {
                lines.add("LXP_PASSWORD=" + newPassword);
            }
            Files.write(file, lines);
        } catch (IOException e) {
            throw new UncheckedIOException("Password changed, but " + file + " could not be updated", e);
        }
    }

    // Without this, a run started from another folder (an IDE "Run" button,
    // a feature-file play icon) found no .env and silently used the staging
    // fallback for BASE_URL.
    private static Path findDotenv() {
        Path found = findUpwards(Paths.get("").toAbsolutePath());
        if (found == null) {
            try {
                // target/test-classes -> java-login -> the folder holding .env
                Path classes = Paths.get(Settings.class.getProtectionDomain().getCodeSource().getLocation().toURI());
                found = findUpwards(classes);
            } catch (Exception ignored) {
                // no code source to start from
            }
        }
        if (found != null) {
            System.out.println("[settings] using " + found);
        } else {
            System.out.println("[settings] no .env found, using defaults");
        }
        return found;
    }

    private static Path findUpwards(Path dir) {
        for (Path d = dir; d != null; d = d.getParent()) {
            Path file = d.resolve(".env");
            if (Files.isRegularFile(file)) {
                return file;
            }
        }
        return null;
    }

    private static Dotenv loadDotenv() {
        if (DOTENV_FILE != null) {
            try {
                return Dotenv.configure().directory(DOTENV_FILE.getParent().toString()).ignoreIfMalformed().load();
            } catch (DotenvException ignored) {
                // fall back to no file
            }
        }
        return Dotenv.configure().ignoreIfMissing().load();
    }

    private static String get(String key, String fallback) {
        String fromProperty = System.getProperty(key);
        if (fromProperty != null && !fromProperty.isBlank()) {
            return fromProperty;
        }
        String fromFile = fromDotenvFile(key);
        if (fromFile != null) {
            return fromFile;
        }
        String fromEnv = System.getenv(key);
        if (fromEnv != null && !fromEnv.isBlank()) {
            return fromEnv;
        }
        return fallback;
    }

    // Dotenv.get() checks the environment first; only the file's own lines count here.
    private static String fromDotenvFile(String key) {
        if (DOTENV == null) {
            return null;
        }
        for (DotenvEntry entry : DOTENV.entries(Dotenv.Filter.DECLARED_IN_ENV_FILE)) {
            if (entry.getKey().equals(key)) {
                return entry.getValue();
            }
        }
        return null;
    }

    private static String sourceOf(String key) {
        String fromProperty = System.getProperty(key);
        if (fromProperty != null && !fromProperty.isBlank()) {
            return "-D" + key;
        }
        if (fromDotenvFile(key) != null) {
            return String.valueOf(DOTENV_FILE);
        }
        String fromEnv = System.getenv(key);
        if (fromEnv != null && !fromEnv.isBlank()) {
            return "the terminal's environment variable, not in .env";
        }
        return "the built-in default";
    }

    private static boolean isTruthy(String value) {
        String v = value.trim().toLowerCase();
        return v.equals("1") || v.equals("true") || v.equals("yes");
    }

    /**
     * Just scheme and host: pages add their own path, so a BASE_URL copied from
     * the address bar (".../welcome?next=%2Fhome") must not keep its page.
     */
    private static String origin(String url) {
        URI uri = URI.create(url.trim());
        if (uri.getScheme() == null || uri.getRawAuthority() == null) {
            throw new IllegalArgumentException("BASE_URL must look like https://host, got: " + url);
        }
        return uri.getScheme() + "://" + uri.getRawAuthority();
    }
}
