package component;

import blocks.Block;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

public class NextPreviewPanel extends JPanel {
    private static final Color BG_PANEL = new Color(30, 35, 50);   
    private static final Color TILE_BG  = new Color(60, 65, 80);   
    private int maxCount = 3;
    private final List<Block> blocks = new ArrayList<>();

    public NextPreviewPanel() {
        setOpaque(true);
        setBackground(new Color(25, 30, 42)); 
        setLayout(new GridLayout(0, 1, 0, 10)); 
        setAlignmentX(Component.CENTER_ALIGNMENT); 
    }

    public void setMaxCount(int n) {
        this.maxCount = Math.max(1, n);
    }

    public void setBlocks(List<Block> nextBlocks) {
        removeAll();
        blocks.clear();

        if (nextBlocks != null) {
            int n = Math.min(maxCount, nextBlocks.size());
            blocks.addAll(nextBlocks.subList(0, n));
        }

        // GridLayout 행 수를 blocks.size()로 고정
        setLayout(new GridLayout(blocks.size(), 1, 0, 10));

        for (Block b : blocks) {
            add(createCell(b));
        }

        revalidate();
        repaint();
    }

    private JComponent createCell(Block b) {
        JPanel container = new JPanel(new BorderLayout());
        container.setBackground(BG_PANEL);
        container.setPreferredSize(new Dimension(120, 70)); // 원본과 동일

        JPanel blockPanel = new JPanel() {
            @Override protected void paintComponent(Graphics g) {
                super.paintComponent(g);

                Graphics2D g2 = (Graphics2D) g;
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

                int blockSize = 18; 
                int totalW = b.width() * blockSize;
                int totalH = b.height() * blockSize;
                int offX = (getWidth()  - totalW) / 2;
                int offY = (getHeight() - totalH) / 2;

                g2.setColor(TILE_BG);
                g2.fillRoundRect(offX - 5, offY - 5, totalW + 10, totalH + 10, 8, 8);

                for (int j = 0; j < b.height(); j++) {
                    for (int i = 0; i < b.width(); i++) {
                        if (b.getShape(i, j) == 1) {
                            int x = offX + i * blockSize;
                            int y = offY + j * blockSize;
                            int s = blockSize - 2;

                            g2.setColor(b.getColor());
                            g2.fillRoundRect(x, y, s, s, 4, 4);

                            g2.setColor(new Color(255, 255, 255, 60));
                            g2.fillRoundRect(x, y, s, s / 3, 4, 4);

                            g2.setColor(new Color(0, 0, 0, 40));
                            g2.fillRoundRect(x, y + (s * 2) / 3, s, s / 3, 4, 4);
                        }
                    }
                }
            }
        };
        blockPanel.setBackground(BG_PANEL);
        blockPanel.setPreferredSize(new Dimension(120, 80)); 
        container.add(blockPanel, BorderLayout.CENTER);
        return container;
    }
}
