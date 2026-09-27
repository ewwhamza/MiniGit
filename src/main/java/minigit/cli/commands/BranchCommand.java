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

public final class BranchCommand implements Command {

    @Override
    public String name() {
        return "branch";
    }

    @Override
    public String summary() {
        return "List, create, or delete branches";
    }

    @Override
    public String usage() {
        return "minigit branch [<name> [<start>] | -d <name> | -D <name>] [-v]";
    }

    @Override
    public String details() {
        return """
                  (no arguments)     list branches; the current one is marked with *
                  <name> [<start>]   create a branch at <start> (default: HEAD)
                  -d <name>          delete a branch whose commits are in the current branch
                  -D <name>          delete a branch regardless
                  -v                 also show each branch's latest commit""";
    }

    @Override
    public int run(CliContext ctx, List<String> rawArgs) {
        Args args = Args.parse(rawArgs, Set.of("-v"), Set.of("-d", "-D"));
        MiniGit repo = ctx.openRepository();
        List<String> positionals = args.positionals();

        Optional<String> delete = args.option("-d").or(() -> args.option("-D"));
        if (delete.isPresent()) {
            if (!positionals.isEmpty()) {
                throw new UsageException("-d takes exactly one branch name");
            }
            String tip = repo.branches().stream().filter(b -> b.name().equals(delete.get()))
                    .map(RefInfo::commit).findFirst().orElse("");
            repo.deleteBranch(delete.get(), args.option("-D").isPresent());
            ctx.out().println("Deleted branch " + delete.get() + " (was " + Hashing.shorten(tip) + ")");
            return 0;
        }

        if (positionals.isEmpty()) {
            List<RefInfo> branches = repo.branches();
            if (branches.isEmpty()) {
                ctx.out().println("(no branches yet; the first commit creates '"
                        + repo.currentBranch().orElse("main") + "')");
            }
            int width = branches.stream().mapToInt(b -> b.name().length()).max().orElse(0);
            for (RefInfo b : branches) {
                String marker = b.current() ? "* " : "  ";
                if (args.has("-v")) {
                    ctx.out().printf("%s%-" + width + "s  %s %s%n", marker, b.name(), Hashing.shorten(b.commit()),
                            repo.show(b.commit()).commit().firstLine());
                } else {
                    ctx.out().println(marker + b.name());
                }
            }
            return 0;
        }
        if (positionals.size() > 2) {
            throw new UsageException("too many arguments");
        }
        Optional<String> start = positionals.size() == 2 ? Optional.of(positionals.get(1)) : Optional.empty();
        RefInfo created = repo.createBranch(positionals.get(0), start);
        ctx.out().println("Created branch " + created.name() + " at " + Hashing.shorten(created.commit()));
        return 0;
    }
}
