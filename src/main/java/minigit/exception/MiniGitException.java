package minigit.exception;

/**
 * Base class for every error MiniGit reports to the user.
 *
 * <p>The message must be a plain-English sentence fit to show directly in the
 * terminal or in a GUI dialog. It is unchecked so it can pass through lambdas
 * and stream pipelines without wrapping.
 */
public class MiniGitException extends RuntimeException {

    public MiniGitException(String message) {
        super(message);
    }

    public MiniGitException(String message, Throwable cause) {
        super(message, cause);
    }
}
