package minigit.core;

import minigit.exception.MiniGitException;
import minigit.exception.ObjectNotFoundException;
import minigit.model.ObjectType;
import minigit.util.Hashing;

import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Turns what a user types into a commit hash. Accepted forms:
 * <ul>
 *   <li>{@code HEAD}, a branch name, a tag name, or a hash / hash prefix;</li>
 *   <li>any of those followed by {@code ~N} (N commits back along first parents)
 *       or {@code ^} (one commit back), e.g. {@code HEAD~2}, {@code main^^}.</li>
 * </ul>
 * A name is looked up as a branch first, then a tag, then a hash prefix.
 */
public final class RevisionResolver {

    private static final Pattern SUFFIX = Pattern.compile("(~\\d*|\\^)$");

    private final ObjectStore store;
    private final RefStore refs;

    public RevisionResolver(ObjectStore store, RefStore refs) {
        this.store = store;
        this.refs = refs;
    }

    public String resolveCommit(String revision) {
        String base = revision.strip();
        int back = 0;
        Matcher m;
        while ((m = SUFFIX.matcher(base)).find()) {
            String s = m.group(1);
            back += s.equals("^") || s.equals("~") ? 1 : Integer.parseInt(s.substring(1));
            base = base.substring(0, m.start());
        }
        if (base.isEmpty()) {
            throw new MiniGitException("'" + revision + "' is not a valid revision");
        }

        String commit = resolveBase(base);
        for (int i = 0; i < back; i++) {
            List<String> parents = store.readCommit(commit).parents();
            if (parents.isEmpty()) {
                throw new MiniGitException("'" + revision + "' goes back further than the first commit");
            }
            commit = parents.get(0);
        }
        return commit;
    }

    private String resolveBase(String name) {
        if (name.equals("HEAD")) {
            return refs.headCommit().orElseThrow(() -> new MiniGitException("there are no commits yet"));
        }
        Optional<String> ref = refs.branch(name).or(() -> refs.tag(name));
        if (ref.isPresent()) {
            return ref.get();
        }
        // Anything that cannot be a hash prefix is most likely a mistyped branch name.
        String lower = name.toLowerCase();
        if (!Hashing.isHex(lower) || lower.length() < ObjectStore.MIN_PREFIX_LENGTH
                || lower.length() > Hashing.HASH_LENGTH) {
            throw unknown(name);
        }
        String hash;
        try {
            hash = store.resolve(lower);
        } catch (ObjectNotFoundException e) {
            throw unknown(name);
        }
        ObjectType type = store.read(hash).type();
        if (type != ObjectType.COMMIT) {
            throw new MiniGitException("'" + name + "' is a " + type.tag() + ", not a commit");
        }
        return hash;
    }

    private static MiniGitException unknown(String name) {
        return new MiniGitException("'" + name + "' is not a branch, tag, or commit");
    }
}
