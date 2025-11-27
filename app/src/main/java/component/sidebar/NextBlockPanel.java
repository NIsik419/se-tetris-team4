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
import java.awt.Polygon;
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
        if (block == null) return;

        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                            RenderingHints.VALUE_ANTIALIAS_ON);

        int w = getWidth();
        int h = getHeight();

        // 안쪽 여백 (테두리 빼고 실제로 블록 놓을 영역)
        int margin = 12;
        int innerX = margin;
        int innerY = margin;
        int innerW = w - margin * 2;
        int innerH = h - margin * 2;

        // 보통 Next 블록용 그리드는 4x4 많이 쓰니까 예시로 4
        int cols = 4;
        int rows = 4;

        int cell = Math.min(innerW / cols, innerH / rows);

        // 1) 블록 shape 에서 min/max x,y 구하기
        int minX = 99, minY = 99, maxX = -1, maxY = -1;
        for (int y = 0; y < block.height(); y++) {
            for (int x = 0; x < block.width(); x++) {
                if (block.getShape(x, y) == 1) {
                    if (x < minX) minX = x;
                    if (y < minY) minY = y;
                    if (x > maxX) maxX = x;
                    if (y > maxY) maxY = y;
                }
            }
        }

        int shapeCols = (maxX - minX + 1);
        int shapeRows = (maxY - minY + 1);

        int shapePixelW = shapeCols * cell;
        int shapePixelH = shapeRows * cell;

        // 2) 패널 안에서 중앙에 오도록 시작 좌표 계산
        int startX = innerX + (innerW - shapePixelW) / 2 - minX * cell;
        int startY = innerY + (innerH - shapePixelH) / 2 - minY * cell;

        // 3) 그릴 때 (x,y)에 이 startX/startY 더해주기
        Color color = ColorBlindPalette.convert(block.getColor(), colorMode);

        for (int by = 0; by < block.height(); by++) {
        for (int bx = 0; bx < block.width(); bx++) {
            if (block.getShape(bx, by) == 1) {
                int px = startX + bx * cell;
                int py = startY + by * cell;

                // 1) 블럭 한 칸 그리기
                drawCell(g2, px, py, cell, color);

                // 2) 아이템 심볼 그리기
                if (block instanceof LineClearItem lci) {
                    // LineClearItem은 특정 칸만 "핵심"이라 그 칸에만 심볼 표시
                    if (bx == lci.getLX() && by == lci.getLY()) {
                        drawItemSymbol(g2, lci, px, py, cell);
                    }
                } else if (block instanceof ItemBlock item) {
                    // 일반 아이템 블럭은 그냥 각 칸 위에 심볼
                    drawItemSymbol(g2, item, px, py, cell);
                }
            }
        }
    }

        g2.dispose();
    }

    // NextBlockPanel 안에서 사용할 버전
    private void drawCell(Graphics2D g2, int px, int py, int size, Color baseColor) {
        // int px, py 는 이미 "픽셀" 좌표
        // size 도 셀 크기 (cell) 그대로 사용

        // 중앙 정사각형 inset 비율 (살짝 줄여서 더 단단한 느낌)
        int inset = (int) (size * 0.22);
        int innerX = px + inset;
        int innerY = py + inset;
        int innerSize = size - inset * 2;

        // 🔹 색 계열: 대비를 확 줄여서 은은하게
        Color topColor    = lighten(baseColor, 0.15f);
        Color leftColor   = lighten(baseColor, 0.07f);
        Color rightColor  = darken(baseColor, 0.12f);
        Color bottomColor = darken(baseColor, 0.20f);
        Color centerColor = darken(baseColor, 0.03f);

        // ===== top facet =====
        Polygon top = new Polygon();
        top.addPoint(px,         py);
        top.addPoint(px + size,  py);
        top.addPoint(innerX + innerSize, innerY);
        top.addPoint(innerX, innerY);
        g2.setColor(topColor);
        g2.fillPolygon(top);

        // ===== bottom facet =====
        Polygon bottom = new Polygon();
        bottom.addPoint(innerX, innerY + innerSize);
        bottom.addPoint(innerX + innerSize, innerY + innerSize);
        bottom.addPoint(px + size, py + size);
        bottom.addPoint(px,       py + size);
        g2.setColor(bottomColor);
        g2.fillPolygon(bottom);

        // ===== left facet =====
        Polygon left = new Polygon();
        left.addPoint(px,       py);
        left.addPoint(innerX,   innerY);
        left.addPoint(innerX,   innerY + innerSize);
        left.addPoint(px,       py + size);
        g2.setColor(leftColor);
        g2.fillPolygon(left);

        // ===== right facet =====
        Polygon right = new Polygon();
        right.addPoint(innerX + innerSize, innerY);
        right.addPoint(px + size,          py);
        right.addPoint(px + size,          py + size);
        right.addPoint(innerX + innerSize, innerY + innerSize);
        g2.setColor(rightColor);
        g2.fillPolygon(right);

        // ===== 중앙 정사각형 =====
        g2.setColor(centerColor);
        g2.fillRect(innerX, innerY, innerSize, innerSize);

        // 바깥 테두리도 살짝만
        g2.setColor(new Color(0, 0, 0, 120));
        g2.drawRect(px, py, size, size);
    }

    // 색 더 밝게
    private Color lighten(Color c, float amount) {
        float r = c.getRed() / 255f;
        float g = c.getGreen() / 255f;
        float b = c.getBlue() / 255f;
        r = Math.min(1f, r + amount);
        g = Math.min(1f, g + amount);
        b = Math.min(1f, b + amount);
        return new Color(r, g, b);
    }

    // 색 더 어둡게
    private Color darken(Color c, float amount) {
        float r = c.getRed() / 255f;
        float g = c.getGreen() / 255f;
        float b = c.getBlue() / 255f;
        r = Math.max(0f, r - amount);
        g = Math.max(0f, g - amount);
        b = Math.max(0f, b - amount);
        return new Color(r, g, b);
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
