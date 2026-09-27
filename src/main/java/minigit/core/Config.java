package minigit.core;

import minigit.exception.MiniGitException;
import minigit.util.FileUtils;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.regex.Pattern;

/**
 * Repository settings such as {@code user.name}, stored in {@code .minigit/config}
 * as sorted {@code key = value} lines. Keys are case-insensitive and stored lowercase.
 */
public final class Config {

    public static final String USER_NAME = "user.name";
    public static final String USER_EMAIL = "user.email";

    private static final Pattern KEY = Pattern.compile("[a-z][a-z0-9-]*\\.[a-z][a-z0-9-]*");

    private final Path file;
    private final SortedMap<String, String> values = new TreeMap<>();

    public Config(Path file) {
        this.file = file;
        load();
    }

    public Optional<String> get(String key) {
        return Optional.ofNullable(values.get(normalizeKey(key)));
    }

    public void set(String key, String value) {
        String v = value.strip();
        if (v.isEmpty()) {
            throw new MiniGitException("value for '" + key + "' cannot be empty");
        }
        if (v.contains("\n") || v.contains("\r")) {
            throw new MiniGitException("value for '" + key + "' must be a single line");
        }
        values.put(normalizeKey(key), v);
        save();
    }

    /** Removes a key. Returns {@code false} if it was not set. */
    public boolean unset(String key) {
        boolean removed = values.remove(normalizeKey(key)) != null;
        if (removed) {
            save();
        }
        return removed;
    }

    /** All settings, sorted by key. */
    public Map<String, String> all() {
        return Collections.unmodifiableSortedMap(values);
    }

    private static String normalizeKey(String key) {
        String k = key.strip().toLowerCase();
        if (!KEY.matcher(k).matches()) {
            throw new MiniGitException("invalid config key '" + key + "'; expected the form section.name, e.g. user.name");
        }
        return k;
    }

    private void load() {
        if (!Files.exists(file)) {
            return;
        }
        String text = new String(FileUtils.readBytes(file), StandardCharsets.UTF_8);
        for (String line : text.split("\\R")) {
            String trimmed = line.strip();
            if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                continue;
            }
            int eq = trimmed.indexOf('=');
            if (eq < 0) {
                throw new MiniGitException("malformed line in " + file + ": " + line);
            }
            values.put(normalizeKey(trimmed.substring(0, eq)), trimmed.substring(eq + 1).strip());
        }
    }

    private void save() {
        StringBuilder out = new StringBuilder();
        values.forEach((k, v) -> out.append(k).append(" = ").append(v).append('\n'));
        FileUtils.writeAtomically(file, out.toString().getBytes(StandardCharsets.UTF_8));
    }
}
