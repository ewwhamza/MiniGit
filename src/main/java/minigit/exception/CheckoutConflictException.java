package minigit.exception;

import java.util.List;

/**
 * Thrown when switching commits would overwrite uncommitted changes (FR-CO-3).
 * Nothing on disk has been changed when this is thrown.
 */
public class CheckoutConflictException extends MiniGitException {

    private final List<String> paths;

    public CheckoutConflictException(List<String> paths) {
        super("your uncommitted changes to these files would be overwritten:\n  "
                + String.join("\n  ", paths)
                + "\ncommit them, or discard them with 'minigit restore', then try again");
        this.paths = List.copyOf(paths);
    }

    /** The files that block the checkout, for display in a GUI dialog. */
    public List<String> paths() {
        return paths;
    }
}
