package minigit.gui;

import minigit.api.StatusResult;
import minigit.util.Hashing;

import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JPanel;
import java.awt.BorderLayout;
import java.awt.Color;

/** The bottom line: branch, file counts, and HEAD's short hash (FR-GUI-14). */
public final class StatusBar extends JPanel {

    private final JLabel left = new JLabel(" ");
    private final JLabel right = new JLabel(" ");

    public StatusBar() {
        super(new BorderLayout());
        setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(1, 0, 0, 0, new Color(0xD0D7DE)),
                BorderFactory.createEmptyBorder(3, 8, 3, 8)));
        add(left, BorderLayout.WEST);
        add(right, BorderLayout.EAST);
    }

    public void show(StatusResult status) {
        String where = status.branch().map(b -> "On branch " + b)
                .orElseGet(() -> "HEAD detached at " + status.headCommit().map(Hashing::shorten).orElse("?"));
        left.setText(where + "  ·  " + status.staged().size() + " staged  ·  " + status.unstaged().size()
                + " unstaged  ·  " + status.untracked().size() + " untracked");
        right.setText(status.headCommit().map(h -> "Last commit " + Hashing.shorten(h)).orElse("No commits yet"));
    }

    public void clear() {
        left.setText(" ");
        right.setText(" ");
    }
}
