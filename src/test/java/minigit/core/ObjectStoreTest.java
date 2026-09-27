package minigit.core;

import minigit.exception.CorruptObjectException;
import minigit.exception.MiniGitException;
import minigit.exception.ObjectNotFoundException;
import minigit.model.Blob;
import minigit.model.ObjectType;
import minigit.model.RawObject;
import minigit.util.Compression;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ObjectStoreTest {

    private static final String HELLO_HASH = "ce013625030ba8dba906f756967f9e9ca394464a";

    @TempDir
    Path tmp;

    private ObjectStore store;

    @BeforeEach
    void setUp() {
        store = new ObjectStore(tmp.resolve("objects"));
    }

    @Test
    void writeThenReadRoundTrips() {
        String hash = store.write(blob("hello\n"));
        assertEquals(HELLO_HASH, hash);

        RawObject obj = store.read(hash);
        assertEquals(ObjectType.BLOB, obj.type());
        assertEquals(6, obj.size());
        assertArrayEquals("hello\n".getBytes(StandardCharsets.UTF_8), obj.content());
    }

    @Test
    void storesUnderTwoCharacterBucket() {
        store.write(blob("hello\n"));
        assertTrue(Files.isRegularFile(tmp.resolve("objects/ce/013625030ba8dba906f756967f9e9ca394464a")));
    }

    @Test
    void identicalContentIsStoredOnce() throws IOException {
        String first = store.write(blob("same"));
        String second = store.write(blob("same"));
        assertEquals(first, second);
        assertEquals(1, countObjectFiles());
    }

    @Test
    void readingMissingObjectFails() {
        assertThrows(ObjectNotFoundException.class, () -> store.read(HELLO_HASH));
    }

    @Test
    void detectsContentThatNoLongerMatchesItsHash() throws IOException {
        store.write(blob("hello\n"));
        Path file = store.pathFor(HELLO_HASH);
        Files.write(file, Compression.compress("blob 6\0HELLO\n".getBytes(StandardCharsets.UTF_8)));
        assertThrows(CorruptObjectException.class, () -> store.read(HELLO_HASH));
    }

    @Test
    void detectsUndecompressableFile() throws IOException {
        store.write(blob("hello\n"));
        Files.writeString(store.pathFor(HELLO_HASH), "garbage");
        assertThrows(CorruptObjectException.class, () -> store.read(HELLO_HASH));
    }

    @Test
    void resolvesUniquePrefix() {
        store.write(blob("hello\n"));
        assertEquals(HELLO_HASH, store.resolve("ce01"));
        assertEquals(HELLO_HASH, store.resolve("CE0136"));
        assertEquals(HELLO_HASH, store.resolve(HELLO_HASH));
    }

    @Test
    void rejectsAmbiguousPrefix() throws IOException {
        // Two fake objects sharing the prefix "abcd".
        Path bucket = Files.createDirectories(tmp.resolve("objects/ab"));
        Files.writeString(bucket.resolve("cd" + "0".repeat(36)), "x");
        Files.writeString(bucket.resolve("cd" + "1".repeat(36)), "x");

        MiniGitException e = assertThrows(MiniGitException.class, () -> store.resolve("abcd"));
        assertTrue(e.getMessage().contains("ambiguous"));
        assertEquals("abcd" + "1".repeat(36), store.resolve("abcd1"));
    }

    @Test
    void ignoresTempFilesWhenResolving() throws IOException {
        Path bucket = Files.createDirectories(tmp.resolve("objects/ab"));
        Files.writeString(bucket.resolve("cdef.1234.tmp"), "partial");
        assertThrows(ObjectNotFoundException.class, () -> store.resolve("abcd"));
    }

    @Test
    void rejectsInvalidPrefixes() {
        assertThrows(MiniGitException.class, () -> store.resolve("ce0"));
        assertThrows(MiniGitException.class, () -> store.resolve("xyz123"));
        assertThrows(ObjectNotFoundException.class, () -> store.resolve("dead"));
    }

    private static Blob blob(String text) {
        return new Blob(text.getBytes(StandardCharsets.UTF_8));
    }

    private long countObjectFiles() throws IOException {
        try (Stream<Path> files = Files.walk(tmp.resolve("objects"))) {
            return files.filter(Files::isRegularFile).count();
        }
    }
}
