package minigit.gui.model;

import minigit.api.CommitInfo;
import minigit.gui.util.Theme;
import minigit.util.Hashing;

import javax.swing.table.AbstractTableModel;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Rows of the History tab: one per commit, newest first (FR-GUI-8). */
public final class CommitTableModel extends AbstractTableModel {

    public static final int HASH = 0;
    public static final int MESSAGE = 1;
    public static final int AUTHOR = 2;
    public static final int DATE = 3;

    private static final String[] COLUMNS = {"Commit", "Message", "Author", "Date"};
    private static final DateTimeFormatter DATE_FORMAT =
            DateTimeFormatter.ofPattern("d MMM yyyy, HH:mm", Locale.ENGLISH);

    private List<CommitInfo> commits = List.of();
    private Map<String, List<String>> labels = Map.of();

    public void setCommits(List<CommitInfo> commits, Map<String, List<String>> labels) {
        this.commits = List.copyOf(commits);
        this.labels = Map.copyOf(labels);
        fireTableDataChanged();
    }

    public CommitInfo commitAt(int row) {
        return commits.get(row);
    }

    /** Row of a commit, or -1. */
    public int indexOf(String hash) {
        for (int i = 0; i < commits.size(); i++) {
            if (commits.get(i).hash().equals(hash)) {
                return i;
            }
        }
        return -1;
    }

    @Override
    public int getRowCount() {
        return commits.size();
    }

    @Override
    public int getColumnCount() {
        return COLUMNS.length;
    }

    @Override
    public String getColumnName(int column) {
        return COLUMNS[column];
    }

    @Override
    public Object getValueAt(int row, int column) {
        CommitInfo c = commits.get(row);
        return switch (column) {
            case HASH -> Hashing.shorten(c.hash());
            case MESSAGE -> messageHtml(row, true);
            case AUTHOR -> c.author().name();
            case DATE -> DATE_FORMAT.format(c.author().dateTime());
            default -> throw new IllegalArgumentException("column " + column);
        };
    }

    /**
     * The summary line, preceded by branch and tag labels.
     *
     * @param colored whether to colour the labels; off for a selected row, where
     *                colours would clash with the selection background
     */
    public String messageHtml(int row, boolean colored) {
        CommitInfo c = commits.get(row);
        StringBuilder html = new StringBuilder("<html>");
        for (String label : labels.getOrDefault(c.hash(), List.of())) {
            String color = label.startsWith("HEAD") ? Theme.HEAD_LABEL_HTML
                    : label.startsWith("tag: ") ? Theme.TAG_LABEL_HTML
                    : Theme.BRANCH_LABEL_HTML;
            html.append("<b>");
            if (colored) {
                html.append("<font color='").append(color).append("'>");
            }
            html.append('[').append(Theme.html(label)).append(']');
            if (colored) {
                html.append("</font>");
            }
            html.append("</b> ");
        }
        return html.append(Theme.html(c.firstLine())).append("</html>").toString();
    }
}
