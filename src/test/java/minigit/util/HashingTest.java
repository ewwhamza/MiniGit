package minigit.util;

import minigit.model.Blob;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HashingTest {

    @Test
    void sha1MatchesKnownVector() {
        // FIPS 180 test vector.
        assertEquals("a9993e364706816aba3e25717850c26c9cd0d89d",
                Hashing.sha1Hex("abc".getBytes(StandardCharsets.US_ASCII)));
    }

    @Test
    void blobHashesMatchRealGit() {
        // MiniGit's blob header is identical to Git's, so these match `git hash-object`.
        assertEquals("e69de29bb2d1d6434b8b29ae775ad8c2e48c5391", new Blob(new byte[0]).hash());
        assertEquals("ce013625030ba8dba906f756967f9e9ca394464a",
                new Blob("hello\n".getBytes(StandardCharsets.UTF_8)).hash());
    }

    @Test
    void recognisesFullHashes() {
        assertTrue(Hashing.isFullHash("ce013625030ba8dba906f756967f9e9ca394464a"));
        assertFalse(Hashing.isFullHash("ce01362"));
        assertFalse(Hashing.isFullHash("CE013625030BA8DBA906F756967F9E9CA394464A"));
        assertFalse(Hashing.isFullHash("zz013625030ba8dba906f756967f9e9ca394464a"));
        assertFalse(Hashing.isFullHash(null));
    }

    @Test
    void shortensToSevenCharacters() {
        assertEquals("ce01362", Hashing.shorten("ce013625030ba8dba906f756967f9e9ca394464a"));
        assertEquals("abc", Hashing.shorten("abc"));
    }
}
