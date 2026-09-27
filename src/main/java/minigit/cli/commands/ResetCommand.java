package minigit.cli.commands;

import minigit.api.MiniGit;
import minigit.cli.Args;
import minigit.cli.CliContext;
import minigit.cli.Command;
import minigit.cli.UsageException;
import minigit.util.Hashing;

import java.util.List;
import java.util.Set;

public final class ResetCommand implements Command {

    @Override
    public String name() {
        return "reset";
    }

    @Override
    public String summary() {
        return "Move the current branch to another commit, keeping your files";
    }

    @Override
    public String usage() {
        return "minigit reset [<commit>]";
    }

    @Override
    public String details() {
        return """
                Points the current branch at <commit> (default: HEAD) and resets the
                staging area to match it. Files on disk are not changed, so anything
                that differs shows up as unstaged changes.

                Examples:
                  minigit reset            unstage everything
                  minigit reset HEAD~1     undo the last commit, keeping its changes""";
    }

    @Override
    public int run(CliContext ctx, List<String> rawArgs) {
        List<String> positionals = Args.parse(rawArgs, Set.of(), Set.of()).positionals();
        if (positionals.size() > 1) {
            throw new UsageException("expected at most one commit");
        }
        MiniGit repo = ctx.openRepository();
        String commit = repo.reset(positionals.isEmpty() ? "HEAD" : positionals.get(0));
        ctx.out().println("HEAD is now at " + Hashing.shorten(commit) + " " + repo.show(commit).commit().firstLine());
        return 0;
    }
}
