package minigit.model;

import minigit.exception.MiniGitException;
import minigit.util.Hashing;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * A snapshot of the whole project: a root tree, the commit(s) it came from, who
 * made it, and why (FR-CMT-2).
 * <pre>
 * tree 9c1185a5c5e9fc54612808977ee8f548b2258d31
 * parent 5d41402abc4b2a76b9719d911017c592ae1c6c3e
 * author Mohit &lt;mohit@example.com&gt; 1790496000 +0530
 *
 * Add login page
 * </pre>
 * The first commit has no parent; a merge commit has two.
 */
public final class Commit extends GitObject {

    private final String tree;
    private final List<String> parents;
    private final Signature author;
    private final String message;

    public Commit(String tree, List<String> parents, Signature author, String message) {
        this.tree = tree;
        this.parents = List.copyOf(parents);
        this.author = author;
        this.message = message.strip();
    }

    public String tree() {
        return tree;
    }

    public List<String> parents() {
        return parents;
    }

    public Signature author() {
        return author;
    }

    /** The full message, without trailing whitespace. */
    public String message() {
        return message;
    }

    public String firstLine() {
        int nl = message.indexOf('\n');
        return nl < 0 ? message : message.substring(0, nl);
    }

    @Override
    public ObjectType type() {
        return ObjectType.COMMIT;
    }

    @Override
    public byte[] content() {
        StringBuilder out = new StringBuilder();
        out.append("tree ").append(tree).append('\n');
        for (String parent : parents) {
            out.append("parent ").append(parent).append('\n');
        }
        out.append("author ").append(author.format()).append('\n');
        out.append('\n').append(message).append('\n');
        return out.toString().getBytes(StandardCharsets.UTF_8);
    }

    public static Commit parse(String hash, byte[] content) {
        String text = new String(content, StandardCharsets.UTF_8);
        int blank = text.indexOf("\n\n");
        if (blank < 0) {
            throw new MiniGitException("commit " + hash + " has no message separator");
        }
        String tree = null;
        List<String> parents = new ArrayList<>();
        Signature author = null;
        try {
            for (String line : text.substring(0, blank).split("\n")) {
                int space = line.indexOf(' ');
                String key = space < 0 ? line : line.substring(0, space);
                String value = space < 0 ? "" : line.substring(space + 1);
                switch (key) {
                    case "tree" -> tree = requireHash(hash, value);
                    case "parent" -> parents.add(requireHash(hash, value));
                    case "author" -> author = Signature.parse(value);
                    default -> {
                        // Unknown headers are ignored, so newer formats stay readable.
                    }
                }
            }
        } catch (IllegalArgumentException e) {
            throw new MiniGitException("commit " + hash + " is malformed: " + e.getMessage(), e);
        }
        if (tree == null || author == null) {
            throw new MiniGitException("commit " + hash + " is missing its tree or author");
        }
        return new Commit(tree, parents, author, text.substring(blank + 2));
    }

    private static String requireHash(String commit, String value) {
        if (!Hashing.isFullHash(value)) {
            throw new MiniGitException("commit " + commit + " has an invalid hash: " + value);
        }
        return value;
    }
}
