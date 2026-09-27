package minigit.core;

import minigit.exception.MiniGitException;
import minigit.util.FileUtils;
import minigit.util.Hashing;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * A merge that stopped on conflicts and is waiting for the user (FR-MRG-4):
 * <ul>
 *   <li>{@code MERGE_HEAD} — the commit being merged in, the future second parent;</li>
 *   <li>{@code MERGE_MSG} — the suggested commit message;</li>
 *   <li>{@code MERGE_CONFLICTS} — files still to resolve, one per line. A file is
 *       resolved when the user stages it.</li>
 * </ul>
 */
public final class MergeState {

    private final Path head;
    private final Path message;
    private final Path conflicts;

    public MergeState(Path gitDir) {
        this.head = gitDir.resolve("MERGE_HEAD");
        this.message = gitDir.resolve("MERGE_MSG");
        this.conflicts = gitDir.resolve("MERGE_CONFLICTS");
    }

    public boolean inProgress() {
        return Files.isRegularFile(head);
    }

    public Optional<String> mergeHead() {
        if (!inProgress()) {
            return Optional.empty();
        }
        String hash = read(head).strip();
        return Hashing.isFullHash(hash) ? Optional.of(hash) : Optional.empty();
    }

    public String message() {
        return Files.isRegularFile(message) ? read(message).strip() : "Merge";
    }

    public List<String> conflicts() {
        List<String> list = new ArrayList<>();
        if (Files.isRegularFile(conflicts)) {
            read(conflicts).lines().filter(l -> !l.isBlank()).forEach(list::add);
        }
        return list;
    }

    public void start(String theirs, String mergeMessage, List<String> conflictPaths) {
        write(message, mergeMessage + "\n");
        write(conflicts, conflictPaths.isEmpty() ? "" : String.join("\n", conflictPaths) + "\n");
        // Written last: its presence is what marks a merge as in progress.
        write(head, theirs + "\n");
    }

    /** Marks files as resolved, e.g. because the user staged them. */
    public void resolve(Collection<String> paths) {
        if (!inProgress() || paths.isEmpty()) {
            return;
        }
        List<String> remaining = conflicts();
        if (remaining.removeAll(paths)) {
            write(conflicts, remaining.isEmpty() ? "" : String.join("\n", remaining) + "\n");
        }
    }

    public void clear() {
        try {
            Files.deleteIfExists(head);
            Files.deleteIfExists(message);
            Files.deleteIfExists(conflicts);
        } catch (IOException e) {
            throw new MiniGitException("could not clear the merge state: " + e.getMessage(), e);
        }
    }

    private static String read(Path file) {
        return new String(FileUtils.readBytes(file), StandardCharsets.UTF_8);
    }

    private static void write(Path file, String text) {
        FileUtils.writeAtomically(file, text.getBytes(StandardCharsets.UTF_8));
    }
}
