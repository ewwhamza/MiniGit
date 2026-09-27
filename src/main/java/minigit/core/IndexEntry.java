package minigit.core;

/**
 * One staged file.
 *
 * @param path         repository-relative path using {@code /}
 * @param hash         the blob holding the staged content
 * @param size         file size in bytes when staged
 * @param lastModified file modification time (epoch millis) when staged
 */
public record IndexEntry(String path, String hash, long size, long lastModified) {
}
