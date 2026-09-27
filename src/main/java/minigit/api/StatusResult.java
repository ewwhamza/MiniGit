package minigit.api;

import java.util.List;
import java.util.Optional;

/**
 * Snapshot of the repository's state, as shown by {@code status}.
 *
 * @param branch     the current branch, or empty when HEAD is detached
 * @param headCommit the commit HEAD points at, or empty before the first commit
 * @param staged     differences between HEAD and the index: what the next commit will contain
 * @param unstaged   differences between the index and the working tree
 * @param untracked  files that are neither staged nor ignored
 * @param merge      the merge waiting to be committed, if one stopped on conflicts
 */
public record StatusResult(
        Optional<String> branch,
        Optional<String> headCommit,
        List<FileChange> staged,
        List<FileChange> unstaged,
        List<String> untracked,
        Optional<MergeInfo> merge) {

    /**
     * @param theirs    the commit being merged in
     * @param message   the suggested commit message
     * @param conflicts files still containing unresolved conflicts
     */
    public record MergeInfo(String theirs, String message, List<String> conflicts) {
    }

    /** Whether there is nothing to commit and nothing unstaged or untracked. */
    public boolean isClean() {
        return staged.isEmpty() && unstaged.isEmpty() && untracked.isEmpty() && merge.isEmpty();
    }

    public boolean isConflicted(String path) {
        return merge.map(m -> m.conflicts().contains(path)).orElse(false);
    }
}
