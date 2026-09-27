package minigit.diff;

import minigit.api.ChangeType;

/** Renders a {@link FileDiff} in the unified format used by {@code git diff} (FR-DIFF-2). */
public final class UnifiedFormatter {

    private UnifiedFormatter() {
    }

    public static String format(FileDiff diff) {
        StringBuilder out = new StringBuilder();
        String path = diff.path();
        out.append("diff --minigit a/").append(path).append(" b/").append(path).append('\n');
        if (diff.type() == ChangeType.ADDED) {
            out.append("new file\n");
        } else if (diff.type() == ChangeType.DELETED) {
            out.append("deleted file\n");
        }
        if (diff.binary()) {
            return out.append("Binary files differ\n").toString();
        }
        if (diff.hunks().isEmpty()) {
            return out.toString();
        }
        out.append("--- ").append(diff.type() == ChangeType.ADDED ? "/dev/null" : "a/" + path).append('\n');
        out.append("+++ ").append(diff.type() == ChangeType.DELETED ? "/dev/null" : "b/" + path).append('\n');
        for (Hunk hunk : diff.hunks()) {
            out.append(hunk.header()).append('\n');
            for (Edit edit : hunk.edits()) {
                out.append(edit.type().symbol()).append(edit.content()).append('\n');
                if (edit.missingNewline()) {
                    out.append("\\ No newline at end of file\n");
                }
            }
        }
        return out.toString();
    }
}
