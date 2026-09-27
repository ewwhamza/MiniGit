package minigit.util;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.regex.Pattern;

/** SHA-1 hashing helpers. An object's hash is its permanent identity. */
public final class Hashing {

    /** Length of a full hash in hex characters (160 bits). */
    public static final int HASH_LENGTH = 40;

    /** Length of the abbreviated hash shown to users, e.g. {@code 3a7bd3e}. */
    public static final int SHORT_LENGTH = 7;

    private static final Pattern FULL_HASH = Pattern.compile("[0-9a-f]{40}");
    private static final Pattern HEX = Pattern.compile("[0-9a-f]+");

    private Hashing() {
    }

    /** Returns the lowercase hex SHA-1 digest of {@code data}. */
    public static String sha1Hex(byte[] data) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-1");
            return HexFormat.of().formatHex(digest.digest(data));
        } catch (NoSuchAlgorithmException e) {
            // Every Java platform is required to support SHA-1.
            throw new IllegalStateException("SHA-1 is not available", e);
        }
    }

    public static boolean isFullHash(String s) {
        return s != null && FULL_HASH.matcher(s).matches();
    }

    public static boolean isHex(String s) {
        return s != null && HEX.matcher(s).matches();
    }

    public static String shorten(String hash) {
        return hash.length() <= SHORT_LENGTH ? hash : hash.substring(0, SHORT_LENGTH);
    }
}
