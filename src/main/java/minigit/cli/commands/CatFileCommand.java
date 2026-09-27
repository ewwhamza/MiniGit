package minigit.cli.commands;

import minigit.cli.Args;
import minigit.cli.CliContext;
import minigit.cli.Command;
import minigit.cli.UsageException;
import minigit.model.RawObject;

import java.util.List;
import java.util.Set;

public final class CatFileCommand implements Command {

    @Override
    public String name() {
        return "cat-file";
    }

    @Override
    public String summary() {
        return "Show the type, size, or content of a stored object";
    }

    @Override
    public String usage() {
        return "minigit cat-file [-t | -s | -p] <object>";
    }

    @Override
    public String details() {
        return """
                <object> is a full hash or a unique prefix of at least 4 characters.
                With no option, prints the type and size followed by the content.

                  -t    print only the object's type
                  -s    print only the object's size in bytes
                  -p    print only the object's content""";
    }

    @Override
    public int run(CliContext ctx, List<String> rawArgs) {
        Args args = Args.parse(rawArgs, Set.of("-t", "-s", "-p"), Set.of());
        long modes = Set.of("-t", "-s", "-p").stream().filter(args::has).count();
        if (modes > 1) {
            throw new UsageException("-t, -s, and -p cannot be combined");
        }
        if (args.positionals().size() != 1) {
            throw new UsageException("expected exactly one object");
        }

        RawObject object = ctx.openRepository().readObject(args.positionals().get(0));
        if (args.has("-t")) {
            ctx.out().println(object.type().tag());
        } else if (args.has("-s")) {
            ctx.out().println(object.size());
        } else {
            if (!args.has("-p")) {
                ctx.out().println("object " + object.hash());
                ctx.out().println("type   " + object.type().tag());
                ctx.out().println("size   " + object.size() + " bytes");
                ctx.out().println();
            }
            // Raw bytes, so file content is reproduced exactly, including binary files.
            ctx.out().write(object.content(), 0, object.size());
            ctx.out().flush();
        }
        return 0;
    }
}
