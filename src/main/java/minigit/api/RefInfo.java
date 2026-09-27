package minigit.api;

/**
 * A branch or tag.
 *
 * @param commit  the commit it points at
 * @param current for a branch, whether it is checked out; always {@code false} for a tag
 */
public record RefInfo(String name, String commit, boolean current) {
}
