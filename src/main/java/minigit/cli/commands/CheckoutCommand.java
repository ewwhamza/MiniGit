package minigit.cli.commands;

import minigit.api.CheckoutResult;
import minigit.api.MiniGit;
import minigit.cli.Args;
import minigit.cli.CliContext;
import minigit.cli.Command;
import minigit.cli.UsageException;
import minigit.util.Hashing;

import java.util.List;
import java.util.Optional;
import java.util.Set;

public final class CheckoutCommand implements Command {

    @Override
    public String name() {
        return "checkout";
    }

    @Override
    public String summary() {
        return "Switch to a branch or an older commit";
    }

    @Override
    public String usage() {
        return "minigit checkout (<branch> | <commit> | -b <new-branch>)";
    }

    @Override
    public String details() {
        return """
                  <branch>          switch to the branch
                  <commit>          look at an older commit ("detached HEAD")
                  -b <new-branch>   create a branch here and switch to it

                Refuses to run if it would overwrite uncommitted changes.""";
    }

    @Override
    public int run(CliContext ctx, List<String> rawArgs) {
        Args args = Args.parse(rawArgs, Set.of(), Set.of("-b"));
        Optional<String> newBranch = args.option("-b");
        MiniGit repo = ctx.openRepository();

        if (newBranch.isPresent()) {
            if (!args.positionals().isEmpty()) {
                throw new UsageException("-b takes only the new branch's name");
            }
            repo.checkoutNewBranch(newBranch.get());
            ctx.out().println("Switched to a new branch '" + newBranch.get() + "'");
            return 0;
        }
        if (args.positionals().size() != 1) {
            throw new UsageException("expected one branch or commit");
        }

        String target = args.positionals().get(0);
        CheckoutResult result = repo.checkout(target);
        if (result.alreadyThere()) {
            ctx.out().println("Already on '" + result.branch().orElse(target) + "'");
        } else if (result.detached()) {
            String hash = Hashing.shorten(result.commit().orElseThrow());
            ctx.out().println("Note: you are now looking at commit " + hash + " (detached HEAD).");
            ctx.out().println("You can look around, but new commits will not belong to any branch.");
            ctx.out().println("To keep work you do here, run: minigit checkout -b <new-branch>");
            ctx.out().println("HEAD is now at " + hash + " " + repo.show("HEAD").commit().firstLine());
        } else {
            ctx.out().println("Switched to branch '" + result.branch().orElseThrow() + "'"
                    + " (" + result.filesChanged() + (result.filesChanged() == 1 ? " file" : " files") + " updated)");
        }
        return 0;
    }
}
