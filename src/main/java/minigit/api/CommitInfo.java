package minigit.api;

import minigit.model.Commit;
import minigit.model.Signature;

import java.util.List;

/** A commit as listed by {@link MiniGit#log}. */
public record CommitInfo(String hash, List<String> parents, Signature author, String message) {

    static CommitInfo of(String hash, Commit commit) {
        return new CommitInfo(hash, commit.parents(), commit.author(), commit.message());
    }

    public String firstLine() {
        int nl = message.indexOf('\n');
        return nl < 0 ? message : message.substring(0, nl);
    }
}
