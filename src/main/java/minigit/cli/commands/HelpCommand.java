package minigit.cli.commands;

import minigit.cli.Args;
import minigit.cli.CliContext;
import minigit.cli.Command;
import minigit.cli.CommandRegistry;
import minigit.cli.UsageException;

import java.io.PrintStream;
import java.util.List;
import java.util.Set;

public final class HelpCommand implements Command {

    private final CommandRegistry registry;

    public HelpCommand(CommandRegistry registry) {
        this.registry = registry;
    }

    @Override
    public String name() {
        return "help";
    }

    @Override
    public String summary() {
        return "Show help for MiniGit or for one command";
    }

    @Override
    public String usage() {
        return "minigit help [<command>]";
    }

    @Override
    public int run(CliContext ctx, List<String> rawArgs) {
        List<String> positionals = Args.parse(rawArgs, Set.of(), Set.of()).positionals();
        if (positionals.isEmpty()) {
            printOverview(ctx.out());
            return 0;
        }
        if (positionals.size() > 1) {
            throw new UsageException("too many arguments");
        }
        Command command = registry.find(positionals.get(0))
                .orElseThrow(() -> new UsageException("'" + positionals.get(0) + "' is not a minigit command"));
        ctx.out().println(command.summary());
        ctx.out().println();
        ctx.out().println("usage: " + command.usage());
        if (!command.details().isEmpty()) {
            ctx.out().println();
            ctx.out().println(command.details());
        }
        return 0;
    }

    /** The command list, also printed by {@code Main} when no command is given. */
    public void printOverview(PrintStream out) {
        out.println("usage: minigit <command> [<args>]");
        out.println();
        out.println("Commands:");
        int width = registry.all().stream().mapToInt(c -> c.name().length()).max().orElse(0);
        for (Command c : registry.all()) {
            out.printf("  %-" + width + "s   %s%n", c.name(), c.summary());
        }
        out.println();
        out.println("Run 'minigit help <command>' for details on one command.");
    }
}
