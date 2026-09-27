package minigit.gui.model;

import minigit.api.ChangeType;

import javax.swing.table.AbstractTableModel;
import java.util.List;
import java.util.function.Consumer;

/**
 * One group in the Changes tab (staged, unstaged, or untracked files), shown as
 * a checkbox, a status letter, and a path (FR-GUI-4, FR-GUI-5).
 *
 * <p>Ticking or unticking a checkbox does not change this model. It asks the
 * owner to stage or unstage the file; the next refresh then shows the real state.
 */
public final class FileGroupModel extends AbstractTableModel {

    public static final int CHECK = 0;
    public static final int STATUS = 1;
    public static final int PATH = 2;

    /**
     * One file row.
     *
     * @param type     null for an untracked file
     * @param conflict whether the file still has unresolved merge conflicts
     */
    public record Row(String path, ChangeType type, boolean conflict) {
        public Row(String path, ChangeType type) {
            this(path, type, false);
        }
    }

    private final boolean checked;
    private final Consumer<String> onToggle;
    private List<Row> rows = List.of();

    /**
     * @param checked  whether every row's box is ticked (true for staged files)
     * @param onToggle called with a file's path when its box is clicked
     */
    public FileGroupModel(boolean checked, Consumer<String> onToggle) {
        this.checked = checked;
        this.onToggle = onToggle;
    }

    public void setRows(List<Row> rows) {
        this.rows = List.copyOf(rows);
        fireTableDataChanged();
    }

    public List<Row> rows() {
        return rows;
    }

    public Row rowAt(int index) {
        return rows.get(index);
    }

    public int indexOf(String path) {
        for (int i = 0; i < rows.size(); i++) {
            if (rows.get(i).path().equals(path)) {
                return i;
            }
        }
        return -1;
    }

    @Override
    public int getRowCount() {
        return rows.size();
    }

    @Override
    public int getColumnCount() {
        return 3;
    }

    @Override
    public Class<?> getColumnClass(int column) {
        return column == CHECK ? Boolean.class : String.class;
    }

    @Override
    public boolean isCellEditable(int row, int column) {
        return column == CHECK;
    }

    @Override
    public Object getValueAt(int row, int column) {
        Row r = rows.get(row);
        return switch (column) {
            case CHECK -> checked;
            case STATUS -> r.conflict() ? "C" : r.type() == null ? "?" : String.valueOf(r.type().letter());
            case PATH -> r.path();
            default -> throw new IllegalArgumentException("column " + column);
        };
    }

    @Override
    public void setValueAt(Object value, int row, int column) {
        if (column == CHECK && !value.equals(checked)) {
            onToggle.accept(rows.get(row).path());
        }
    }
}
