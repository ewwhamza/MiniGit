package minigit.model;

/**
 * One line of a tree: a file or subdirectory name and the object it points to.
 *
 * @param mode {@link #FILE_MODE} for a file, {@link #DIR_MODE} for a directory
 * @param type {@link ObjectType#BLOB} for a file, {@link ObjectType#TREE} for a directory
 * @param hash the blob or tree the name refers to
 * @param name a single path segment, never containing {@code /}
 */
public record TreeEntry(String mode, ObjectType type, String hash, String name) {

    public static final String FILE_MODE = "100644";
    public static final String DIR_MODE = "040000";

    public static TreeEntry file(String name, String blobHash) {
        return new TreeEntry(FILE_MODE, ObjectType.BLOB, blobHash, name);
    }

    public static TreeEntry directory(String name, String treeHash) {
        return new TreeEntry(DIR_MODE, ObjectType.TREE, treeHash, name);
    }

    public boolean isDirectory() {
        return type == ObjectType.TREE;
    }
}
