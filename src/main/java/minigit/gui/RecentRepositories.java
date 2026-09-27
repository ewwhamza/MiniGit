package minigit.gui;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.prefs.BackingStoreException;
import java.util.prefs.Preferences;

/**
 * The last few repositories opened, remembered between runs with
 * {@link Preferences} (FR-GUI-21). Failure to read or write preferences is
 * never an error; the list is simply empty.
 */
public final class RecentRepositories {

    private static final int MAX = 5;
    private static final String KEY = "recentRepositories";

    private final Preferences prefs;

    public RecentRepositories() {
        this(Preferences.userRoot().node("minigit/gui"));
    }

    RecentRepositories(Preferences prefs) {
        this.prefs = prefs;
    }

    /** Most recent first; folders that no longer exist are skipped. */
    public List<Path> list() {
        List<Path> result = new ArrayList<>();
        try {
            for (String line : prefs.get(KEY, "").split("\n")) {
                if (!line.isBlank()) {
                    Path p = Path.of(line);
                    if (Files.isDirectory(p)) {
                        result.add(p);
                    }
                }
            }
        } catch (RuntimeException e) {
            // Unreadable preferences or a malformed path: treat as no history.
        }
        return result;
    }

    public void add(Path repo) {
        List<Path> list = new ArrayList<>(list());
        Path normalized = repo.toAbsolutePath().normalize();
        list.remove(normalized);
        list.add(0, normalized);
        List<String> lines = list.stream().limit(MAX).map(Path::toString).toList();
        try {
            prefs.put(KEY, String.join("\n", lines));
            prefs.flush();
        } catch (BackingStoreException | RuntimeException e) {
            // Not remembering a recent folder is harmless.
        }
    }
}
