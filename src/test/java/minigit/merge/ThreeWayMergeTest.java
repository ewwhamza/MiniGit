package minigit.merge;

import minigit.diff.LineDiff;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ThreeWayMergeTest {

    private static ThreeWayMerge.Result merge(String base, String ours, String theirs) {
        return ThreeWayMerge.merge(LineDiff.splitLines(base), LineDiff.splitLines(ours),
                LineDiff.splitLines(theirs), "main", "feature");
    }

    @Test
    void changesToDifferentLinesCombine() {
        ThreeWayMerge.Result r = merge("a\nb\nc\nd\ne\n", "A\nb\nc\nd\ne\n", "a\nb\nc\nd\nE\n");
        assertTrue(r.clean());
        assertEquals("A\nb\nc\nd\nE\n", r.text());
    }

    @Test
    void oneSidedChangesAreTaken() {
        assertEquals("a\nX\nc\n", merge("a\nb\nc\n", "a\nb\nc\n", "a\nX\nc\n").text());
        assertEquals("a\nc\n", merge("a\nb\nc\n", "a\nc\n", "a\nb\nc\n").text());
        assertEquals("a\nb\nc\nd\n", merge("a\nb\nc\n", "a\nb\nc\n", "a\nb\nc\nd\n").text());
    }

    @Test
    void identicalChangesOnBothSidesAreNotAConflict() {
        ThreeWayMerge.Result r = merge("a\nb\nc\n", "a\nB\nc\n", "a\nB\nc\n");
        assertTrue(r.clean());
        assertEquals("a\nB\nc\n", r.text());
    }

    @Test
    void differentChangesToSameLineConflictWithMarkers() {
        ThreeWayMerge.Result r = merge("a\nb\nc\n", "a\nours\nc\n", "a\ntheirs\nc\n");
        assertFalse(r.clean());
        assertEquals(1, r.conflicts());
        assertEquals("""
                a
                <<<<<<< main
                ours
                =======
                theirs
                >>>>>>> feature
                c
                """, r.text());
    }

    @Test
    void insertionsAtSamePlaceConflict() {
        ThreeWayMerge.Result r = merge("a\nz\n", "a\none\nz\n", "a\ntwo\nz\n");
        assertEquals(1, r.conflicts());
        assertTrue(r.text().contains("one\n=======\ntwo\n"));
    }

    @Test
    void conflictAndCleanChangesInOneFile() {
        String base = "1\n2\n3\n4\n5\n6\n7\n8\n";
        String ours = "ONE\n2\n3\n4\n5\n6\nours7\n8\n";
        String theirs = "1\n2\n3\n4\n5\n6\ntheirs7\nEIGHT\n";
        ThreeWayMerge.Result r = merge(base, ours, theirs);
        assertEquals(1, r.conflicts());
        assertTrue(r.text().startsWith("ONE\n2\n"), "clean change on line 1 kept");
    }

    @Test
    void missingFinalNewlineStillGetsMarkersOnTheirOwnLines() {
        ThreeWayMerge.Result r = merge("x", "ours", "theirs");
        assertEquals("<<<<<<< main\nours\n=======\ntheirs\n>>>>>>> feature\n", r.text());
    }

    @Test
    void emptyBaseWithBothSidesAddingDifferentContentConflicts() {
        assertEquals(1, merge("", "mine\n", "yours\n").conflicts());
        assertTrue(merge("", "same\n", "same\n").clean());
    }
}
