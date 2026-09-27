package minigit.util;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.zip.DeflaterOutputStream;
import java.util.zip.InflaterInputStream;

/** zlib compression for stored objects, using {@link java.util.zip}. */
public final class Compression {

    private Compression() {
    }

    public static byte[] compress(byte[] data) {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        try (DeflaterOutputStream out = new DeflaterOutputStream(buffer)) {
            out.write(data);
        } catch (IOException e) {
            // Writing to an in-memory buffer cannot fail.
            throw new UncheckedIOException(e);
        }
        return buffer.toByteArray();
    }

    /**
     * Decompresses zlib data.
     *
     * @throws IOException if the data is not valid zlib (e.g. a corrupted file)
     */
    public static byte[] decompress(byte[] data) throws IOException {
        try (InputStream in = new InflaterInputStream(new ByteArrayInputStream(data))) {
            return in.readAllBytes();
        }
    }
}
