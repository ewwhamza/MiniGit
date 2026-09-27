package minigit.core;

import minigit.exception.CorruptObjectException;
import minigit.exception.MiniGitException;
import minigit.exception.ObjectNotFoundException;
import minigit.model.Commit;
import minigit.model.GitObject;
import minigit.model.ObjectType;
import minigit.model.RawObject;
import minigit.model.Tree;
import minigit.util.Compression;
import minigit.util.FileUtils;
import minigit.util.Hashing;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/**
 * Content-addressed storage for blobs, trees, and commits.
 *
 * <p>Each object lives at {@code objects/<first 2 hex chars>/<remaining 38>},
 * zlib-compressed (FR-OBJ-3). Because the file name <em>is</em> the hash of the
 * content, identical content is only ever stored once (FR-OBJ-4).
 */
public final class ObjectStore {

    /** Shortest hash prefix accepted from the user (FR-OBJ-7). */
    public static final int MIN_PREFIX_LENGTH = 4;

    private final Path objectsDir;

    public ObjectStore(Path objectsDir) {
        this.objectsDir = objectsDir;
    }

    /**
     * Stores an object and returns its hash. Writing an object that already
     * exists does nothing, since existing objects are never modified (FR-OBJ-5).
     */
    public String write(GitObject object) {
        byte[] data = object.serialize();
        String hash = Hashing.sha1Hex(data);
        Path file = pathFor(hash);
        if (!Files.exists(file)) {
            FileUtils.writeAtomically(file, Compression.compress(data));
        }
        return hash;
    }

    public boolean exists(String hash) {
        return Hashing.isFullHash(hash) && Files.isRegularFile(pathFor(hash));
    }

    /**
     * Reads an object by its full hash, recomputing the hash to detect
     * corruption (FR-OBJ-6).
     */
    public RawObject read(String hash) {
        if (!exists(hash)) {
            throw new ObjectNotFoundException(hash);
        }
        byte[] data;
        try {
            data = Compression.decompress(FileUtils.readBytes(pathFor(hash)));
        } catch (IOException e) {
            throw new CorruptObjectException(hash, "cannot decompress", e);
        }
        if (!Hashing.sha1Hex(data).equals(hash)) {
            throw new CorruptObjectException(hash, "content does not match its hash");
        }
        return parse(hash, data);
    }

    public Tree readTree(String hash) {
        return Tree.parse(hash, readExpecting(hash, ObjectType.TREE).content());
    }

    public Commit readCommit(String hash) {
        return Commit.parse(hash, readExpecting(hash, ObjectType.COMMIT).content());
    }

    private RawObject readExpecting(String hash, ObjectType expected) {
        RawObject raw = read(hash);
        if (raw.type() != expected) {
            throw new MiniGitException("object " + Hashing.shorten(hash) + " is a " + raw.type().tag()
                    + ", not a " + expected.tag());
        }
        return raw;
    }

    /**
     * Expands a hash prefix of at least {@value #MIN_PREFIX_LENGTH} characters to
     * the one full hash it matches (FR-OBJ-7).
     *
     * @throws ObjectNotFoundException if nothing matches
     * @throws MiniGitException        if the prefix is invalid or matches several objects
     */
    public String resolve(String prefix) {
        String p = prefix.toLowerCase();
        if (!Hashing.isHex(p) || p.length() > Hashing.HASH_LENGTH) {
            throw new MiniGitException("'" + prefix + "' is not a valid object hash");
        }
        if (p.length() < MIN_PREFIX_LENGTH) {
            throw new MiniGitException("hash prefix '" + prefix + "' is too short; use at least "
                    + MIN_PREFIX_LENGTH + " characters");
        }
        if (p.length() == Hashing.HASH_LENGTH) {
            if (!exists(p)) {
                throw new ObjectNotFoundException(prefix);
            }
            return p;
        }

        List<String> matches = findByPrefix(p);
        if (matches.isEmpty()) {
            throw new ObjectNotFoundException(prefix);
        }
        if (matches.size() > 1) {
            throw new MiniGitException("hash prefix '" + prefix + "' is ambiguous; it matches:\n  "
                    + String.join("\n  ", matches));
        }
        return matches.get(0);
    }

    Path pathFor(String hash) {
        return objectsDir.resolve(hash.substring(0, 2)).resolve(hash.substring(2));
    }

    private List<String> findByPrefix(String prefix) {
        Path bucket = objectsDir.resolve(prefix.substring(0, 2));
        String rest = prefix.substring(2);
        List<String> matches = new ArrayList<>();
        if (!Files.isDirectory(bucket)) {
            return matches;
        }
        try (Stream<Path> files = Files.list(bucket)) {
            files.map(f -> f.getFileName().toString())
                    // Skip leftover temp files from interrupted writes.
                    .filter(name -> name.length() == Hashing.HASH_LENGTH - 2 && Hashing.isHex(name))
                    .filter(name -> name.startsWith(rest))
                    .map(name -> prefix.substring(0, 2) + name)
                    .sorted()
                    .forEach(matches::add);
        } catch (IOException e) {
            throw new MiniGitException("could not list " + bucket + ": " + e.getMessage(), e);
        }
        return matches;
    }

    /** Splits {@code <type> <size>\0<content>} into its parts, validating the header. */
    private static RawObject parse(String hash, byte[] data) {
        int nul = indexOf(data, (byte) 0);
        if (nul < 0) {
            throw new CorruptObjectException(hash, "missing header");
        }
        String header = new String(data, 0, nul, StandardCharsets.US_ASCII);
        int space = header.indexOf(' ');
        if (space < 0) {
            throw new CorruptObjectException(hash, "malformed header '" + header + "'");
        }
        ObjectType type = ObjectType.fromTag(header.substring(0, space))
                .orElseThrow(() -> new CorruptObjectException(hash, "unknown type in header '" + header + "'"));
        int size;
        try {
            size = Integer.parseInt(header.substring(space + 1));
        } catch (NumberFormatException e) {
            throw new CorruptObjectException(hash, "malformed size in header '" + header + "'");
        }
        int actual = data.length - nul - 1;
        if (size != actual) {
            throw new CorruptObjectException(hash, "header says " + size + " bytes but content is " + actual);
        }
        byte[] content = new byte[actual];
        System.arraycopy(data, nul + 1, content, 0, actual);
        return new RawObject(hash, type, content);
    }

    private static int indexOf(byte[] data, byte value) {
        for (int i = 0; i < data.length; i++) {
            if (data[i] == value) {
                return i;
            }
        }
        return -1;
    }
}
