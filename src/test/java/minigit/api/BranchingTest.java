package minigit.api;

import minigit.diff.FileDiff;
import minigit.exception.CheckoutConflictException;
import minigit.exception.MiniGitException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Diffs, branches, checkout, tags, restore, and reset through the facade. */
class BranchingTest {

    @TempDir
    Path root;

    private MiniGit git;

    @BeforeEach
    void setUp() {
        MiniGit.init(root);
        git = MiniGit.open(root);
        git.setConfig("user.name", "Mohit");
    }

    // ---------------------------------------------------------------- diff and show

    @Test
    void diffUnstagedStagedAndBetweenCommits() throws IOException {
        write("a.txt", "one\ntwo\n");
        String first = commitAll("First");

        write("a.txt", "one\n2\n");
        List<FileDiff> unstaged = git.diffUnstaged();
        assertEquals(1, unstaged.size());
        assertEquals(1, unstaged.get(0).additions());
        assertTrue(git.diffStaged().isEmpty());

        git.add(List.of(root));
        assertTrue(git.diffUnstaged().isEmpty());
        assertEquals(1, git.diffStaged().size());

        write("b.txt", "new\n");
        String second = commitAll("Second");
        List<FileDiff> between = git.diffCommits(first, second);
        assertEquals(List.of("a.txt", "b.txt"), between.stream().map(FileDiff::path).toList());
        assertEquals(ChangeType.ADDED, between.get(1).type());
    }

    @Test
    void showDiffsAgainstParentAndRootAgainstEmpty() throws IOException {
        write("a.txt", "x\n");
        commitAll("First");
        write("a.txt", "y\n");
        commitAll("Second");

        CommitDetails head = git.show("HEAD");
        assertEquals("Second", head.commit().firstLine());
        assertEquals(ChangeType.MODIFIED, head.diffs().get(0).type());

        CommitDetails root = git.show("HEAD~1");
        assertEquals("First", root.commit().firstLine());
        assertEquals(ChangeType.ADDED, root.diffs().get(0).type());
    }

    // ---------------------------------------------------------------- revisions

    @Test
    void resolvesRevisionSyntax() throws IOException {
        write("a.txt", "1");
        String c1 = commitAll("1");
        write("a.txt", "2");
        String c2 = commitAll("2");
        write("a.txt", "3");
        String c3 = commitAll("3");
        git.createTag("v1", Optional.of(c1));

        assertEquals(c3, git.resolveCommit("HEAD"));
        assertEquals(c3, git.resolveCommit("main"));
        assertEquals(c2, git.resolveCommit("HEAD^"));
        assertEquals(c1, git.resolveCommit("HEAD~2"));
        assertEquals(c1, git.resolveCommit("main^^"));
        assertEquals(c1, git.resolveCommit("v1"));
        assertEquals(c2, git.resolveCommit(c2.substring(0, 6)));
        assertThrows(MiniGitException.class, () -> git.resolveCommit("HEAD~3"));
        assertThrows(MiniGitException.class, () -> git.resolveCommit("nope"));
    }

    // ---------------------------------------------------------------- branches and checkout

    @Test
    void branchesDivergeAndCheckoutSwapsFiles() throws IOException {
        write("shared.txt", "base\n");
        String base = commitAll("Base");

        git.checkoutNewBranch("feature");
        write("feature.txt", "only on feature\n");
        write("shared.txt", "changed on feature\n");
        commitAll("Feature work");

        CheckoutResult back = git.checkout("main");
        assertEquals(Optional.of("main"), back.branch());
        assertEquals(2, back.filesChanged());
        assertFalse(Files.exists(root.resolve("feature.txt")));
        assertEquals("base\n", Files.readString(root.resolve("shared.txt")));
        assertTrue(git.status().isClean());
        assertEquals(base, git.resolveCommit("HEAD"));

        git.checkout("feature");
        assertEquals("only on feature\n", Files.readString(root.resolve("feature.txt")));
        assertTrue(git.status().isClean());

        List<RefInfo> branches = git.branches();
        assertEquals(List.of("feature", "main"), branches.stream().map(RefInfo::name).toList());
        assertTrue(branches.get(0).current());
    }

    @Test
    void checkoutRestoresNestedFoldersAndRemovesEmptyOnes() throws IOException {
        write("a.txt", "a");
        commitAll("Base");
        git.checkoutNewBranch("deep");
        write("src/app/Main.java", "class Main {}");
        commitAll("Add source");

        git.checkout("main");
        assertFalse(Files.exists(root.resolve("src")));
        git.checkout("deep");
        assertEquals("class Main {}", Files.readString(root.resolve("src/app/Main.java")));
    }

    @Test
    void checkoutRefusesToOverwriteUncommittedWork() throws IOException {
        write("a.txt", "base\n");
        commitAll("Base");
        git.checkoutNewBranch("other");
        write("a.txt", "other\n");
        commitAll("Other");
        git.checkout("main");

        write("a.txt", "my unsaved edit\n");
        CheckoutConflictException e = assertThrows(CheckoutConflictException.class, () -> git.checkout("other"));
        assertEquals(List.of("a.txt"), e.paths());
        assertEquals("my unsaved edit\n", Files.readString(root.resolve("a.txt")), "nothing was touched");
        assertEquals(Optional.of("main"), git.currentBranch());
    }

    @Test
    void checkoutRefusesToOverwriteUntrackedFile() throws IOException {
        write("a.txt", "a");
        commitAll("Base");
        git.checkoutNewBranch("other");
        write("new.txt", "committed on other");
        commitAll("Other");
        git.checkout("main");

        write("new.txt", "my own untracked file");
        assertThrows(CheckoutConflictException.class, () -> git.checkout("other"));
        assertEquals("my own untracked file", Files.readString(root.resolve("new.txt")));
    }

    @Test
    void checkoutCarriesOverChangesToUnaffectedFiles() throws IOException {
        write("a.txt", "a");
        write("b.txt", "b");
        commitAll("Base");
        git.checkoutNewBranch("other");
        write("b.txt", "b2");
        commitAll("Change b");
        git.checkout("main");

        write("a.txt", "edited a");
        write("untracked.txt", "u");
        git.checkout("other");
        assertEquals("edited a", Files.readString(root.resolve("a.txt")));
        assertEquals("b2", Files.readString(root.resolve("b.txt")));
        assertEquals(List.of(new FileChange("a.txt", ChangeType.MODIFIED)), git.status().unstaged());
        assertEquals(List.of("untracked.txt"), git.status().untracked());
    }

    @Test
    void checkoutCommitDetachesHeadAndCommitsStayOffBranch() throws IOException {
        write("a.txt", "1");
        String c1 = commitAll("1");
        write("a.txt", "2");
        String c2 = commitAll("2");

        CheckoutResult r = git.checkout(c1.substring(0, 7));
        assertTrue(r.detached());
        assertEquals("1", Files.readString(root.resolve("a.txt")));
        assertTrue(git.status().branch().isEmpty());

        write("a.txt", "experiment");
        String c3 = commitAll("Experiment");
        assertEquals(c3, git.resolveCommit("HEAD"));
        assertEquals(c2, git.resolveCommit("main"), "main did not move");

        git.checkoutNewBranch("experiment");
        assertEquals(c3, git.resolveCommit("experiment"));
    }

    @Test
    void checkoutSameBranchIsNoOp() throws IOException {
        write("a.txt", "1");
        commitAll("1");
        assertTrue(git.checkout("main").alreadyThere());
    }

    @Test
    void deleteBranchSafety() throws IOException {
        write("a.txt", "1");
        commitAll("1");
        git.createBranch("merged", Optional.empty());
        git.checkoutNewBranch("unmerged");
        write("a.txt", "2");
        commitAll("2");
        git.checkout("main");

        assertThrows(MiniGitException.class, () -> git.deleteBranch("main", false), "current branch");
        git.deleteBranch("merged", false);
        assertThrows(MiniGitException.class, () -> git.deleteBranch("unmerged", false));
        git.deleteBranch("unmerged", true);
        assertEquals(List.of("main"), git.branches().stream().map(RefInfo::name).toList());
    }

    @Test
    void checkoutNewBranchBeforeFirstCommitRenamesTheUnbornBranch() throws IOException {
        git.checkoutNewBranch("trunk");
        write("a.txt", "1");
        CommitResult c = commitResult("First");
        assertEquals(Optional.of("trunk"), c.branch());
        assertTrue(git.branches().stream().noneMatch(b -> b.name().equals("main")));
    }

    @Test
    void refLabelsDecorateCommits() throws IOException {
        write("a.txt", "1");
        String c1 = commitAll("1");
        git.createTag("v1.0", Optional.empty());
        git.createBranch("backup", Optional.empty());
        assertEquals(List.of("HEAD -> main", "backup", "tag: v1.0"), git.refLabels().get(c1));
    }

    // ---------------------------------------------------------------- restore and reset

    @Test
    void restoreDiscardsLocalEdits() throws IOException {
        write("a.txt", "committed");
        write("dir/b.txt", "committed");
        commitAll("1");
        write("a.txt", "oops");
        Files.delete(root.resolve("dir/b.txt"));

        assertEquals(List.of("a.txt", "dir/b.txt"), git.restore(List.of(root)));
        assertEquals("committed", Files.readString(root.resolve("a.txt")));
        assertEquals("committed", Files.readString(root.resolve("dir/b.txt")));
        assertTrue(git.status().isClean());
    }

    @Test
    void unstageKeepsWorkingCopy() throws IOException {
        write("a.txt", "v1");
        commitAll("1");
        write("a.txt", "v2");
        write("new.txt", "n");
        git.add(List.of(root));

        assertEquals(List.of("a.txt", "new.txt"), git.unstage(List.of(root)));
        StatusResult s = git.status();
        assertTrue(s.staged().isEmpty());
        assertEquals(List.of(new FileChange("a.txt", ChangeType.MODIFIED)), s.unstaged());
        assertEquals(List.of("new.txt"), s.untracked());
        assertEquals("v2", Files.readString(root.resolve("a.txt")));
    }

    @Test
    void resetUndoesCommitButKeepsFiles() throws IOException {
        write("a.txt", "v1");
        String c1 = commitAll("1");
        write("a.txt", "v2");
        write("b.txt", "b");
        commitAll("2");

        assertEquals(c1, git.reset("HEAD~1"));
        assertEquals(c1, git.resolveCommit("main"));
        assertEquals("v2", Files.readString(root.resolve("a.txt")), "files untouched");
        StatusResult s = git.status();
        assertTrue(s.staged().isEmpty());
        assertEquals(List.of(new FileChange("a.txt", ChangeType.MODIFIED)), s.unstaged());
        assertEquals(List.of("b.txt"), s.untracked());
    }

    private String commitAll(String message) {
        return commitResult(message).hash();
    }

    private CommitResult commitResult(String message) {
        git.add(List.of(root));
        return git.commit(message);
    }

    private void write(String path, String content) throws IOException {
        Path file = root.resolve(path);
        Files.createDirectories(file.getParent());
        Files.writeString(file, content);
    }
}
