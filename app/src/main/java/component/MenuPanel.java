package component;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.util.function.Consumer;

public class MenuPanel extends JPanel {

    public enum MenuItem { START, SETTINGS, SCOREBOARD, EXIT }

    private final String title = "TETRIS";
    private final MenuItem[] items = {
            MenuItem.START, MenuItem.SETTINGS, MenuItem.SCOREBOARD, MenuItem.EXIT
    };
    private int selected = 0;

    public MenuPanel(Consumer<MenuItem> onSelect) {
        setFocusable(true);
        setBackground(Color.BLACK);

        // Key bindings
        InputMap im = getInputMap(WHEN_IN_FOCUSED_WINDOW);
        ActionMap am = getActionMap();

        im.put(KeyStroke.getKeyStroke("UP"), "up");
        im.put(KeyStroke.getKeyStroke("DOWN"), "down");
        im.put(KeyStroke.getKeyStroke("ENTER"), "enter");
        im.put(KeyStroke.getKeyStroke("ESCAPE"), "esc");

        am.put("up", new AbstractAction() {
            @Override public void actionPerformed(ActionEvent e) {
                selected = (selected - 1 + items.length) % items.length;
                repaint();
            }
        });
        am.put("down", new AbstractAction() {
            @Override public void actionPerformed(ActionEvent e) {
                selected = (selected + 1) % items.length;
                repaint();
            }
        });
        am.put("enter", new AbstractAction() {
            @Override public void actionPerformed(ActionEvent e) {
                onSelect.accept(items[selected]);
            }
        });
        am.put("esc", new AbstractAction() {
            @Override public void actionPerformed(ActionEvent e) {
                onSelect.accept(MenuItem.EXIT);
            }
        });

        setLayout(new GridBagLayout());
        var gb = new GridBagConstraints();
        gb.gridx = 0; gb.gridy = 0; gb.insets = new Insets(8, 16, 8, 16);

        JLabel titleLabel = new JLabel(title, SwingConstants.CENTER);
        titleLabel.setForeground(new Color(0x00E0FF));
        titleLabel.setFont(new Font(Font.MONOSPACED, Font.BOLD, 36));
        titleLabel.setBorder(BorderFactory.createEmptyBorder(16, 16, 24, 16));
        add(titleLabel, gb);

        gb.gridy = 1;
        add(Box.createVerticalStrut(260), gb); // spacer; items are custom-drawn
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);

        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        int w = getWidth();

        String[] labels = { "시작", "설정", "스코어보드", "종료" };
        int y = 200;
        for (int i = 0; i < labels.length; i++) {
            String text = (i == selected ? "▶ " : "   ") + labels[i];
            g2.setFont(new Font(Font.MONOSPACED, i == selected ? Font.BOLD : Font.PLAIN, 24));
            g2.setColor(i == selected ? new Color(0x00FF99) : new Color(0xCCCCCC));
            int textW = g2.getFontMetrics().stringWidth(text);
            g2.drawString(text, (w - textW) / 2, y);
            y += 40;
        }
        g2.dispose();
    }
}
