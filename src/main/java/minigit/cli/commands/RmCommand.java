package minigit.cli.commands;

import minigit.cli.Args;
import minigit.cli.CliContext;
import minigit.cli.Command;
import minigit.cli.UsageException;

import java.nio.file.Path;
import java.util.List;
import java.util.Set;

public final class RmCommand implements Command {

    @Override
    public String name() {
        return "rm";
    }

    @Override
    public String summary() {
        return "Stop tracking files, and delete them";
    }

    @Override
    public String usage() {
        return "minigit rm [--cached] [-r] [-f] <path>...";
    }

    @Override
    public String details() {
        return """
                  --cached   stop tracking but keep the files on disk
                  -r         allow removing whole folders
                  -f         delete even if the files have uncommitted changes""";
    }

    @Override
    public int run(CliContext ctx, List<String> rawArgs) {
        Args args = Args.parse(rawArgs, Set.of("--cached", "-r", "-f"), Set.of());
        if (args.positionals().isEmpty()) {
            throw new UsageException("no path given");
        }
        List<Path> paths = args.positionals().stream().map(ctx::resolve).toList();
        List<String> removed = ctx.openRepository().remove(paths, args.has("--cached"), args.has("-r"), args.has("-f"));
        removed.forEach(path -> ctx.out().println("rm '" + path + "'"));
        return 0;
    }
}
