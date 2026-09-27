package minigit.model;

import minigit.util.Hashing;

import java.nio.charset.StandardCharsets;

/**
 * An immutable object in the object store: a blob, tree, or commit.
 *
 * <p>Every object is stored as {@code <type> <size>\0<content>} (FR-OBJ-1), and its
 * identity is the SHA-1 of exactly those bytes (FR-OBJ-2). Subclasses only decide
 * what their content is; the header and hashing are the same for all of them.
 */
public abstract class GitObject {

    public abstract ObjectType type();

    /** The object's body, without the header. */
    public abstract byte[] content();

    /** The full bytes that are hashed and stored: header followed by content. */
    public final byte[] serialize() {
        byte[] body = content();
        byte[] header = (type().tag() + " " + body.length + "\0").getBytes(StandardCharsets.US_ASCII);
        byte[] result = new byte[header.length + body.length];
        System.arraycopy(header, 0, result, 0, header.length);
        System.arraycopy(body, 0, result, header.length, body.length);
        return result;
    }

    public final String hash() {
        return Hashing.sha1Hex(serialize());
    }
}
