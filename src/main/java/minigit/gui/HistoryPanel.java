package minigit.gui;

import minigit.api.CommitDetails;
import minigit.api.CommitInfo;
import minigit.diff.FileDiff;
import minigit.gui.model.CommitTableModel;
import minigit.gui.util.Theme;

import javax.swing.BorderFactory;
import javax.swing.DefaultListCellRenderer;
import javax.swing.DefaultListModel;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.ListSelectionModel;
import javax.swing.table.DefaultTableCellRenderer;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;

/**
 * The History tab (FR-GUI-8): a table of commits; selecting one shows its details
 * and changed files, and selecting a file shows that file's diff.
 */
public final class HistoryPanel extends JPanel {

    private static final DateTimeFormatter FULL_DATE =
            DateTimeFormatter.ofPattern("EEEE d MMMM yyyy, HH:mm:ss xxx", Locale.ENGLISH);

    private final CommitTableModel commits = new CommitTableModel();
    private final JTable table = new JTable(commits);
    private final JTextArea details = new JTextArea();
    private final DefaultListModel<FileDiff> files = new DefaultListModel<>();
    private final JList<FileDiff> fileList = new JList<>(files);
    private final DiffViewer diff = new DiffViewer();

    /** Hash of the commit whose details are showing, to discard stale results. */
    private String shownHash;

    /** Right-click actions on a commit (FR-GUI-18); each receives the commit's full hash. */
    public interface CommitActions {
        void checkout(String hash);

        void createBranchAt(String hash);

        void createTagAt(String hash);

        void resetTo(String hash);
    }

    /** @param loadDetails asked to fetch a commit's details; calls {@link #showDetails} when done */
    public HistoryPanel(Consumer<String> loadDetails, CommitActions actions) {
        super(new BorderLayout());
        table.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                showMenu(e, actions);
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                showMenu(e, actions);
            }
        });

        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table.setRowHeight(24);
        // Otherwise the table asks for most of the height and squeezes the details below.
        table.setPreferredScrollableViewportSize(new Dimension(800, 200));
        table.setShowVerticalLines(false);
        table.setAutoCreateRowSorter(false);
        table.getColumnModel().getColumn(CommitTableModel.HASH).setMaxWidth(90);
        table.getColumnModel().getColumn(CommitTableModel.HASH).setPreferredWidth(80);
        table.getColumnModel().getColumn(CommitTableModel.MESSAGE).setPreferredWidth(520);
        table.getColumnModel().getColumn(CommitTableModel.MESSAGE).setCellRenderer(new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable t, Object value, boolean selected, boolean focused,
                                                           int row, int column) {
                return super.getTableCellRendererComponent(t, commits.messageHtml(row, !selected), selected,
                        focused, row, column);
            }
        });
        table.getColumnModel().getColumn(CommitTableModel.AUTHOR).setPreferredWidth(140);
        table.getColumnModel().getColumn(CommitTableModel.DATE).setPreferredWidth(150);
        table.getSelectionModel().addListSelectionListener(e -> {
            int row = table.getSelectedRow();
            if (!e.getValueIsAdjusting() && row >= 0) {
                String hash = commits.commitAt(row).hash();
                if (!hash.equals(shownHash)) {
                    shownHash = hash;
                    loadDetails.accept(hash);
                }
            }
        });

        details.setEditable(false);
        details.setFont(Theme.MONO_SMALL);
        details.setBorder(BorderFactory.createEmptyBorder(6, 8, 6, 8));
        details.setLineWrap(true);
        details.setWrapStyleWord(true);

        fileList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        fileList.setCellRenderer(new FileRenderer());
        fileList.addListSelectionListener(e -> {
            FileDiff selected = fileList.getSelectedValue();
            if (!e.getValueIsAdjusting() && selected != null) {
                diff.showDiff(selected.path(), selected);
            }
        });

        JPanel filesPanel = new JPanel(new BorderLayout());
        JLabel filesTitle = new JLabel("Changed files");
        filesTitle.setBorder(BorderFactory.createEmptyBorder(4, 8, 4, 8));
        filesPanel.add(filesTitle, BorderLayout.NORTH);
        filesPanel.add(new JScrollPane(fileList), BorderLayout.CENTER);

        JScrollPane detailsScroll = new JScrollPane(details);
        detailsScroll.setPreferredSize(new Dimension(360, 170));
        JSplitPane left = new JSplitPane(JSplitPane.VERTICAL_SPLIT, detailsScroll, filesPanel);
        left.setResizeWeight(0.5);
        JSplitPane bottom = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, left, diff);
        bottom.setResizeWeight(0.3);
        JSplitPane main = new JSplitPane(JSplitPane.VERTICAL_SPLIT, new JScrollPane(table), bottom);
        main.setResizeWeight(0.4);
        add(main, BorderLayout.CENTER);
        clearDetails();
    }

    /** Replaces the commit list, keeping the selected commit selected if it is still there. */
    public void setCommits(List<CommitInfo> list, Map<String, List<String>> labels) {
        commits.setCommits(list, labels);
        int row = shownHash == null ? -1 : commits.indexOf(shownHash);
        if (row < 0) {
            // The shown commit is gone (or none was shown): select the newest, which loads it.
            shownHash = null;
            row = list.isEmpty() ? -1 : 0;
        }
        if (row >= 0) {
            table.setRowSelectionInterval(row, row);
            table.scrollRectToVisible(table.getCellRect(row, 0, true));
        } else {
            clearDetails();
        }
    }

    public void showDetails(CommitDetails result) {
        CommitInfo c = result.commit();
        if (!c.hash().equals(shownHash)) {
            return;
        }
        StringBuilder text = new StringBuilder();
        text.append("Commit:  ").append(c.hash()).append('\n');
        text.append("Author:  ").append(c.author().name());
        if (!c.author().email().isEmpty()) {
            text.append(" <").append(c.author().email()).append('>');
        }
        text.append('\n');
        text.append("Date:    ").append(FULL_DATE.format(c.author().dateTime())).append('\n');
        if (!c.parents().isEmpty()) {
            text.append(c.parents().size() > 1 ? "Parents: " : "Parent:  ")
                    .append(String.join(", ", c.parents().stream().map(minigit.util.Hashing::shorten).toList()))
                    .append('\n');
        }
        text.append('\n').append(c.message());
        details.setText(text.toString());
        details.setCaretPosition(0);

        files.clear();
        files.addAll(result.diffs());
        if (!files.isEmpty()) {
            fileList.setSelectedIndex(0);
        } else {
            diff.showMessage("This commit changed no files.");
        }
    }

    /** The commit selected in the table, if any. */
    public java.util.Optional<String> selectedHash() {
        return java.util.Optional.ofNullable(shownHash);
    }

    private void showMenu(MouseEvent e, CommitActions actions) {
        if (!e.isPopupTrigger()) {
            return;
        }
        int row = table.rowAtPoint(e.getPoint());
        if (row < 0) {
            return;
        }
        table.setRowSelectionInterval(row, row);
        String hash = commits.commitAt(row).hash();
        JPopupMenu menu = new JPopupMenu();
        menu.add(menuItem("Check Out This Commit…", () -> actions.checkout(hash)));
        menu.add(menuItem("Create Branch Here…", () -> actions.createBranchAt(hash)));
        menu.add(menuItem("Create Tag Here…", () -> actions.createTagAt(hash)));
        menu.addSeparator();
        menu.add(menuItem("Reset Current Branch to Here…", () -> actions.resetTo(hash)));
        menu.show(table, e.getX(), e.getY());
    }

    private static JMenuItem menuItem(String text, Runnable action) {
        JMenuItem item = new JMenuItem(text);
        item.addActionListener(ev -> action.run());
        return item;
    }

    private void clearDetails() {
        details.setText("No commits yet.");
        files.clear();
        diff.showMessage("Select a commit to see what it changed.");
    }

    /** Shows "M  src/Main.java   +3 −1". */
    private static final class FileRenderer extends DefaultListCellRenderer {
        @Override
        public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean selected,
                                                      boolean focused) {
            FileDiff d = (FileDiff) value;
            String counts = d.binary() ? "binary" : "+" + d.additions() + " −" + d.deletions();
            String color = selected ? "" : " color='" + String.format("#%06X",
                    Theme.colorFor(d.type()).getRGB() & 0xFFFFFF) + "'";
            super.getListCellRendererComponent(list, "<html><b><font" + color + ">" + d.type().letter()
                    + "</font></b>&nbsp;&nbsp;" + Theme.html(d.path()) + "&nbsp;&nbsp;<font color='gray'>"
                    + counts + "</font></html>", index, selected, focused);
            return this;
        }
    }
}
