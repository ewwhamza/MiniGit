package minigit.cli;

import minigit.exception.MiniGitException;

/** Thrown when a command is given invalid arguments. {@code Main} follows it with the command's usage. */
public class UsageException extends MiniGitException {

    public UsageException(String message) {
        super(message);
    }
}
