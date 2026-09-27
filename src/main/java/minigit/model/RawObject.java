package minigit.model;

/**
 * An object as read back from the store: its hash, type, and undecoded content.
 *
 * @param hash    full 40-character hash
 * @param type    the type from the object's header
 * @param content the body bytes, without the header
 */
public record RawObject(String hash, ObjectType type, byte[] content) {

    public int size() {
        return content.length;
    }
}
