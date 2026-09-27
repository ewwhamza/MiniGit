package minigit.core;

import minigit.exception.MiniGitException;
import minigit.util.FileUtils;
import minigit.util.Hashing;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * HEAD and the branch/tag pointers under {@code refs/}.
 *
 * <p>A branch or tag is just a file holding one commit hash. HEAD normally holds
 * {@code ref: refs/heads/<branch>}, meaning "the current branch". When it holds a
 * raw commit hash instead, HEAD is <em>detached</em>.
 */
public final class RefStore {

    private static final String REF_PREFIX = "ref: ";
    private static final String HEADS = "refs/heads/";
    private static final String TAGS = "refs/tags/";

    /** Letters, digits, and {@code - _ / .} (FR-BR-3); further rules in {@link #validateName}. */
    private static final Pattern NAME = Pattern.compile("[A-Za-z0-9._/-]+");

    private final Path gitDir;

    public RefStore(Path gitDir) {
        this.gitDir = gitDir;
    }

    // ---------------------------------------------------------------- HEAD

    /** The checked-out branch's name, or empty when HEAD is detached. */
    public Optional<String> currentBranch() {
        String head = readHead();
        if (head.startsWith(REF_PREFIX + HEADS)) {
            return Optional.of(head.substring((REF_PREFIX + HEADS).length()));
        }
        return Optional.empty();
    }

    /**
     * The commit HEAD points at, or empty if the current branch has no commits
     * yet (a brand-new repository).
     */
    public Optional<String> headCommit() {
        String head = readHead();
        if (head.startsWith(REF_PREFIX)) {
            return readRef(head.substring(REF_PREFIX.length()));
        }
        return Optional.of(requireHash("HEAD", head));
    }

    /**
     * Records a new commit as the tip of the current branch, or as HEAD itself when
     * detached (FR-CMT-3).
     */
    public void advanceHead(String commit) {
        String head = readHead();
        String ref = head.startsWith(REF_PREFIX) ? head.substring(REF_PREFIX.length()) : "HEAD";
        writeRef(ref, commit);
    }

    /** Points HEAD at a branch, making it the current branch. */
    public void attachHead(String branch) {
        writeText("HEAD", REF_PREFIX + HEADS + branch);
    }

    /** Points HEAD directly at a commit (detached HEAD, FR-CO-4). */
    public void detachHead(String commit) {
        writeRef("HEAD", commit);
    }

    // ---------------------------------------------------------------- branches

    public Optional<String> branch(String name) {
        return isSafeName(name) ? readRef(HEADS + name) : Optional.empty();
    }

    public List<String> branches() {
        return list(HEADS);
    }

    public void createBranch(String name, String commit) {
        create("branch", HEADS, name, commit);
    }

    /** Moves an existing branch to a different commit. */
    public void updateBranch(String name, String commit) {
        writeRef(HEADS + name, commit);
    }

    public void deleteBranch(String name) {
        delete(HEADS, name);
    }

    // ---------------------------------------------------------------- tags

    public Optional<String> tag(String name) {
        return isSafeName(name) ? readRef(TAGS + name) : Optional.empty();
    }

    public List<String> tags() {
        return list(TAGS);
    }

    public void createTag(String name, String commit) {
        create("tag", TAGS, name, commit);
    }

    public void deleteTag(String name) {
        delete(TAGS, name);
    }

    // ---------------------------------------------------------------- helpers

    /**
     * Enforces FR-BR-3. Names become file paths under {@code refs/}, so anything
     * that could escape that folder or confuse revision syntax is rejected.
     */
    public static void validateName(String kind, String name) {
        if (!isSafeName(name)) {
            throw new MiniGitException("'" + name + "' is not a valid " + kind + " name: use letters, digits, "
                    + "'-', '_', '.', and '/'; no spaces, no '..', and not starting with '-' or '.'");
        }
    }

    private static boolean isSafeName(String name) {
        if (name == null || !NAME.matcher(name).matches() || name.equals("HEAD")
                || name.startsWith("-") || name.contains("..") || name.contains("//")
                || name.endsWith("/") || name.endsWith(".") || name.endsWith(".tmp")) {
            return false;
        }
        for (String segment : name.split("/")) {
            if (segment.isEmpty() || segment.startsWith(".")) {
                return false;
            }
        }
        return true;
    }

    private void create(String kind, String dir, String name, String commit) {
        validateName(kind, name);
        List<String> existing = list(dir);
        if (existing.contains(name)) {
            throw new MiniGitException("a " + kind + " named '" + name + "' already exists");
        }
        // "feature" and "feature/x" cannot both exist: one is a file, the other a folder.
        for (String other : existing) {
            if (other.startsWith(name + "/") || name.startsWith(other + "/")) {
                throw new MiniGitException("'" + name + "' clashes with the existing " + kind + " '" + other + "'");
            }
        }
        writeRef(dir + name, commit);
    }

    private void delete(String dir, String name) {
        Path file = gitDir.resolve(dir + name);
        if (!isSafeName(name) || !Files.isRegularFile(file)) {
            throw new MiniGitException("'" + name + "' does not exist");
        }
        try {
            Files.delete(file);
            // Remove folders left empty by names like "feature/x".
            Path root = gitDir.resolve(dir);
            for (Path p = file.getParent(); !p.equals(root); p = p.getParent()) {
                try (Stream<Path> children = Files.list(p)) {
                    if (children.findAny().isPresent()) {
                        break;
                    }
                }
                Files.delete(p);
            }
        } catch (IOException e) {
            throw new MiniGitException("could not delete " + file + ": " + e.getMessage(), e);
        }
    }

    private List<String> list(String dir) {
        Path root = gitDir.resolve(dir);
        List<String> names = new ArrayList<>();
        if (!Files.isDirectory(root)) {
            return names;
        }
        try (Stream<Path> files = Files.walk(root)) {
            files.filter(Files::isRegularFile)
                    .map(f -> root.relativize(f).toString().replace('\\', '/'))
                    .filter(RefStore::isSafeName)
                    .forEach(names::add);
        } catch (IOException e) {
            throw new MiniGitException("could not list " + root + ": " + e.getMessage(), e);
        }
        Collections.sort(names);
        return names;
    }

    private Optional<String> readRef(String ref) {
        Path file = gitDir.resolve(ref);
        if (!Files.isRegularFile(file)) {
            return Optional.empty();
        }
        return Optional.of(requireHash(ref, read(file)));
    }

    private void writeRef(String ref, String commit) {
        writeText(ref, requireHash(ref, commit));
    }

    private void writeText(String ref, String text) {
        FileUtils.writeAtomically(gitDir.resolve(ref), (text + "\n").getBytes(StandardCharsets.UTF_8));
    }

    private String readHead() {
        Path file = gitDir.resolve("HEAD");
        if (!Files.isRegularFile(file)) {
            throw new MiniGitException("the repository has no HEAD file: " + file);
        }
        return read(file);
    }

    private static String read(Path file) {
        return new String(FileUtils.readBytes(file), StandardCharsets.UTF_8).strip();
    }

    private static String requireHash(String ref, String value) {
        if (!Hashing.isFullHash(value)) {
            throw new MiniGitException(ref + " is corrupt: '" + value + "' is not a commit hash");
        }
        return value;
    }
}
