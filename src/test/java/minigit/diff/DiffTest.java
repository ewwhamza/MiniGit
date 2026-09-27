package minigit.diff;

import minigit.api.ChangeType;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DiffTest {

    /** Renders edits compactly, e.g. " a|-b|+c". */
    private static String render(List<Edit> edits) {
        return edits.stream().map(e -> e.type().symbol() + e.content()).collect(Collectors.joining("|"));
    }

    private static List<String> lines(String... lines) {
        List<String> out = new ArrayList<>();
        for (String l : lines) {
            out.add(l + "\n");
        }
        return out;
    }

    @Test
    void splitLinesKeepsTerminators() {
        assertEquals(List.of("a\n", "b\r\n", "c"), LineDiff.splitLines("a\nb\r\nc"));
        assertEquals(List.of("a\n"), LineDiff.splitLines("a\n"));
        assertEquals(List.of(), LineDiff.splitLines(""));
    }

    @Test
    void identicalTextsHaveOnlyEqualEdits() {
        assertEquals(" a| b", render(LineDiff.diff(lines("a", "b"), lines("a", "b"))));
    }

    @Test
    void findsMinimalChangeUsingLcs() {
        // LCS of ABCABBA and CBABAC has length 4, so 3 deletions + 2 insertions.
        List<Edit> edits = LineDiff.diff(lines("A", "B", "C", "A", "B", "B", "A"),
                lines("C", "B", "A", "B", "A", "C"));
        assertEquals(4, edits.stream().filter(e -> e.type() == Edit.Type.EQUAL).count());
        assertEquals(3, edits.stream().filter(e -> e.type() == Edit.Type.DELETE).count());
        assertEquals(2, edits.stream().filter(e -> e.type() == Edit.Type.INSERT).count());
    }

    @Test
    void replacementShowsDeletionBeforeInsertion() {
        assertEquals(" a|-b|+B| c", render(LineDiff.diff(lines("a", "b", "c"), lines("a", "B", "c"))));
    }

    @Test
    void handlesEmptySides() {
        assertEquals("+a|+b", render(LineDiff.diff(List.of(), lines("a", "b"))));
        assertEquals("-a", render(LineDiff.diff(lines("a"), List.of())));
    }

    @Test
    void editsCarryLineNumbers() {
        List<Edit> edits = LineDiff.diff(lines("a", "b"), lines("a", "x", "b"));
        assertEquals(new Edit(Edit.Type.INSERT, "x\n", -1, 1), edits.get(1));
        assertEquals(new Edit(Edit.Type.EQUAL, "b\n", 1, 2), edits.get(2));
    }

    @Test
    void hunkHeadersMatchGit() {
        List<String> old = new ArrayList<>();
        for (int i = 1; i <= 20; i++) {
            old.add(i + "\n");
        }
        List<String> now = new ArrayList<>(old);
        now.set(9, "ten\n");          // line 10 changed
        now.add(15, "extra\n");       // inserted after line 15

        List<Hunk> hunks = Hunk.fromEdits(LineDiff.diff(old, now), 3);
        assertEquals(1, hunks.size(), "changes 5 lines apart share a hunk");
        assertEquals("@@ -7,12 +7,13 @@", hunks.get(0).header());

        now = new ArrayList<>(old);
        now.set(1, "two\n");
        now.set(17, "eighteen\n");
        hunks = Hunk.fromEdits(LineDiff.diff(old, now), 3);
        assertEquals(List.of("@@ -1,5 +1,5 @@", "@@ -15,6 +15,6 @@"), hunks.stream().map(Hunk::header).toList());
    }

    @Test
    void emptyFileRangesUseZero() {
        List<Hunk> hunks = Hunk.fromEdits(LineDiff.diff(List.of(), lines("a")), 3);
        assertEquals("@@ -0,0 +1 @@", hunks.get(0).header());
    }

    @Test
    void unifiedFormatForModifiedFile() {
        FileDiff diff = FileDiff.of("src/Main.java", bytes("a\nb\nc\n"), bytes("a\nB\nc\n"));
        assertEquals("""
                diff --minigit a/src/Main.java b/src/Main.java
                --- a/src/Main.java
                +++ b/src/Main.java
                @@ -1,3 +1,3 @@
                 a
                -b
                +B
                 c
                """, UnifiedFormatter.format(diff));
        assertEquals(1, diff.additions());
        assertEquals(1, diff.deletions());
    }

    @Test
    void unifiedFormatForAddedFileAndMissingNewline() {
        FileDiff diff = FileDiff.of("new.txt", null, bytes("hi"));
        assertEquals(ChangeType.ADDED, diff.type());
        assertEquals("""
                diff --minigit a/new.txt b/new.txt
                new file
                --- /dev/null
                +++ b/new.txt
                @@ -0,0 +1 @@
                +hi
                \\ No newline at end of file
                """, UnifiedFormatter.format(diff));
    }

    @Test
    void binaryFilesAreNotLineDiffed() {
        FileDiff diff = FileDiff.of("img.png", new byte[]{1, 0, 2}, new byte[]{1, 0, 3});
        assertTrue(diff.binary());
        assertTrue(UnifiedFormatter.format(diff).contains("Binary files differ"));
    }

    @Test
    void largeInputsStayCorrect() {
        List<String> old = new ArrayList<>();
        List<String> now = new ArrayList<>();
        for (int i = 0; i < 2000; i++) {
            old.add("line " + i + "\n");
            now.add((i % 100 == 0 ? "changed " : "line ") + i + "\n");
        }
        List<Edit> edits = LineDiff.diff(old, now);
        assertEquals(20, edits.stream().filter(e -> e.type() == Edit.Type.INSERT).count());
        assertEquals(1980, edits.stream().filter(e -> e.type() == Edit.Type.EQUAL).count());
    }

    private static byte[] bytes(String s) {
        return s.getBytes(StandardCharsets.UTF_8);
    }
}
