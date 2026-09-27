package minigit.exception;

/** Thrown when a stored object cannot be decoded or its content no longer matches its hash. */
public class CorruptObjectException extends MiniGitException {

    public CorruptObjectException(String hash, String reason) {
        super("object " + hash + " is corrupt: " + reason);
    }

    public CorruptObjectException(String hash, String reason, Throwable cause) {
        super("object " + hash + " is corrupt: " + reason, cause);
    }
}
