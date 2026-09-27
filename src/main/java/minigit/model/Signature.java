package minigit.model;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;

/**
 * Who made a commit and when, written as {@code Name <email> 1790496000 +0530}.
 *
 * @param time   the moment of the commit, stored to the second
 * @param offset the author's time zone at that moment, so dates display in their local time
 */
public record Signature(String name, String email, Instant time, ZoneOffset offset) {

    public static Signature now(String name, String email) {
        Instant now = Instant.now();
        return new Signature(name, email, Instant.ofEpochSecond(now.getEpochSecond()),
                ZoneId.systemDefault().getRules().getOffset(now));
    }

    public OffsetDateTime dateTime() {
        return time.atOffset(offset);
    }

    public String format() {
        return name + " <" + email + "> " + time.getEpochSecond() + " " + formatOffset(offset);
    }

    public static Signature parse(String text) {
        int lt = text.lastIndexOf('<');
        int gt = text.lastIndexOf('>');
        if (lt < 0 || gt < lt) {
            throw new IllegalArgumentException("malformed signature: " + text);
        }
        String[] when = text.substring(gt + 1).strip().split(" ");
        if (when.length != 2) {
            throw new IllegalArgumentException("malformed signature: " + text);
        }
        return new Signature(text.substring(0, lt).strip(), text.substring(lt + 1, gt),
                Instant.ofEpochSecond(Long.parseLong(when[0])), parseOffset(when[1]));
    }

    /** {@code +05:30} becomes {@code +0530}. */
    static String formatOffset(ZoneOffset offset) {
        int total = offset.getTotalSeconds() / 60;
        char sign = total < 0 ? '-' : '+';
        total = Math.abs(total);
        return String.format("%c%02d%02d", sign, total / 60, total % 60);
    }

    static ZoneOffset parseOffset(String s) {
        if (!s.matches("[+-]\\d{4}")) {
            throw new IllegalArgumentException("malformed time zone: " + s);
        }
        int hours = Integer.parseInt(s.substring(1, 3));
        int minutes = Integer.parseInt(s.substring(3, 5));
        int sign = s.charAt(0) == '-' ? -1 : 1;
        return ZoneOffset.ofHoursMinutes(sign * hours, sign * minutes);
    }
}
