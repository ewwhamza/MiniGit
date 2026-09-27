package minigit.cli.commands;

import minigit.cli.Args;
import minigit.cli.CliContext;
import minigit.cli.Command;
import minigit.cli.UsageException;

import java.nio.file.Path;
import java.util.List;
import java.util.Set;

public final class AddCommand implements Command {

    @Override
    public String name() {
        return "add";
    }

    @Override
    public String summary() {
        return "Stage files for the next commit";
    }

    @Override
    public String usage() {
        return "minigit add <path>...";
    }

    @Override
    public String details() {
        return """
                Each <path> can be a file or a folder; 'minigit add .' stages everything.
                Tracked files that were deleted are staged as deletions. Files matched
                by .minigitignore are skipped inside folders.""";
    }

    @Override
    public int run(CliContext ctx, List<String> rawArgs) {
        List<String> positionals = Args.parse(rawArgs, Set.of(), Set.of()).positionals();
        if (positionals.isEmpty()) {
            throw new UsageException("nothing specified; did you mean 'minigit add .'?");
        }
        List<Path> paths = positionals.stream().map(ctx::resolve).toList();
        // Silent on success, like Git; 'minigit status' shows the result.
        ctx.openRepository().add(paths);
        return 0;
    }
}
