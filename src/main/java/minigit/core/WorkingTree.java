package minigit.core;

import minigit.exception.MiniGitException;
import minigit.util.IgnoreRules;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * The user's project files: everything in the repository folder except
 * {@code .minigit}. Converts between disk paths and repository-relative paths,
 * which always use {@code /} (FR-IDX-4).
 */
public final class WorkingTree {

    /** A file's size and modification time, used to skip re-hashing unchanged files. */
    public record FileStat(long size, long lastModified) {
    }

    private final Path root;

    public WorkingTree(Path root) {
        this.root = root;
    }

    public Path root() {
        return root;
    }

    /**
     * Converts a disk path to a repository-relative one ({@code ""} for the root).
     *
     * @throws MiniGitException if the path is outside the repository or inside {@code .minigit}
     */
    public String relativize(Path path) {
        Path abs = path.toAbsolutePath().normalize();
        if (!abs.startsWith(root)) {
            throw new MiniGitException("'" + path + "' is outside the repository at " + root);
        }
        List<String> parts = new ArrayList<>();
        for (Path part : root.relativize(abs)) {
            parts.add(part.toString());
        }
        if (parts.size() == 1 && parts.get(0).isEmpty()) {
            return "";
        }
        if (!parts.isEmpty() && parts.get(0).equals(Repository.DIR_NAME)) {
            throw new MiniGitException("'" + path + "' is inside the " + Repository.DIR_NAME + " folder");
        }
        String rel = String.join("/", parts);
        if (rel.contains("\n") || rel.contains("\r")) {
            throw new MiniGitException("file names containing line breaks are not supported: " + path);
        }
        return rel;
    }

    public Path toPath(String relative) {
        return relative.isEmpty() ? root : root.resolve(relative);
    }

    /** Size and modification time, or empty if the path is not a regular file. */
    public Optional<FileStat> stat(String relative) {
        Path file = toPath(relative);
        try {
            BasicFileAttributes attrs = Files.readAttributes(file, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
            if (!attrs.isRegularFile()) {
                return Optional.empty();
            }
            return Optional.of(new FileStat(attrs.size(), attrs.lastModifiedTime().toMillis()));
        } catch (java.nio.file.NoSuchFileException e) {
            return Optional.empty();
        } catch (IOException e) {
            throw new MiniGitException("could not read " + file + ": " + e.getMessage(), e);
        }
    }

    /**
     * All regular files under {@code dir} ({@code ""} for everything), as sorted
     * repository-relative paths. Skips {@code .minigit} folders and anything
     * matched by {@code ignore}; ignored directories are not even entered.
     */
    public List<String> listFiles(String dir, IgnoreRules ignore) {
        List<String> files = new ArrayList<>();
        Path start = toPath(dir);
        if (!Files.isDirectory(start)) {
            return files;
        }
        try {
            Files.walkFileTree(start, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult preVisitDirectory(Path d, BasicFileAttributes attrs) {
                    if (d.getFileName() != null && d.getFileName().toString().equals(Repository.DIR_NAME)) {
                        return FileVisitResult.SKIP_SUBTREE;
                    }
                    String rel = relativize(d);
                    return !rel.isEmpty() && ignore.isIgnored(rel, true)
                            ? FileVisitResult.SKIP_SUBTREE
                            : FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFile(Path f, BasicFileAttributes attrs) {
                    if (attrs.isRegularFile()) {
                        String rel = relativize(f);
                        if (!ignore.isIgnored(rel, false)) {
                            files.add(rel);
                        }
                    }
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException e) {
            throw new MiniGitException("could not scan " + start + ": " + e.getMessage(), e);
        }
        Collections.sort(files);
        return files;
    }

    /** Deletes a file, then any parent folders it leaves empty (but never the root). */
    public void delete(String relative) {
        try {
            Files.deleteIfExists(toPath(relative));
            for (Path dir = toPath(relative).getParent(); dir != null && !dir.equals(root); dir = dir.getParent()) {
                try (var children = Files.list(dir)) {
                    if (children.findAny().isPresent()) {
                        break;
                    }
                }
                Files.delete(dir);
            }
        } catch (IOException e) {
            throw new MiniGitException("could not delete " + relative + ": " + e.getMessage(), e);
        }
    }
}
