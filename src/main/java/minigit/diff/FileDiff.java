package minigit.diff;

import minigit.api.ChangeType;

import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * The changes to one file between two versions.
 *
 * @param path   repository-relative path
 * @param type   whether the file was added, modified, or deleted
 * @param binary whether either version is binary, in which case {@code hunks} is empty
 * @param hunks  the changed regions, with context
 */
public record FileDiff(String path, ChangeType type, boolean binary, List<Hunk> hunks) {

    /** Bytes checked for a NUL character to decide whether a file is binary (FR-DIFF-7). */
    static final int BINARY_CHECK_BYTES = 8000;

    /**
     * Diffs two versions of a file.
     *
     * @param before the old content, or {@code null} if the file was added
     * @param after  the new content, or {@code null} if the file was deleted
     */
    public static FileDiff of(String path, byte[] before, byte[] after) {
        ChangeType type = before == null ? ChangeType.ADDED : after == null ? ChangeType.DELETED : ChangeType.MODIFIED;
        byte[] a = before == null ? new byte[0] : before;
        byte[] b = after == null ? new byte[0] : after;
        if (isBinary(a) || isBinary(b)) {
            return new FileDiff(path, type, true, List.of());
        }
        List<Edit> edits = LineDiff.diff(
                LineDiff.splitLines(new String(a, StandardCharsets.UTF_8)),
                LineDiff.splitLines(new String(b, StandardCharsets.UTF_8)));
        return new FileDiff(path, type, false, Hunk.fromEdits(edits, Hunk.CONTEXT));
    }

    public int additions() {
        return count(Edit.Type.INSERT);
    }

    public int deletions() {
        return count(Edit.Type.DELETE);
    }

    private int count(Edit.Type type) {
        return (int) hunks.stream().flatMap(h -> h.edits().stream()).filter(e -> e.type() == type).count();
    }

    static boolean isBinary(byte[] data) {
        int limit = Math.min(data.length, BINARY_CHECK_BYTES);
        for (int i = 0; i < limit; i++) {
            if (data[i] == 0) {
                return true;
            }
        }
        return false;
    }
}
