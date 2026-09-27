package minigit.gui;

import minigit.api.ChangeType;
import minigit.api.CommitInfo;
import minigit.diff.FileDiff;
import minigit.gui.model.CommitTableModel;
import minigit.gui.model.DiffLine;
import minigit.gui.model.FileGroupModel;
import minigit.model.Signature;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.prefs.Preferences;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The GUI's data models, which need no window to test. */
class GuiModelTest {

    @TempDir
    Path tmp;

    @Test
    void diffLinesCarryKindsAndLineNumbers() {
        FileDiff diff = FileDiff.of("a.txt", bytes("a\nb\nc\n"), bytes("a\nB\nc"));
        List<DiffLine> rows = DiffLine.from(diff);

        assertEquals(DiffLine.Kind.HUNK, rows.get(0).kind());
        assertEquals(new DiffLine(DiffLine.Kind.CONTEXT, 1, 1, "a"), rows.get(1));
        assertEquals(new DiffLine(DiffLine.Kind.REMOVED, 2, 0, "b"), rows.get(2));
        assertEquals(new DiffLine(DiffLine.Kind.REMOVED, 3, 0, "c"), rows.get(3));
        assertEquals(new DiffLine(DiffLine.Kind.ADDED, 0, 2, "B"), rows.get(4));
        assertEquals(new DiffLine(DiffLine.Kind.ADDED, 0, 3, "c"), rows.get(5));
        assertEquals(DiffLine.Kind.INFO, rows.get(6).kind(), "no newline at end of file");
        assertEquals('+', rows.get(4).symbol());
    }

    @Test
    void binaryAndEmptyDiffsBecomeInfoLines() {
        assertEquals(DiffLine.Kind.INFO, DiffLine.from(FileDiff.of("x.bin", null, new byte[]{0, 1})).get(0).kind());
        assertEquals("Empty file", DiffLine.from(FileDiff.of("empty.txt", null, new byte[0])).get(0).text());
    }

    @Test
    void commitTableShowsLabelsAndEscapesHtml() {
        Signature author = new Signature("Mohit", "m@x", Instant.ofEpochSecond(1790496000L), ZoneOffset.UTC);
        CommitInfo c = new CommitInfo("a".repeat(40), List.of(), author, "Fix <b> & stuff\n\nbody");
        CommitTableModel model = new CommitTableModel();
        model.setCommits(List.of(c), Map.of(c.hash(), List.of("HEAD -> main", "tag: v1")));

        assertEquals(1, model.getRowCount());
        assertEquals("aaaaaaa", model.getValueAt(0, CommitTableModel.HASH));
        String html = model.messageHtml(0, true);
        assertTrue(html.contains("[HEAD -&gt; main]"));
        assertTrue(html.contains("[tag: v1]"));
        assertTrue(html.contains("Fix &lt;b&gt; &amp; stuff"));
        assertFalse(html.contains("body"), "only the first line");
        assertFalse(model.messageHtml(0, false).contains("<font"), "no colours on a selected row");
        assertEquals(0, model.indexOf(c.hash()));
    }

    @Test
    void fileGroupCheckboxRequestsToggleWithoutChangingItself() {
        List<String> toggled = new ArrayList<>();
        FileGroupModel staged = new FileGroupModel(true, toggled::add);
        staged.setRows(List.of(new FileGroupModel.Row("a.txt", ChangeType.ADDED),
                new FileGroupModel.Row("b.txt", ChangeType.DELETED)));

        assertEquals(Boolean.TRUE, staged.getValueAt(0, FileGroupModel.CHECK));
        assertEquals("D", staged.getValueAt(1, FileGroupModel.STATUS));
        staged.setValueAt(Boolean.FALSE, 1, FileGroupModel.CHECK);
        assertEquals(List.of("b.txt"), toggled);
        assertEquals(Boolean.TRUE, staged.getValueAt(1, FileGroupModel.CHECK), "state comes from the next refresh");

        FileGroupModel untracked = new FileGroupModel(false, toggled::add);
        untracked.setRows(List.of(new FileGroupModel.Row("new.txt", null)));
        assertEquals("?", untracked.getValueAt(0, FileGroupModel.STATUS));
    }

    @Test
    void recentRepositoriesKeepsFiveMostRecentExistingFolders() throws Exception {
        Preferences node = Preferences.userRoot().node("minigit-test/" + System.nanoTime());
        try {
            RecentRepositories recent = new RecentRepositories(node);
            List<Path> dirs = new ArrayList<>();
            for (int i = 0; i < 7; i++) {
                Path d = tmp.resolve("repo" + i);
                java.nio.file.Files.createDirectories(d);
                dirs.add(d);
                recent.add(d);
            }
            recent.add(dirs.get(3));
            List<Path> list = recent.list();
            assertEquals(5, list.size());
            assertEquals(dirs.get(3).toAbsolutePath().normalize(), list.get(0));
            assertEquals(dirs.get(6).toAbsolutePath().normalize(), list.get(1));

            java.nio.file.Files.delete(dirs.get(6));
            assertEquals(4, recent.list().size(), "deleted folders are skipped");
        } finally {
            node.removeNode();
        }
    }

    private static byte[] bytes(String s) {
        return s.getBytes(StandardCharsets.UTF_8);
    }
}
