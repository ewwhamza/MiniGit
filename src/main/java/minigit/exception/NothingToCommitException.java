package minigit.exception;

/** Thrown by a commit when the index is identical to HEAD (FR-CMT-5). */
public class NothingToCommitException extends MiniGitException {

    public NothingToCommitException() {
        super("nothing to commit (use \"minigit add\" to stage changes)");
    }
}
