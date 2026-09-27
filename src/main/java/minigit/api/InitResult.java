package minigit.api;

import java.nio.file.Path;

/**
 * Outcome of {@link MiniGit#init}.
 *
 * @param gitDir  absolute path of the {@code .minigit} directory
 * @param created {@code false} if a repository already existed and nothing was changed
 */
public record InitResult(Path gitDir, boolean created) {
}
