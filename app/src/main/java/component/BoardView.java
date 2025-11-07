package component;

import logic.BoardLogic;
import blocks.Block;
import component.items.ColorBombItem;
import component.items.ItemBlock;
import component.items.LightningItem;
import component.items.LineClearItem;
import component.items.SpinLockItem;
import component.items.WeightItem;

import javax.swing.*;
import java.awt.*;
import logic.MovementService;

/**
 * BoardView - 실제 테트리스 블록이 그려지는 보드.
 * 10x20 비율 고정 + 셀 크기 자동 계산 (대전 모드에서도 동일 비율 유지)
 */
public class BoardView extends JPanel {
    private final BoardLogic logic;
    private final MovementService move;;
    private ColorBlindPalette.Mode colorMode = ColorBlindPalette.Mode.NORMAL;

    public static final int WIDTH = BoardLogic.WIDTH;
    public static final int HEIGHT = BoardLogic.HEIGHT;
    private static final int MAX_HEIGHT = 700; // 전체 높이 제한

    public BoardView(BoardLogic logic) {
        this.logic = logic;
        this.move = new MovementService(logic.getState());
        setBackground(new Color(25, 30, 42));
        setBorder(BorderFactory.createLineBorder(new Color(40, 45, 60), 3));
    }

    @Override
    public Dimension getPreferredSize() {
        // 셀 크기를 프레임 크기에 따라 자동 조정 (최대 700px 높이 기준)
        int cellSize = Math.min(MAX_HEIGHT / HEIGHT, 35); // 35 이상 커지지 않게 제한
        return new Dimension(WIDTH * cellSize, HEIGHT * cellSize);
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        // === 고정 블록 ===
        Color[][] grid = logic.getBoard();
        Color[][] fade = logic.getFadeLayer();

        for (int y = 0; y < BoardLogic.HEIGHT; y++) {
            for (int x = 0; x < BoardLogic.WIDTH; x++) {
                if (grid[y][x] != null)
                    drawCell(g2, x, y, ColorBlindPalette.convert(grid[y][x], colorMode));
                else if (fade[y][x] != null)
                    drawFade(g2, x, y);
            }
        }

        // === 현재 블록 ===
        Block curr = logic.getCurr();
        if (curr != null)
            drawCurrentBlock(g2, curr);

        drawGhostBlock(g2);
        g2.dispose();

    }

    private void drawFade(Graphics2D g2, int x, int y) {
        int CELL_SIZE = 35;
        int CELL_GAP = 2;
        int px = x * CELL_SIZE + CELL_GAP;
        int py = y * CELL_SIZE + CELL_GAP;
        int size = CELL_SIZE - CELL_GAP * 2;
        int shake = (int) (Math.random() * 4 - 2);
        px += shake;
        py += shake;
        g2.setColor(new Color(255, 255, 255, 180));
        g2.setStroke(new BasicStroke(3));
        g2.drawRoundRect(px, py, size, size, 8, 8);
    }

    private void drawCell(Graphics2D g2, int x, int y, Color color) {
        int CELL_SIZE = 35, CELL_GAP = 2, ARC = 8;
        int px = x * CELL_SIZE + CELL_GAP;
        int py = y * CELL_SIZE + CELL_GAP;
        int size = CELL_SIZE - CELL_GAP * 2;
        g2.setColor(color);
        g2.fillRoundRect(px, py, size, size, ARC, ARC);
        g2.setColor(new Color(255, 255, 255, 60));
        g2.fillRoundRect(px, py, size, size / 3, ARC, ARC);
        g2.setColor(new Color(0, 0, 0, 40));
        g2.fillRoundRect(px, py + size * 2 / 3, size, size / 3, ARC, ARC);
    }

    // 예측 낙하 위치 그리기
    private void drawGhostBlock(Graphics2D g2) {
        Block curr = logic.getCurr();
        if (curr == null)
            return;

        int bx = logic.getX();
        int by = logic.getY();
        int ghostY = move.getGhostY(curr); // BoardLogic에 getGhostY() 메서드 추가 필요

        g2.setColor(new Color(200, 200, 200, 120));
        Stroke oldStroke = g2.getStroke();
        g2.setStroke(new BasicStroke(2f));

        for (int j = 0; j < curr.height(); j++) {
            for (int i = 0; i < curr.width(); i++) {
                if (curr.getShape(i, j) == 1) {
                    int x = (bx + i) * 35 + 2;
                    int y = (ghostY + j) * 35 + 2;
                    int size = 35 - 4;
                    g2.drawRect(x, y, size, size);
                }
            }
        }

        g2.setStroke(oldStroke);
    }

    private void drawCurrentBlock(Graphics2D g2, Block block) {
        int bx = logic.getX(), by = logic.getY();

        for (int j = 0; j < block.height(); j++) {
            for (int i = 0; i < block.width(); i++) {
                if (block.getShape(i, j) == 1) {
                    int x = bx + i;
                    int y = by + j;
                    Color color = ColorBlindPalette.convert(block.getColor(), colorMode);
                    drawCell(g2, x, y, color);

                    // 🟨 아이템 블록이면 문자/아이콘 표시
                    if (block instanceof ItemBlock item) {
                        drawItemSymbol(g2, item, x, y);
                    }
                }
            }
        }
    }

    /** 아이템 블록 문자/아이콘 오버레이 */
    private void drawItemSymbol(Graphics2D g2, ItemBlock item, int gridX, int gridY) {
        int CELL_SIZE = 35, CELL_GAP = 2;
        int px = gridX * CELL_SIZE + CELL_GAP;
        int py = gridY * CELL_SIZE + CELL_GAP;
        int size = CELL_SIZE - CELL_GAP * 2;

        g2.setFont(new Font("Segoe UI Emoji", Font.BOLD, 18)); // 이모지 폰트 지원
        FontMetrics fm = g2.getFontMetrics();

        String symbol = switch (item) {
            case LineClearItem l -> "L";
            case WeightItem w -> "W";
            case SpinLockItem s -> SpinLockItem.getSymbol(); // 예: "S" 또는 "🔒"
            case ColorBombItem b -> "💥";
            case LightningItem l -> "⚡";
            default -> null;
        };

        if (symbol != null) {
            g2.setColor(Color.BLACK);
            int tx = px + (size - fm.stringWidth(symbol)) / 2;
            int ty = py + (size + fm.getAscent() - fm.getDescent()) / 2;
            g2.drawString(symbol, tx, ty);
        }
    }

    public void setColorMode(ColorBlindPalette.Mode mode) {
        this.colorMode = mode;
        System.out.println("[DEBUG] BoardView.setColorMode() → " + mode);
        repaint();
    }

    public ColorBlindPalette.Mode getColorMode() {
        return colorMode;
    }
}
