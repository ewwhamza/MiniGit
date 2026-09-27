package minigit.gui.dialogs;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.KeyStroke;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.awt.Window;
import java.awt.event.KeyEvent;
import java.util.Optional;

/**
 * Asks for the author name and email recorded on commits (FR-GUI-3), shown
 * before the first commit and from Repository → Settings.
 */
public final class IdentityDialog extends JDialog {

    /** The name is required; the email may be empty. */
    public record Identity(String name, String email) {
    }

    private final JTextField name = new JTextField(26);
    private final JTextField email = new JTextField(26);
    private Identity result;

    private IdentityDialog(Window owner, String message, Identity current) {
        super(owner, "Your Identity", ModalityType.APPLICATION_MODAL);
        name.setText(current.name());
        email.setText(current.email());

        JPanel form = new JPanel(new GridLayout(0, 1, 0, 4));
        form.setBorder(BorderFactory.createEmptyBorder(12, 12, 4, 12));
        form.add(new JLabel("<html>" + message + "</html>"));
        form.add(new JLabel("Name:"));
        form.add(name);
        form.add(new JLabel("Email (optional):"));
        form.add(email);

        JButton ok = new JButton("Save");
        ok.addActionListener(e -> {
            result = new Identity(name.getText().strip(), email.getText().strip());
            dispose();
        });
        JButton cancel = new JButton("Cancel");
        cancel.addActionListener(e -> dispose());
        name.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                ok.setEnabled(!name.getText().isBlank());
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                ok.setEnabled(!name.getText().isBlank());
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                ok.setEnabled(!name.getText().isBlank());
            }
        });
        ok.setEnabled(!name.getText().isBlank());

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        buttons.add(cancel);
        buttons.add(ok);
        getRootPane().setDefaultButton(ok);
        getRootPane().registerKeyboardAction(e -> dispose(), KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0),
                JComponent.WHEN_IN_FOCUSED_WINDOW);
        getContentPane().add(form, BorderLayout.CENTER);
        getContentPane().add(buttons, BorderLayout.SOUTH);
        pack();
        setResizable(false);
        setLocationRelativeTo(owner);
    }

    public static Optional<Identity> show(Window owner, String message, Identity current) {
        IdentityDialog dialog = new IdentityDialog(owner, message, current);
        dialog.setVisible(true);
        return Optional.ofNullable(dialog.result);
    }
}
