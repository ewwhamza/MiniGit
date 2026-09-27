package minigit.util;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CompressionTest {

    @Test
    void roundTripsText() throws IOException {
        byte[] data = "hello hello hello hello hello\n".repeat(100).getBytes(StandardCharsets.UTF_8);
        byte[] compressed = Compression.compress(data);
        assertTrue(compressed.length < data.length, "repetitive text should shrink");
        assertArrayEquals(data, Compression.decompress(compressed));
    }

    @Test
    void roundTripsBinaryAndEmpty() throws IOException {
        byte[] binary = new byte[4096];
        new Random(42).nextBytes(binary);
        assertArrayEquals(binary, Compression.decompress(Compression.compress(binary)));
        assertArrayEquals(new byte[0], Compression.decompress(Compression.compress(new byte[0])));
    }

    @Test
    void rejectsGarbage() {
        assertThrows(IOException.class, () -> Compression.decompress("not zlib".getBytes(StandardCharsets.UTF_8)));
    }
}
