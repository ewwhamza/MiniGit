package minigit.core;

import minigit.exception.CheckoutConflictException;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.SortedMap;
import java.util.TreeSet;

/**
 * Moves the working tree and index from HEAD's snapshot to another commit's
 * (FR-CO-1 to FR-CO-3).
 *
 * <p>Only files that differ between the two commits are touched. Uncommitted
 * changes to other files are carried over untouched, as in Git. If any file that
 * must change has uncommitted changes of its own, or an untracked file is in the
 * way, the whole checkout is refused before anything is written.
 */
public final class Checkout {

    private Checkout() {
    }

    /**
     * Updates the working tree and index to match {@code target}. Does not move HEAD;
     * the caller does that once the files are in place.
     *
     * @return the number of files written or deleted
     * @throws CheckoutConflictException if uncommitted work would be lost; nothing is changed
     */
    public static int apply(Repository repo, String target) {
        SortedMap<String, String> current = repo.headFiles();
        SortedMap<String, String> wanted = repo.commitFiles(target);
        Index index = repo.loadIndex();

        TreeSet<String> changing = new TreeSet<>(current.keySet());
        changing.addAll(wanted.keySet());
        changing.removeIf(path -> Objects.equals(current.get(path), wanted.get(path)));

        List<String> conflicts = new ArrayList<>();
        for (String path : changing) {
            if (wouldLoseWork(repo, index, path, current.get(path), wanted.get(path))) {
                conflicts.add(path);
            }
        }
        if (!conflicts.isEmpty()) {
            throw new CheckoutConflictException(conflicts);
        }

        // Deletions first, so a file can be replaced by a folder of the same name.
        for (String path : changing) {
            // An untracked copy (e.g. after 'rm --cached') is the user's file: leave it.
            if (!wanted.containsKey(path) && index.contains(path)) {
                repo.files().delete(path);
                index.remove(path);
            }
        }
        for (Map.Entry<String, String> e : wanted.entrySet()) {
            if (changing.contains(e.getKey())) {
                index.put(repo.writeWorkingFile(e.getKey(), e.getValue()));
            }
        }
        index.save();
        return changing.size();
    }

    /**
     * Whether replacing {@code path} (HEAD version {@code headHash}, target version
     * {@code targetHash}; either may be null) would destroy something not committed.
     */
    private static boolean wouldLoseWork(Repository repo, Index index, String path,
                                         String headHash, String targetHash) {
        Optional<IndexEntry> entry = index.get(path);
        if (entry.isEmpty()) {
            if (headHash != null) {
                // Staged deletion: the file only survives if the target deletes it too.
                return targetHash != null;
            }
            // Untracked file in the way, unless it already has the target's content.
            Optional<String> onDisk = repo.workingHash(path);
            return onDisk.isPresent() && !onDisk.get().equals(targetHash);
        }
        String staged = entry.get().hash();
        Optional<String> onDisk = repo.workingHash(index, entry.get());
        boolean stagedChange = !staged.equals(headHash);
        boolean localChange = onDisk.map(h -> !h.equals(staged)).orElse(true);
        if (!stagedChange && !localChange) {
            return false;
        }
        // Harmless if the working copy already matches what checkout would write.
        return !Objects.equals(onDisk.orElse(null), targetHash) || !Objects.equals(staged, targetHash);
    }
}
