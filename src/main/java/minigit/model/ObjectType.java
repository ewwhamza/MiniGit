package minigit.model;

import java.util.Optional;

/** The three kinds of object MiniGit stores. */
public enum ObjectType {
    BLOB("blob"),
    TREE("tree"),
    COMMIT("commit");

    private final String tag;

    ObjectType(String tag) {
        this.tag = tag;
    }

    /** The word written in an object's header, e.g. {@code blob}. */
    public String tag() {
        return tag;
    }

    public static Optional<ObjectType> fromTag(String tag) {
        for (ObjectType type : values()) {
            if (type.tag.equals(tag)) {
                return Optional.of(type);
            }
        }
        return Optional.empty();
    }
}
