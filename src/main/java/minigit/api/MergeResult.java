package minigit.api;

import java.util.List;
import java.util.Optional;

/**
 * Outcome of {@link MiniGit#merge}.
 *
 * @param commit       the new HEAD: the merge commit, or the fast-forward target
 * @param conflicts    files left with conflict markers, for {@link Kind#CONFLICTS}
 * @param filesChanged working-tree files written or deleted
 */
public record MergeResult(Kind kind, Optional<String> commit, List<String> conflicts, int filesChanged) {

    public enum Kind {
        /** The other commit is already part of this branch's history. */
        UP_TO_DATE,
        /** This branch had no new commits, so it simply moved forward (FR-MRG-1). */
        FAST_FORWARD,
        /** Both sides had new commits; they were combined into a merge commit (FR-MRG-5). */
        MERGED,
        /** Some files conflict; the merge waits for the user to resolve them (FR-MRG-4). */
        CONFLICTS
    }
}
