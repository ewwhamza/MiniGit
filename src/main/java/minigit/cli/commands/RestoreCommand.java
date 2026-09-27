package minigit.cli.commands;

import minigit.api.MiniGit;
import minigit.cli.Args;
import minigit.cli.CliContext;
import minigit.cli.Command;
import minigit.cli.UsageException;

import java.nio.file.Path;
import java.util.List;
import java.util.Set;

public final class RestoreCommand implements Command {

    @Override
    public String name() {
        return "restore";
    }

    @Override
    public String summary() {
        return "Discard changes to files, or unstage them";
    }

    @Override
    public String usage() {
        return "minigit restore [--staged] <path>...";
    }

    @Override
    public String details() {
        return """
                  <path>...            discard working-tree changes, going back to the staged version
                                       (this throws away your edits to those files)
                  --staged <path>...   unstage: keep your edits, but take them out of the next commit""";
    }

    @Override
    public int run(CliContext ctx, List<String> rawArgs) {
        Args args = Args.parse(rawArgs, Set.of("--staged"), Set.of());
        if (args.positionals().isEmpty()) {
            throw new UsageException("no path given");
        }
        List<Path> paths = args.positionals().stream().map(ctx::resolve).toList();
        MiniGit repo = ctx.openRepository();
        if (args.has("--staged")) {
            repo.unstage(paths).forEach(p -> ctx.out().println("Unstaged " + p));
        } else {
            repo.restore(paths).forEach(p -> ctx.out().println("Restored " + p));
        }
        return 0;
    }
}
