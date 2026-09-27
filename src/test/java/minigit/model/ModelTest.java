package minigit.model;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ModelTest {

    private static final String A = "a".repeat(40);
    private static final String B = "b".repeat(40);

    @Test
    void treeSortsEntriesSoHashIsStable() {
        Tree one = new Tree(List.of(TreeEntry.file("b.txt", A), TreeEntry.directory("a", B)));
        Tree two = new Tree(List.of(TreeEntry.directory("a", B), TreeEntry.file("b.txt", A)));
        assertEquals(one.hash(), two.hash());
        assertEquals("040000 tree " + B + "\ta\n100644 blob " + A + "\tb.txt\n",
                new String(one.content(), StandardCharsets.UTF_8));
    }

    @Test
    void treeRoundTripsNamesWithSpaces() {
        Tree tree = new Tree(List.of(TreeEntry.file("my notes.txt", A), TreeEntry.directory("src", B)));
        Tree parsed = Tree.parse(tree.hash(), tree.content());
        assertEquals(tree.entries(), parsed.entries());
        assertEquals(tree.hash(), parsed.hash());
    }

    @Test
    void treeRejectsDuplicateNames() {
        assertThrows(IllegalArgumentException.class,
                () -> new Tree(List.of(TreeEntry.file("x", A), TreeEntry.file("x", B))));
    }

    @Test
    void commitHasDocumentedFormatAndRoundTrips() {
        Signature author = new Signature("Mohit", "mohit@example.com",
                Instant.ofEpochSecond(1790496000L), ZoneOffset.ofHoursMinutes(5, 30));
        Commit commit = new Commit(A, List.of(B), author, "Add login page\n\nWith validation.\n");

        assertEquals("""
                tree %s
                parent %s
                author Mohit <mohit@example.com> 1790496000 +0530

                Add login page

                With validation.
                """.formatted(A, B), new String(commit.content(), StandardCharsets.UTF_8));

        Commit parsed = Commit.parse(commit.hash(), commit.content());
        assertEquals(commit.hash(), parsed.hash());
        assertEquals(List.of(B), parsed.parents());
        assertEquals(author, parsed.author());
        assertEquals("Add login page", parsed.firstLine());
    }

    @Test
    void rootCommitHasNoParentLine() {
        Signature author = new Signature("M", "", Instant.ofEpochSecond(0), ZoneOffset.UTC);
        Commit commit = new Commit(A, List.of(), author, "first");
        assertEquals(List.of(), Commit.parse(commit.hash(), commit.content()).parents());
    }

    @Test
    void signatureHandlesNegativeOffsets() {
        Signature s = Signature.parse("Ana Maria <ana@x.org> 100 -0330");
        assertEquals("Ana Maria", s.name());
        assertEquals(ZoneOffset.ofHoursMinutes(-3, -30), s.offset());
        assertEquals("Ana Maria <ana@x.org> 100 -0330", s.format());
    }
}
