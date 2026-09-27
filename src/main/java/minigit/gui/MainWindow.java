package minigit.gui;

import minigit.api.CommitInfo;
import minigit.api.MiniGit;
import minigit.api.RefInfo;
import minigit.api.StatusResult;
import minigit.diff.FileDiff;
import minigit.exception.NotARepositoryException;
import minigit.gui.ChangesPanel.Group;
import minigit.gui.dialogs.IdentityDialog;
import minigit.gui.dialogs.NewBranchDialog;
import minigit.gui.util.BackgroundTask;
import minigit.gui.util.Dialogs;
import minigit.gui.util.Theme;
import minigit.util.Hashing;

import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JPanel;
import javax.swing.JSplitPane;
import javax.swing.JTabbedPane;
import javax.swing.JToolBar;
import javax.swing.KeyStroke;
import javax.swing.WindowConstants;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.event.ActionEvent;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * The application window. Holds the open repository and wires the panels to the
 * {@link MiniGit} facade. Every repository call goes through {@link BackgroundTask},
 * and every result is applied back on the Event Dispatch Thread.
 */
public final class MainWindow extends JFrame {

    private static final String WELCOME = "welcome";
    private static final String REPOSITORY = "repository";
    private static final int CHANGES_TAB = 0;
    private static final int HISTORY_TAB = 1;
    private static final int OBJECTS_TAB = 2;
    private static final int HISTORY_LIMIT = 1000;

    /** What one refresh reads from the repository. */
    private record Snapshot(StatusResult status, List<RefInfo> branches) {
    }

    private record History(List<CommitInfo> commits, Map<String, List<String>> labels) {
    }

    private final CardLayout cards = new CardLayout();
    private final JPanel content = new JPanel(cards);
    private final RecentRepositories recent = new RecentRepositories();
    private final WelcomePanel welcome;
    private final ChangesPanel changes;
    private final DiffViewer changesDiff = new DiffViewer();
    private final CommitPanel commitPanel;
    private final HistoryPanel history;
    private final ObjectsPanel objects;
    private final BranchSelector branchSelector;
    private final JLabel mergeLabel = new JLabel();
    private final JPanel mergeBanner = new JPanel(new BorderLayout(8, 0));
    private final StatusBar statusBar = new StatusBar();
    private final JTabbedPane tabs = new JTabbedPane();
    private final List<Action> repositoryActions = new ArrayList<>();

    private MiniGit git;
    private Snapshot last;
    private boolean historyStale = true;

    public MainWindow() {
        super("MiniGit");
        setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
        setMinimumSize(new Dimension(1000, 650));
        setSize(1240, 800);
        setLocationRelativeTo(null);

        welcome = new WelcomePanel(this::chooseAndOpen, this::chooseAndCreate, this::openRepository);
        changes = new ChangesPanel(new ChangesPanel.Listener() {
            @Override
            public void fileSelected(Group group, String path) {
                showFileDiff(group, path);
            }

            @Override
            public void selectionCleared() {
                changesDiff.clear();
            }

            @Override
            public void stage(List<String> paths) {
                runOnRepository(g -> g.add(toPaths(g, paths)), r -> refresh());
            }

            @Override
            public void unstage(List<String> paths) {
                runOnRepository(g -> g.unstage(toPaths(g, paths)), r -> refresh());
            }

            @Override
            public void discard(String path) {
                if (Dialogs.confirm(MainWindow.this, "Discard Changes",
                        "Throw away your edits to " + path + "?\nThe file goes back to its staged version. "
                                + "This cannot be undone.")) {
                    runOnRepository(g -> g.restore(toPaths(g, List.of(path))), r -> refresh());
                }
            }

            @Override
            public void stopTracking(String path) {
                runOnRepository(g -> g.remove(toPaths(g, List.of(path)), true, false, false), r -> refresh());
            }

            @Override
            public void ignore(String path) {
                runOnRepository(g -> {
                    g.addIgnorePattern("/" + path);
                    return path;
                }, r -> refresh());
            }
        });
        commitPanel = new CommitPanel(this::commit);
        history = new HistoryPanel(this::loadCommitDetails, new HistoryPanel.CommitActions() {
            @Override
            public void checkout(String hash) {
                if (Dialogs.confirm(MainWindow.this, "Check Out Commit",
                        "Look at commit " + Hashing.shorten(hash) + "?\n\nYour files will show that commit's "
                                + "snapshot (\"detached HEAD\"). New commits made there belong to no branch "
                                + "unless you create one. Switch back with the branch box.")) {
                    runOnRepository(g -> g.checkout(hash), r -> refresh());
                }
            }

            @Override
            public void createBranchAt(String hash) {
                List<String> existing = last == null ? List.of() : last.branches().stream().map(RefInfo::name).toList();
                NewBranchDialog.show(MainWindow.this, Hashing.shorten(hash), existing)
                        .ifPresent(choice -> runOnRepository(g -> {
                            g.createBranch(choice.name(), Optional.of(hash));
                            return choice.switchToIt() ? g.checkout(choice.name()) : null;
                        }, r -> refresh()));
            }

            @Override
            public void createTagAt(String hash) {
                String name = javax.swing.JOptionPane.showInputDialog(MainWindow.this,
                        "Tag name for commit " + Hashing.shorten(hash) + " (e.g. v1.0):", "Create Tag",
                        javax.swing.JOptionPane.PLAIN_MESSAGE);
                if (name == null || name.isBlank()) {
                    return;
                }
                if (!MiniGit.isValidRefName(name.strip())) {
                    Dialogs.info(MainWindow.this, "Create Tag", "'" + name + "' is not a valid tag name. "
                            + "Use letters, digits, - _ . / only.");
                    return;
                }
                runOnRepository(g -> g.createTag(name.strip(), Optional.of(hash)), r -> refresh());
            }

            @Override
            public void resetTo(String hash) {
                String branch = last == null ? "HEAD" : last.status().branch().orElse("HEAD");
                if (Dialogs.confirm(MainWindow.this, "Reset Branch",
                        "Move " + branch + " back to commit " + Hashing.shorten(hash) + "?\n\n"
                                + "Your files are not changed: everything that differs shows up as unstaged "
                                + "changes. Commits after this one will no longer be on " + branch + ".")) {
                    runOnRepository(g -> g.reset(hash), r -> refresh());
                }
            }
        });
        objects = new ObjectsPanel(() -> git);
        branchSelector = new BranchSelector(this::checkout);

        content.add(welcome, WELCOME);
        content.add(buildRepositoryView(), REPOSITORY);
        getContentPane().add(content, BorderLayout.CENTER);
        setJMenuBar(buildMenus());

        // Pick up changes made outside the app, e.g. in an editor (FR-GUI-15).
        addWindowFocusListener(new WindowAdapter() {
            @Override
            public void windowGainedFocus(WindowEvent e) {
                if (git != null && !BackgroundTask.isBusy()) {
                    refresh();
                }
            }
        });
        showWelcome();
    }

    // ---------------------------------------------------------------- layout

    private JPanel buildRepositoryView() {
        JSplitPane filesAndDiff = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, changes, changesDiff);
        filesAndDiff.setResizeWeight(0.3);
        filesAndDiff.setDividerLocation(360);
        // Shown only while a merge waits for conflicts to be resolved.
        JButton abort = new JButton("Abort Merge");
        abort.addActionListener(e -> abortMerge());
        mergeBanner.setBackground(new Color(0xFFF8C5));
        mergeBanner.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, new Color(0xD4A72C)),
                BorderFactory.createEmptyBorder(6, 10, 6, 10)));
        mergeBanner.add(mergeLabel, BorderLayout.CENTER);
        mergeBanner.add(abort, BorderLayout.EAST);
        mergeBanner.setVisible(false);

        JPanel changesTab = new JPanel(new BorderLayout());
        changesTab.add(mergeBanner, BorderLayout.NORTH);
        changesTab.add(filesAndDiff, BorderLayout.CENTER);
        changesTab.add(commitPanel, BorderLayout.SOUTH);

        tabs.addTab("Changes", changesTab);
        tabs.addTab("History", history);
        tabs.addTab("Objects", objects);
        tabs.addChangeListener(e -> {
            if (tabs.getSelectedIndex() == HISTORY_TAB && historyStale) {
                loadHistory();
            } else if (tabs.getSelectedIndex() == OBJECTS_TAB) {
                browseObjects();
            }
        });

        JToolBar toolbar = new JToolBar();
        toolbar.setFloatable(false);
        toolbar.setBorder(BorderFactory.createEmptyBorder(4, 6, 4, 6));
        toolbar.add(new JButton(repositoryAction("Refresh", "Re-read the repository (F5)", this::refresh)));
        toolbar.add(new JButton(repositoryAction("New Branch…", "Create a branch here (Ctrl+B)", this::newBranch)));
        toolbar.add(new JButton(repositoryAction("Merge…", "Merge another branch into this one", this::merge)));
        toolbar.add(Box.createHorizontalGlue());
        toolbar.add(branchSelector);

        JPanel view = new JPanel(new BorderLayout());
        view.add(toolbar, BorderLayout.NORTH);
        view.add(tabs, BorderLayout.CENTER);
        view.add(statusBar, BorderLayout.SOUTH);
        return view;
    }

    private JMenuBar buildMenus() {
        JMenu file = new JMenu("File");
        file.setMnemonic(KeyEvent.VK_F);
        file.add(action("Open Repository…", KeyStroke.getKeyStroke(KeyEvent.VK_O, InputEvent.CTRL_DOWN_MASK),
                this::chooseAndOpen));
        file.add(action("Create Repository…", null, this::chooseAndCreate));
        file.add(repositoryAction("Close Repository", null, this::showWelcome));
        file.addSeparator();
        file.add(action("Exit", null, this::dispose));

        JMenu repository = new JMenu("Repository");
        repository.setMnemonic(KeyEvent.VK_R);
        Action refresh = repositoryAction("Refresh", null, this::refresh);
        refresh.putValue(Action.ACCELERATOR_KEY, KeyStroke.getKeyStroke(KeyEvent.VK_F5, 0));
        repository.add(refresh);
        repository.add(repositoryAction("Settings…", null, this::editIdentity));

        JMenu branch = new JMenu("Branch");
        branch.setMnemonic(KeyEvent.VK_B);
        Action newBranch = repositoryAction("New Branch…", null, this::newBranch);
        newBranch.putValue(Action.ACCELERATOR_KEY, KeyStroke.getKeyStroke(KeyEvent.VK_B, InputEvent.CTRL_DOWN_MASK));
        branch.add(newBranch);
        branch.add(repositoryAction("Merge…", null, this::merge));

        JMenu help = new JMenu("Help");
        help.add(action("About MiniGit", null, () -> Dialogs.info(this, "About MiniGit",
                "MiniGit " + minigit.Main.VERSION + "\nA lightweight version control system written in pure Java.")));

        JMenuBar bar = new JMenuBar();
        bar.add(file);
        bar.add(repository);
        bar.add(branch);
        bar.add(help);
        return bar;
    }

    private static Action action(String name, KeyStroke key, Runnable run) {
        Action action = new AbstractAction(name) {
            @Override
            public void actionPerformed(ActionEvent e) {
                run.run();
            }
        };
        if (key != null) {
            action.putValue(Action.ACCELERATOR_KEY, key);
        }
        return action;
    }

    /** An action that is only enabled while a repository is open. */
    private Action repositoryAction(String name, String tooltip, Runnable run) {
        Action action = action(name, null, run);
        action.putValue(Action.SHORT_DESCRIPTION, tooltip);
        repositoryActions.add(action);
        return action;
    }

    // ---------------------------------------------------------------- opening repositories

    /** Opens the repository containing {@code dir}, offering to create one if there is none. */
    public void openRepository(Path dir) {
        BackgroundTask.run(this, () -> MiniGit.open(dir), this::showRepository, error -> {
            if (error instanceof NotARepositoryException) {
                if (Dialogs.confirm(this, "No repository",
                        "There is no MiniGit repository in\n" + dir + "\n\nCreate a new one there?")) {
                    createRepository(dir);
                }
            } else {
                Dialogs.showError(this, error);
            }
        });
    }

    /** Opens {@code dir} only if it is inside a repository; otherwise stays on the welcome screen. */
    public void openIfRepository(Path dir) {
        BackgroundTask.run(this, () -> MiniGit.open(dir), this::showRepository, error -> {
        });
    }

    private void createRepository(Path dir) {
        BackgroundTask.run(this, () -> {
            MiniGit.init(dir);
            return MiniGit.open(dir);
        }, this::showRepository);
    }

    private void chooseAndOpen() {
        chooseDirectory("Open Repository", "Open").ifPresent(this::openRepository);
    }

    private void chooseAndCreate() {
        chooseDirectory("Create Repository in Folder", "Create").ifPresent(this::createRepository);
    }

    private Optional<Path> chooseDirectory(String title, String approve) {
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle(title);
        chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        chooser.setAcceptAllFileFilterUsed(false);
        List<Path> recentList = recent.list();
        if (git != null) {
            chooser.setCurrentDirectory(git.workTree().toFile());
        } else if (!recentList.isEmpty()) {
            chooser.setCurrentDirectory(recentList.get(0).toFile());
        }
        return chooser.showDialog(this, approve) == JFileChooser.APPROVE_OPTION
                ? Optional.of(chooser.getSelectedFile().toPath())
                : Optional.empty();
    }

    private void showRepository(MiniGit opened) {
        git = opened;
        last = null;
        historyStale = true;
        recent.add(opened.workTree());
        setTitle("MiniGit — " + opened.workTree());
        repositoryActions.forEach(a -> a.setEnabled(true));
        changesDiff.clear();
        commitPanel.clearMessage();
        objects.reset();
        tabs.setSelectedIndex(CHANGES_TAB);
        cards.show(content, REPOSITORY);
        refresh();
    }

    private void showWelcome() {
        git = null;
        setTitle("MiniGit");
        repositoryActions.forEach(a -> a.setEnabled(false));
        statusBar.clear();
        welcome.setRecent(recent.list());
        cards.show(content, WELCOME);
    }

    // ---------------------------------------------------------------- refreshing

    /** Re-reads status and branches, then updates every panel. */
    private void refresh() {
        runOnRepository(g -> new Snapshot(g.status(), g.branches()), this::apply);
    }

    private void apply(Snapshot snapshot) {
        last = snapshot;
        StatusResult status = snapshot.status();
        Optional<Group> selected = changes.update(status);
        if (selected.isPresent()) {
            showFileDiff(selected.get(), changes.selectedPath().orElseThrow());
        } else {
            changesDiff.clear();
        }
        commitPanel.setStagedCount(status.staged().size());
        commitPanel.setMerging(status.merge().isPresent(), status.merge().map(StatusResult.MergeInfo::message).orElse(""));
        branchSelector.setBranches(snapshot.branches(), status.branch(), status.headCommit());
        statusBar.show(status);
        showMergeBanner(status.merge());

        historyStale = true;
        if (tabs.getSelectedIndex() == HISTORY_TAB) {
            loadHistory();
        } else if (tabs.getSelectedIndex() == OBJECTS_TAB) {
            browseObjects();
        }
    }

    private void showMergeBanner(Optional<StatusResult.MergeInfo> merge) {
        mergeBanner.setVisible(merge.isPresent());
        merge.ifPresent(m -> mergeLabel.setText(m.conflicts().isEmpty()
                ? "<html><b>Merging.</b> All conflicts are resolved — press <b>Commit Merge</b> to finish.</html>"
                : "<html><b>Merging.</b> " + m.conflicts().size() + " file(s) still have conflicts (marked <b>C</b>): "
                + Theme.html(String.join(", ", m.conflicts())) + ". Edit each file to remove the "
                + "&lt;&lt;&lt;&lt;&lt;&lt;&lt; ======= &gt;&gt;&gt;&gt;&gt;&gt;&gt; markers, "
                + "tick it to stage, then commit.</html>"));
    }

    /** Shows the Objects tab for the commit selected in History, or HEAD. */
    private void browseObjects() {
        Optional<String> commit = history.selectedHash().or(() -> last == null ? Optional.empty() : last.status().headCommit());
        commit.ifPresent(objects::browse);
    }

    private void loadHistory() {
        historyStale = false;
        runOnRepository(g -> new History(g.log(HISTORY_LIMIT), g.refLabels()),
                h -> history.setCommits(h.commits(), h.labels()));
    }

    private void loadCommitDetails(String hash) {
        runOnRepository(g -> g.show(hash), history::showDetails);
    }

    private void showFileDiff(Group group, String path) {
        MiniGit g = git;
        if (g == null) {
            return;
        }
        String where = switch (group) {
            case STAGED -> "staged";
            case UNSTAGED -> "not staged";
            case UNTRACKED -> "new file";
        };
        BackgroundTask.run(this, () -> switch (group) {
            case STAGED -> g.diffStaged(path);
            case UNSTAGED -> g.diffUnstaged(path);
            case UNTRACKED -> Optional.of(g.diffUntracked(path));
        }, (Optional<FileDiff> diff) -> {
            // Ignore results for a file that is no longer selected.
            if (g == git && changes.selectedPath().equals(Optional.of(path))) {
                diff.ifPresentOrElse(d -> changesDiff.showDiff(path + "  —  " + where, d),
                        () -> changesDiff.showMessage("No changes in " + path));
            }
        }, error -> changesDiff.showMessage(error.getMessage()));
    }

    // ---------------------------------------------------------------- actions

    private void commit(String message) {
        runOnRepository(g -> g.getConfig("user.name"), name -> {
            if (name.isPresent()) {
                runOnRepository(g -> g.commit(message), r -> committed());
                return;
            }
            IdentityDialog.show(this, "Before your first commit, tell MiniGit who you are.<br>"
                            + "Your name is recorded on every commit.",
                    new IdentityDialog.Identity("", "")).ifPresent(id -> runOnRepository(g -> {
                        saveIdentity(g, id);
                        return g.commit(message);
                    }, r -> committed()));
        });
    }

    private void committed() {
        commitPanel.clearMessage();
        refresh();
    }

    private void checkout(String branch) {
        runOnRepository(g -> g.checkout(branch), r -> refresh(), error -> {
            branchSelector.revert();
            Dialogs.showError(this, error);
            refresh();
        });
    }

    private void newBranch() {
        if (last == null) {
            return;
        }
        Optional<String> head = last.status().headCommit();
        List<String> existing = last.branches().stream().map(RefInfo::name).toList();
        String start = head.map(h -> Hashing.shorten(h) + " on " + last.status().branch().orElse("detached HEAD"))
                .orElse("the first commit");
        NewBranchDialog.show(this, start, existing).ifPresent(choice -> runOnRepository(g -> {
            // Before the first commit there is nothing to point a second branch at, so switch.
            if (choice.switchToIt() || head.isEmpty()) {
                return g.checkoutNewBranch(choice.name());
            }
            return g.createBranch(choice.name(), Optional.empty());
        }, r -> refresh()));
    }

    /** Asks which branch to merge in, runs the merge, and explains the outcome (FR-GUI-19). */
    private void merge() {
        if (last == null) {
            return;
        }
        Optional<String> current = last.status().branch();
        List<String> others = last.branches().stream().map(RefInfo::name)
                .filter(n -> !current.equals(Optional.of(n))).toList();
        if (others.isEmpty()) {
            Dialogs.info(this, "Merge", "There is no other branch to merge. Create one with New Branch… first.");
            return;
        }
        JComboBox<String> choice = new JComboBox<>(others.toArray(String[]::new));
        JPanel panel = new JPanel(new BorderLayout(0, 6));
        panel.add(new JLabel("Merge this branch into " + current.orElse("the detached HEAD") + ":"), BorderLayout.NORTH);
        panel.add(choice, BorderLayout.CENTER);
        if (javax.swing.JOptionPane.showConfirmDialog(this, panel, "Merge Branch", javax.swing.JOptionPane.OK_CANCEL_OPTION,
                javax.swing.JOptionPane.PLAIN_MESSAGE) != javax.swing.JOptionPane.OK_OPTION) {
            return;
        }
        String name = (String) choice.getSelectedItem();
        String into = current.orElse("HEAD");
        runOnRepository(g -> g.merge(name), result -> {
            refresh();
            switch (result.kind()) {
                case UP_TO_DATE -> Dialogs.info(this, "Merge", "Already up to date: " + into
                        + " already contains everything from " + name + ".");
                case FAST_FORWARD -> Dialogs.info(this, "Merge", "Fast-forward: " + into + " had no new commits of "
                        + "its own, so it simply moved forward to " + name + " ("
                        + Hashing.shorten(result.commit().orElseThrow()) + ", " + result.filesChanged() + " file(s) updated).");
                case MERGED -> Dialogs.info(this, "Merge", "Merged " + name + " into " + into + ".\n\nBoth branches had "
                        + "new commits, so MiniGit combined them file by file and created merge commit "
                        + Hashing.shorten(result.commit().orElseThrow()) + " with two parents.");
                case CONFLICTS -> {
                    tabs.setSelectedIndex(CHANGES_TAB);
                    Dialogs.warn(this, "Merge Conflicts", "Both branches changed the same lines in:\n\n  "
                            + String.join("\n  ", result.conflicts())
                            + "\n\nThose files now contain both versions between <<<<<<<, =======, and >>>>>>> "
                            + "markers. Edit each file to keep what you want, remove the markers, tick it to "
                            + "stage it, then press Commit Merge. Or press Abort Merge to undo.");
                }
            }
        });
    }

    private void abortMerge() {
        if (Dialogs.confirm(this, "Abort Merge", "Undo the merge and put every file back as it was before?")) {
            runOnRepository(g -> {
                g.abortMerge();
                return true;
            }, r -> {
                commitPanel.clearMessage();
                refresh();
            });
        }
    }

    private void editIdentity() {
        runOnRepository(g -> new IdentityDialog.Identity(g.getConfig("user.name").orElse(""),
                        g.getConfig("user.email").orElse("")),
                current -> IdentityDialog.show(this, "The name and email recorded on your commits.", current)
                        .ifPresent(id -> runOnRepository(g -> {
                            saveIdentity(g, id);
                            return id;
                        }, r -> {
                        })));
    }

    private static void saveIdentity(MiniGit g, IdentityDialog.Identity id) {
        g.setConfig("user.name", id.name());
        if (id.email().isEmpty()) {
            g.unsetConfig("user.email");
        } else {
            g.setConfig("user.email", id.email());
        }
    }

    // ---------------------------------------------------------------- helpers

    /** A repository operation, given the repository it should act on. */
    private interface RepoCall<T> {
        T call(MiniGit git) throws Exception;
    }

    private <T> void runOnRepository(RepoCall<T> work, Consumer<T> onSuccess) {
        runOnRepository(work, onSuccess, error -> Dialogs.showError(this, error));
    }

    /**
     * Runs {@code work} on the open repository in the background. The result is
     * dropped if the user has since closed or switched repositories.
     */
    private <T> void runOnRepository(RepoCall<T> work, Consumer<T> onSuccess, Consumer<Throwable> onFailure) {
        MiniGit g = git;
        if (g == null) {
            return;
        }
        BackgroundTask.run(this, () -> work.call(g), result -> {
            if (g == git) {
                onSuccess.accept(result);
            }
        }, error -> {
            if (g == git) {
                onFailure.accept(error);
            }
        });
    }

    private static List<Path> toPaths(MiniGit g, List<String> paths) {
        return paths.stream().map(p -> g.workTree().resolve(p)).toList();
    }
}
