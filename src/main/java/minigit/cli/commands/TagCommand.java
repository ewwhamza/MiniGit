package minigit.cli.commands;

import minigit.api.MiniGit;
import minigit.api.RefInfo;
import minigit.cli.Args;
import minigit.cli.CliContext;
import minigit.cli.Command;
import minigit.cli.UsageException;
import minigit.util.Hashing;

import java.util.List;
import java.util.Optional;
import java.util.Set;

public final class TagCommand implements Command {

    @Override
    public String name() {
        return "tag";
    }

    @Override
    public String summary() {
        return "List, create, or delete tags";
    }

    @Override
    public String usage() {
        return "minigit tag [<name> [<commit>] | -d <name>]";
    }

    @Override
    public String details() {
        return """
                  (no arguments)      list tags
                  <name> [<commit>]   tag <commit> (default: HEAD), e.g. minigit tag v1.0
                  -d <name>           delete a tag

                A tag names a commit permanently; use it anywhere a commit is expected.""";
    }

    @Override
    public int run(CliContext ctx, List<String> rawArgs) {
        Args args = Args.parse(rawArgs, Set.of(), Set.of("-d"));
        MiniGit repo = ctx.openRepository();
        List<String> positionals = args.positionals();

        if (args.option("-d").isPresent()) {
            if (!positionals.isEmpty()) {
                throw new UsageException("-d takes exactly one tag name");
            }
            repo.deleteTag(args.option("-d").get());
            ctx.out().println("Deleted tag " + args.option("-d").get());
            return 0;
        }
        if (positionals.isEmpty()) {
            repo.tags().forEach(t -> ctx.out().println(t.name()));
            return 0;
        }
        if (positionals.size() > 2) {
            throw new UsageException("too many arguments");
        }
        Optional<String> commit = positionals.size() == 2 ? Optional.of(positionals.get(1)) : Optional.empty();
        RefInfo tag = repo.createTag(positionals.get(0), commit);
        ctx.out().println("Tagged " + Hashing.shorten(tag.commit()) + " as " + tag.name());
        return 0;
    }
}
