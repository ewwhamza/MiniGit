package minigit.core;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IndexTest {

    private static final String A = "a".repeat(40);
    private static final String B = "b".repeat(40);

    @TempDir
    Path tmp;

    @Test
    void savesSortedLinesAndReloads() throws IOException {
        Path file = tmp.resolve("index");
        Index index = new Index(file);
        index.put(new IndexEntry("src/Main.java", B, 412, 2000));
        index.put(new IndexEntry("README.md", A, 23, 1000));
        index.put(new IndexEntry("my notes.txt", A, 5, 3000));
        index.save();

        assertEquals(List.of(
                A + " 23 1000 README.md",
                A + " 5 3000 my notes.txt",
                B + " 412 2000 src/Main.java"), Files.readAllLines(file));

        Index reloaded = new Index(file);
        assertEquals(index.entries().stream().toList(), reloaded.entries().stream().toList());
    }

    @Test
    void pathsUnderMatchesWholeFolderNamesOnly() {
        Index index = new Index(tmp.resolve("index"));
        for (String p : List.of("src/A.java", "src/util/B.java", "src2/C.java", "srcfile.txt", "README.md")) {
            index.put(new IndexEntry(p, A, 1, 1));
        }
        assertEquals(Set.of("src/A.java", "src/util/B.java"), index.pathsUnder("src"));
        assertEquals(Set.of("src/util/B.java"), index.pathsUnder("src/util"));
        assertEquals(5, index.pathsUnder("").size());
    }

    @Test
    void entriesStagedAtWriteTimeAreNotTrusted() throws IOException {
        Path file = tmp.resolve("index");
        new Index(file).save();
        long writtenAt = Files.getLastModifiedTime(file).toMillis();
        Index index = new Index(file);

        IndexEntry old = new IndexEntry("a", A, 10, writtenAt - 5000);
        assertTrue(index.isUnchanged(old, 10, writtenAt - 5000));
        assertFalse(index.isUnchanged(old, 11, writtenAt - 5000), "size changed");
        assertFalse(index.isUnchanged(old, 10, writtenAt - 4000), "timestamp changed");

        IndexEntry racy = new IndexEntry("b", A, 10, writtenAt);
        assertFalse(index.isUnchanged(racy, 10, writtenAt), "same millisecond as the index write");
    }
}
