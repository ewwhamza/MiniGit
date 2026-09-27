package minigit.api;

import java.util.List;

/**
 * Outcome of {@link MiniGit#add}.
 *
 * @param staged  paths whose new or changed content was staged
 * @param removed tracked paths staged for deletion because they no longer exist
 */
public record AddResult(List<String> staged, List<String> removed) {
}
