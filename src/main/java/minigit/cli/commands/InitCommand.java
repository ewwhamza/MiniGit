package minigit.cli.commands;

import minigit.api.InitResult;
import minigit.api.MiniGit;
import minigit.cli.Args;
import minigit.cli.CliContext;
import minigit.cli.Command;
import minigit.cli.UsageException;

import java.nio.file.Path;
import java.util.List;
import java.util.Set;

public final class InitCommand implements Command {

    @Override
    public String name() {
        return "init";
    }

    @Override
    public String summary() {
        return "Create an empty MiniGit repository";
    }

    @Override
    public String usage() {
        return "minigit init [<directory>]";
    }

    @Override
    public String details() {
        return """
                Creates a .minigit folder in <directory>, or in the current folder if
                none is given. Running it where a repository already exists changes nothing.""";
    }

    @Override
    public int run(CliContext ctx, List<String> rawArgs) {
        List<String> positionals = Args.parse(rawArgs, Set.of(), Set.of()).positionals();
        if (positionals.size() > 1) {
            throw new UsageException("too many arguments");
        }
        Path dir = positionals.isEmpty() ? ctx.cwd() : ctx.resolve(positionals.get(0));
        InitResult result = MiniGit.init(dir);
        if (result.created()) {
            ctx.out().println("Initialized empty MiniGit repository in " + result.gitDir());
        } else {
            ctx.out().println("MiniGit repository already exists in " + result.gitDir() + " (nothing changed)");
        }
        return 0;
    }
}
