package minigit.api;

import java.util.List;
import java.util.Optional;

/**
 * Outcome of {@link MiniGit#commit}.
 *
 * @param hash    the new commit's hash
 * @param branch  the branch that moved, or empty if HEAD is detached
 * @param root    whether this is the repository's first commit
 * @param summary the first line of the message
 * @param changes the files this commit changed relative to its parent
 */
public record CommitResult(String hash, Optional<String> branch, boolean root, String summary,
                           List<FileChange> changes) {
}
