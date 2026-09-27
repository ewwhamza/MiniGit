package minigit.diff;

import java.util.ArrayList;
import java.util.List;

/**
 * Line-level diff using the Longest Common Subsequence algorithm (FR-DIFF-1).
 *
 * <p>The longest sequence of lines that appears, in order, in both texts is kept;
 * every other old line is a deletion and every other new line an insertion. That
 * gives the smallest possible set of changes.
 *
 * <p>The LCS is found with dynamic programming: {@code lcs[i][j]} is the LCS length
 * of {@code a[i..]} and {@code b[j..]}, filled from the end backwards, where
 * <pre>
 *   lcs[i][j] = a[i] == b[j] ? 1 + lcs[i+1][j+1]
 *                            : max(lcs[i+1][j], lcs[i][j+1])
 * </pre>
 * Walking the table from {@code [0][0]} then reads off the edits. This is
 * O(n·m) in time and memory, so identical leading and trailing lines are
 * trimmed first; in typical edits that leaves only a small middle section.
 */
public final class LineDiff {

    /** Above this many table cells (~64 MB), fall back to replacing the whole middle section. */
    static final long MAX_CELLS = 16_000_000L;

    private LineDiff() {
    }

    /** Splits text into lines, each keeping its {@code \n} (the last may have none). */
    public static List<String> splitLines(String text) {
        List<String> lines = new ArrayList<>();
        int start = 0;
        for (int i = 0; i < text.length(); i++) {
            if (text.charAt(i) == '\n') {
                lines.add(text.substring(start, i + 1));
                start = i + 1;
            }
        }
        if (start < text.length()) {
            lines.add(text.substring(start));
        }
        return lines;
    }

    /** The edits that turn {@code a} into {@code b}, in order; deletions come before insertions. */
    public static List<Edit> diff(List<String> a, List<String> b) {
        int n = a.size();
        int m = b.size();
        int prefix = 0;
        while (prefix < n && prefix < m && a.get(prefix).equals(b.get(prefix))) {
            prefix++;
        }
        int suffix = 0;
        while (suffix < n - prefix && suffix < m - prefix
                && a.get(n - 1 - suffix).equals(b.get(m - 1 - suffix))) {
            suffix++;
        }

        List<Edit> edits = new ArrayList<>(n + m);
        for (int i = 0; i < prefix; i++) {
            edits.add(new Edit(Edit.Type.EQUAL, a.get(i), i, i));
        }
        diffMiddle(a, b, prefix, n - suffix, prefix, m - suffix, edits);
        for (int k = suffix; k > 0; k--) {
            edits.add(new Edit(Edit.Type.EQUAL, a.get(n - k), n - k, m - k));
        }
        return edits;
    }

    /** LCS over {@code a[aFrom..aTo)} and {@code b[bFrom..bTo)}. */
    private static void diffMiddle(List<String> a, List<String> b, int aFrom, int aTo, int bFrom, int bTo,
                                   List<Edit> out) {
        int n = aTo - aFrom;
        int m = bTo - bFrom;
        if ((long) (n + 1) * (m + 1) > MAX_CELLS) {
            // Too large for the table: still correct, just not minimal.
            for (int i = aFrom; i < aTo; i++) {
                out.add(new Edit(Edit.Type.DELETE, a.get(i), i, -1));
            }
            for (int j = bFrom; j < bTo; j++) {
                out.add(new Edit(Edit.Type.INSERT, b.get(j), -1, j));
            }
            return;
        }

        int[][] lcs = new int[n + 1][m + 1];
        for (int i = n - 1; i >= 0; i--) {
            for (int j = m - 1; j >= 0; j--) {
                lcs[i][j] = a.get(aFrom + i).equals(b.get(bFrom + j))
                        ? lcs[i + 1][j + 1] + 1
                        : Math.max(lcs[i + 1][j], lcs[i][j + 1]);
            }
        }

        int i = 0;
        int j = 0;
        while (i < n || j < m) {
            if (i < n && j < m && a.get(aFrom + i).equals(b.get(bFrom + j))) {
                out.add(new Edit(Edit.Type.EQUAL, a.get(aFrom + i), aFrom + i, bFrom + j));
                i++;
                j++;
            } else if (j == m || (i < n && lcs[i + 1][j] >= lcs[i][j + 1])) {
                out.add(new Edit(Edit.Type.DELETE, a.get(aFrom + i), aFrom + i, -1));
                i++;
            } else {
                out.add(new Edit(Edit.Type.INSERT, b.get(bFrom + j), -1, bFrom + j));
                j++;
            }
        }
    }
}
