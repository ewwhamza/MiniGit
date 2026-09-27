package minigit.gui.util;

import minigit.api.ChangeType;

import java.awt.Color;
import java.awt.Font;

/** Colours and fonts shared across the GUI. */
public final class Theme {

    public static final Font MONO = new Font(Font.MONOSPACED, Font.PLAIN, 13);
    public static final Font MONO_SMALL = new Font(Font.MONOSPACED, Font.PLAIN, 12);

    // Diff viewer.
    public static final Color ADDED_BG = new Color(0xE6FFEC);
    public static final Color REMOVED_BG = new Color(0xFFEBE9);
    public static final Color HUNK_BG = new Color(0xDDF4FF);
    public static final Color HUNK_FG = new Color(0x57606A);
    public static final Color INFO_FG = new Color(0x6E7781);
    public static final Color CONTEXT_BG = Color.WHITE;
    public static final Color GUTTER_FG = new Color(0x8C959F);
    public static final Color SELECTION_BG = new Color(0xB6E3FF);

    // File status letters.
    public static final Color ADDED_FG = new Color(0x1A7F37);
    public static final Color MODIFIED_FG = new Color(0x9A6700);
    public static final Color DELETED_FG = new Color(0xCF222E);
    public static final Color UNTRACKED_FG = new Color(0x6E7781);

    // Branch and tag labels in history.
    public static final String BRANCH_LABEL_HTML = "#0969DA";
    public static final String HEAD_LABEL_HTML = "#1A7F37";
    public static final String TAG_LABEL_HTML = "#9A6700";

    private Theme() {
    }

    public static Color colorFor(ChangeType type) {
        return switch (type) {
            case ADDED -> ADDED_FG;
            case MODIFIED -> MODIFIED_FG;
            case DELETED -> DELETED_FG;
        };
    }

    /** Escapes text for use inside a Swing HTML label. */
    public static String html(String text) {
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
