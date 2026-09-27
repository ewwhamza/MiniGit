package minigit.cli;

import minigit.Main;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Runs whole commands through {@link Main#run}, the same path the terminal uses. */
class CliTest {

    private static final String HELLO_HASH = "ce013625030ba8dba906f756967f9e9ca394464a";

    @TempDir
    Path tmp;

    private record Result(int status, String out, String err) {
    }

    private Result run(String... args) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ByteArrayOutputStream err = new ByteArrayOutputStream();
        int status = Main.run(args, tmp,
                new PrintStream(out, true, StandardCharsets.UTF_8),
                new PrintStream(err, true, StandardCharsets.UTF_8));
        return new Result(status, out.toString(StandardCharsets.UTF_8), err.toString(StandardCharsets.UTF_8));
    }

    @Test
    void initThenReinit() {
        Result first = run("init");
        assertEquals(0, first.status());
        assertTrue(first.out().startsWith("Initialized empty MiniGit repository in"));

        Result second = run("init");
        assertEquals(0, second.status());
        assertTrue(second.out().contains("already exists"));
    }

    @Test
    void hashStoreAndReadBack() throws IOException {
        run("init");
        Files.writeString(tmp.resolve("hello.txt"), "hello\n");

        assertEquals(HELLO_HASH, run("hash-object", "hello.txt").out().strip());
        assertFalse(Files.exists(tmp.resolve(".minigit/objects/ce")), "without -w nothing is stored");

        assertEquals(HELLO_HASH, run("hash-object", "-w", "hello.txt").out().strip());
        assertEquals("blob", run("cat-file", "-t", "ce01").out().strip());
        assertEquals("6", run("cat-file", "-s", "ce01").out().strip());
        assertEquals("hello\n", run("cat-file", "-p", "ce01").out());

        String overview = run("cat-file", "ce01").out();
        assertTrue(overview.contains("type   blob"));
        assertTrue(overview.endsWith("hello\n"));
    }

    @Test
    void configSetGetListUnset() {
        run("init");
        assertEquals(0, run("config", "user.name", "Mohit Kumar").status());
        assertEquals("Mohit Kumar", run("config", "user.name").out().strip());
        assertTrue(run("config", "--list").out().contains("user.name=Mohit Kumar"));
        assertEquals(0, run("config", "--unset", "user.name").status());

        Result missing = run("config", "user.name");
        assertEquals(1, missing.status());
        assertEquals("", missing.out());
    }

    @Test
    void commandsOutsideRepositoryFailCleanly() {
        Result r = run("cat-file", "-p", "ce01");
        assertEquals(1, r.status());
        assertTrue(r.err().startsWith("fatal: not a minigit repository"));
        assertFalse(r.err().contains("Exception"), "no stack trace without --debug");
    }

    @Test
    void unknownCommandAndBadArguments() {
        Result unknown = run("frobnicate");
        assertEquals(1, unknown.status());
        assertTrue(unknown.err().contains("'frobnicate' is not a minigit command"));

        run("init");
        Result badOption = run("cat-file", "-z", "ce01");
        assertEquals(1, badOption.status());
        assertTrue(badOption.err().contains("unknown option '-z'"));
        assertTrue(badOption.err().contains("usage: minigit cat-file"));

        Result noFile = run("hash-object", "missing.txt");
        assertEquals(1, noFile.status());
        assertTrue(noFile.err().contains("no such file"));
    }

    @Test
    void addCommitStatusLogCycle() throws IOException {
        run("init");
        run("config", "user.name", "Mohit");
        Files.writeString(tmp.resolve("README.md"), "hello\n");

        String status = run("status").out();
        assertTrue(status.contains("On branch main"));
        assertTrue(status.contains("No commits yet"));
        assertTrue(status.contains("Untracked files:"));

        assertEquals(0, run("add", ".").status());
        assertTrue(run("status").out().contains("new file:  README.md"));

        Result commit = run("commit", "-m", "Initial commit");
        assertEquals(0, commit.status());
        assertTrue(commit.out().matches("(?s)\\[main \\(root-commit\\) [0-9a-f]{7}] Initial commit.*"), commit.out());
        assertTrue(commit.out().contains("1 file changed, 1 added"));
        assertTrue(run("status").out().contains("nothing to commit, working tree clean"));

        Files.writeString(tmp.resolve("README.md"), "hello again\n");
        assertTrue(run("status").out().contains("Changes not staged for commit:"));
        run("add", "README.md");
        run("commit", "-m", "Update readme");

        String log = run("log").out();
        assertTrue(log.contains("(HEAD -> main)"));
        assertTrue(log.contains("Author: Mohit <>"));
        assertTrue(log.indexOf("Update readme") < log.indexOf("Initial commit"), "newest first");

        String[] oneline = run("log", "--oneline").out().strip().split("\\R");
        assertEquals(2, oneline.length);
        assertTrue(oneline[0].matches("[0-9a-f]{7} \\(HEAD -> main\\) Update readme"), oneline[0]);
        assertEquals(1, run("log", "-n", "1", "--oneline").out().strip().split("\\R").length);
    }

    @Test
    void commitEdgeCases() throws IOException {
        run("init");
        Result noName = run("commit", "-m", "x");
        assertEquals(1, noName.status());

        run("config", "user.name", "Mohit");
        Result nothing = run("commit", "-m", "x");
        assertEquals(1, nothing.status());
        assertTrue(nothing.out().startsWith("nothing to commit"));

        Result noMessage = run("commit");
        assertEquals(1, noMessage.status());
        assertTrue(noMessage.err().contains("a message is required"));

        Result unquoted = run("commit", "-m", "two", "words");
        assertEquals(1, unquoted.status());
        assertTrue(unquoted.err().contains("put the message in quotes"));

        assertEquals(1, run("log").status());
    }

    @Test
    void rmCommand() throws IOException {
        run("init");
        run("config", "user.name", "Mohit");
        Files.writeString(tmp.resolve("a.txt"), "x");
        run("add", ".");
        run("commit", "-m", "First");

        Result rm = run("rm", "a.txt");
        assertEquals(0, rm.status());
        assertEquals("rm 'a.txt'", rm.out().strip());
        assertFalse(Files.exists(tmp.resolve("a.txt")));
        assertTrue(run("status").out().contains("deleted:   a.txt"));
    }

    @Test
    void diffBranchCheckoutTagCommands() throws IOException {
        run("init");
        run("config", "user.name", "Mohit");
        Files.writeString(tmp.resolve("a.txt"), "one\ntwo\n");
        run("add", ".");
        run("commit", "-m", "First");

        Files.writeString(tmp.resolve("a.txt"), "one\n2\n");
        String diff = run("diff").out();
        assertTrue(diff.contains("--- a/a.txt"));
        assertTrue(diff.contains("-two"));
        assertTrue(diff.contains("+2"));
        assertEquals("", run("diff", "--staged").out());
        assertTrue(run("diff", "--stat").out().contains("a.txt | +1 -1"));

        run("add", ".");
        run("commit", "-m", "Second");
        assertTrue(run("diff", "HEAD~1", "HEAD").out().contains("+2"));
        assertTrue(run("show").out().contains("    Second"));

        assertTrue(run("branch", "feature").out().startsWith("Created branch feature at "));
        assertEquals("  feature\n* main\n", run("branch").out().replace("\r", ""));
        assertTrue(run("checkout", "feature").out().startsWith("Switched to branch 'feature'"));
        assertTrue(run("checkout", "feature").out().startsWith("Already on 'feature'"));

        assertTrue(run("tag", "v1.0").out().startsWith("Tagged "));
        assertTrue(run("log", "--oneline", "-n", "1").out().contains("(HEAD -> feature, main, tag: v1.0)"));

        Result detached = run("checkout", "HEAD~1");
        assertTrue(detached.out().contains("detached HEAD"));
        assertTrue(run("status").out().startsWith("HEAD detached at "));
    }

    @Test
    void checkoutConflictIsReportedClearly() throws IOException {
        run("init");
        run("config", "user.name", "Mohit");
        Files.writeString(tmp.resolve("a.txt"), "base");
        run("add", ".");
        run("commit", "-m", "Base");
        run("checkout", "-b", "other");
        Files.writeString(tmp.resolve("a.txt"), "other");
        run("add", ".");
        run("commit", "-m", "Other");
        run("checkout", "main");

        Files.writeString(tmp.resolve("a.txt"), "unsaved");
        Result r = run("checkout", "other");
        assertEquals(1, r.status());
        assertTrue(r.err().contains("would be overwritten"));
        assertTrue(r.err().contains("a.txt"));
    }

    @Test
    void restoreAndReset() throws IOException {
        run("init");
        run("config", "user.name", "Mohit");
        Files.writeString(tmp.resolve("a.txt"), "v1");
        run("add", ".");
        run("commit", "-m", "First");

        Files.writeString(tmp.resolve("a.txt"), "oops");
        assertEquals("Restored a.txt", run("restore", "a.txt").out().strip());
        assertEquals("v1", Files.readString(tmp.resolve("a.txt")));

        Files.writeString(tmp.resolve("a.txt"), "v2");
        run("add", ".");
        assertEquals("Unstaged a.txt", run("restore", "--staged", "a.txt").out().strip());
        run("add", ".");
        run("commit", "-m", "Second");

        assertTrue(run("reset", "HEAD~1").out().startsWith("HEAD is now at "));
        assertTrue(run("status").out().contains("modified:  a.txt"));
    }

    @Test
    void mergeConflictResolveCommitViaCli() throws IOException {
        run("init");
        run("config", "user.name", "Mohit");
        Files.writeString(tmp.resolve("a.txt"), "base\n");
        run("add", ".");
        run("commit", "-m", "Base");
        run("checkout", "-b", "feature");
        Files.writeString(tmp.resolve("a.txt"), "feature\n");
        run("add", ".");
        run("commit", "-m", "Feature");
        run("checkout", "main");
        Files.writeString(tmp.resolve("a.txt"), "main\n");
        run("add", ".");
        run("commit", "-m", "Main");

        Result merge = run("merge", "feature");
        assertEquals(1, merge.status());
        assertTrue(merge.out().contains("CONFLICT: merge conflict in a.txt"));
        String status = run("status").out();
        assertTrue(status.contains("You are in the middle of a merge"));
        assertTrue(status.contains("both modified: a.txt"));
        assertEquals(1, run("commit").status(), "unresolved conflicts block the commit");

        Files.writeString(tmp.resolve("a.txt"), "both\n");
        run("add", "a.txt");
        Result commit = run("commit");
        assertEquals(0, commit.status(), commit.err());
        assertTrue(commit.out().contains("Merge branch 'feature' into main"));
        assertTrue(run("log", "-n", "1").out().contains("Merge:  "));
        assertEquals("Already up to date.", run("merge", "feature").out().strip());
    }

    @Test
    void helpListsEveryCommand() {
        Result help = run("help");
        assertEquals(0, help.status());
        for (String name : new String[]{"init", "add", "rm", "commit", "status", "diff", "log", "show",
                "branch", "checkout", "tag", "restore", "reset", "hash-object", "cat-file", "config", "help"}) {
            assertTrue(help.out().contains(name), "help should list " + name);
        }
        assertTrue(run("help", "cat-file").out().contains("usage: minigit cat-file"));
        assertEquals(0, run("--version").status());
    }
}
