package minigit.api;

import java.util.Optional;

/**
 * Outcome of {@link MiniGit#checkout}.
 *
 * @param branch       the branch now checked out, or empty for a detached HEAD
 * @param commit       the commit now checked out, or empty on a branch with no commits yet
 * @param filesChanged how many working-tree files were written or deleted
 * @param alreadyThere whether the target was already checked out, so nothing happened
 */
public record CheckoutResult(Optional<String> branch, Optional<String> commit, int filesChanged,
                             boolean alreadyThere) {

    public boolean detached() {
        return branch.isEmpty();
    }
}
