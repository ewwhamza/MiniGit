package minigit.exception;

import java.nio.file.Path;

/** Thrown when no {@code .minigit} directory exists in a folder or any of its parents. */
public class NotARepositoryException extends MiniGitException {

    public NotARepositoryException(Path start) {
        super("not a minigit repository (or any of the parent directories): " + start);
    }
}
