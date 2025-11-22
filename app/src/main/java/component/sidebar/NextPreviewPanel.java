package component.sidebar;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridLayout;
import java.awt.RenderingHints;
import java.util.ArrayList;
import java.util.List;

import javax.swing.JComponent;
import javax.swing.JPanel;

import blocks.Block;
import component.ColorBlindPalette;

public class NextPreviewPanel extends JPanel {
    private static final Color BG_PANEL = new Color(30, 35, 50);
    private static final Color TILE_BG = new Color(60, 65, 80);
    private int maxCount = 1;
    private final List<Block> blocks = new ArrayList<>();

    private ColorBlindPalette.Mode colorMode = ColorBlindPalette.Mode.NORMAL;

    public void setColorMode(ColorBlindPalette.Mode mode) {
        System.out.println("[NextPreviewPanel] colorMode=" + mode);
        this.colorMode = mode;
        repaint();
    }

    public NextPreviewPanel() {
        setOpaque(true);
        setBackground(new Color(25, 30, 42));
        setLayout(new GridLayout(0, 1, 0, 10));
        setAlignmentX(Component.CENTER_ALIGNMENT);
    }

    public void setMaxCount(int n) {
        this.maxCount = Math.max(1, n);
    }

    public void setBlocks(List<Block> blocks) {
       this.blocks.clear();
       if (blocks != null && !blocks.isEmpty()) {
            this.blocks.add(blocks.get(0));
        }
        repaint();
    }

    private JComponent createCell(Block b) {
        JPanel container = new JPanel(new BorderLayout());
        container.setBackground(BG_PANEL);
        container.setPreferredSize(new Dimension(110, 40));

        JPanel blockPanel = new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);

                Graphics2D g2 = (Graphics2D) g;
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

                int blockSize = 22;
                int totalW = b.width() * blockSize;
                int totalH = b.height() * blockSize;
                int offX = (getWidth() - totalW) / 2;
                int offY = (getHeight() - totalH) / 2;

                g2.setColor(TILE_BG);
                g2.fillRoundRect(offX - 3, offY - 3, totalW + 6, totalH + 6, 8, 8);

                for (int j = 0; j < b.height(); j++) {
                    for (int i = 0; i < b.width(); i++) {
                        if (b.getShape(i, j) == 1) {
                            int x = offX + i * blockSize;
                            int y = offY + j * blockSize;
                            int s = blockSize - 2;

                            Color base = ColorBlindPalette.convert(b.getColor(), colorMode);

                            g2.setColor(base);
                            g2.fillRoundRect(x, y, s, s, 4, 4);

                            g2.setColor(new Color(255, 255, 255, 60));
                            g2.fillRoundRect(x, y, s, s / 3, 4, 4);

                            g2.setColor(new Color(0, 0, 0, 40));
                            g2.fillRoundRect(x, y + (s * 2) / 3, s, s / 3, 4, 4);

                            // 각 칸마다 아이템 오버레이
                            if (b instanceof ItemBlock item) {
                                drawItemSymbol(g2, item, x, y, s);
                            }
                        }
                    }
                }
            }
        };
        blockPanel.setBackground(BG_PANEL);
        blockPanel.setPreferredSize(new Dimension(110, 40));
        container.add(blockPanel, BorderLayout.CENTER);
        return container;
    }

    /** NextPreviewPanel용 아이템 오버레이 - 각 칸마다 */
    private void drawItemSymbol(Graphics2D g2, ItemBlock item, int px, int py, int size) {
        g2.setFont(new Font("Segoe UI Emoji", Font.BOLD, 14));
        FontMetrics fm = g2.getFontMetrics();

        String symbol = switch (item) {
            case LineClearItem l -> "L";
            case WeightItem w -> "W";
            case SpinLockItem s -> SpinLockItem.getSymbol();
            case ColorBombItem b -> "💥";
            case LightningItem l -> "⚡";
            default -> null;
        };

        if (item instanceof ColorBombItem) {
            g2.setColor(new Color(255, 220, 100, 120));
            g2.setStroke(new BasicStroke(2f));
            g2.drawOval(px + 3, py + 3, size - 6, size - 6);
        } else if (item instanceof LightningItem) {
            g2.setColor(new Color(100, 180, 255, 140));
            g2.setStroke(new BasicStroke(2f));
            g2.drawOval(px + 3, py + 3, size - 6, size - 6);
        }

        if (symbol != null) {
            g2.setColor(Color.BLACK);
            int tx = px + (size - fm.stringWidth(symbol)) / 2;
            int ty = py + (size + fm.getAscent() - fm.getDescent()) / 2;
            g2.drawString(symbol, tx, ty);
        }
    }
}
