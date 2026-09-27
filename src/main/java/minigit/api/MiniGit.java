package minigit.api;

import minigit.core.Checkout;
import minigit.core.Config;
import minigit.core.History;
import minigit.core.Index;
import minigit.core.IndexEntry;
import minigit.core.MergeState;
import minigit.core.ObjectStore;
import minigit.core.RefStore;
import minigit.core.Repository;
import minigit.core.Trees;
import minigit.core.WorkingTree;
import minigit.core.WorkingTree.FileStat;
import minigit.diff.FileDiff;
import minigit.diff.LineDiff;
import minigit.exception.CheckoutConflictException;
import minigit.exception.MiniGitException;
import minigit.exception.NothingToCommitException;
import minigit.merge.ThreeWayMerge;
import minigit.model.Blob;
import minigit.model.Commit;
import minigit.model.RawObject;
import minigit.model.Signature;
import minigit.model.TreeEntry;
import minigit.util.FileUtils;
import minigit.util.Hashing;
import minigit.util.IgnoreRules;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.PriorityQueue;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * The single entry point to MiniGit's engine, used by both the CLI and the GUI.
 *
 * <p>Methods return plain result objects and never print; presenting results is
 * the caller's job. All errors are reported as {@link MiniGitException}s.
 * Wherever a method takes a {@code revision}, it accepts anything
 * {@link minigit.core.RevisionResolver} does: {@code HEAD}, a branch, a tag, a
 * hash prefix, optionally followed by {@code ~N} or {@code ^}.
 */
public final class MiniGit {

    private final Repository repo;

    private MiniGit(Repository repo) {
        this.repo = repo;
    }

    /**
     * Creates an empty repository in {@code dir}, or does nothing if one already
     * exists there (FR-REPO-2).
     */
    public static InitResult init(Path dir) {
        Path root = dir.toAbsolutePath().normalize();
        if (Files.exists(root) && !Files.isDirectory(root)) {
            throw new MiniGitException(root + " is not a directory");
        }
        if (Repository.existsAt(root)) {
            return new InitResult(root.resolve(Repository.DIR_NAME), false);
        }
        return new InitResult(Repository.init(root).gitDir(), true);
    }

    /** Opens the repository containing {@code dir} or any of its parents. */
    public static MiniGit open(Path dir) {
        return new MiniGit(Repository.find(dir));
    }

    /** Computes the blob hash {@code file} would have, without storing anything. */
    public static String hashFile(Path file) {
        return Blob.fromFile(requireRegularFile(file)).hash();
    }

    public Path workTree() {
        return repo.workTree();
    }

    /** Stores {@code file}'s content as a blob and returns its hash. */
    public String storeFile(Path file) {
        return repo.objects().write(Blob.fromFile(requireRegularFile(file)));
    }

    /** Reads the object named by a full hash or a unique prefix of one. */
    public RawObject readObject(String hashOrPrefix) {
        return repo.objects().read(repo.objects().resolve(hashOrPrefix));
    }

    /** The full hash of the commit a revision names. */
    public String resolveCommit(String revision) {
        return repo.revisions().resolveCommit(revision);
    }

    // ---------------------------------------------------------------- staging

    /**
     * Stages files for the next commit (FR-IDX-1, FR-IDX-2). A directory stages
     * every non-ignored file inside it, and a tracked file that no longer exists
     * is staged as deleted. Nothing is changed if any path is invalid.
     */
    public AddResult add(List<Path> paths) {
        WorkingTree files = repo.files();
        IgnoreRules ignore = IgnoreRules.load(repo.workTree());
        Index index = repo.loadIndex();
        Set<String> staged = new TreeSet<>();
        Set<String> removed = new TreeSet<>();
        // Every file the user staged, changed or not: staging a conflicted file resolves it.
        Set<String> touched = new TreeSet<>();

        for (Path path : paths) {
            String rel = files.relativize(path);
            Path abs = files.toPath(rel);
            if (Files.isDirectory(abs)) {
                for (String file : files.listFiles(rel, ignore)) {
                    stage(index, file, staged);
                    touched.add(file);
                }
                for (String tracked : List.copyOf(index.pathsUnder(rel))) {
                    if (files.stat(tracked).isEmpty()) {
                        index.remove(tracked);
                        removed.add(tracked);
                    }
                }
            } else if (Files.isRegularFile(abs)) {
                if (ignore.isIgnored(rel, false) && !index.contains(rel)) {
                    throw new MiniGitException("'" + rel + "' is ignored by " + IgnoreRules.FILE_NAME);
                }
                stage(index, rel, staged);
                touched.add(rel);
            } else if (index.contains(rel)) {
                index.remove(rel);
                removed.add(rel);
            } else if (!index.pathsUnder(rel).isEmpty()) {
                // A tracked folder that was deleted from disk.
                for (String tracked : List.copyOf(index.pathsUnder(rel))) {
                    index.remove(tracked);
                    removed.add(tracked);
                }
            } else {
                throw new MiniGitException("'" + path + "' did not match any files");
            }
        }
        index.save();
        touched.addAll(removed);
        repo.mergeState().resolve(touched);
        return new AddResult(List.copyOf(staged), List.copyOf(removed));
    }

    /**
     * Stops tracking files (FR-IDX-6). Unless {@code keepFiles} is set, the files
     * are also deleted from disk, which is refused for any file with uncommitted
     * changes unless {@code force} is set (NFR-SAFE-1).
     *
     * @return the paths removed from the index
     */
    public List<String> remove(List<Path> paths, boolean keepFiles, boolean recursive, boolean force) {
        WorkingTree files = repo.files();
        Index index = repo.loadIndex();
        Set<String> targets = new TreeSet<>();

        for (Path path : paths) {
            String rel = files.relativize(path);
            if (index.contains(rel)) {
                targets.add(rel);
            } else if (!index.pathsUnder(rel).isEmpty()) {
                if (!recursive) {
                    throw new MiniGitException("not removing folder '" + path + "' without -r");
                }
                targets.addAll(index.pathsUnder(rel));
            } else {
                throw new MiniGitException("'" + path + "' is not tracked");
            }
        }

        if (!keepFiles && !force) {
            Map<String, String> head = repo.headFiles();
            List<String> unsafe = new ArrayList<>();
            for (String rel : targets) {
                IndexEntry entry = index.get(rel).orElseThrow();
                boolean stagedChange = !entry.hash().equals(head.get(rel));
                boolean localChange = repo.workingHash(index, entry).map(h -> !h.equals(entry.hash())).orElse(false);
                if (stagedChange || localChange) {
                    unsafe.add(rel);
                }
            }
            if (!unsafe.isEmpty()) {
                throw new MiniGitException("these files have uncommitted changes and would be lost:\n  "
                        + String.join("\n  ", unsafe)
                        + "\nuse --cached to keep the files, or -f to delete them anyway");
            }
        }

        for (String rel : targets) {
            index.remove(rel);
        }
        index.save();
        if (!keepFiles) {
            targets.forEach(files::delete);
        }
        repo.mergeState().resolve(targets);
        return List.copyOf(targets);
    }

    /**
     * Discards working-tree changes, restoring files to their staged version
     * (FR-CO-6). This deliberately overwrites local edits.
     *
     * @return the files restored
     */
    public List<String> restore(List<Path> paths) {
        Index index = repo.loadIndex();
        List<String> targets = matchTracked(paths, index.paths());
        List<String> restored = new ArrayList<>();
        for (String path : targets) {
            IndexEntry entry = index.get(path).orElseThrow();
            if (!repo.workingHash(index, entry).equals(Optional.of(entry.hash()))) {
                index.put(repo.writeWorkingFile(path, entry.hash()));
                restored.add(path);
            }
        }
        index.save();
        return restored;
    }

    /**
     * Unstages files: resets their index entries to HEAD's version, or removes them
     * if they are new (FR-IDX-8). The working tree is not touched.
     *
     * @return the files unstaged
     */
    public List<String> unstage(List<Path> paths) {
        Index index = repo.loadIndex();
        SortedMap<String, String> head = repo.headFiles();
        Set<String> known = new TreeSet<>(index.paths());
        known.addAll(head.keySet());
        List<String> unstaged = new ArrayList<>();
        for (String path : matchTracked(paths, known)) {
            String headHash = head.get(path);
            Optional<IndexEntry> entry = index.get(path);
            if (entry.map(IndexEntry::hash).equals(Optional.ofNullable(headHash))) {
                continue;
            }
            if (headHash == null) {
                index.remove(path);
            } else {
                index.put(unstatted(path, headHash));
            }
            unstaged.add(path);
        }
        index.save();
        return unstaged;
    }

    // ---------------------------------------------------------------- commits

    /**
     * Snapshots the index as a new commit on the current branch (FR-CMT-1 to FR-CMT-6).
     * While a merge is waiting, this creates the merge commit instead: it has two
     * parents, and {@code message} may be null to use the suggested merge message.
     *
     * @throws NothingToCommitException if the index matches HEAD
     */
    public CommitResult commit(String message) {
        MergeState merge = repo.mergeState();
        Optional<String> mergeHead = merge.mergeHead();
        String text = message == null || message.isBlank()
                ? mergeHead.map(h -> merge.message()).orElse("")
                : message.strip();
        if (text.isEmpty()) {
            throw new MiniGitException("the commit message cannot be empty");
        }
        if (mergeHead.isPresent() && !merge.conflicts().isEmpty()) {
            throw new MiniGitException("these files still have merge conflicts:\n  "
                    + String.join("\n  ", merge.conflicts())
                    + "\nedit them to remove the <<<<<<< ======= >>>>>>> markers, then 'minigit add' them");
        }
        requireIdentity();

        Index index = repo.loadIndex();
        List<FileChange> changes = compare(repo.headFiles(), indexFiles(index));
        // A merge commit is worth making even if the result equals this branch's tree.
        if (changes.isEmpty() && mergeHead.isEmpty()) {
            throw new NothingToCommitException();
        }

        List<String> parents = new ArrayList<>(repo.refs().headCommit().stream().toList());
        mergeHead.ifPresent(parents::add);
        String hash = writeCommit(Trees.write(repo.objects(), index.entries()), parents, text);
        merge.clear();
        return new CommitResult(hash, repo.refs().currentBranch(), parents.isEmpty(), firstLine(text), changes);
    }

    // ---------------------------------------------------------------- merge

    /**
     * Merges another branch or commit into the current branch (FR-MRG-1 to FR-MRG-5).
     * <ul>
     *   <li>If it is already part of this branch's history: nothing to do.</li>
     *   <li>If this branch has no commits of its own since they split: fast-forward.</li>
     *   <li>Otherwise every file is merged three ways against the common ancestor. A
     *       clean result is committed straight away; conflicts are written into the
     *       files with markers and the merge waits (see {@link #abortMerge}).</li>
     * </ul>
     * Requires a clean working tree, so no uncommitted work can be mixed in or lost.
     */
    public MergeResult merge(String revision) {
        MergeState state = repo.mergeState();
        if (state.inProgress()) {
            throw new MiniGitException("a merge is already in progress; commit it, or undo it with 'minigit merge --abort'");
        }
        ObjectStore store = repo.objects();
        String ours = repo.refs().headCommit().orElseThrow(() -> new MiniGitException("there are no commits to merge into yet"));
        String theirs = resolveCommit(revision);
        requireNoLocalChanges("merging");

        if (History.isAncestor(store, theirs, ours)) {
            return new MergeResult(MergeResult.Kind.UP_TO_DATE, Optional.of(ours), List.of(), 0);
        }
        if (History.isAncestor(store, ours, theirs)) {
            int changed = Checkout.apply(repo, theirs);
            repo.refs().advanceHead(theirs);
            return new MergeResult(MergeResult.Kind.FAST_FORWARD, Optional.of(theirs), List.of(), changed);
        }

        Map<String, String> base = History.mergeBase(store, ours, theirs).map(repo::commitFiles).orElseGet(TreeMap::new);
        Map<String, String> mine = repo.headFiles();
        Map<String, String> other = repo.commitFiles(theirs);
        String oursLabel = repo.refs().currentBranch().orElse(Hashing.shorten(ours));
        boolean isBranch = repo.refs().branch(revision).isPresent();
        String theirsLabel = isBranch ? revision : Hashing.shorten(theirs);

        // Decide every file's outcome before touching the disk.
        Map<String, String> clean = new TreeMap<>();      // path -> blob hash, or null to delete
        Map<String, byte[]> conflicted = new TreeMap<>(); // path -> content to leave for the user
        Set<String> paths = new TreeSet<>(base.keySet());
        paths.addAll(mine.keySet());
        paths.addAll(other.keySet());
        for (String path : paths) {
            String b = base.get(path);
            String o = mine.get(path);
            String t = other.get(path);
            if (Objects.equals(o, t) || Objects.equals(t, b)) {
                continue; // Nothing new from their side.
            }
            if (Objects.equals(o, b)) {
                clean.put(path, t); // Only they changed it.
                continue;
            }
            // Both sides changed the file differently.
            if (o == null || t == null) {
                conflicted.put(path, blob(o != null ? o : t)); // Changed on one side, deleted on the other.
                continue;
            }
            byte[] ob = blob(o);
            byte[] tb = blob(t);
            if (FileDiff.of(path, ob, tb).binary()) {
                conflicted.put(path, ob);
                continue;
            }
            ThreeWayMerge.Result merged = ThreeWayMerge.merge(
                    LineDiff.splitLines(b == null ? "" : new String(blob(b), StandardCharsets.UTF_8)),
                    LineDiff.splitLines(new String(ob, StandardCharsets.UTF_8)),
                    LineDiff.splitLines(new String(tb, StandardCharsets.UTF_8)),
                    oursLabel, theirsLabel);
            byte[] result = merged.text().getBytes(StandardCharsets.UTF_8);
            if (merged.clean()) {
                clean.put(path, store.write(new Blob(result)));
            } else {
                conflicted.put(path, result);
            }
        }

        Index index = repo.loadIndex();
        List<String> blocked = new ArrayList<>();
        for (String path : paths) {
            boolean writes = (clean.containsKey(path) && clean.get(path) != null) || conflicted.containsKey(path);
            if (writes && !index.contains(path) && repo.workingHash(path).isPresent()) {
                blocked.add(path); // An untracked file is in the way.
            }
        }
        if (!blocked.isEmpty()) {
            throw new CheckoutConflictException(blocked);
        }

        clean.forEach((path, hash) -> {
            if (hash == null) {
                repo.files().delete(path);
                index.remove(path);
            } else {
                index.put(repo.writeWorkingFile(path, hash));
            }
        });
        // Conflicted files keep this branch's version in the index until the user stages a fix.
        conflicted.forEach((path, content) -> FileUtils.writeAtomically(repo.files().toPath(path), content));
        index.save();

        String message = "Merge " + (isBranch ? "branch '" + revision + "'" : "commit " + theirsLabel)
                + " into " + oursLabel;
        int changed = clean.size() + conflicted.size();
        if (!conflicted.isEmpty()) {
            List<String> list = List.copyOf(conflicted.keySet());
            state.start(theirs, message, list);
            return new MergeResult(MergeResult.Kind.CONFLICTS, Optional.empty(), list, changed);
        }
        requireIdentity();
        String hash = writeCommit(Trees.write(store, index.entries()), List.of(ours, theirs), message);
        return new MergeResult(MergeResult.Kind.MERGED, Optional.of(hash), List.of(), changed);
    }

    /** Throws away a merge that stopped on conflicts, restoring HEAD's files. */
    public void abortMerge() {
        MergeState state = repo.mergeState();
        if (!state.inProgress()) {
            throw new MiniGitException("there is no merge to abort");
        }
        Map<String, String> head = repo.headFiles();
        Index index = repo.loadIndex();
        Set<String> paths = new TreeSet<>(head.keySet());
        paths.addAll(index.paths());
        paths.addAll(state.conflicts());
        for (String path : paths) {
            String h = head.get(path);
            if (h == null) {
                repo.files().delete(path);
                index.remove(path);
            } else if (!repo.workingHash(path).equals(Optional.of(h))
                    || !index.get(path).map(IndexEntry::hash).equals(Optional.of(h))) {
                index.put(repo.writeWorkingFile(path, h));
            }
        }
        index.save();
        state.clear();
    }

    /**
     * History reachable from HEAD, newest first (FR-LOG-1). Commits are ordered by
     * time, so the history of a merge interleaves both branches.
     *
     * @param limit maximum number of commits, or 0 for all
     */
    public List<CommitInfo> log(int limit) {
        record Pending(long seq, String hash, Commit commit) {
        }
        List<CommitInfo> result = new ArrayList<>();
        Optional<String> head = repo.refs().headCommit();
        if (head.isEmpty()) {
            return result;
        }
        PriorityQueue<Pending> queue = new PriorityQueue<>(
                Comparator.comparing((Pending p) -> p.commit().author().time()).reversed()
                        .thenComparingLong(Pending::seq));
        Set<String> seen = new HashSet<>();
        long seq = 0;
        seen.add(head.get());
        queue.add(new Pending(seq++, head.get(), repo.objects().readCommit(head.get())));

        while (!queue.isEmpty() && (limit <= 0 || result.size() < limit)) {
            Pending next = queue.poll();
            result.add(CommitInfo.of(next.hash(), next.commit()));
            for (String parent : next.commit().parents()) {
                if (seen.add(parent)) {
                    queue.add(new Pending(seq++, parent, repo.objects().readCommit(parent)));
                }
            }
        }
        return result;
    }

    /** A commit and its changes relative to its first parent (FR-LOG-3). */
    public CommitDetails show(String revision) {
        String hash = resolveCommit(revision);
        Commit commit = repo.objects().readCommit(hash);
        Map<String, String> before = commit.parents().isEmpty()
                ? Map.of()
                : repo.commitFiles(commit.parents().get(0));
        return new CommitDetails(CommitInfo.of(hash, commit), diffSnapshots(before, repo.commitFiles(hash)));
    }

    /**
     * Labels to show next to commits in a history view, keyed by commit hash,
     * e.g. {@code HEAD -> main}, {@code feature}, {@code tag: v1.0}.
     */
    public Map<String, List<String>> refLabels() {
        Map<String, List<String>> labels = new HashMap<>();
        Optional<String> current = repo.refs().currentBranch();
        Optional<String> head = repo.refs().headCommit();
        if (head.isPresent() && current.isEmpty()) {
            labels.computeIfAbsent(head.get(), k -> new ArrayList<>()).add("HEAD");
        }
        for (String branch : repo.refs().branches()) {
            String tip = repo.refs().branch(branch).orElseThrow();
            List<String> list = labels.computeIfAbsent(tip, k -> new ArrayList<>());
            if (current.equals(Optional.of(branch))) {
                list.add(0, "HEAD -> " + branch);
            } else {
                list.add(branch);
            }
        }
        for (String tag : repo.refs().tags()) {
            labels.computeIfAbsent(repo.refs().tag(tag).orElseThrow(), k -> new ArrayList<>()).add("tag: " + tag);
        }
        return labels;
    }

    // ---------------------------------------------------------------- diff

    /** Unstaged changes: tracked files whose working copy differs from the index (FR-DIFF-4). */
    public List<FileDiff> diffUnstaged() {
        Index index = repo.loadIndex();
        List<FileDiff> diffs = new ArrayList<>();
        for (IndexEntry entry : index.entries()) {
            unstagedDiff(index, entry).ifPresent(diffs::add);
        }
        return diffs;
    }

    /** One file's unstaged changes, or empty if it has none or is not tracked. */
    public Optional<FileDiff> diffUnstaged(String path) {
        Index index = repo.loadIndex();
        return index.get(path).flatMap(entry -> unstagedDiff(index, entry));
    }

    /** Staged changes: what the next commit will contain, relative to HEAD (FR-DIFF-5). */
    public List<FileDiff> diffStaged() {
        return diffSnapshots(repo.headFiles(), indexFiles(repo.loadIndex()));
    }

    /** One file's staged changes, or empty if it has none. */
    public Optional<FileDiff> diffStaged(String path) {
        String before = repo.headFiles().get(path);
        String after = repo.loadIndex().get(path).map(IndexEntry::hash).orElse(null);
        if (Objects.equals(before, after)) {
            return Optional.empty();
        }
        return Optional.of(FileDiff.of(path, before == null ? null : blob(before), after == null ? null : blob(after)));
    }

    /** An untracked file's whole content, shown as added lines. */
    public FileDiff diffUntracked(String path) {
        return FileDiff.of(path, null, FileUtils.readBytes(repo.files().toPath(path)));
    }

    /** Changes between two commits (FR-DIFF-6). */
    public List<FileDiff> diffCommits(String fromRevision, String toRevision) {
        return diffSnapshots(repo.commitFiles(resolveCommit(fromRevision)),
                repo.commitFiles(resolveCommit(toRevision)));
    }

    // ---------------------------------------------------------------- status

    /** Compares HEAD, the index, and the working tree (FR-STAT-1 to FR-STAT-4). */
    public StatusResult status() {
        Index index = repo.loadIndex();
        List<FileChange> staged = compare(repo.headFiles(), indexFiles(index));

        List<FileChange> unstaged = new ArrayList<>();
        for (IndexEntry entry : index.entries()) {
            Optional<String> current = repo.workingHash(index, entry);
            if (current.isEmpty()) {
                unstaged.add(new FileChange(entry.path(), ChangeType.DELETED));
            } else if (!current.get().equals(entry.hash())) {
                unstaged.add(new FileChange(entry.path(), ChangeType.MODIFIED));
            }
        }

        List<String> untracked = repo.files().listFiles("", IgnoreRules.load(repo.workTree())).stream()
                .filter(path -> !index.contains(path))
                .toList();

        MergeState state = repo.mergeState();
        Optional<StatusResult.MergeInfo> merge = state.mergeHead()
                .map(theirs -> new StatusResult.MergeInfo(theirs, state.message(), state.conflicts()));
        return new StatusResult(repo.refs().currentBranch(), repo.refs().headCommit(),
                staged, unstaged, untracked, merge);
    }

    // ---------------------------------------------------------------- branches and tags

    /** All branches, sorted by name (FR-BR-1). */
    public List<RefInfo> branches() {
        Optional<String> current = repo.refs().currentBranch();
        return repo.refs().branches().stream()
                .map(b -> new RefInfo(b, repo.refs().branch(b).orElseThrow(), current.equals(Optional.of(b))))
                .toList();
    }

    public Optional<String> currentBranch() {
        return repo.refs().currentBranch();
    }

    /** Whether {@code name} is allowed as a branch or tag name (FR-BR-3). */
    public static boolean isValidRefName(String name) {
        try {
            RefStore.validateName("branch", name);
            return true;
        } catch (MiniGitException e) {
            return false;
        }
    }

    /** Creates a branch at {@code startRevision}, or at HEAD if empty (FR-BR-2). */
    public RefInfo createBranch(String name, Optional<String> startRevision) {
        String commit = resolveCommit(startRevision.orElse("HEAD"));
        repo.refs().createBranch(name, commit);
        return new RefInfo(name, commit, false);
    }

    /**
     * Deletes a branch (FR-BR-4). Refused for the current branch, and, unless
     * {@code force} is set, for a branch whose commits are not part of HEAD's
     * history, since they would become unreachable.
     */
    public void deleteBranch(String name, boolean force) {
        RefStore refs = repo.refs();
        String tip = refs.branch(name).orElseThrow(() -> new MiniGitException("branch '" + name + "' does not exist"));
        if (refs.currentBranch().equals(Optional.of(name))) {
            throw new MiniGitException("cannot delete branch '" + name + "' while it is checked out");
        }
        if (!force) {
            Optional<String> head = refs.headCommit();
            if (head.isEmpty() || !History.isAncestor(repo.objects(), tip, head.get())) {
                throw new MiniGitException("branch '" + name + "' has commits that are not in the current branch;"
                        + " merge it first, or force deletion with -D");
            }
        }
        refs.deleteBranch(name);
    }

    public List<RefInfo> tags() {
        return repo.refs().tags().stream()
                .map(t -> new RefInfo(t, repo.refs().tag(t).orElseThrow(), false))
                .toList();
    }

    /** Creates a lightweight tag at {@code revision}, or at HEAD if empty (FR-TAG-1). */
    public RefInfo createTag(String name, Optional<String> revision) {
        String commit = resolveCommit(revision.orElse("HEAD"));
        repo.refs().createTag(name, commit);
        return new RefInfo(name, commit, false);
    }

    public void deleteTag(String name) {
        repo.refs().deleteTag(name);
    }

    // ---------------------------------------------------------------- checkout and reset

    /**
     * Switches to a branch, or to any other revision as a detached HEAD
     * (FR-CO-1 to FR-CO-4). A branch name always means the branch, not its commit.
     *
     * @throws minigit.exception.CheckoutConflictException if uncommitted work would
     *         be overwritten; nothing is changed in that case
     */
    public CheckoutResult checkout(String target) {
        requireNoMerge("switching");
        RefStore refs = repo.refs();
        Optional<String> branchTip = refs.branch(target);
        if (branchTip.isPresent() && refs.currentBranch().equals(Optional.of(target))) {
            return new CheckoutResult(Optional.of(target), branchTip, 0, true);
        }
        String commit = branchTip.orElseGet(() -> resolveCommit(target));
        Optional<String> head = refs.headCommit();
        if (branchTip.isEmpty() && refs.currentBranch().isEmpty() && head.equals(Optional.of(commit))) {
            return new CheckoutResult(Optional.empty(), head, 0, true);
        }

        int changed = head.equals(Optional.of(commit)) ? 0 : Checkout.apply(repo, commit);
        if (branchTip.isPresent()) {
            refs.attachHead(target);
            return new CheckoutResult(Optional.of(target), Optional.of(commit), changed, false);
        }
        refs.detachHead(commit);
        return new CheckoutResult(Optional.empty(), Optional.of(commit), changed, false);
    }

    /**
     * Creates a branch at HEAD and switches to it (FR-CO-5). Before the first
     * commit, this just renames the branch that the first commit will go on.
     */
    public CheckoutResult checkoutNewBranch(String name) {
        RefStore refs = repo.refs();
        Optional<String> head = refs.headCommit();
        if (head.isPresent()) {
            refs.createBranch(name, head.get());
        } else {
            RefStore.validateName("branch", name);
            if (refs.branch(name).isPresent()) {
                throw new MiniGitException("a branch named '" + name + "' already exists");
            }
        }
        refs.attachHead(name);
        return new CheckoutResult(Optional.of(name), head, 0, false);
    }

    /**
     * Moves the current branch (or detached HEAD) to another commit and resets the
     * index to match it. The working tree is left as it is, so the difference
     * shows up as unstaged changes (mixed reset, FR-RST-1).
     *
     * @return the commit HEAD now points at
     */
    public String reset(String revision) {
        String target = resolveCommit(revision);
        Index index = repo.loadIndex();
        SortedMap<String, String> files = repo.commitFiles(target);

        for (String path : List.copyOf(index.paths())) {
            if (!files.containsKey(path)) {
                index.remove(path);
            }
        }
        files.forEach((path, hash) -> {
            // Keep the recorded size and timestamp when the content is unchanged.
            if (!index.get(path).map(IndexEntry::hash).equals(Optional.of(hash))) {
                index.put(unstatted(path, hash));
            }
        });

        Optional<String> branch = repo.refs().currentBranch();
        if (branch.isPresent()) {
            repo.refs().updateBranch(branch.get(), target);
        } else {
            repo.refs().detachHead(target);
        }
        index.save();
        // Resetting abandons any half-finished merge, as in Git.
        repo.mergeState().clear();
        return target;
    }

    // ---------------------------------------------------------------- objects and ignore rules

    /** The root tree hash of a commit, for browsing its snapshot (FR-GUI-17). */
    public String treeOf(String revision) {
        return repo.objects().readCommit(resolveCommit(revision)).tree();
    }

    /** The entries of a tree object: names with their blob or subtree hashes. */
    public List<TreeEntry> treeEntries(String treeHash) {
        return repo.objects().readTree(treeHash).entries();
    }

    /** Where an object is stored, relative to the repository root. */
    public String objectPath(String hash) {
        return Repository.DIR_NAME + "/objects/" + hash.substring(0, 2) + "/" + hash.substring(2);
    }

    /** Appends a pattern to {@code .minigitignore}, creating it if needed (FR-GUI-16). */
    public void addIgnorePattern(String pattern) {
        Path file = repo.workTree().resolve(IgnoreRules.FILE_NAME);
        String existing = Files.exists(file) ? new String(FileUtils.readBytes(file), StandardCharsets.UTF_8) : "";
        String prefix = existing.isEmpty() || existing.endsWith("\n") ? "" : "\n";
        FileUtils.writeAtomically(file, (existing + prefix + pattern.strip() + "\n").getBytes(StandardCharsets.UTF_8));
    }

    private void requireNoMerge(String action) {
        if (repo.mergeState().inProgress()) {
            throw new MiniGitException("a merge is in progress; finish it with 'minigit commit' or undo it with "
                    + "'minigit merge --abort' before " + action);
        }
    }

    // ---------------------------------------------------------------- config

    public Optional<String> getConfig(String key) {
        return repo.config().get(key);
    }

    public void setConfig(String key, String value) {
        repo.config().set(key, value);
    }

    /** Returns {@code false} if the key was not set. */
    public boolean unsetConfig(String key) {
        return repo.config().unset(key);
    }

    public Map<String, String> listConfig() {
        return repo.config().all();
    }

    // ---------------------------------------------------------------- helpers

    /** Stages one existing file, skipping the hash if it is unchanged since last staged. */
    private void stage(Index index, String rel, Set<String> staged) {
        FileStat stat = repo.files().stat(rel)
                .orElseThrow(() -> new MiniGitException("'" + rel + "' disappeared while staging"));
        Optional<IndexEntry> existing = index.get(rel);
        if (existing.isPresent() && index.isUnchanged(existing.get(), stat.size(), stat.lastModified())) {
            return;
        }
        // Stat first: if the file changes while being read, the next status sees a new timestamp.
        String hash = repo.objects().write(Blob.fromFile(repo.files().toPath(rel)));
        if (existing.isEmpty() || !existing.get().hash().equals(hash)) {
            staged.add(rel);
        }
        index.put(new IndexEntry(rel, hash, stat.size(), stat.lastModified()));
    }

    /** Writes a commit object by the configured author and moves HEAD to it. */
    private String writeCommit(String tree, List<String> parents, String message) {
        requireIdentity();
        String name = repo.config().get(Config.USER_NAME).orElseThrow();
        String email = repo.config().get(Config.USER_EMAIL).orElse("");
        String hash = repo.objects().write(new Commit(tree, parents, Signature.now(name, email), message));
        repo.refs().advanceHead(hash);
        return hash;
    }

    private void requireIdentity() {
        if (repo.config().get(Config.USER_NAME).isEmpty()) {
            throw new MiniGitException("tell MiniGit who you are first: minigit config user.name \"Your Name\"");
        }
    }

    private static String firstLine(String text) {
        int nl = text.indexOf('\n');
        return nl < 0 ? text : text.substring(0, nl);
    }

    /** Refuses an operation while any tracked file has staged or unstaged changes. */
    private void requireNoLocalChanges(String action) {
        StatusResult status = status();
        Set<String> dirty = new TreeSet<>();
        status.staged().forEach(c -> dirty.add(c.path()));
        status.unstaged().forEach(c -> dirty.add(c.path()));
        if (!dirty.isEmpty()) {
            throw new MiniGitException("commit or discard your changes before " + action + ":\n  "
                    + String.join("\n  ", dirty));
        }
    }

    /** An index entry with no recorded size or timestamp, so the file is always re-hashed. */
    private static IndexEntry unstatted(String path, String hash) {
        return new IndexEntry(path, hash, -1, 0);
    }

    /**
     * Expands user paths to the known paths they name: a path itself, or every
     * path inside it if it is a folder.
     */
    private List<String> matchTracked(List<Path> paths, Collection<String> known) {
        Set<String> result = new TreeSet<>();
        for (Path path : paths) {
            String rel = repo.files().relativize(path);
            List<String> matches = known.stream()
                    .filter(k -> rel.isEmpty() || k.equals(rel) || k.startsWith(rel + "/"))
                    .toList();
            if (matches.isEmpty()) {
                throw new MiniGitException("'" + path + "' is not tracked");
            }
            result.addAll(matches);
        }
        return List.copyOf(result);
    }

    private byte[] blob(String hash) {
        return repo.objects().read(hash).content();
    }

    private Optional<FileDiff> unstagedDiff(Index index, IndexEntry entry) {
        Optional<String> current = repo.workingHash(index, entry);
        if (current.equals(Optional.of(entry.hash()))) {
            return Optional.empty();
        }
        byte[] after = current.isPresent() ? FileUtils.readBytes(repo.files().toPath(entry.path())) : null;
        return Optional.of(FileDiff.of(entry.path(), blob(entry.hash()), after));
    }

    /** Line diffs for every file that differs between two path → blob hash maps. */
    private List<FileDiff> diffSnapshots(Map<String, String> before, Map<String, String> after) {
        List<FileDiff> diffs = new ArrayList<>();
        for (FileChange change : compare(before, after)) {
            String path = change.path();
            diffs.add(FileDiff.of(path,
                    before.containsKey(path) ? blob(before.get(path)) : null,
                    after.containsKey(path) ? blob(after.get(path)) : null));
        }
        return diffs;
    }

    private static SortedMap<String, String> indexFiles(Index index) {
        SortedMap<String, String> files = new TreeMap<>();
        index.entries().forEach(e -> files.put(e.path(), e.hash()));
        return files;
    }

    /** What changed going from {@code before} to {@code after}, sorted by path. */
    private static List<FileChange> compare(Map<String, String> before, Map<String, String> after) {
        Set<String> paths = new TreeSet<>(before.keySet());
        paths.addAll(after.keySet());
        List<FileChange> changes = new ArrayList<>();
        for (String path : paths) {
            String old = before.get(path);
            String now = after.get(path);
            if (old == null) {
                changes.add(new FileChange(path, ChangeType.ADDED));
            } else if (now == null) {
                changes.add(new FileChange(path, ChangeType.DELETED));
            } else if (!old.equals(now)) {
                changes.add(new FileChange(path, ChangeType.MODIFIED));
            }
        }
        return changes;
    }

    private static Path requireRegularFile(Path file) {
        if (!Files.exists(file)) {
            throw new MiniGitException("no such file: " + file);
        }
        if (!Files.isRegularFile(file)) {
            throw new MiniGitException("not a regular file: " + file);
        }
        return file;
    }
}
