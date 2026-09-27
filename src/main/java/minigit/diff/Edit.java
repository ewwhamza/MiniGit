package minigit.diff;

/**
 * One line of a diff.
 *
 * @param type     whether the line is kept, removed, or added
 * @param text     the line including its line terminator, if it had one
 * @param oldIndex 0-based line number in the old text, or -1 for an insertion
 * @param newIndex 0-based line number in the new text, or -1 for a deletion
 */
public record Edit(Type type, String text, int oldIndex, int newIndex) {

    public enum Type {
        EQUAL(' '),
        DELETE('-'),
        INSERT('+');

        private final char symbol;

        Type(char symbol) {
            this.symbol = symbol;
        }

        /** The prefix character used in unified diff output. */
        public char symbol() {
            return symbol;
        }
    }

    /** The line without its terminator, for display. */
    public String content() {
        if (text.endsWith("\r\n")) {
            return text.substring(0, text.length() - 2);
        }
        return text.endsWith("\n") ? text.substring(0, text.length() - 1) : text;
    }

    /** Whether this is the last line of a file that does not end with a newline. */
    public boolean missingNewline() {
        return !text.endsWith("\n");
    }
}
