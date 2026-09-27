package minigit.gui.util;

import minigit.exception.CheckoutConflictException;
import minigit.exception.MiniGitException;

import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.io.PrintWriter;
import java.io.StringWriter;

/** Standard message boxes. Every error reaches the user as plain English (FR-GUI-12). */
public final class Dialogs {

    private Dialogs() {
    }

    public static void showError(Component owner, Throwable error) {
        if (error instanceof CheckoutConflictException conflict) {
            showCheckoutConflict(owner, conflict);
        } else if (error instanceof MiniGitException) {
            JOptionPane.showMessageDialog(owner, wrap(error.getMessage()), "MiniGit", JOptionPane.ERROR_MESSAGE);
        } else {
            showUnexpected(owner, error);
        }
    }

    /** Lists the files blocking a checkout (FR-GUI-11). */
    public static void showCheckoutConflict(Component owner, CheckoutConflictException conflict) {
        JPanel panel = new JPanel(new BorderLayout(0, 8));
        panel.add(new JLabel("<html>You have uncommitted changes that switching would overwrite:</html>"),
                BorderLayout.NORTH);
        JList<String> files = new JList<>(conflict.paths().toArray(String[]::new));
        files.setFont(Theme.MONO);
        JScrollPane scroll = new JScrollPane(files);
        scroll.setPreferredSize(new Dimension(420, Math.min(200, 24 + 20 * conflict.paths().size())));
        panel.add(scroll, BorderLayout.CENTER);
        panel.add(new JLabel("<html>Commit these changes, or discard them, then try again.</html>"),
                BorderLayout.SOUTH);
        JOptionPane.showMessageDialog(owner, panel, "Cannot switch", JOptionPane.WARNING_MESSAGE);
    }

    public static boolean confirm(Component owner, String title, String message) {
        return JOptionPane.showConfirmDialog(owner, wrap(message), title, JOptionPane.OK_CANCEL_OPTION,
                JOptionPane.WARNING_MESSAGE) == JOptionPane.OK_OPTION;
    }

    public static void warn(Component owner, String title, String message) {
        JOptionPane.showMessageDialog(owner, wrap(message), title, JOptionPane.WARNING_MESSAGE);
    }

    public static void info(Component owner, String title, String message) {
        JOptionPane.showMessageDialog(owner, wrap(message), title, JOptionPane.INFORMATION_MESSAGE);
    }

    /** A bug rather than a user error: say so, and offer the details for a bug report. */
    private static void showUnexpected(Component owner, Throwable error) {
        StringWriter trace = new StringWriter();
        error.printStackTrace(new PrintWriter(trace));
        JTextArea details = new JTextArea(trace.toString(), 12, 60);
        details.setEditable(false);
        details.setFont(Theme.MONO_SMALL);
        JPanel panel = new JPanel(new BorderLayout(0, 8));
        panel.add(new JLabel("Something unexpected went wrong: " + error), BorderLayout.NORTH);
        JScrollPane scroll = new JScrollPane(details);
        scroll.setBorder(BorderFactory.createTitledBorder("Details"));
        panel.add(scroll, BorderLayout.CENTER);
        JOptionPane.showMessageDialog(owner, panel, "MiniGit — unexpected error", JOptionPane.ERROR_MESSAGE);
    }

    /** Keeps long messages from producing a screen-wide dialog; preserves line breaks. */
    private static Object wrap(String message) {
        JTextArea text = new JTextArea(message);
        text.setEditable(false);
        text.setOpaque(false);
        text.setLineWrap(true);
        text.setWrapStyleWord(true);
        text.setFont(new JLabel().getFont());
        text.setColumns(Math.min(60, Math.max(20, message.length())));
        return text;
    }
}
