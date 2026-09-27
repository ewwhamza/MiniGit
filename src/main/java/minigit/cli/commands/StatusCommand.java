package minigit.cli.commands;

import minigit.api.FileChange;
import minigit.api.StatusResult;
import minigit.cli.Args;
import minigit.cli.CliContext;
import minigit.cli.Command;
import minigit.cli.UsageException;
import minigit.util.Hashing;

import java.io.PrintStream;
import java.util.List;
import java.util.Set;

public final class StatusCommand implements Command {

    @Override
    public String name() {
        return "status";
    }

    @Override
    public String summary() {
        return "Show staged, unstaged, and untracked files";
    }

    @Override
    public String usage() {
        return "minigit status";
    }

    @Override
    public int run(CliContext ctx, List<String> rawArgs) {
        if (!Args.parse(rawArgs, Set.of(), Set.of()).positionals().isEmpty()) {
            throw new UsageException("status takes no arguments");
        }
        StatusResult status = ctx.openRepository().status();
        PrintStream out = ctx.out();

        out.println(status.branch()
                .map(b -> "On branch " + b)
                .orElseGet(() -> "HEAD detached at " + Hashing.shorten(status.headCommit().orElseThrow())));
        if (status.headCommit().isEmpty()) {
            out.println();
            out.println("No commits yet");
        }
        status.merge().ifPresent(m -> {
            out.println();
            out.println("You are in the middle of a merge (" + m.message() + ").");
            if (m.conflicts().isEmpty()) {
                out.println("All conflicts are fixed: run \"minigit commit\" to finish the merge.");
            } else {
                out.println("Fix the conflicts and \"minigit add\" each file, then \"minigit commit\".");
                out.println();
                out.println("Unmerged files:");
                m.conflicts().forEach(p -> out.println("        both modified: " + p));
            }
        });

        printChanges(out, "Changes to be committed:", status.staged());
        printChanges(out, "Changes not staged for commit:", status.unstaged());
        if (!status.untracked().isEmpty()) {
            out.println();
            out.println("Untracked files:");
            status.untracked().forEach(path -> out.println("        " + path));
        }

        out.println();
        if (status.isClean()) {
            out.println("nothing to commit, working tree clean");
        } else if (status.staged().isEmpty()) {
            out.println("no changes added to commit (use \"minigit add\" to stage them)");
        }
        return 0;
    }

    private static void printChanges(PrintStream out, String heading, List<FileChange> changes) {
        if (changes.isEmpty()) {
            return;
        }
        out.println();
        out.println(heading);
        for (FileChange c : changes) {
            out.printf("        %-10s %s%n", c.type().label() + ":", c.path());
        }
    }
}
