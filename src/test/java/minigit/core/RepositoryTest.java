package minigit.core;

import minigit.api.InitResult;
import minigit.api.MiniGit;
import minigit.exception.NotARepositoryException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RepositoryTest {

    @TempDir
    Path tmp;

    @Test
    void initCreatesLayout() throws IOException {
        InitResult result = MiniGit.init(tmp);
        Path gitDir = tmp.resolve(".minigit");

        assertTrue(result.created());
        assertEquals(gitDir.toAbsolutePath().normalize(), result.gitDir());
        assertTrue(Files.isDirectory(gitDir.resolve("objects")));
        assertTrue(Files.isDirectory(gitDir.resolve("refs/heads")));
        assertTrue(Files.isDirectory(gitDir.resolve("refs/tags")));
        assertTrue(Files.isRegularFile(gitDir.resolve("index")));
        assertTrue(Files.isRegularFile(gitDir.resolve("config")));
        assertEquals("ref: refs/heads/main\n", Files.readString(gitDir.resolve("HEAD")));
    }

    @Test
    void initTwiceChangesNothing() throws IOException {
        MiniGit.init(tmp);
        MiniGit.open(tmp).setConfig("user.name", "Mohit");

        InitResult second = MiniGit.init(tmp);

        assertFalse(second.created());
        assertEquals("Mohit", MiniGit.open(tmp).getConfig("user.name").orElseThrow());
        assertEquals("ref: refs/heads/main\n", Files.readString(tmp.resolve(".minigit/HEAD")));
    }

    @Test
    void initCreatesMissingDirectory() {
        Path dir = tmp.resolve("new/project");
        assertTrue(MiniGit.init(dir).created());
        assertTrue(Files.isDirectory(dir.resolve(".minigit")));
    }

    @Test
    void findsRepositoryFromNestedFolder() throws IOException {
        MiniGit.init(tmp);
        Path nested = Files.createDirectories(tmp.resolve("src/main/java"));
        assertEquals(tmp.toAbsolutePath().normalize(), Repository.find(nested).workTree());
    }

    @Test
    void findFailsOutsideRepository() {
        assertThrows(NotARepositoryException.class, () -> Repository.find(tmp));
    }
}
