package minigit.cli.commands;

import minigit.api.MiniGit;
import minigit.cli.Args;
import minigit.cli.CliContext;
import minigit.cli.Command;
import minigit.cli.UsageException;
import minigit.diff.FileDiff;
import minigit.diff.UnifiedFormatter;

import java.util.List;
import java.util.Set;

public final class DiffCommand implements Command {

    @Override
    public String name() {
        return "diff";
    }

    @Override
    public String summary() {
        return "Show line-by-line changes";
    }

    @Override
    public String usage() {
        return "minigit diff [--staged | <commit> <commit>] [--stat]";
    }

    @Override
    public String details() {
        return """
                  (no arguments)      changes not yet staged: working tree vs. index
                  --staged            changes staged for the next commit: index vs. HEAD
                  <commit> <commit>   changes between two commits, e.g. HEAD~1 HEAD
                  --stat              only list the files with added/removed line counts""";
    }

    @Override
    public int run(CliContext ctx, List<String> rawArgs) {
        Args args = Args.parse(rawArgs, Set.of("--staged", "--cached", "--stat"), Set.of());
        boolean staged = args.has("--staged") || args.has("--cached");
        List<String> revs = args.positionals();
        MiniGit repo = ctx.openRepository();

        List<FileDiff> diffs;
        if (revs.isEmpty()) {
            diffs = staged ? repo.diffStaged() : repo.diffUnstaged();
        } else if (revs.size() == 2 && !staged) {
            diffs = repo.diffCommits(revs.get(0), revs.get(1));
        } else {
            throw new UsageException("give either --staged or exactly two commits");
        }
        print(ctx, diffs, args.has("--stat"));
        return 0;
    }

    /** Shared with {@code show}. */
    static void print(CliContext ctx, List<FileDiff> diffs, boolean statOnly) {
        if (statOnly) {
            int width = diffs.stream().mapToInt(d -> d.path().length()).max().orElse(0);
            for (FileDiff d : diffs) {
                String counts = d.binary() ? "binary" : "+" + d.additions() + " -" + d.deletions();
                ctx.out().printf(" %-" + width + "s | %s%n", d.path(), counts);
            }
            ctx.out().println(" " + diffs.size() + (diffs.size() == 1 ? " file" : " files") + " changed");
            return;
        }
        for (FileDiff d : diffs) {
            ctx.out().print(UnifiedFormatter.format(d));
        }
    }
}
