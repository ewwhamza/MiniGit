package minigit.gui.dialogs;

import minigit.api.MiniGit;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.KeyStroke;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.awt.Window;
import java.awt.event.KeyEvent;
import java.util.List;
import java.util.Optional;

/**
 * Asks for a new branch's name (FR-GUI-10). OK is only enabled for a valid,
 * unused name, and the reason is shown as the user types.
 */
public final class NewBranchDialog extends JDialog {

    /** What the user chose. */
    public record Choice(String name, boolean switchToIt) {
    }

    private final JTextField name = new JTextField(24);
    private final JCheckBox switchTo = new JCheckBox("Switch to the new branch", true);
    private final JLabel problem = new JLabel(" ");
    private final JButton ok = new JButton("Create Branch");
    private final List<String> existing;
    private Choice choice;

    private NewBranchDialog(Window owner, String startingPoint, List<String> existing) {
        super(owner, "New Branch", ModalityType.APPLICATION_MODAL);
        this.existing = existing;

        JPanel form = new JPanel(new GridLayout(0, 1, 0, 4));
        form.setBorder(BorderFactory.createEmptyBorder(12, 12, 4, 12));
        form.add(new JLabel("Branch name (starts at " + startingPoint + "):"));
        form.add(name);
        problem.setForeground(new Color(0xCF222E));
        form.add(problem);
        form.add(switchTo);

        JButton cancel = new JButton("Cancel");
        cancel.addActionListener(e -> dispose());
        ok.addActionListener(e -> {
            choice = new Choice(name.getText().strip(), switchTo.isSelected());
            dispose();
        });
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        buttons.add(cancel);
        buttons.add(ok);

        name.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                validateName();
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                validateName();
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                validateName();
            }
        });
        validateName();

        getRootPane().setDefaultButton(ok);
        getRootPane().registerKeyboardAction(e -> dispose(), KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0),
                JComponent.WHEN_IN_FOCUSED_WINDOW);
        getContentPane().add(form, BorderLayout.CENTER);
        getContentPane().add(buttons, BorderLayout.SOUTH);
        pack();
        setResizable(false);
        setLocationRelativeTo(owner);
    }

    /**
     * @param startingPoint shown to the user, e.g. "3a7bd3e"
     * @param existing      names already taken
     */
    public static Optional<Choice> show(Window owner, String startingPoint, List<String> existing) {
        NewBranchDialog dialog = new NewBranchDialog(owner, startingPoint, existing);
        dialog.setVisible(true);
        return Optional.ofNullable(dialog.choice);
    }

    private void validateName() {
        String n = name.getText().strip();
        String error = n.isEmpty() ? " "
                : existing.contains(n) ? "A branch with this name already exists."
                : !MiniGit.isValidRefName(n) ? "Use letters, digits, - _ . / only; no spaces or '..'."
                : " ";
        problem.setText(error);
        ok.setEnabled(!n.isEmpty() && error.isBlank());
    }
}
