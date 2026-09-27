package minigit.cli;

import minigit.api.MiniGit;

import java.io.PrintStream;
import java.nio.file.Path;

/**
 * Where a command runs and where it writes. Passing these in, rather than using
 * {@code System.out} and the process working directory, lets tests run commands
 * in a temporary folder and capture their output.
 */
public record CliContext(Path cwd, PrintStream out, PrintStream err) {

    /** Opens the repository containing the working directory. */
    public MiniGit openRepository() {
        return MiniGit.open(cwd);
    }

    /** Resolves a path typed by the user against the working directory. */
    public Path resolve(String path) {
        return cwd.resolve(path).normalize();
    }
}
