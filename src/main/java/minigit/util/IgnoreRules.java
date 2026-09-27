package minigit.util;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Patterns from {@code .minigitignore} (FR-IDX-7). One pattern per line:
 * <ul>
 *   <li>blank lines and lines starting with {@code #} are skipped;</li>
 *   <li>{@code *} matches anything except {@code /}, {@code ?} one such character,
 *       and {@code **} anything including {@code /};</li>
 *   <li>a trailing {@code /} matches directories only, e.g. {@code build/};</li>
 *   <li>a pattern with no other {@code /} matches a name at any depth, e.g. {@code *.class};</li>
 *   <li>a pattern with a {@code /} matches from the repository root, e.g. {@code docs/*.pdf}.</li>
 * </ul>
 * Everything inside an ignored directory is ignored too.
 */
public final class IgnoreRules {

    public static final String FILE_NAME = ".minigitignore";

    private record Rule(Pattern pattern, boolean directoryOnly, boolean anchored) {
    }

    private final List<Rule> rules;

    private IgnoreRules(List<Rule> rules) {
        this.rules = rules;
    }

    public static IgnoreRules none() {
        return new IgnoreRules(List.of());
    }

    /** Reads {@code .minigitignore} from the working-tree root, if there is one. */
    public static IgnoreRules load(Path workTree) {
        Path file = workTree.resolve(FILE_NAME);
        if (!Files.isRegularFile(file)) {
            return none();
        }
        return parse(new String(FileUtils.readBytes(file), StandardCharsets.UTF_8));
    }

    public static IgnoreRules parse(String text) {
        List<Rule> rules = new ArrayList<>();
        for (String line : text.split("\\R")) {
            String p = line.strip();
            if (p.isEmpty() || p.startsWith("#")) {
                continue;
            }
            boolean directoryOnly = p.endsWith("/");
            if (directoryOnly) {
                p = p.substring(0, p.length() - 1);
            }
            if (p.startsWith("/")) {
                p = p.substring(1);
            }
            if (p.isEmpty()) {
                continue;
            }
            // "/build" and "docs/*.pdf" are anchored at the root; "*.log" matches anywhere.
            boolean anchored = p.contains("/") || line.strip().startsWith("/");
            rules.add(new Rule(globToRegex(p), directoryOnly, anchored));
        }
        return new IgnoreRules(rules);
    }

    /**
     * Whether a repository-relative path is ignored, either directly or because
     * one of its parent directories is.
     */
    public boolean isIgnored(String path, boolean isDirectory) {
        if (rules.isEmpty()) {
            return false;
        }
        String[] segments = path.split("/");
        StringBuilder prefix = new StringBuilder();
        for (int i = 0; i < segments.length; i++) {
            if (i > 0) {
                prefix.append('/');
            }
            prefix.append(segments[i]);
            boolean dir = i < segments.length - 1 || isDirectory;
            if (matches(prefix.toString(), segments[i], dir)) {
                return true;
            }
        }
        return false;
    }

    private boolean matches(String path, String name, boolean isDirectory) {
        for (Rule rule : rules) {
            if (rule.directoryOnly() && !isDirectory) {
                continue;
            }
            String subject = rule.anchored() ? path : name;
            if (rule.pattern().matcher(subject).matches()) {
                return true;
            }
        }
        return false;
    }

    private static Pattern globToRegex(String glob) {
        StringBuilder regex = new StringBuilder();
        for (int i = 0; i < glob.length(); i++) {
            char c = glob.charAt(i);
            if (c == '*' && i + 1 < glob.length() && glob.charAt(i + 1) == '*') {
                regex.append(".*");
                i++;
            } else if (c == '*') {
                regex.append("[^/]*");
            } else if (c == '?') {
                regex.append("[^/]");
            } else {
                regex.append(Pattern.quote(String.valueOf(c)));
            }
        }
        return Pattern.compile(regex.toString());
    }
}
