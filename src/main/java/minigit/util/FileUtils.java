package minigit.util;

import minigit.exception.MiniGitException;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/** File helpers shared by the repository services. */
public final class FileUtils {

    private FileUtils() {
    }

    /**
     * Writes {@code data} to {@code target} so that readers only ever see the old
     * file or the complete new one, never a half-written file (NFR-SAFE-2).
     *
     * <p>The data goes to a temporary file in the same directory, which is then
     * renamed over the target in a single step.
     */
    public static void writeAtomically(Path target, byte[] data) {
        Path dir = target.toAbsolutePath().getParent();
        Path tmp = null;
        try {
            Files.createDirectories(dir);
            tmp = Files.createTempFile(dir, target.getFileName().toString() + ".", ".tmp");
            Files.write(tmp, data);
            try {
                Files.move(tmp, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            deleteQuietly(tmp);
            throw new MiniGitException("could not write " + target + ": " + e.getMessage(), e);
        }
    }

    public static byte[] readBytes(Path file) {
        try {
            return Files.readAllBytes(file);
        } catch (IOException e) {
            throw new MiniGitException("could not read " + file + ": " + e.getMessage(), e);
        }
    }

    private static void deleteQuietly(Path path) {
        if (path == null) {
            return;
        }
        try {
            Files.deleteIfExists(path);
        } catch (IOException ignored) {
            // Best effort: a leftover .tmp file is harmless.
        }
    }
}
