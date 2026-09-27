package minigit.gui.util;

import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Window;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

/**
 * Runs repository work off the Event Dispatch Thread so the window never freezes
 * (FR-GUI-13, NFR-UX-2), then hands the result back on the EDT.
 *
 * <p>All tasks run one at a time on a single worker thread, so two operations can
 * never touch the index at once. While any task is running, the window shows a
 * busy cursor.
 */
public final class BackgroundTask {

    private static final ExecutorService WORKER = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "minigit-worker");
        t.setDaemon(true);
        return t;
    });

    /** Tasks queued or running. Only touched on the EDT. */
    private static int pending;

    private BackgroundTask() {
    }

    /** Runs {@code work}; on failure, shows the error in a dialog. */
    public static <T> void run(Component owner, Callable<T> work, Consumer<T> onSuccess) {
        run(owner, work, onSuccess, error -> Dialogs.showError(owner, error));
    }

    /** Runs {@code work}, then calls exactly one of {@code onSuccess} or {@code onFailure} on the EDT. */
    public static <T> void run(Component owner, Callable<T> work, Consumer<T> onSuccess, Consumer<Throwable> onFailure) {
        pending++;
        updateCursor(owner);
        SwingWorker<T, Void> worker = new SwingWorker<>() {
            @Override
            protected T doInBackground() throws Exception {
                return work.call();
            }

            @Override
            protected void done() {
                pending--;
                updateCursor(owner);
                T result;
                try {
                    result = get();
                } catch (ExecutionException e) {
                    onFailure.accept(e.getCause());
                    return;
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }
                try {
                    onSuccess.accept(result);
                } catch (RuntimeException e) {
                    Dialogs.showError(owner, e);
                }
            }
        };
        WORKER.execute(worker);
    }

    public static boolean isBusy() {
        return pending > 0;
    }

    private static void updateCursor(Component owner) {
        Window window = owner instanceof Window w ? w : SwingUtilities.getWindowAncestor(owner);
        if (window != null) {
            window.setCursor(pending > 0 ? Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR) : Cursor.getDefaultCursor());
        }
    }
}
