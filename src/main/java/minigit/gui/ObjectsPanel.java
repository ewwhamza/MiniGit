package minigit.gui;

import minigit.api.MiniGit;
import minigit.model.ObjectType;
import minigit.model.RawObject;
import minigit.model.TreeEntry;
import minigit.gui.util.BackgroundTask;
import minigit.gui.util.Theme;
import minigit.util.Hashing;

import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTextArea;
import javax.swing.JTree;
import javax.swing.event.TreeExpansionEvent;
import javax.swing.event.TreeWillExpandListener;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;
import java.awt.BorderLayout;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.function.Supplier;

/**
 * The Objects tab (FR-GUI-17): browses a commit's snapshot as the object store
 * sees it. The commit points to a root tree, trees point to subtrees and blobs,
 * and selecting any node shows its hash, where it is stored, and its content.
 */
public final class ObjectsPanel extends JPanel {

    /** What a tree node stands for. */
    private record Node(ObjectType type, String hash, String name) {
        @Override
        public String toString() {
            return switch (type) {
                case COMMIT -> "commit " + Hashing.shorten(hash) + "  " + name;
                case TREE -> (name.isEmpty() ? "(root folder)" : name + "/") + "    tree " + Hashing.shorten(hash);
                case BLOB -> name + "    blob " + Hashing.shorten(hash);
            };
        }
    }

    private static final String LOADING = "Loading…";

    private final Supplier<MiniGit> repository;
    private final DefaultTreeModel model = new DefaultTreeModel(new DefaultMutableTreeNode("No commit selected"));
    private final JTree tree = new JTree(model);
    private final JTextArea content = new JTextArea();
    private final JLabel heading = new JLabel(" ");
    private String shownCommit;

    public ObjectsPanel(Supplier<MiniGit> repository) {
        super(new BorderLayout());
        this.repository = repository;

        heading.setBorder(BorderFactory.createEmptyBorder(6, 8, 6, 8));
        add(heading, BorderLayout.NORTH);

        tree.setRootVisible(true);
        tree.setShowsRootHandles(true);
        tree.addTreeWillExpandListener(new TreeWillExpandListener() {
            @Override
            public void treeWillExpand(TreeExpansionEvent event) {
                loadChildren((DefaultMutableTreeNode) event.getPath().getLastPathComponent());
            }

            @Override
            public void treeWillCollapse(TreeExpansionEvent event) {
            }
        });
        tree.addTreeSelectionListener(e -> {
            DefaultMutableTreeNode node = (DefaultMutableTreeNode) tree.getLastSelectedPathComponent();
            if (node != null && node.getUserObject() instanceof Node n) {
                showObject(n);
            }
        });

        content.setEditable(false);
        content.setFont(Theme.MONO);
        content.setBorder(BorderFactory.createEmptyBorder(6, 8, 6, 8));

        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, new JScrollPane(tree), new JScrollPane(content));
        split.setResizeWeight(0.35);
        split.setDividerLocation(380);
        add(split, BorderLayout.CENTER);
    }

    /** Shows the snapshot of {@code commit}, unless it is already showing. */
    public void browse(String commit) {
        MiniGit g = repository.get();
        if (g == null || commit.equals(shownCommit)) {
            return;
        }
        shownCommit = commit;
        record Root(RawObject commit, String tree, List<TreeEntry> entries) {
        }
        BackgroundTask.run(this, () -> {
            String treeHash = g.treeOf(commit);
            return new Root(g.readObject(commit), treeHash, g.treeEntries(treeHash));
        }, root -> {
            String text = new String(root.commit().content(), StandardCharsets.UTF_8);
            String summary = text.substring(text.indexOf("\n\n") + 2).lines().findFirst().orElse("");
            DefaultMutableTreeNode commitNode = new DefaultMutableTreeNode(new Node(ObjectType.COMMIT, commit, summary));
            DefaultMutableTreeNode treeNode = new DefaultMutableTreeNode(new Node(ObjectType.TREE, root.tree(), ""));
            addEntries(treeNode, root.entries());
            commitNode.add(treeNode);
            model.setRoot(commitNode);
            tree.expandPath(new TreePath(treeNode.getPath()));
            tree.setSelectionPath(new TreePath(commitNode.getPath()));
            heading.setText("<html>Snapshot of commit <b>" + Hashing.shorten(commit) + "</b>. "
                    + "A commit points to a tree, trees point to subtrees and blobs. "
                    + "Files with identical content share one blob. Select a commit in History to browse another.</html>");
        });
    }

    /** Forgets the shown commit, e.g. when another repository is opened. */
    public void reset() {
        shownCommit = null;
        model.setRoot(new DefaultMutableTreeNode("No commit selected"));
        content.setText("");
        heading.setText(" ");
    }

    private void loadChildren(DefaultMutableTreeNode node) {
        if (node.getChildCount() != 1 || !LOADING.equals(((DefaultMutableTreeNode) node.getChildAt(0)).getUserObject())) {
            return;
        }
        MiniGit g = repository.get();
        Node n = (Node) node.getUserObject();
        BackgroundTask.run(this, () -> g.treeEntries(n.hash()), entries -> {
            node.removeAllChildren();
            addEntries(node, entries);
            model.nodeStructureChanged(node);
        });
    }

    private static void addEntries(DefaultMutableTreeNode parent, List<TreeEntry> entries) {
        // Folders first, then files, each alphabetically (entries arrive sorted by name).
        for (boolean folders : new boolean[]{true, false}) {
            for (TreeEntry e : entries) {
                if (e.isDirectory() != folders) {
                    continue;
                }
                DefaultMutableTreeNode child = new DefaultMutableTreeNode(new Node(e.type(), e.hash(), e.name()));
                if (e.isDirectory()) {
                    child.add(new DefaultMutableTreeNode(LOADING));
                }
                parent.add(child);
            }
        }
    }

    private void showObject(Node node) {
        MiniGit g = repository.get();
        if (g == null) {
            return;
        }
        BackgroundTask.run(this, () -> g.readObject(node.hash()), raw -> {
            StringBuilder text = new StringBuilder();
            text.append(raw.type().tag()).append(' ').append(raw.hash()).append('\n');
            text.append("Stored at: ").append(g.objectPath(raw.hash())).append(" (zlib-compressed)\n");
            text.append("Size:      ").append(raw.size()).append(" bytes\n");
            text.append("\n──────── content ────────\n\n");
            if (isBinary(raw.content())) {
                text.append("(binary content, ").append(raw.size()).append(" bytes)");
            } else {
                text.append(new String(raw.content(), StandardCharsets.UTF_8));
            }
            content.setText(text.toString());
            content.setCaretPosition(0);
        });
    }

    private static boolean isBinary(byte[] data) {
        for (int i = 0; i < Math.min(data.length, 8000); i++) {
            if (data[i] == 0) {
                return true;
            }
        }
        return false;
    }
}
