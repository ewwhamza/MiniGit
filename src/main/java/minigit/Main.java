package minigit;

import minigit.cli.CliContext;
import minigit.cli.Command;
import minigit.cli.CommandRegistry;
import minigit.cli.UsageException;
import minigit.cli.commands.HelpCommand;
import minigit.exception.MiniGitException;
import minigit.gui.MiniGitApp;

import java.awt.GraphicsEnvironment;
import java.io.PrintStream;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Entry point. With arguments, runs a CLI command; with none, opens the GUI (FR-GUI-1).
 */
public final class Main {

    public static final String VERSION = "0.1.0";

    private Main() {
    }

    public static void main(String[] args) {
        Path cwd = Path.of("").toAbsolutePath();
        if (args.length == 0) {
            if (!GraphicsEnvironment.isHeadless()) {
                // The GUI keeps the JVM alive until its window closes.
                MiniGitApp.launch(cwd);
                return;
            }
            System.out.println("No display is available for the GUI. Showing command-line help instead.");
            System.out.println();
        }
        System.exit(run(args, cwd, System.out, System.err));
    }

    /**
     * Runs one CLI invocation and returns its exit status. Never throws and never
     * prints a stack trace unless {@code --debug} is given (FR-HELP-2, NFR-QA-4).
     */
    public static int run(String[] rawArgs, Path cwd, PrintStream out, PrintStream err) {
        List<String> args = new ArrayList<>(List.of(rawArgs));
        boolean debug = args.remove("--debug");
        CommandRegistry registry = CommandRegistry.createDefault();
        CliContext ctx = new CliContext(cwd, out, err);

        if (args.isEmpty() || args.get(0).equals("-h") || args.get(0).equals("--help")) {
            new HelpCommand(registry).printOverview(out);
            return 0;
        }
        if (args.get(0).equals("--version")) {
            out.println("minigit version " + VERSION);
            return 0;
        }

        String name = args.get(0);
        Optional<Command> command = registry.find(name);
        if (command.isEmpty()) {
            err.println("minigit: '" + name + "' is not a minigit command. See 'minigit help'.");
            return 1;
        }

        try {
            return command.get().run(ctx, args.subList(1, args.size()));
        } catch (UsageException e) {
            err.println("error: " + e.getMessage());
            err.println("usage: " + command.get().usage());
            return 1;
        } catch (MiniGitException e) {
            err.println("fatal: " + e.getMessage());
            if (debug) {
                e.printStackTrace(err);
            }
            return 1;
        } catch (RuntimeException e) {
            err.println("fatal: unexpected error: " + e);
            if (debug) {
                e.printStackTrace(err);
            } else {
                err.println("Run again with --debug for details.");
            }
            return 1;
        }
    }
}
