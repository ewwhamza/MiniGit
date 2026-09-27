package minigit.core;

import minigit.model.Tree;
import minigit.model.TreeEntry;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;

/** Converts between the flat file list of the index and nested tree objects. */
public final class Trees {

    private Trees() {
    }

    /**
     * Writes one tree per directory, deepest first, and returns the root tree's
     * hash (FR-CMT-1). Unchanged directories produce the same hash as before, so
     * no new objects are written for them.
     */
    public static String write(ObjectStore store, Collection<IndexEntry> entries) {
        Dir root = new Dir();
        for (IndexEntry e : entries) {
            String[] parts = e.path().split("/");
            Dir dir = root;
            for (int i = 0; i < parts.length - 1; i++) {
                dir = dir.subdirs.computeIfAbsent(parts[i], k -> new Dir());
            }
            dir.files.put(parts[parts.length - 1], e.hash());
        }
        return writeDir(store, root);
    }

    /** Every file in a tree, as repository-relative path → blob hash, sorted by path. */
    public static SortedMap<String, String> flatten(ObjectStore store, String treeHash) {
        SortedMap<String, String> files = new TreeMap<>();
        collect(store, treeHash, "", files);
        return files;
    }

    private static String writeDir(ObjectStore store, Dir dir) {
        List<TreeEntry> entries = new ArrayList<>();
        dir.files.forEach((name, hash) -> entries.add(TreeEntry.file(name, hash)));
        dir.subdirs.forEach((name, sub) -> entries.add(TreeEntry.directory(name, writeDir(store, sub))));
        return store.write(new Tree(entries));
    }

    private static void collect(ObjectStore store, String treeHash, String prefix, Map<String, String> out) {
        for (TreeEntry e : store.readTree(treeHash).entries()) {
            String path = prefix + e.name();
            if (e.isDirectory()) {
                collect(store, e.hash(), path + "/", out);
            } else {
                out.put(path, e.hash());
            }
        }
    }

    private static final class Dir {
        final SortedMap<String, String> files = new TreeMap<>();
        final SortedMap<String, Dir> subdirs = new TreeMap<>();
    }
}
