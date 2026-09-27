package minigit.api;

/**
 * One changed file.
 *
 * @param path repository-relative path using {@code /}
 */
public record FileChange(String path, ChangeType type) {
}
