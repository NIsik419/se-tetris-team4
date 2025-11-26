package component.sidebar;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.GradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.BasicStroke;
import java.util.List;

import javax.swing.BorderFactory;
import javax.swing.JPanel;

import blocks.Block;
import component.ColorBlindPalette;
import component.items.ItemBlock;
import component.items.LineClearItem;
import component.items.WeightItem;
import component.items.SpinLockItem;
import component.items.ColorBombItem;
import component.items.LightningItem;

public class NextBlockPanel extends JPanel {
    private char[][] shape; // maintained
    private Block block;    // maintained
    private final int box;
    private ColorBlindPalette.Mode colorMode = ColorBlindPalette.Mode.NORMAL;

    public NextBlockPanel(int sizePx) {
        this.box = sizePx;
        Dimension d = new Dimension(box, box);
        setPreferredSize(new Dimension(box, box));
        setMinimumSize(d);
        setMaximumSize(d); 
        setBackground(new Color(0x191E28));
        setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(0x303540), 2),
                BorderFactory.createEmptyBorder(6, 10, 6, 10)));
    }

    public void setShape(char[][] s) {
        this.shape = s;
        this.block = null;
        repaint();
    }

    public void setBlock(Block b) {
        this.block = b;
        this.shape = null;
        repaint();
    }

    public void setBlocks(List<Block> blocks) {
        if (blocks == null || blocks.isEmpty()) {
            this.block = null;
        } else {
            this.block = blocks.get(0);
        }
        this.shape = null;
        repaint();
    }

    public void setColorMode(ColorBlindPalette.Mode mode) {
        this.colorMode = mode;
        repaint();
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        int w = getWidth();
        int h = getHeight();
        
        // 최소 8픽셀은 보장
        int cellW = Math.max(8, (w - 20) / 4);
        int cellH = Math.max(8, (h - 20) / 4);
        int cell = Math.min(cellW, cellH);

        int offX = (w - cell * 4) / 2;
        int offY = (h - cell * 4) / 2;

        // inner board
        g2.setColor(new Color(0x232937));
        g2.fillRoundRect(6, 6, getWidth() - 12, getHeight() - 12, 16, 16);

        // 1) 블록 기반 렌더링
        if (block != null) {
            drawBlock(g2, cell, offX, offY);
        }

        // 2) shape 기반 렌더링
        else if (shape != null) {
            drawShape(g2, cell, offX, offY);
        }

        g2.dispose();
    }

    /** Block 기반 렌더링 */
    private void drawBlock(Graphics2D g2, int cell, int offX, int offY) {
        int bw = block.width();
        int bh = block.height();

        int ox = offX + (4 - bw) / 2 * cell;
        int oy = offY + (4 - bh) / 2 * cell;

        for (int r = 0; r < bh; r++) {
            for (int c = 0; c < bw; c++) {
                if (block.getShape(c, r) == 1) {
                    int x = ox + c * cell + 2;
                    int y = oy + r * cell + 2;
                    int s = cell - 4;

                    Color base = ColorBlindPalette.convert(block.getColor(), colorMode);
                    g2.setPaint(new GradientPaint(x, y, base.brighter(), x, y + s, base.darker()));
                    g2.fillRoundRect(x, y, s, s, 10, 10);

                    // 아이템 심볼 표시
                    if (block instanceof ItemBlock item) {
                        drawItemSymbol(g2, item, x, y, s);
                    }
                }
            }
        }
    }

    /** shape 기반 렌더링 */
    private void drawShape(Graphics2D g2, int cell, int offX, int offY) {
        for (int r = 0; r < shape.length; r++) {
            for (int c = 0; c < shape[r].length; c++) {
                if (shape[r][c] != ' ') {
                    int x = offX + c * cell + 2;
                    int y = offY + r * cell + 2;
                    int s = cell - 4;

                    Color base = new Color(0xFFD764);
                    g2.setPaint(new GradientPaint(x, y, base.brighter(), x, y + s, base.darker()));
                    g2.fillRoundRect(x, y, s, s, 10, 10);
                }
            }
        }
    }

    /** 아이템 심볼 */
    private void drawItemSymbol(Graphics2D g2, ItemBlock item, int px, int py, int size) {
        g2.setFont(new Font("Segoe UI Emoji", Font.BOLD, 18));
        FontMetrics fm = g2.getFontMetrics();

        String symbol = switch (item) {
            case LineClearItem l -> "L";
            case WeightItem w -> "W";
            case SpinLockItem s -> SpinLockItem.getSymbol();
            case ColorBombItem b -> "💥";
            case LightningItem l -> "⚡";
            default -> null;
        };

        // 원형 강조(💥, ⚡)
        if (item instanceof ColorBombItem) {
            g2.setColor(new Color(255, 220, 100, 150));
            g2.setStroke(new BasicStroke(2f));
            g2.drawOval(px + 3, py + 3, size - 6, size - 6);
        } else if (item instanceof LightningItem) {
            g2.setColor(new Color(100, 180, 255, 150));
            g2.setStroke(new BasicStroke(2f));
            g2.drawOval(px + 3, py + 3, size - 6, size - 6);
        }

        // 텍스트 심볼
        if (symbol != null) {
            g2.setColor(Color.BLACK);
            int tx = px + (size - fm.stringWidth(symbol)) / 2;
            int ty = py + (size + fm.getAscent() - fm.getDescent()) / 2;
            g2.drawString(symbol, tx, ty);
        }
    }
}
