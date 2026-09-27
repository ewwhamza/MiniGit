package minigit.gui;

import minigit.gui.util.Dialogs;

import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import java.nio.file.Path;

/** Starts the desktop GUI (FR-GUI-1). */
public final class MiniGitApp {

    private MiniGitApp() {
    }

    /**
     * Opens the main window. If {@code startDir} is inside a repository, that
     * repository opens straight away; otherwise the welcome screen is shown.
     */
    public static void launch(Path startDir) {
        SwingUtilities.invokeLater(() -> {
            try {
                UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
            } catch (ReflectiveOperationException | javax.swing.UnsupportedLookAndFeelException e) {
                // Fall back to Swing's default look and feel.
            }
            MainWindow window = new MainWindow();
            // Last line of defence: a bug shows a dialog instead of silently breaking the app.
            Thread.setDefaultUncaughtExceptionHandler((thread, error) ->
                    SwingUtilities.invokeLater(() -> Dialogs.showError(window, error)));
            window.setVisible(true);
            window.openIfRepository(startDir);
        });
    }
}
