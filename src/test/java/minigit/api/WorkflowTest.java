package minigit.api;

import minigit.exception.MiniGitException;
import minigit.exception.NothingToCommitException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The add → commit → status → log cycle, driven through the facade the GUI will use. */
class WorkflowTest {

    @TempDir
    Path root;

    private MiniGit git;

    @BeforeEach
    void setUp() {
        MiniGit.init(root);
        git = MiniGit.open(root);
        git.setConfig("user.name", "Mohit");
        git.setConfig("user.email", "mohit@example.com");
    }

    @Test
    void freshRepositoryIsCleanWithNoCommits() {
        StatusResult s = git.status();
        assertEquals(Optional.of("main"), s.branch());
        assertTrue(s.headCommit().isEmpty());
        assertTrue(s.isClean());
        assertTrue(git.log(0).isEmpty());
    }

    @Test
    void newFilesAreUntrackedThenStagedThenCommitted() throws IOException {
        write("README.md", "hello\n");
        write("src/Main.java", "class Main {}\n");

        assertEquals(List.of("README.md", "src/Main.java"), git.status().untracked());

        AddResult added = git.add(List.of(root));
        assertEquals(List.of("README.md", "src/Main.java"), added.staged());
        assertEquals(List.of(
                new FileChange("README.md", ChangeType.ADDED),
                new FileChange("src/Main.java", ChangeType.ADDED)), git.status().staged());

        CommitResult c = git.commit("Initial commit");
        assertTrue(c.root());
        assertEquals(Optional.of("main"), c.branch());
        assertEquals(2, c.changes().size());
        assertEquals(c.hash(), Files.readString(root.resolve(".minigit/refs/heads/main")).strip());
        assertTrue(git.status().isClean());
    }

    @Test
    void commitStoresNestedTrees() throws IOException {
        write("src/app/Main.java", "x");
        git.add(List.of(root));
        String hash = git.commit("nested").hash();

        String commitText = new String(git.readObject(hash).content());
        String rootTree = commitText.lines().findFirst().orElseThrow().substring("tree ".length());
        String rootListing = new String(git.readObject(rootTree).content());
        assertTrue(rootListing.startsWith("040000 tree "));
        assertTrue(rootListing.strip().endsWith("\tsrc"));
    }

    @Test
    void secondCommitHasParentAndLogIsNewestFirst() throws IOException {
        write("a.txt", "one\n");
        git.add(List.of(root));
        CommitResult first = git.commit("First");

        write("a.txt", "two\n");
        git.add(List.of(root.resolve("a.txt")));
        CommitResult second = git.commit("Second\n\nMore detail.");
        assertFalse(second.root());
        assertEquals(List.of(new FileChange("a.txt", ChangeType.MODIFIED)), second.changes());

        List<CommitInfo> log = git.log(0);
        assertEquals(List.of(second.hash(), first.hash()), log.stream().map(CommitInfo::hash).toList());
        assertEquals(List.of(first.hash()), log.get(0).parents());
        assertEquals("Second", log.get(0).firstLine());
        assertEquals("Second\n\nMore detail.", log.get(0).message());
        assertEquals("Mohit", log.get(0).author().name());
        assertEquals(1, git.log(1).size());
    }

    @Test
    void nothingToCommitIsRejected() throws IOException {
        assertThrows(NothingToCommitException.class, () -> git.commit("empty"));
        write("a.txt", "x");
        git.add(List.of(root));
        git.commit("First");
        assertThrows(NothingToCommitException.class, () -> git.commit("again"));
        assertEquals(1, git.log(0).size());
    }

    @Test
    void commitNeedsMessageAndName() throws IOException {
        write("a.txt", "x");
        git.add(List.of(root));
        assertThrows(MiniGitException.class, () -> git.commit("   "));
        git.unsetConfig("user.name");
        MiniGitException e = assertThrows(MiniGitException.class, () -> git.commit("msg"));
        assertTrue(e.getMessage().contains("user.name"));
    }

    @Test
    void statusSeparatesStagedAndUnstagedChanges() throws IOException {
        write("a.txt", "one\n");
        write("b.txt", "keep\n");
        git.add(List.of(root));
        git.commit("First");

        write("a.txt", "two\n");
        git.add(List.of(root.resolve("a.txt")));
        write("a.txt", "three\n");
        Files.delete(root.resolve("b.txt"));
        write("c.txt", "new\n");

        StatusResult s = git.status();
        assertEquals(List.of(new FileChange("a.txt", ChangeType.MODIFIED)), s.staged());
        assertEquals(List.of(
                new FileChange("a.txt", ChangeType.MODIFIED),
                new FileChange("b.txt", ChangeType.DELETED)), s.unstaged());
        assertEquals(List.of("c.txt"), s.untracked());
    }

    @Test
    void racyEntryIsRehashedEvenWithSameSizeAndTimestamp() throws IOException {
        Path file = write("a.txt", "aaaa");
        // A timestamp at or after the index write means the stat shortcut cannot be trusted.
        FileTime time = FileTime.fromMillis(System.currentTimeMillis() + 60_000);
        Files.setLastModifiedTime(file, time);
        git.add(List.of(file));

        Files.writeString(file, "bbbb");
        Files.setLastModifiedTime(file, time);
        assertEquals(List.of(new FileChange("a.txt", ChangeType.MODIFIED)), git.status().unstaged());
    }

    @Test
    void unchangedFilesAreNotRehashed() throws IOException {
        Path file = write("a.txt", "aaaa");
        FileTime past = FileTime.fromMillis(System.currentTimeMillis() - 60_000);
        Files.setLastModifiedTime(file, past);
        git.add(List.of(file));

        // Content changes but size and (old) timestamp match: trusted as unchanged (FR-STAT-5).
        Files.writeString(file, "bbbb");
        Files.setLastModifiedTime(file, past);
        assertTrue(git.status().unstaged().isEmpty());
    }

    @Test
    void addingDeletedFileOrFolderStagesDeletion() throws IOException {
        write("a.txt", "x");
        write("docs/one.md", "1");
        write("docs/two.md", "2");
        git.add(List.of(root));
        git.commit("First");

        Files.delete(root.resolve("a.txt"));
        Files.delete(root.resolve("docs/one.md"));
        Files.delete(root.resolve("docs/two.md"));
        Files.delete(root.resolve("docs"));

        assertEquals(List.of("a.txt"), git.add(List.of(root.resolve("a.txt"))).removed());
        assertEquals(List.of("docs/one.md", "docs/two.md"), git.add(List.of(root.resolve("docs"))).removed());
        assertEquals(3, git.status().staged().stream().filter(c -> c.type() == ChangeType.DELETED).count());
    }

    @Test
    void addRejectsUnknownPathsWithoutStagingAnything() throws IOException {
        write("a.txt", "x");
        assertThrows(MiniGitException.class, () -> git.add(List.of(root.resolve("a.txt"), root.resolve("nope"))));
        assertTrue(git.status().staged().isEmpty());
    }

    @Test
    void addNeverStagesMinigitFolderAndRejectsOutsidePaths() throws IOException {
        write("a.txt", "x");
        git.add(List.of(root));
        assertTrue(git.status().staged().stream().noneMatch(c -> c.path().startsWith(".minigit")));
        assertThrows(MiniGitException.class, () -> git.add(List.of(root.resolve(".minigit/HEAD"))));
        assertThrows(MiniGitException.class, () -> git.add(List.of(root.getParent())));
    }

    @Test
    void ignoredFilesAreSkipped() throws IOException {
        write(".minigitignore", "*.log\nbuild/\n");
        write("app.java", "x");
        write("debug.log", "x");
        write("build/out.jar", "x");

        assertEquals(List.of(".minigitignore", "app.java"), git.status().untracked());
        git.add(List.of(root));
        assertEquals(List.of(".minigitignore", "app.java"),
                git.status().staged().stream().map(FileChange::path).toList());
        assertThrows(MiniGitException.class, () -> git.add(List.of(root.resolve("debug.log"))));
    }

    @Test
    void identicalFilesShareOneBlob() throws IOException {
        write("a.txt", "same\n");
        write("copy/b.txt", "same\n");
        git.add(List.of(root));
        long blobs;
        try (var files = Files.walk(root.resolve(".minigit/objects"))) {
            blobs = files.filter(Files::isRegularFile).count();
        }
        assertEquals(1, blobs);
    }

    @Test
    void rmDeletesCleanFilesAndRefusesToLoseWork() throws IOException {
        write("a.txt", "x");
        write("b.txt", "y");
        write("dir/c.txt", "z");
        git.add(List.of(root));
        git.commit("First");

        assertEquals(List.of("a.txt"), git.remove(List.of(root.resolve("a.txt")), false, false, false));
        assertFalse(Files.exists(root.resolve("a.txt")));

        write("b.txt", "changed");
        assertThrows(MiniGitException.class, () -> git.remove(List.of(root.resolve("b.txt")), false, false, false));
        assertTrue(Files.exists(root.resolve("b.txt")));

        git.remove(List.of(root.resolve("b.txt")), true, false, false);
        assertEquals("changed", Files.readString(root.resolve("b.txt")), "--cached keeps the file");
        assertEquals(List.of("b.txt"), git.status().untracked());

        assertThrows(MiniGitException.class, () -> git.remove(List.of(root.resolve("dir")), false, false, false));
        git.remove(List.of(root.resolve("dir")), false, true, false);
        assertFalse(Files.exists(root.resolve("dir")), "empty folder is cleaned up");
    }

    private Path write(String path, String content) throws IOException {
        Path file = root.resolve(path);
        Files.createDirectories(file.getParent());
        Files.writeString(file, content);
        return file;
    }
}
