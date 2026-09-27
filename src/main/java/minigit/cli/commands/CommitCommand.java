package minigit.cli.commands;

import minigit.api.ChangeType;
import minigit.api.CommitResult;
import minigit.api.MiniGit;
import minigit.cli.Args;
import minigit.cli.CliContext;
import minigit.cli.Command;
import minigit.cli.UsageException;
import minigit.exception.NothingToCommitException;
import minigit.util.Hashing;

import java.util.List;
import java.util.Set;

public final class CommitCommand implements Command {

    @Override
    public String name() {
        return "commit";
    }

    @Override
    public String summary() {
        return "Record the staged changes as a new commit";
    }

    @Override
    public String usage() {
        return "minigit commit -m <message>";
    }

    @Override
    public String details() {
        return """
                  -m <message>   the commit message (required)

                Set your name first with: minigit config user.name "Your Name"
                """.stripTrailing();
    }

    @Override
    public int run(CliContext ctx, List<String> rawArgs) {
        Args args = Args.parse(rawArgs, Set.of(), Set.of("-m"));
        if (!args.positionals().isEmpty()) {
            throw new UsageException("unexpected argument '" + args.positionals().get(0)
                    + "'; put the message in quotes after -m");
        }
        MiniGit repo = ctx.openRepository();
        // Finishing a merge may reuse the suggested message; anything else needs -m.
        if (args.option("-m").isEmpty() && repo.status().merge().isEmpty()) {
            throw new UsageException("a message is required: -m \"...\"");
        }

        CommitResult result;
        try {
            result = repo.commit(args.option("-m").orElse(null));
        } catch (NothingToCommitException e) {
            ctx.out().println(e.getMessage());
            return 1;
        }

        String where = result.branch().orElse("detached HEAD") + (result.root() ? " (root-commit)" : "");
        ctx.out().println("[" + where + " " + Hashing.shorten(result.hash()) + "] " + result.summary());
        long added = result.changes().stream().filter(c -> c.type() == ChangeType.ADDED).count();
        long deleted = result.changes().stream().filter(c -> c.type() == ChangeType.DELETED).count();
        int total = result.changes().size();
        ctx.out().println(" " + total + (total == 1 ? " file" : " files") + " changed"
                + (added > 0 ? ", " + added + " added" : "")
                + (deleted > 0 ? ", " + deleted + " deleted" : ""));
        return 0;
    }
}
