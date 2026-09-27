package minigit.model;

import minigit.exception.MiniGitException;
import minigit.util.Hashing;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * A directory listing: names mapped to blob (file) or tree (subdirectory) hashes.
 *
 * <p>Stored as one text line per entry, sorted by name, so the same directory
 * contents always produce the same hash:
 * <pre>
 * 100644 blob 3a7bd3e2360a3d29eea436fcfb7e44c735d117c4&lt;TAB&gt;README.md
 * 040000 tree 9c1185a5c5e9fc54612808977ee8f548b2258d31&lt;TAB&gt;src
 * </pre>
 */
public final class Tree extends GitObject {

    private final List<TreeEntry> entries;

    public Tree(List<TreeEntry> entries) {
        List<TreeEntry> sorted = new ArrayList<>(entries);
        sorted.sort(Comparator.comparing(TreeEntry::name));
        for (int i = 1; i < sorted.size(); i++) {
            if (sorted.get(i).name().equals(sorted.get(i - 1).name())) {
                throw new IllegalArgumentException("duplicate tree entry: " + sorted.get(i).name());
            }
        }
        this.entries = List.copyOf(sorted);
    }

    /** Entries sorted by name. */
    public List<TreeEntry> entries() {
        return entries;
    }

    @Override
    public ObjectType type() {
        return ObjectType.TREE;
    }

    @Override
    public byte[] content() {
        StringBuilder out = new StringBuilder();
        for (TreeEntry e : entries) {
            out.append(e.mode()).append(' ').append(e.type().tag()).append(' ')
                    .append(e.hash()).append('\t').append(e.name()).append('\n');
        }
        return out.toString().getBytes(StandardCharsets.UTF_8);
    }

    public static Tree parse(String hash, byte[] content) {
        List<TreeEntry> entries = new ArrayList<>();
        String text = new String(content, StandardCharsets.UTF_8);
        for (String line : text.split("\n")) {
            if (line.isEmpty()) {
                continue;
            }
            int tab = line.indexOf('\t');
            String[] fields = tab < 0 ? new String[0] : line.substring(0, tab).split(" ");
            if (fields.length != 3 || !Hashing.isFullHash(fields[2])) {
                throw new MiniGitException("tree " + hash + " has a malformed entry: " + line);
            }
            ObjectType type = ObjectType.fromTag(fields[1])
                    .orElseThrow(() -> new MiniGitException("tree " + hash + " has an unknown entry type: " + line));
            entries.add(new TreeEntry(fields[0], type, fields[2], line.substring(tab + 1)));
        }
        return new Tree(entries);
    }
}
