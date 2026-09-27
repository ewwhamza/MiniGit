package minigit.cli.commands;

import minigit.api.CommitDetails;
import minigit.api.MiniGit;
import minigit.cli.Args;
import minigit.cli.CliContext;
import minigit.cli.Command;
import minigit.cli.UsageException;

import java.util.List;
import java.util.Set;

public final class ShowCommand implements Command {

    @Override
    public String name() {
        return "show";
    }

    @Override
    public String summary() {
        return "Show a commit and the changes it made";
    }

    @Override
    public String usage() {
        return "minigit show [<commit>] [--stat]";
    }

    @Override
    public String details() {
        return """
                <commit> defaults to HEAD. The diff is against the commit's parent.

                  --stat   only list the changed files""";
    }

    @Override
    public int run(CliContext ctx, List<String> rawArgs) {
        Args args = Args.parse(rawArgs, Set.of("--stat"), Set.of());
        if (args.positionals().size() > 1) {
            throw new UsageException("expected at most one commit");
        }
        String revision = args.positionals().isEmpty() ? "HEAD" : args.positionals().get(0);
        MiniGit repo = ctx.openRepository();
        CommitDetails details = repo.show(revision);

        LogCommand.printCommit(ctx, details.commit(), repo.refLabels());
        ctx.out().println();
        DiffCommand.print(ctx, details.diffs(), args.has("--stat"));
        return 0;
    }
}
