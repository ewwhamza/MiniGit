package minigit.model;

import minigit.util.FileUtils;

import java.nio.file.Path;

/** The raw content of one file. A blob knows nothing about the file's name. */
public final class Blob extends GitObject {

    private final byte[] data;

    public Blob(byte[] data) {
        this.data = data.clone();
    }

    public static Blob fromFile(Path file) {
        return new Blob(FileUtils.readBytes(file));
    }

    @Override
    public ObjectType type() {
        return ObjectType.BLOB;
    }

    @Override
    public byte[] content() {
        return data.clone();
    }
}
