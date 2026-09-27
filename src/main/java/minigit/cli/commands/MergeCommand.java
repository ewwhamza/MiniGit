package minigit.cli.commands;

import minigit.api.MergeResult;
import minigit.cli.Args;
import minigit.cli.CliContext;
import minigit.cli.Command;
import minigit.cli.UsageException;
import minigit.util.Hashing;

import java.util.List;
import java.util.Set;

public final class MergeCommand implements Command {

    @Override
    public String name() {
        return "merge";
    }

    @Override
    public String summary() {
        return "Combine another branch into the current one";
    }

    @Override
    public String usage() {
        return "minigit merge (<branch> | --abort)";
    }

    @Override
    public String details() {
        return """
                  <branch>   merge <branch> (or any commit) into the current branch
                  --abort    give up a merge that stopped on conflicts

                When both branches changed the same lines, the file is left with
                <<<<<<< ======= >>>>>>> markers. Edit it, 'minigit add' it, then
                'minigit commit' to finish the merge.""";
    }

    @Override
    public int run(CliContext ctx, List<String> rawArgs) {
        Args args = Args.parse(rawArgs, Set.of("--abort"), Set.of());
        if (args.has("--abort")) {
            if (!args.positionals().isEmpty()) {
                throw new UsageException("--abort takes no other arguments");
            }
            ctx.openRepository().abortMerge();
            ctx.out().println("Merge aborted; your files are back as they were.");
            return 0;
        }
        if (args.positionals().size() != 1) {
            throw new UsageException("expected one branch or commit to merge");
        }

        MergeResult result = ctx.openRepository().merge(args.positionals().get(0));
        switch (result.kind()) {
            case UP_TO_DATE -> ctx.out().println("Already up to date.");
            case FAST_FORWARD -> ctx.out().println("Fast-forward to " + Hashing.shorten(result.commit().orElseThrow())
                    + " (" + result.filesChanged() + (result.filesChanged() == 1 ? " file" : " files") + " updated)");
            case MERGED -> ctx.out().println("Merge made: " + Hashing.shorten(result.commit().orElseThrow())
                    + " (" + result.filesChanged() + (result.filesChanged() == 1 ? " file" : " files") + " merged)");
            case CONFLICTS -> {
                result.conflicts().forEach(p -> ctx.out().println("CONFLICT: merge conflict in " + p));
                ctx.out().println("Automatic merge failed. Fix the conflicts, 'minigit add' the files,");
                ctx.out().println("then run 'minigit commit' (or 'minigit merge --abort' to give up).");
                return 1;
            }
        }
        return 0;
    }
}
