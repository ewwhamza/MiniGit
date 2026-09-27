package minigit.cli.commands;

import minigit.api.MiniGit;
import minigit.cli.Args;
import minigit.cli.CliContext;
import minigit.cli.Command;
import minigit.cli.UsageException;

import java.util.List;
import java.util.Optional;
import java.util.Set;

public final class ConfigCommand implements Command {

    @Override
    public String name() {
        return "config";
    }

    @Override
    public String summary() {
        return "Get and set repository options such as user.name";
    }

    @Override
    public String usage() {
        return "minigit config (<key> [<value>] | --list | --unset <key>)";
    }

    @Override
    public String details() {
        return """
                  <key>             print the value of <key>
                  <key> <value>     set <key> to <value>
                  -l, --list        print every setting
                  --unset <key>     remove <key>

                Example:
                  minigit config user.name "Mohit"
                  minigit config user.email "mohit@example.com"
                """.stripTrailing();
    }

    @Override
    public int run(CliContext ctx, List<String> rawArgs) {
        Args args = Args.parse(rawArgs, Set.of("-l", "--list"), Set.of("--unset"));
        List<String> positionals = args.positionals();
        boolean list = args.has("-l") || args.has("--list");
        Optional<String> unset = args.option("--unset");
        MiniGit repo = ctx.openRepository();

        if (list) {
            if (!positionals.isEmpty() || unset.isPresent()) {
                throw new UsageException("--list takes no other arguments");
            }
            repo.listConfig().forEach((k, v) -> ctx.out().println(k + "=" + v));
            return 0;
        }
        if (unset.isPresent()) {
            if (!positionals.isEmpty()) {
                throw new UsageException("--unset takes only a key");
            }
            if (!repo.unsetConfig(unset.get())) {
                ctx.err().println("error: '" + unset.get() + "' is not set");
                return 1;
            }
            return 0;
        }
        switch (positionals.size()) {
            case 1 -> {
                // Like Git: an unset key prints nothing and exits with status 1.
                Optional<String> value = repo.getConfig(positionals.get(0));
                value.ifPresent(ctx.out()::println);
                return value.isPresent() ? 0 : 1;
            }
            case 2 -> {
                repo.setConfig(positionals.get(0), positionals.get(1));
                return 0;
            }
            default -> throw new UsageException(positionals.isEmpty()
                    ? "no key given"
                    : "too many arguments; put values containing spaces in quotes");
        }
    }
}
