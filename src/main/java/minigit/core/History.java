package minigit.core;

import minigit.model.Commit;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/** Questions about the commit graph, answered by walking parent links. */
public final class History {

    private History() {
    }

    /**
     * Whether {@code ancestor} is reachable from {@code descendant} by following
     * parents (a commit counts as its own ancestor). Breadth-first search.
     */
    public static boolean isAncestor(ObjectStore store, String ancestor, String descendant) {
        return ancestors(store, descendant).contains(ancestor);
    }

    /**
     * The best common ancestor of two commits: the point where their histories
     * split, used as the base of a three-way merge (FR-MRG-3).
     *
     * <p>Common ancestors are found by walking back from {@code b} until reaching
     * commits that are also ancestors of {@code a}. Any candidate that is itself an
     * ancestor of another candidate is dropped, since the other is closer. If more
     * than one remains (criss-cross history), the newest is used.
     *
     * @return empty if the histories share no commit at all
     */
    public static Optional<String> mergeBase(ObjectStore store, String a, String b) {
        Set<String> ofA = ancestors(store, a);
        List<String> candidates = new ArrayList<>();
        Deque<String> queue = new ArrayDeque<>();
        Set<String> seen = new HashSet<>();
        queue.add(b);
        seen.add(b);
        while (!queue.isEmpty()) {
            String hash = queue.poll();
            if (ofA.contains(hash)) {
                // Everything behind a common ancestor is common too, and further away.
                candidates.add(hash);
                continue;
            }
            for (String parent : store.readCommit(hash).parents()) {
                if (seen.add(parent)) {
                    queue.add(parent);
                }
            }
        }
        List<String> best = candidates.stream()
                .filter(c -> candidates.stream().noneMatch(other -> !other.equals(c) && isAncestor(store, c, other)))
                .toList();
        return best.stream().max(Comparator.comparing((String h) -> commitTime(store, h)));
    }

    /** {@code start} and every commit reachable from it. */
    static Set<String> ancestors(ObjectStore store, String start) {
        Set<String> seen = new HashSet<>();
        Deque<String> queue = new ArrayDeque<>();
        queue.add(start);
        seen.add(start);
        while (!queue.isEmpty()) {
            for (String parent : store.readCommit(queue.poll()).parents()) {
                if (seen.add(parent)) {
                    queue.add(parent);
                }
            }
        }
        return seen;
    }

    private static long commitTime(ObjectStore store, String hash) {
        Commit c = store.readCommit(hash);
        return c.author().time().getEpochSecond();
    }
}
