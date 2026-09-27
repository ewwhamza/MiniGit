package minigit.gui;

import minigit.api.RefInfo;
import minigit.util.Hashing;

import javax.swing.DefaultComboBoxModel;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * Toolbar combo box of branches with the current one selected; picking another
 * branch asks the owner to check it out (FR-GUI-9).
 */
public final class BranchSelector extends JPanel {

    private static final String DETACHED_PREFIX = "(detached at ";

    private final DefaultComboBoxModel<String> model = new DefaultComboBoxModel<>();
    private final JComboBox<String> combo = new JComboBox<>(model);

    /** Set while the list is being filled, so programmatic changes are not taken as user choices. */
    private boolean updating;
    private String current;

    public BranchSelector(Consumer<String> onCheckout) {
        super(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        setOpaque(false);
        add(new JLabel("Branch:"));
        combo.setPrototypeDisplayValue("feature/a-long-branch-name");
        combo.setPreferredSize(new Dimension(220, combo.getPreferredSize().height));
        combo.addActionListener(e -> {
            String chosen = (String) combo.getSelectedItem();
            if (!updating && chosen != null && !chosen.equals(current) && !chosen.startsWith(DETACHED_PREFIX)) {
                onCheckout.accept(chosen);
            }
        });
        add(combo);
    }

    /**
     * @param branch current branch, or empty when HEAD is detached
     * @param head   HEAD's commit, used to label a detached HEAD
     */
    public void setBranches(List<RefInfo> branches, Optional<String> branch, Optional<String> head) {
        updating = true;
        try {
            model.removeAllElements();
            if (branch.isEmpty()) {
                current = DETACHED_PREFIX + head.map(Hashing::shorten).orElse("?") + ")";
                model.addElement(current);
            } else {
                current = branch.get();
            }
            branches.forEach(b -> model.addElement(b.name()));
            // Before the first commit, the current branch has no ref file yet.
            if (branch.isPresent() && branches.stream().noneMatch(b -> b.name().equals(current))) {
                model.addElement(current);
            }
            combo.setSelectedItem(current);
        } finally {
            updating = false;
        }
    }

    /** Puts the selection back on the current branch, e.g. after a refused checkout. */
    public void revert() {
        updating = true;
        combo.setSelectedItem(current);
        updating = false;
    }
}
