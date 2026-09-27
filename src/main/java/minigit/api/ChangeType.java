package minigit.api;

/** How a file differs between two states (e.g. HEAD and the index). */
public enum ChangeType {
    ADDED('A', "new file"),
    MODIFIED('M', "modified"),
    DELETED('D', "deleted");

    private final char letter;
    private final String label;

    ChangeType(char letter, String label) {
        this.letter = letter;
        this.label = label;
    }

    /** Single-letter code shown in the GUI and short listings. */
    public char letter() {
        return letter;
    }

    /** Wording used by {@code status}, e.g. {@code new file}. */
    public String label() {
        return label;
    }
}
