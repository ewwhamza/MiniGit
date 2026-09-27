package minigit.gui;

import minigit.diff.FileDiff;
import minigit.gui.model.DiffLine;
import minigit.gui.util.Theme;

import javax.swing.BorderFactory;
import javax.swing.DefaultListModel;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.ListCellRenderer;
import javax.swing.ListSelectionModel;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Font;

/**
 * Shows one file's diff with coloured lines and old/new line numbers (FR-GUI-6):
 * removed lines on red, added lines on green, hunk headers on blue-grey.
 */
public final class DiffViewer extends JPanel {

    private final JLabel title = new JLabel(" ");
    private final DefaultListModel<DiffLine> lines = new DefaultListModel<>();
    private final JList<DiffLine> list = new JList<>(lines);

    public DiffViewer() {
        super(new BorderLayout());
        title.setBorder(BorderFactory.createEmptyBorder(6, 8, 6, 8));
        title.setFont(title.getFont().deriveFont(Font.BOLD));
        add(title, BorderLayout.NORTH);

        list.setCellRenderer(new LineRenderer());
        list.setFont(Theme.MONO);
        list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        // A fixed row height lets JList skip measuring every row: fast for long files.
        list.setFixedCellHeight(list.getFontMetrics(Theme.MONO).getHeight() + 2);
        list.setBackground(Theme.CONTEXT_BG);
        add(new JScrollPane(list), BorderLayout.CENTER);
        showMessage("Select a file to see its changes.");
    }

    /** Shows a diff under a heading such as "src/Main.java — staged". */
    public void showDiff(String heading, FileDiff diff) {
        String stats = diff.binary() ? "binary" : "+" + diff.additions() + "  −" + diff.deletions();
        title.setText(heading + "    " + stats);
        lines.clear();
        lines.addAll(DiffLine.from(diff));
        list.ensureIndexIsVisible(0);
    }

    public void showMessage(String message) {
        title.setText(" ");
        lines.clear();
        lines.addElement(DiffLine.info(message));
    }

    public void clear() {
        showMessage("Select a file to see its changes.");
    }

    /** Draws one row: two line-number columns, the +/- symbol, then the text. */
    private static final class LineRenderer extends JLabel implements ListCellRenderer<DiffLine> {

        LineRenderer() {
            setOpaque(true);
            setFont(Theme.MONO);
            setBorder(BorderFactory.createEmptyBorder(0, 4, 0, 4));
        }

        @Override
        public Component getListCellRendererComponent(JList<? extends DiffLine> list, DiffLine line, int index,
                                                      boolean selected, boolean focused) {
            String text = line.text().replace("\t", "    ");
            if (line.kind() == DiffLine.Kind.HUNK || line.kind() == DiffLine.Kind.INFO) {
                setText(" ".repeat(12) + text);
            } else {
                setText(number(line.oldLine()) + " " + number(line.newLine()) + "  " + line.symbol() + " " + text);
            }
            // An empty JLabel collapses; a space keeps blank lines visible.
            if (getText().isEmpty()) {
                setText(" ");
            }
            setBackground(selected ? Theme.SELECTION_BG : background(line.kind()));
            setForeground(line.kind() == DiffLine.Kind.HUNK ? Theme.HUNK_FG
                    : line.kind() == DiffLine.Kind.INFO ? Theme.INFO_FG
                    : Color.BLACK);
            return this;
        }

        private static String number(int n) {
            return n <= 0 ? "     " : String.format("%5d", n);
        }

        private static Color background(DiffLine.Kind kind) {
            return switch (kind) {
                case ADDED -> Theme.ADDED_BG;
                case REMOVED -> Theme.REMOVED_BG;
                case HUNK -> Theme.HUNK_BG;
                default -> Theme.CONTEXT_BG;
            };
        }
    }
}
