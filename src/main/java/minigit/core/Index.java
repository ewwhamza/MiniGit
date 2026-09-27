package minigit.core;

import minigit.exception.MiniGitException;
import minigit.util.FileUtils;
import minigit.util.Hashing;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Collections;
import java.util.Optional;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;

/**
 * The staging area: the exact file list the next commit will contain.
 *
 * <p>Stored as sorted text lines, {@code <hash> <size> <last-modified> <path>}
 * (FR-IDX-3). Changes are held in memory until {@link #save()}.
 */
public final class Index {

    private final Path file;
    private final SortedMap<String, IndexEntry> entries = new TreeMap<>();

    /** Modification time of the index file itself; see {@link #isUnchanged}. */
    private long writtenAt;

    public Index(Path file) {
        this.file = file;
        load();
    }

    public Optional<IndexEntry> get(String path) {
        return Optional.ofNullable(entries.get(path));
    }

    public boolean contains(String path) {
        return entries.containsKey(path);
    }

    public void put(IndexEntry entry) {
        entries.put(entry.path(), entry);
    }

    public void remove(String path) {
        entries.remove(path);
    }

    public boolean isEmpty() {
        return entries.isEmpty();
    }

    /** Entries sorted by path. */
    public Collection<IndexEntry> entries() {
        return Collections.unmodifiableCollection(entries.values());
    }

    public Set<String> paths() {
        return Collections.unmodifiableSet(entries.keySet());
    }

    /** Tracked paths inside directory {@code dir} ({@code ""} for the whole tree). */
    public Set<String> pathsUnder(String dir) {
        if (dir.isEmpty()) {
            return paths();
        }
        // Every path under "src" sorts between "src/" and "src0" ('0' follows '/').
        return Collections.unmodifiableSet(entries.subMap(dir + "/", dir + "0").keySet());
    }

    /**
     * Whether a file's size and modification time still match its entry, meaning it
     * can be treated as unchanged without re-hashing (FR-STAT-5).
     *
     * <p>If the file was staged in the same millisecond the index was written, it
     * could have been edited again within that millisecond without its timestamp
     * changing. Such entries are never trusted, so the caller hashes the file.
     */
    public boolean isUnchanged(IndexEntry entry, long size, long lastModified) {
        return entry.size() == size
                && entry.lastModified() == lastModified
                && entry.lastModified() < writtenAt;
    }

    public void save() {
        StringBuilder out = new StringBuilder();
        for (IndexEntry e : entries.values()) {
            out.append(e.hash()).append(' ').append(e.size()).append(' ')
                    .append(e.lastModified()).append(' ').append(e.path()).append('\n');
        }
        FileUtils.writeAtomically(file, out.toString().getBytes(StandardCharsets.UTF_8));
        writtenAt = readModifiedTime();
    }

    private void load() {
        if (!Files.exists(file)) {
            return;
        }
        String text = new String(FileUtils.readBytes(file), StandardCharsets.UTF_8);
        for (String line : text.split("\n")) {
            if (line.isEmpty()) {
                continue;
            }
            String[] f = line.split(" ", 4);
            try {
                if (f.length != 4 || !Hashing.isFullHash(f[0])) {
                    throw new NumberFormatException();
                }
                entries.put(f[3], new IndexEntry(f[3], f[0], Long.parseLong(f[1]), Long.parseLong(f[2])));
            } catch (NumberFormatException e) {
                throw new MiniGitException("the index file is corrupt; bad line: " + line);
            }
        }
        writtenAt = readModifiedTime();
    }

    private long readModifiedTime() {
        try {
            return Files.getLastModifiedTime(file).toMillis();
        } catch (IOException e) {
            throw new MiniGitException("could not read " + file + ": " + e.getMessage(), e);
        }
    }
}
