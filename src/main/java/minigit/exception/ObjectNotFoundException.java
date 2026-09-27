package minigit.exception;

/** Thrown when a hash or hash prefix does not match any stored object. */
public class ObjectNotFoundException extends MiniGitException {

    public ObjectNotFoundException(String hashOrPrefix) {
        super("no object found for '" + hashOrPrefix + "'");
    }
}
