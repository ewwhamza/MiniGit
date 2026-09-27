package minigit.gui;

import minigit.api.ChangeType;
import minigit.api.FileChange;
import minigit.api.StatusResult;
import minigit.gui.model.FileGroupModel;
import minigit.gui.model.FileGroupModel.Row;
import minigit.gui.util.Theme;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.ListSelectionModel;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.TableColumn;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The left side of the Changes tab: staged, unstaged, and untracked files, each
 * with a checkbox to stage or unstage it (FR-GUI-4, FR-GUI-5).
 */
public final class ChangesPanel extends JPanel {

    /** Which group a file is listed in. */
    public enum Group {
        STAGED("Staged changes", "Unstage all"),
        UNSTAGED("Unstaged changes", "Stage all"),
        UNTRACKED("Untracked files", "Stage all");

        final String title;
        final String bulkAction;

        Group(String title, String bulkAction) {
            this.title = title;
            this.bulkAction = bulkAction;
        }
    }

    /** What the panel asks its owner to do. */
    public interface Listener {
        void fileSelected(Group group, String path);

        void selectionCleared();

        void stage(List<String> paths);

        void unstage(List<String> paths);

        /** Throw away working-tree edits to a file (the owner confirms first). */
        void discard(String path);

        /** Stop tracking a file but keep it on disk. */
        void stopTracking(String path);

        /** Add a file to {@code .minigitignore}. */
        void ignore(String path);
    }

    private final Listener listener;
    private final Map<Group, Section> sections = new EnumMap<>(Group.class);

    /** The file the user last selected, kept across refreshes. */
    private Group selectedGroup;
    private String selectedPath;

    public ChangesPanel(Listener listener) {
        super(new BorderLayout());
        this.listener = listener;

        JPanel stack = new JPanel();
        stack.setLayout(new BoxLayout(stack, BoxLayout.Y_AXIS));
        stack.setBackground(Color.WHITE);
        for (Group group : Group.values()) {
            Section section = new Section(group);
            sections.put(group, section);
            stack.add(section);
        }
        stack.add(Box.createVerticalGlue());

        JScrollPane scroll = new JScrollPane(stack);
        scroll.getVerticalScrollBar().setUnitIncrement(16);
        scroll.setBorder(BorderFactory.createEmptyBorder());
        add(scroll, BorderLayout.CENTER);
        setMinimumSize(new Dimension(240, 100));
    }

    /**
     * Shows a new status, keeping the selected file selected. If that file moved to
     * another group (e.g. it was just staged), the selection follows it.
     *
     * @return the group and path still selected, if any
     */
    public Optional<Group> update(StatusResult status) {
        sections.get(Group.STAGED).setRows(status.staged().stream().map(c -> row(c, status)).toList());
        sections.get(Group.UNSTAGED).setRows(status.unstaged().stream().map(c -> row(c, status)).toList());
        sections.get(Group.UNTRACKED).setRows(status.untracked().stream()
                .map(p -> new Row(p, null, status.isConflicted(p))).toList());

        if (selectedPath == null) {
            return Optional.empty();
        }
        Group found = sections.get(selectedGroup).model.indexOf(selectedPath) >= 0 ? selectedGroup : null;
        if (found == null) {
            for (Group g : Group.values()) {
                if (sections.get(g).model.indexOf(selectedPath) >= 0) {
                    found = g;
                    break;
                }
            }
        }
        if (found == null) {
            selectedGroup = null;
            selectedPath = null;
            return Optional.empty();
        }
        select(found, selectedPath);
        return Optional.of(found);
    }

    public Optional<String> selectedPath() {
        return Optional.ofNullable(selectedPath);
    }

    private void select(Group group, String path) {
        selectedGroup = group;
        selectedPath = path;
        for (Section s : sections.values()) {
            int row = s.group == group ? s.model.indexOf(path) : -1;
            s.selecting = true;
            if (row >= 0) {
                s.table.setRowSelectionInterval(row, row);
            } else {
                s.table.clearSelection();
            }
            s.selecting = false;
        }
    }

    private static Row row(FileChange change, StatusResult status) {
        return new Row(change.path(), change.type(), status.isConflicted(change.path()));
    }

    /** One titled group with its table. */
    private final class Section extends JPanel {

        final Group group;
        final FileGroupModel model;
        final JTable table;
        final JLabel heading = new JLabel();
        final JLabel empty = new JLabel();
        final JButton bulk;
        boolean selecting;

        Section(Group group) {
            super(new BorderLayout());
            this.group = group;
            setBackground(Color.WHITE);
            setAlignmentX(Component.LEFT_ALIGNMENT);

            model = new FileGroupModel(group == Group.STAGED, path -> toggle(List.of(path)));
            table = new JTable(model);
            table.setShowGrid(false);
            table.setTableHeader(null);
            table.setRowHeight(22);
            table.setFillsViewportHeight(false);
            table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
            table.setIntercellSpacing(new Dimension(0, 0));
            fix(table.getColumnModel().getColumn(FileGroupModel.CHECK), 28);
            fix(table.getColumnModel().getColumn(FileGroupModel.STATUS), 24);
            table.getColumnModel().getColumn(FileGroupModel.STATUS).setCellRenderer(new StatusRenderer());
            table.getSelectionModel().addListSelectionListener(e -> {
                if (e.getValueIsAdjusting() || selecting) {
                    return;
                }
                int row = table.getSelectedRow();
                if (row >= 0) {
                    select(group, model.rowAt(row).path());
                    listener.fileSelected(group, selectedPath);
                } else if (selectedGroup == group) {
                    selectedGroup = null;
                    selectedPath = null;
                    listener.selectionCleared();
                }
            });

            table.addMouseListener(new MouseAdapter() {
                @Override
                public void mousePressed(MouseEvent e) {
                    maybeShowMenu(e);
                }

                @Override
                public void mouseReleased(MouseEvent e) {
                    maybeShowMenu(e);
                }
            });

            bulk = new JButton(group.bulkAction);
            bulk.setFocusable(false);
            bulk.putClientProperty("JButton.buttonType", "roundRect");
            bulk.addActionListener(e -> toggle(model.rows().stream().map(Row::path).toList()));

            heading.setFont(heading.getFont().deriveFont(Font.BOLD));
            JPanel header = new JPanel(new BorderLayout());
            header.setBackground(new Color(0xF6F8FA));
            header.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createMatteBorder(1, 0, 1, 0, new Color(0xD0D7DE)),
                    BorderFactory.createEmptyBorder(4, 8, 4, 6)));
            header.add(heading, BorderLayout.CENTER);
            header.add(bulk, BorderLayout.EAST);
            add(header, BorderLayout.NORTH);

            empty.setForeground(Theme.INFO_FG);
            empty.setBorder(BorderFactory.createEmptyBorder(6, 12, 8, 8));
            add(table, BorderLayout.CENTER);
            setRows(List.of());
        }

        void setRows(List<Row> rows) {
            selecting = true;
            model.setRows(rows);
            selecting = false;
            heading.setText(group.title + " (" + rows.size() + ")");
            bulk.setEnabled(!rows.isEmpty());
            remove(table);
            remove(empty);
            if (rows.isEmpty()) {
                empty.setText(group == Group.STAGED ? "Nothing staged yet — tick files below to stage them."
                        : group == Group.UNSTAGED ? "No changes to tracked files." : "No new files.");
                add(empty, BorderLayout.CENTER);
            } else {
                add(table, BorderLayout.CENTER);
            }
            revalidate();
            repaint();
        }

        /** Right-click menu (FR-GUI-16), with the actions that make sense for this group. */
        private void maybeShowMenu(MouseEvent e) {
            if (!e.isPopupTrigger()) {
                return;
            }
            int row = table.rowAtPoint(e.getPoint());
            if (row < 0) {
                return;
            }
            table.setRowSelectionInterval(row, row);
            String path = model.rowAt(row).path();
            JPopupMenu menu = new JPopupMenu();
            menu.add(item(group == Group.STAGED ? "Unstage" : "Stage", () -> toggle(List.of(path))));
            if (group == Group.UNSTAGED) {
                menu.add(item("Discard Changes…", () -> listener.discard(path)));
            }
            if (group != Group.UNTRACKED) {
                menu.add(item("Stop Tracking (keep file)", () -> listener.stopTracking(path)));
            } else {
                menu.add(item("Add to .minigitignore", () -> listener.ignore(path)));
            }
            menu.show(table, e.getX(), e.getY());
        }

        private static JMenuItem item(String text, Runnable action) {
            JMenuItem item = new JMenuItem(text);
            item.addActionListener(ev -> action.run());
            return item;
        }

        private void toggle(List<String> paths) {
            if (group == Group.STAGED) {
                listener.unstage(paths);
            } else {
                listener.stage(paths);
            }
        }

        @Override
        public Dimension getMaximumSize() {
            // Stretch horizontally, but never taller than the content in a vertical box.
            return new Dimension(Integer.MAX_VALUE, getPreferredSize().height);
        }
    }

    private static void fix(TableColumn column, int width) {
        column.setMinWidth(width);
        column.setMaxWidth(width);
        column.setPreferredWidth(width);
    }

    /** Colours the A / M / D / ? letter. */
    private static final class StatusRenderer extends DefaultTableCellRenderer {
        @Override
        public Component getTableCellRendererComponent(JTable table, Object value, boolean selected, boolean focused,
                                                       int row, int column) {
            super.getTableCellRendererComponent(table, value, selected, false, row, column);
            setHorizontalAlignment(CENTER);
            setFont(getFont().deriveFont(Font.BOLD));
            Row r = ((FileGroupModel) table.getModel()).rowAt(row);
            if (!selected) {
                setForeground(r.conflict() ? Theme.DELETED_FG
                        : r.type() == null ? Theme.UNTRACKED_FG : Theme.colorFor(r.type()));
            }
            setToolTipText(r.conflict() ? "Merge conflict: edit the file, then stage it" : null);
            return this;
        }
    }
}
