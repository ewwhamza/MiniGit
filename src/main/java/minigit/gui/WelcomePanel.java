package minigit.gui;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.ListSelectionModel;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GridBagLayout;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.nio.file.Path;
import java.util.List;
import java.util.function.Consumer;

/** First screen: open or create a repository, or pick a recent one (FR-GUI-2). */
public final class WelcomePanel extends JPanel {

    private final DefaultListModel<Path> recent = new DefaultListModel<>();
    private final JPanel recentPanel = new JPanel();

    public WelcomePanel(Runnable onOpen, Runnable onCreate, Consumer<Path> onOpenRecent) {
        super(new GridBagLayout());
        setBackground(Color.WHITE);

        JPanel column = new JPanel();
        column.setOpaque(false);
        column.setLayout(new BoxLayout(column, BoxLayout.Y_AXIS));

        JLabel title = new JLabel("MiniGit");
        title.setFont(title.getFont().deriveFont(Font.BOLD, 40f));
        JLabel subtitle = new JLabel("A version control system written in pure Java");
        subtitle.setFont(subtitle.getFont().deriveFont(15f));
        subtitle.setForeground(new Color(0x57606A));

        JButton open = new JButton("Open Repository…");
        open.addActionListener(e -> onOpen.run());
        JButton create = new JButton("Create Repository…");
        create.addActionListener(e -> onCreate.run());
        JPanel buttons = new JPanel();
        buttons.setOpaque(false);
        buttons.add(open);
        buttons.add(create);

        JList<Path> list = new JList<>(recent);
        list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        list.setVisibleRowCount(5);
        list.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2 && list.getSelectedValue() != null) {
                    onOpenRecent.accept(list.getSelectedValue());
                }
            }
        });
        list.addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                if (e.getKeyCode() == KeyEvent.VK_ENTER && list.getSelectedValue() != null) {
                    onOpenRecent.accept(list.getSelectedValue());
                }
            }
        });
        JScrollPane scroll = new JScrollPane(list);
        scroll.setPreferredSize(new Dimension(460, 110));
        recentPanel.setOpaque(false);
        recentPanel.setLayout(new BoxLayout(recentPanel, BoxLayout.Y_AXIS));
        JLabel recentTitle = new JLabel("Recent repositories (double-click to open)");
        recentTitle.setBorder(BorderFactory.createEmptyBorder(0, 0, 4, 0));
        recentPanel.add(recentTitle);
        recentPanel.add(scroll);

        for (JComponent c : List.<JComponent>of(title, subtitle, buttons, recentPanel)) {
            c.setAlignmentX(Component.CENTER_ALIGNMENT);
        }
        column.add(title);
        column.add(Box.createVerticalStrut(4));
        column.add(subtitle);
        column.add(Box.createVerticalStrut(24));
        column.add(buttons);
        column.add(Box.createVerticalStrut(24));
        column.add(recentPanel);
        add(column);
    }

    public void setRecent(List<Path> paths) {
        recent.clear();
        recent.addAll(paths);
        recentPanel.setVisible(!paths.isEmpty());
    }
}
