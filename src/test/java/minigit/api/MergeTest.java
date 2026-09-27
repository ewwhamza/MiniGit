package minigit.api;

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

/** Merging branches through the facade. */
class MergeTest {

    @TempDir
    Path root;

    private MiniGit git;

    @BeforeEach
    void setUp() {
        MiniGit.init(root);
        git = MiniGit.open(root);
        git.setConfig("user.name", "Mohit");
    }

    @Test
    void upToDateWhenOtherBranchIsBehind() throws IOException {
        write("a.txt", "1\n");
        commitAll("1");
        git.createBranch("old", Optional.empty());
        write("a.txt", "2\n");
        commitAll("2");
        assertEquals(MergeResult.Kind.UP_TO_DATE, git.merge("old").kind());
    }

    @Test
    void fastForwardMovesBranchAndFiles() throws IOException {
        write("a.txt", "1\n");
        commitAll("1");
        git.checkoutNewBranch("feature");
        write("b.txt", "new\n");
        String tip = commitAll("Feature");
        git.checkout("main");

        MergeResult r = git.merge("feature");
        assertEquals(MergeResult.Kind.FAST_FORWARD, r.kind());
        assertEquals(tip, git.resolveCommit("main"));
        assertEquals("new\n", Files.readString(root.resolve("b.txt")));
        assertTrue(git.status().isClean());
    }

    @Test
    void divergedBranchesMergeCleanlyIntoTwoParentCommit() throws IOException {
        write("shared.txt", "a\nb\nc\nd\ne\n");
        String base = commitAll("Base");
        git.checkoutNewBranch("feature");
        write("shared.txt", "a\nb\nc\nd\nE\n");
        write("feature.txt", "f\n");
        String theirs = commitAll("Feature");
        git.checkout("main");
        write("shared.txt", "A\nb\nc\nd\ne\n");
        String ours = commitAll("Main");

        MergeResult r = git.merge("feature");
        assertEquals(MergeResult.Kind.MERGED, r.kind());
        assertEquals("A\nb\nc\nd\nE\n", Files.readString(root.resolve("shared.txt")));
        assertEquals("f\n", Files.readString(root.resolve("feature.txt")));
        assertTrue(git.status().isClean());

        CommitInfo merge = git.log(1).get(0);
        assertEquals(List.of(ours, theirs), merge.parents());
        assertEquals("Merge branch 'feature' into main", merge.message());
        List<String> history = git.log(0).stream().map(CommitInfo::hash).toList();
        assertTrue(history.containsAll(List.of(base, ours, theirs)));
    }

    @Test
    void deletionOnOneSideIsMerged() throws IOException {
        write("keep.txt", "k\n");
        write("gone.txt", "g\n");
        commitAll("Base");
        git.checkoutNewBranch("cleanup");
        Files.delete(root.resolve("gone.txt"));
        commitAll("Remove gone.txt");
        git.checkout("main");
        write("keep.txt", "k2\n");
        commitAll("Edit keep");

        assertEquals(MergeResult.Kind.MERGED, git.merge("cleanup").kind());
        assertFalse(Files.exists(root.resolve("gone.txt")));
        assertEquals("k2\n", Files.readString(root.resolve("keep.txt")));
    }

    @Test
    void conflictStopsThenResolveAndCommitFinishesMerge() throws IOException {
        write("a.txt", "line\n");
        commitAll("Base");
        git.checkoutNewBranch("feature");
        write("a.txt", "feature line\n");
        String theirs = commitAll("Feature");
        git.checkout("main");
        write("a.txt", "main line\n");
        String ours = commitAll("Main");

        MergeResult r = git.merge("feature");
        assertEquals(MergeResult.Kind.CONFLICTS, r.kind());
        assertEquals(List.of("a.txt"), r.conflicts());
        String content = Files.readString(root.resolve("a.txt"));
        assertTrue(content.contains("<<<<<<< main\nmain line\n=======\nfeature line\n>>>>>>> feature\n"));

        StatusResult s = git.status();
        assertTrue(s.merge().isPresent());
        assertEquals(List.of("a.txt"), s.merge().get().conflicts());
        assertTrue(s.isConflicted("a.txt"));

        assertThrows(MiniGitException.class, () -> git.commit(null), "conflicts unresolved");
        assertThrows(MiniGitException.class, () -> git.checkout("feature"), "no switching mid-merge");
        assertThrows(MiniGitException.class, () -> git.merge("feature"), "no second merge");

        write("a.txt", "resolved line\n");
        git.add(List.of(root.resolve("a.txt")));
        assertTrue(git.status().merge().get().conflicts().isEmpty());

        CommitResult c = git.commit(null);
        assertEquals("Merge branch 'feature' into main", git.log(1).get(0).message());
        assertEquals(List.of(ours, theirs), git.log(1).get(0).parents());
        assertTrue(git.status().merge().isEmpty());
        assertEquals(c.hash(), git.resolveCommit("HEAD"));
    }

    @Test
    void resolvingByKeepingOurVersionStillCommits() throws IOException {
        write("a.txt", "base\n");
        commitAll("Base");
        git.checkoutNewBranch("feature");
        write("a.txt", "theirs\n");
        commitAll("Feature");
        git.checkout("main");
        write("a.txt", "ours\n");
        commitAll("Main");

        git.merge("feature");
        // Exactly this branch's content: the index does not change, but the conflict is resolved.
        write("a.txt", "ours\n");
        git.add(List.of(root.resolve("a.txt")));
        git.commit("Keep ours");
        assertEquals(2, git.log(1).get(0).parents().size());
        assertEquals("ours\n", Files.readString(root.resolve("a.txt")));
    }

    @Test
    void abortRestoresPreMergeState() throws IOException {
        write("a.txt", "base\n");
        commitAll("Base");
        git.checkoutNewBranch("feature");
        write("a.txt", "theirs\n");
        write("new.txt", "added by feature\n");
        commitAll("Feature");
        git.checkout("main");
        write("a.txt", "ours\n");
        String ours = commitAll("Main");

        assertEquals(MergeResult.Kind.CONFLICTS, git.merge("feature").kind());
        assertTrue(Files.exists(root.resolve("new.txt")), "clean part of the merge was applied");

        git.abortMerge();
        assertEquals("ours\n", Files.readString(root.resolve("a.txt")));
        assertFalse(Files.exists(root.resolve("new.txt")));
        assertTrue(git.status().isClean());
        assertEquals(ours, git.resolveCommit("HEAD"));
        assertThrows(MiniGitException.class, () -> git.abortMerge());
    }

    @Test
    void modifyDeleteConflict() throws IOException {
        write("a.txt", "base\n");
        write("other.txt", "o\n");
        commitAll("Base");
        git.checkoutNewBranch("feature");
        Files.delete(root.resolve("a.txt"));
        commitAll("Delete a");
        git.checkout("main");
        write("a.txt", "edited\n");
        commitAll("Edit a");

        MergeResult r = git.merge("feature");
        assertEquals(List.of("a.txt"), r.conflicts());
        assertEquals("edited\n", Files.readString(root.resolve("a.txt")), "the edited version is kept for review");
    }

    @Test
    void refusesToMergeWithUncommittedChanges() throws IOException {
        write("a.txt", "1\n");
        commitAll("1");
        git.checkoutNewBranch("feature");
        write("b.txt", "b\n");
        commitAll("2");
        git.checkout("main");
        write("a.txt", "dirty\n");

        MiniGitException e = assertThrows(MiniGitException.class, () -> git.merge("feature"));
        assertTrue(e.getMessage().contains("a.txt"));
    }

    @Test
    void refusesWhenUntrackedFileIsInTheWay() throws IOException {
        write("a.txt", "1\n");
        commitAll("1");
        git.checkoutNewBranch("feature");
        write("b.txt", "from feature\n");
        commitAll("Feature");
        git.checkout("main");
        write("c.txt", "c\n");
        commitAll("Main");
        write("b.txt", "my untracked b\n");

        assertThrows(CheckoutConflictException.class, () -> git.merge("feature"));
        assertEquals("my untracked b\n", Files.readString(root.resolve("b.txt")));
        assertTrue(git.status().merge().isEmpty());
    }

    private String commitAll(String message) {
        git.add(List.of(root));
        return git.commit(message).hash();
    }

    private void write(String path, String content) throws IOException {
        Path file = root.resolve(path);
        Files.createDirectories(file.getParent());
        Files.writeString(file, content);
    }
}
