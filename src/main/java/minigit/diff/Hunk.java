package minigit.diff;

import java.util.ArrayList;
import java.util.List;

/**
 * A block of nearby changes with surrounding context lines (FR-DIFF-3).
 *
 * @param oldStart 1-based first old line (or the line before, if {@code oldCount} is 0)
 * @param newStart 1-based first new line (or the line before, if {@code newCount} is 0)
 */
public record Hunk(int oldStart, int oldCount, int newStart, int newCount, List<Edit> edits) {

    /** Lines of unchanged context shown around each change. */
    public static final int CONTEXT = 3;

    /** e.g. {@code @@ -10,6 +10,7 @@} */
    public String header() {
        return "@@ -" + range(oldStart, oldCount) + " +" + range(newStart, newCount) + " @@";
    }

    private static String range(int start, int count) {
        return count == 1 ? String.valueOf(start) : start + "," + count;
    }

    /**
     * Groups edits into hunks. Changes separated by at most {@code 2 * context}
     * unchanged lines share a hunk, so context lines are never printed twice.
     */
    public static List<Hunk> fromEdits(List<Edit> edits, int context) {
        List<Hunk> hunks = new ArrayList<>();
        int i = 0;
        while (i < edits.size()) {
            if (edits.get(i).type() == Edit.Type.EQUAL) {
                i++;
                continue;
            }
            int start = Math.max(0, i - context);
            int end = i;
            // Extend while the next change is close enough to join this hunk.
            int k = i;
            while (k < edits.size()) {
                if (edits.get(k).type() != Edit.Type.EQUAL) {
                    end = k;
                    k++;
                    continue;
                }
                int run = 0;
                while (k + run < edits.size() && edits.get(k + run).type() == Edit.Type.EQUAL) {
                    run++;
                }
                if (k + run >= edits.size() || run > 2 * context) {
                    break;
                }
                k += run;
            }
            int stop = Math.min(edits.size(), end + 1 + context);
            hunks.add(build(edits, start, stop));
            i = stop;
        }
        return hunks;
    }

    private static Hunk build(List<Edit> edits, int from, int to) {
        int oldBefore = 0;
        int newBefore = 0;
        for (int k = 0; k < from; k++) {
            if (edits.get(k).type() != Edit.Type.INSERT) {
                oldBefore++;
            }
            if (edits.get(k).type() != Edit.Type.DELETE) {
                newBefore++;
            }
        }
        List<Edit> slice = List.copyOf(edits.subList(from, to));
        int oldCount = (int) slice.stream().filter(e -> e.type() != Edit.Type.INSERT).count();
        int newCount = (int) slice.stream().filter(e -> e.type() != Edit.Type.DELETE).count();
        return new Hunk(oldCount > 0 ? oldBefore + 1 : oldBefore, oldCount,
                newCount > 0 ? newBefore + 1 : newBefore, newCount, slice);
    }
}
