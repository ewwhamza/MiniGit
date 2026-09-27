package minigit.api;

import minigit.diff.FileDiff;

import java.util.List;

/**
 * A commit together with what it changed relative to its first parent, as shown
 * by {@code show} and the GUI's History tab.
 */
public record CommitDetails(CommitInfo commit, List<FileDiff> diffs) {
}
