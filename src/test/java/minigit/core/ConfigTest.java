package minigit.core;

import minigit.exception.MiniGitException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConfigTest {

    @TempDir
    Path tmp;

    @Test
    void persistsAcrossInstances() {
        Path file = tmp.resolve("config");
        new Config(file).set("user.name", "  Mohit Kumar  ");

        Config reloaded = new Config(file);
        assertEquals("Mohit Kumar", reloaded.get("user.name").orElseThrow());
        assertEquals("Mohit Kumar", reloaded.get("USER.NAME").orElseThrow());
    }

    @Test
    void writesSortedKeyValueLines() throws IOException {
        Path file = tmp.resolve("config");
        Config config = new Config(file);
        config.set("user.name", "Mohit");
        config.set("core.editor", "notepad");

        assertEquals(List.of("core.editor = notepad", "user.name = Mohit"), Files.readAllLines(file));
    }

    @Test
    void unsetRemovesKey() {
        Config config = new Config(tmp.resolve("config"));
        config.set("user.name", "Mohit");

        assertTrue(config.unset("user.name"));
        assertFalse(config.unset("user.name"));
        assertTrue(config.get("user.name").isEmpty());
    }

    @Test
    void rejectsBadKeysAndValues() {
        Config config = new Config(tmp.resolve("config"));
        assertThrows(MiniGitException.class, () -> config.set("name", "x"));
        assertThrows(MiniGitException.class, () -> config.set("user.", "x"));
        assertThrows(MiniGitException.class, () -> config.set("user.name", "   "));
        assertThrows(MiniGitException.class, () -> config.set("user.name", "a\nb"));
    }

    @Test
    void ignoresCommentsAndBlankLines() throws IOException {
        Path file = tmp.resolve("config");
        Files.writeString(file, "# settings\n\nuser.name = Mohit\n");
        assertEquals("Mohit", new Config(file).get("user.name").orElseThrow());
    }
}
