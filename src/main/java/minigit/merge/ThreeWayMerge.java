package minigit.merge;

import minigit.diff.Edit;
import minigit.diff.LineDiff;

import java.util.ArrayList;
import java.util.List;

/**
 * Line-level three-way merge (FR-MRG-3, FR-MRG-4).
 *
 * <p>Both sides are diffed against their common ancestor, the <em>base</em>. Each
 * diff is a list of changes, each replacing a range of base lines. Walking the
 * base from top to bottom:
 * <ul>
 *   <li>a region changed by only one side takes that side's version;</li>
 *   <li>a region changed identically by both sides takes it once;</li>
 *   <li>a region changed differently by both sides (overlapping or touching) is a
 *       conflict, written out between {@code <<<<<<<}, {@code =======}, and
 *       {@code >>>>>>>} markers for the user to resolve.</li>
 * </ul>
 */
public final class ThreeWayMerge {

    /**
     * @param lines     the merged file, each line with its terminator
     * @param conflicts how many conflicting regions were written with markers
     */
    public record Result(List<String> lines, int conflicts) {

        public boolean clean() {
            return conflicts == 0;
        }

        public String text() {
            return String.join("", lines);
        }
    }

    /** Base lines {@code [start, end)} are replaced by {@code lines}. */
    record Change(int start, int end, List<String> lines) {
    }

    private ThreeWayMerge() {
    }

    public static Result merge(List<String> base, List<String> ours, List<String> theirs,
                               String oursLabel, String theirsLabel) {
        List<Change> a = changes(base, ours);
        List<Change> b = changes(base, theirs);
        List<String> out = new ArrayList<>();
        int conflicts = 0;
        int pos = 0;
        int ia = 0;
        int ib = 0;

        while (ia < a.size() || ib < b.size()) {
            boolean fromA = ib >= b.size() || (ia < a.size() && a.get(ia).start() <= b.get(ib).start());
            Change first = fromA ? a.get(ia++) : b.get(ib++);
            List<Change> ca = new ArrayList<>();
            List<Change> cb = new ArrayList<>();
            (fromA ? ca : cb).add(first);
            int start = first.start();
            int end = first.end();

            // Grow the region while either side has a change starting inside or touching it.
            boolean grew = true;
            while (grew) {
                grew = false;
                if (ia < a.size() && a.get(ia).start() <= end) {
                    Change c = a.get(ia++);
                    ca.add(c);
                    end = Math.max(end, c.end());
                    grew = true;
                }
                if (ib < b.size() && b.get(ib).start() <= end) {
                    Change c = b.get(ib++);
                    cb.add(c);
                    end = Math.max(end, c.end());
                    grew = true;
                }
            }

            out.addAll(base.subList(pos, start));
            pos = end;
            List<String> oursVersion = apply(base, start, end, ca);
            List<String> theirsVersion = apply(base, start, end, cb);
            if (cb.isEmpty() || oursVersion.equals(theirsVersion)) {
                out.addAll(oursVersion);
            } else if (ca.isEmpty()) {
                out.addAll(theirsVersion);
            } else {
                conflicts++;
                out.add("<<<<<<< " + oursLabel + "\n");
                addTerminated(out, oursVersion);
                out.add("=======\n");
                addTerminated(out, theirsVersion);
                out.add(">>>>>>> " + theirsLabel + "\n");
            }
        }
        out.addAll(base.subList(pos, base.size()));
        return new Result(out, conflicts);
    }

    /** Groups consecutive non-equal edits into changes against the base. */
    static List<Change> changes(List<String> base, List<String> other) {
        List<Edit> edits = LineDiff.diff(base, other);
        List<Change> changes = new ArrayList<>();
        int basePos = 0;
        int i = 0;
        while (i < edits.size()) {
            if (edits.get(i).type() == Edit.Type.EQUAL) {
                basePos++;
                i++;
                continue;
            }
            int start = basePos;
            List<String> replacement = new ArrayList<>();
            while (i < edits.size() && edits.get(i).type() != Edit.Type.EQUAL) {
                if (edits.get(i).type() == Edit.Type.DELETE) {
                    basePos++;
                } else {
                    replacement.add(edits.get(i).text());
                }
                i++;
            }
            changes.add(new Change(start, basePos, replacement));
        }
        return changes;
    }

    /** One side's version of base lines {@code [start, end)}. */
    private static List<String> apply(List<String> base, int start, int end, List<Change> changes) {
        List<String> result = new ArrayList<>();
        int p = start;
        for (Change c : changes) {
            result.addAll(base.subList(p, c.start()));
            result.addAll(c.lines());
            p = c.end();
        }
        result.addAll(base.subList(p, end));
        return result;
    }

    /** Adds lines, making sure the last one ends with a newline so a marker can follow. */
    private static void addTerminated(List<String> out, List<String> lines) {
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            out.add(i == lines.size() - 1 && !line.endsWith("\n") ? line + "\n" : line);
        }
    }
}
