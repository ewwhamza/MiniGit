package minigit.cli;

import java.util.List;

/**
 * One user command, such as {@code init} or {@code cat-file} (Command pattern).
 *
 * <p>Implementations parse their arguments, call the {@link minigit.api.MiniGit}
 * facade, and format the result as text. They contain no version-control logic.
 */
public interface Command {

    /** The word typed after {@code minigit}, e.g. {@code cat-file}. */
    String name();

    /** One-line description shown in {@code minigit help}. */
    String summary();

    /** Synopsis, e.g. {@code minigit hash-object [-w] <file>...}. */
    String usage();

    /** Extra lines shown by {@code minigit help <command>}: options, examples. */
    default String details() {
        return "";
    }

    /**
     * Runs the command.
     *
     * @return the process exit status: 0 on success
     * @throws UsageException                     if the arguments are wrong
     * @throws minigit.exception.MiniGitException if the operation fails
     */
    int run(CliContext ctx, List<String> args);
}
