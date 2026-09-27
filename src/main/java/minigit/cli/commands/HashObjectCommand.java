package minigit.cli.commands;

import minigit.api.MiniGit;
import minigit.cli.Args;
import minigit.cli.CliContext;
import minigit.cli.Command;
import minigit.cli.UsageException;

import java.nio.file.Path;
import java.util.List;
import java.util.Set;

public final class HashObjectCommand implements Command {

    @Override
    public String name() {
        return "hash-object";
    }

    @Override
    public String summary() {
        return "Compute a file's object hash, and optionally store it";
    }

    @Override
    public String usage() {
        return "minigit hash-object [-w] <file>...";
    }

    @Override
    public String details() {
        return """
                Prints the blob hash of each file.

                  -w    also write the file into the object store""";
    }

    @Override
    public int run(CliContext ctx, List<String> rawArgs) {
        Args args = Args.parse(rawArgs, Set.of("-w"), Set.of());
        if (args.positionals().isEmpty()) {
            throw new UsageException("no file given");
        }
        MiniGit repo = args.has("-w") ? ctx.openRepository() : null;
        for (String name : args.positionals()) {
            Path file = ctx.resolve(name);
            ctx.out().println(repo != null ? repo.storeFile(file) : MiniGit.hashFile(file));
        }
        return 0;
    }
}
