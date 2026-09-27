package minigit.cli.commands;

import minigit.api.CommitInfo;
import minigit.api.MiniGit;
import minigit.cli.Args;
import minigit.cli.CliContext;
import minigit.cli.Command;
import minigit.cli.UsageException;
import minigit.util.Hashing;

import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public final class LogCommand implements Command {

    private static final DateTimeFormatter DATE =
            DateTimeFormatter.ofPattern("EEE MMM d HH:mm:ss yyyy Z", Locale.ENGLISH);

    @Override
    public String name() {
        return "log";
    }

    @Override
    public String summary() {
        return "Show the commit history";
    }

    @Override
    public String usage() {
        return "minigit log [--oneline] [-n <count>]";
    }

    @Override
    public String details() {
        return """
                  --oneline    one line per commit: short hash and message summary
                  -n <count>   show only the latest <count> commits""";
    }

    @Override
    public int run(CliContext ctx, List<String> rawArgs) {
        Args args = Args.parse(rawArgs, Set.of("--oneline"), Set.of("-n"));
        if (!args.positionals().isEmpty()) {
            throw new UsageException("unexpected argument '" + args.positionals().get(0) + "'");
        }
        int limit = args.option("-n").map(LogCommand::parseCount).orElse(0);

        MiniGit repo = ctx.openRepository();
        List<CommitInfo> commits = repo.log(limit);
        if (commits.isEmpty()) {
            ctx.err().println("fatal: there are no commits yet");
            return 1;
        }

        Map<String, List<String>> labels = repo.refLabels();
        for (int i = 0; i < commits.size(); i++) {
            CommitInfo c = commits.get(i);
            if (args.has("--oneline")) {
                ctx.out().println(Hashing.shorten(c.hash()) + decoration(labels, c.hash()) + " " + c.firstLine());
                continue;
            }
            if (i > 0) {
                ctx.out().println();
            }
            printCommit(ctx, c, labels);
        }
        return 0;
    }

    /** Prints a commit's header and indented message. Shared with {@code show}. */
    static void printCommit(CliContext ctx, CommitInfo c, Map<String, List<String>> labels) {
        ctx.out().println("commit " + c.hash() + decoration(labels, c.hash()));
        if (c.parents().size() > 1) {
            ctx.out().println("Merge:  " + String.join(" ", c.parents().stream().map(Hashing::shorten).toList()));
        }
        ctx.out().println("Author: " + c.author().name() + " <" + c.author().email() + ">");
        ctx.out().println("Date:   " + DATE.format(c.author().dateTime()));
        ctx.out().println();
        c.message().lines().forEach(line -> ctx.out().println("    " + line));
    }

    /** e.g. {@code " (HEAD -> main, tag: v1.0)"}, or empty. */
    private static String decoration(Map<String, List<String>> labels, String hash) {
        List<String> names = labels.get(hash);
        return names == null || names.isEmpty() ? "" : " (" + String.join(", ", names) + ")";
    }

    private static int parseCount(String value) {
        try {
            int n = Integer.parseInt(value);
            if (n > 0) {
                return n;
            }
        } catch (NumberFormatException ignored) {
            // Reported below.
        }
        throw new UsageException("-n needs a positive number, not '" + value + "'");
    }
}
