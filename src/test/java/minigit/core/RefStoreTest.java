package minigit.core;

import minigit.exception.MiniGitException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RefStoreTest {

    private static final String A = "a".repeat(40);
    private static final String B = "b".repeat(40);

    @TempDir
    Path tmp;

    private RefStore refs;

    @BeforeEach
    void setUp() {
        Repository.init(tmp);
        refs = new RefStore(tmp.resolve(".minigit"));
    }

    @Test
    void branchesAreFilesHoldingAHash() throws IOException {
        refs.createBranch("feature/login", A);
        assertEquals(A + "\n", Files.readString(tmp.resolve(".minigit/refs/heads/feature/login")));
        assertEquals(Optional.of(A), refs.branch("feature/login"));
        assertEquals(List.of("feature/login"), refs.branches());
    }

    @Test
    void attachAndDetachHead() {
        refs.createBranch("dev", A);
        refs.attachHead("dev");
        assertEquals(Optional.of("dev"), refs.currentBranch());
        assertEquals(Optional.of(A), refs.headCommit());

        refs.detachHead(B);
        assertTrue(refs.currentBranch().isEmpty());
        assertEquals(Optional.of(B), refs.headCommit());
    }

    @Test
    void rejectsBadNames() {
        for (String bad : List.of("has space", "-flag", "a..b", "../escape", ".hidden", "a/.b", "end/", "end.",
                "HEAD", "x.tmp", "a//b", "")) {
            assertThrows(MiniGitException.class, () -> refs.createBranch(bad, A), bad);
        }
        for (String good : List.of("main", "feature/x", "v1.0", "fix_42", "release-2")) {
            assertDoesNotThrow(() -> refs.createTag(good, A), good);
        }
    }

    @Test
    void rejectsDuplicatesAndFolderClashes() {
        refs.createBranch("feature", A);
        assertThrows(MiniGitException.class, () -> refs.createBranch("feature", B));
        assertThrows(MiniGitException.class, () -> refs.createBranch("feature/x", B));
    }

    @Test
    void deleteCleansUpEmptyFolders() {
        refs.createBranch("feature/deep/x", A);
        refs.deleteBranch("feature/deep/x");
        assertFalse(Files.exists(tmp.resolve(".minigit/refs/heads/feature")));
        assertTrue(Files.isDirectory(tmp.resolve(".minigit/refs/heads")));
        assertThrows(MiniGitException.class, () -> refs.deleteBranch("feature/deep/x"));
    }
}
