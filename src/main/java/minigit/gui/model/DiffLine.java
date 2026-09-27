package minigit.gui.model;

import minigit.diff.Edit;
import minigit.diff.FileDiff;
import minigit.diff.Hunk;

import java.util.ArrayList;
import java.util.List;

/**
 * One row of the diff viewer.
 *
 * @param oldLine 1-based line number in the old version, or 0 if none
 * @param newLine 1-based line number in the new version, or 0 if none
 */
public record DiffLine(Kind kind, int oldLine, int newLine, String text) {

    public enum Kind {
        HUNK, CONTEXT, ADDED, REMOVED, INFO
    }

    /** Flattens a file diff into display rows. */
    public static List<DiffLine> from(FileDiff diff) {
        List<DiffLine> rows = new ArrayList<>();
        if (diff.binary()) {
            rows.add(info("Binary file — no line-by-line diff"));
            return rows;
        }
        if (diff.hunks().isEmpty()) {
            rows.add(info(diff.type() == minigit.api.ChangeType.ADDED ? "Empty file" : "No content changes"));
            return rows;
        }
        for (Hunk hunk : diff.hunks()) {
            rows.add(new DiffLine(Kind.HUNK, 0, 0, hunk.header()));
            for (Edit e : hunk.edits()) {
                Kind kind = switch (e.type()) {
                    case EQUAL -> Kind.CONTEXT;
                    case INSERT -> Kind.ADDED;
                    case DELETE -> Kind.REMOVED;
                };
                rows.add(new DiffLine(kind, e.oldIndex() + 1, e.newIndex() + 1, e.content()));
                if (e.missingNewline()) {
                    rows.add(info("\\ No newline at end of file"));
                }
            }
        }
        return rows;
    }

    public static DiffLine info(String message) {
        return new DiffLine(Kind.INFO, 0, 0, message);
    }

    /** The prefix character shown before the text. */
    public char symbol() {
        return switch (kind) {
            case ADDED -> '+';
            case REMOVED -> '-';
            default -> ' ';
        };
    }
}
