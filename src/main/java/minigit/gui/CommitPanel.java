package minigit.gui;

import javax.swing.AbstractAction;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.KeyStroke;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.event.ActionEvent;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.util.function.Consumer;

/**
 * The commit message box and button (FR-GUI-7). The button stays disabled until
 * there is a message and at least one staged file; Ctrl+Enter also commits.
 */
public final class CommitPanel extends JPanel {

    private final JTextArea message = new JTextArea(3, 40);
    private final JButton commit = new JButton("Commit");
    private int stagedCount;
    private boolean merging;

    public CommitPanel(Consumer<String> onCommit) {
        super(new BorderLayout(0, 4));
        setBorder(BorderFactory.createEmptyBorder(6, 8, 8, 8));

        add(new JLabel("Commit message:"), BorderLayout.NORTH);
        message.setLineWrap(true);
        message.setWrapStyleWord(true);
        message.setFont(message.getFont().deriveFont(14f));
        message.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                updateButton();
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                updateButton();
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                updateButton();
            }
        });
        add(new JScrollPane(message), BorderLayout.CENTER);

        commit.addActionListener(e -> onCommit.accept(message.getText()));
        message.getInputMap(JComponent.WHEN_FOCUSED)
                .put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, InputEvent.CTRL_DOWN_MASK), "commit");
        message.getActionMap().put("commit", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                if (commit.isEnabled()) {
                    commit.doClick();
                }
            }
        });

        JLabel hint = new JLabel("Ctrl+Enter to commit");
        hint.setForeground(minigit.gui.util.Theme.INFO_FG);
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        buttons.add(hint);
        buttons.add(commit);
        add(buttons, BorderLayout.SOUTH);
        updateButton();
    }

    public void setStagedCount(int count) {
        stagedCount = count;
        updateButton();
    }

    /**
     * Switches the panel in or out of merge mode. While merging, a commit is allowed
     * even with nothing staged, and an empty message box is filled with the suggested
     * merge message.
     */
    public void setMerging(boolean merging, String suggestedMessage) {
        this.merging = merging;
        if (merging && message.getText().isBlank()) {
            message.setText(suggestedMessage);
        }
        updateButton();
    }

    public void clearMessage() {
        message.setText("");
    }

    public void focusMessage() {
        message.requestFocusInWindow();
    }

    private void updateButton() {
        if (merging) {
            commit.setText("Commit Merge");
        } else {
            commit.setText(stagedCount == 0 ? "Commit" : "Commit " + stagedCount + (stagedCount == 1 ? " file" : " files"));
        }
        commit.setEnabled((stagedCount > 0 || merging) && !message.getText().isBlank());
        commit.setToolTipText(stagedCount == 0 && !merging ? "Stage some files first"
                : message.getText().isBlank() ? "Write a commit message first" : null);
    }
}
