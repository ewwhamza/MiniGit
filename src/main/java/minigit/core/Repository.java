package minigit.core;

import minigit.exception.MiniGitException;
import minigit.exception.NotARepositoryException;
import minigit.model.Blob;
import minigit.util.FileUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.SortedMap;
import java.util.TreeMap;

/**
 * A MiniGit repository: a working tree plus its {@code .minigit} directory.
 * Owns the services that read and write the repository's files.
 */
public final class Repository {

    public static final String DIR_NAME = ".minigit";
    public static final String DEFAULT_BRANCH = "main";

    private final Path workTree;
    private final Path gitDir;
    private final ObjectStore objects;
    private final Config config;
    private final RefStore refs;
    private final WorkingTree files;

    private Repository(Path workTree) {
        this.workTree = workTree;
        this.gitDir = workTree.resolve(DIR_NAME);
        this.objects = new ObjectStore(gitDir.resolve("objects"));
        this.config = new Config(gitDir.resolve("config"));
        this.refs = new RefStore(gitDir);
        this.files = new WorkingTree(workTree);
    }

    /** Whether {@code dir} itself contains a repository (parents are not checked). */
    public static boolean existsAt(Path dir) {
        return Files.isDirectory(dir.resolve(DIR_NAME));
    }

    /**
     * Creates an empty repository in {@code dir} (FR-REPO-1). The caller must
     * check {@link #existsAt} first; this method refuses to overwrite one.
     */
    public static Repository init(Path dir) {
        Path root = dir.toAbsolutePath().normalize();
        Path gitDir = root.resolve(DIR_NAME);
        if (Files.exists(gitDir)) {
            throw new MiniGitException(gitDir + " already exists");
        }
        try {
            Files.createDirectories(gitDir.resolve("objects"));
            Files.createDirectories(gitDir.resolve("refs").resolve("heads"));
            Files.createDirectories(gitDir.resolve("refs").resolve("tags"));
        } catch (IOException e) {
            throw new MiniGitException("could not create " + gitDir + ": " + e.getMessage(), e);
        }
        FileUtils.writeAtomically(gitDir.resolve("HEAD"),
                ("ref: refs/heads/" + DEFAULT_BRANCH + "\n").getBytes(StandardCharsets.UTF_8));
        FileUtils.writeAtomically(gitDir.resolve("index"), new byte[0]);
        FileUtils.writeAtomically(gitDir.resolve("config"), new byte[0]);
        return new Repository(root);
    }

    /**
     * Finds the repository containing {@code start} by walking up through its
     * parent directories (FR-REPO-3).
     *
     * @throws NotARepositoryException if no {@code .minigit} directory is found
     */
    public static Repository find(Path start) {
        Path dir = start.toAbsolutePath().normalize();
        for (Path p = dir; p != null; p = p.getParent()) {
            if (existsAt(p)) {
                return new Repository(p);
            }
        }
        throw new NotARepositoryException(dir);
    }

    public Path workTree() {
        return workTree;
    }

    public Path gitDir() {
        return gitDir;
    }

    public ObjectStore objects() {
        return objects;
    }

    public Config config() {
        return config;
    }

    public RefStore refs() {
        return refs;
    }

    public WorkingTree files() {
        return files;
    }

    /** Loads the index fresh from disk; call {@link Index#save()} to persist changes. */
    public Index loadIndex() {
        return new Index(gitDir.resolve("index"));
    }

    public MergeState mergeState() {
        return new MergeState(gitDir);
    }

    public RevisionResolver revisions() {
        return new RevisionResolver(objects, refs);
    }

    /** Files in a commit's snapshot, as path → blob hash. */
    public SortedMap<String, String> commitFiles(String commit) {
        return Trees.flatten(objects, objects.readCommit(commit).tree());
    }

    /** Files in HEAD's snapshot; empty before the first commit. */
    public SortedMap<String, String> headFiles() {
        return refs.headCommit().map(this::commitFiles).orElseGet(TreeMap::new);
    }

    /**
     * The blob hash of a tracked file's current content, or empty if it was
     * deleted. Skips hashing when the index says the file is unchanged (FR-STAT-5).
     */
    public Optional<String> workingHash(Index index, IndexEntry entry) {
        return workingHash(entry.path(), index, entry);
    }

    /** The blob hash a working-tree file would have, or empty if there is no such file. */
    public Optional<String> workingHash(String path) {
        return workingHash(path, null, null);
    }

    private Optional<String> workingHash(String path, Index index, IndexEntry entry) {
        Optional<WorkingTree.FileStat> stat = files.stat(path);
        if (stat.isEmpty()) {
            return Optional.empty();
        }
        if (entry != null && index.isUnchanged(entry, stat.get().size(), stat.get().lastModified())) {
            return Optional.of(entry.hash());
        }
        return Optional.of(Blob.fromFile(files.toPath(path)).hash());
    }

    /**
     * Writes a stored blob to a working-tree file and returns the index entry that
     * records it, with the file's new size and timestamp.
     */
    public IndexEntry writeWorkingFile(String path, String blobHash) {
        Path file = files.toPath(path);
        if (Files.isDirectory(file)) {
            throw new MiniGitException("cannot create file '" + path + "': a folder with that name is in the way");
        }
        FileUtils.writeAtomically(file, objects.read(blobHash).content());
        WorkingTree.FileStat stat = files.stat(path).orElseThrow();
        return new IndexEntry(path, blobHash, stat.size(), stat.lastModified());
    }
}
