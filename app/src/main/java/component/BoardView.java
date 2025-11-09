package component;

import logic.BoardLogic;
import blocks.Block;
import component.items.*;
import javax.swing.*;
import java.awt.*;
import logic.MovementService;

public class BoardView extends JPanel {
    private final BoardLogic logic;
    private final MovementService move;
    private ColorBlindPalette.Mode colorMode = ColorBlindPalette.Mode.NORMAL;

    // === 상수 통일 (Board 기준) ===
    private static final int CELL_SIZE = 35;
    private static final int CELL_GAP = 2;
    private static final int ARC = 8;
    private static final int MAX_HEIGHT = 700;
    public static final int WIDTH = BoardLogic.WIDTH;
    public static final int HEIGHT = BoardLogic.HEIGHT;
    private static final Color GRID_LINE = new Color(50, 55, 70);
    private static final Color BG_GAME = new Color(25, 30, 42);

    public BoardView(BoardLogic logic) {
        this.logic = logic;
        this.move = new MovementService(logic.getState());
        setBackground(BG_GAME);
        setBorder(BorderFactory.createLineBorder(GRID_LINE, 3));
    }

    @Override
    public Dimension getPreferredSize() {
        int cellSize = Math.min(MAX_HEIGHT / HEIGHT, 35);
        return new Dimension(WIDTH * cellSize, HEIGHT * cellSize);
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setComposite(AlphaComposite.SrcOver.derive(0.9f));

        Color[][] grid = logic.getBoard();
        Color[][] fade = logic.getFadeLayer();

        // 디버그: fadeLayer 상태 확인
        int fadeCount = 0;
        for (int y = 0; y < BoardLogic.HEIGHT; y++) {
            for (int x = 0; x < BoardLogic.WIDTH; x++) {
                if (fade[y][x] != null) fadeCount++;
            }
        }
        if (fadeCount > 0) {
            System.out.println("[DEBUG] paintComponent() - fadeLayer has " + fadeCount + " non-null cells");
        }

        // ===배경 격자 ===
        g2.setColor(GRID_LINE);
        for (int r = 0; r <= BoardLogic.HEIGHT; r++)
            g2.drawLine(0, r * CELL_SIZE, BoardLogic.WIDTH * CELL_SIZE, r * CELL_SIZE);
        for (int c = 0; c <= BoardLogic.WIDTH; c++)
            g2.drawLine(c * CELL_SIZE, 0, c * CELL_SIZE, BoardLogic.HEIGHT * CELL_SIZE);

        // ===  고정 블록 먼저 그리기 ===
        for (int y = 0; y < BoardLogic.HEIGHT; y++) {
            for (int x = 0; x < BoardLogic.WIDTH; x++) {
                if (grid[y][x] != null) {
                    drawCell(g2, x, y, ColorBlindPalette.convert(grid[y][x], colorMode));
                }
            }
        }

        // ===  fadeLayer는 무조건 위에 덮어씌우기 ===
        for (int y = 0; y < BoardLogic.HEIGHT; y++) {
            for (int x = 0; x < BoardLogic.WIDTH; x++) {
                if (fade[y][x] != null) {
                    drawFade(g2, x, y, fade[y][x]);
                }
            }
        }

        // === Ghost 블록 ===
        drawGhostBlock(g2);

        // ===  현재 블록 ===
        Block curr = logic.getCurr();
        if (curr != null)
            drawCurrentBlock(g2, curr);

        g2.dispose();
    }

   
    private void drawFade(Graphics2D g2, int x, int y, Color fadeColor) {
        int px = x * CELL_SIZE + CELL_GAP;
        int py = y * CELL_SIZE + CELL_GAP;
        int size = CELL_SIZE - CELL_GAP * 2;

        // 배경을 꽉 채움 (화이트 플래시가 확실히 보임)
        g2.setColor(fadeColor);
        g2.fillRoundRect(px, py, size, size, ARC, ARC);

        // 
        int alpha = fadeColor.getAlpha();
        if (alpha > 150) {
            g2.setColor(new Color(255, 255, 255, Math.min(255, alpha + 50)));
            g2.setStroke(new BasicStroke(2));
            g2.drawRoundRect(px, py, size, size, ARC, ARC);
        }
    }

    /** 기본 셀 렌더링 */
    private void drawCell(Graphics2D g2, int x, int y, Color color) {
        int px = x * CELL_SIZE + CELL_GAP;
        int py = y * CELL_SIZE + CELL_GAP;
        int size = CELL_SIZE - CELL_GAP * 2;

        g2.setColor(color);
        g2.fillRoundRect(px, py, size, size, ARC, ARC);
        
        // 하이라이트
        g2.setColor(new Color(255, 255, 255, 60));
        g2.fillRoundRect(px, py, size, size / 3, ARC, ARC);
        
        // 그림자
        g2.setColor(new Color(0, 0, 0, 40));
        g2.fillRoundRect(px, py + size * 2 / 3, size, size / 3, ARC, ARC);
    }

    /** 유령 블록 (Ghost) */
    private void drawGhostBlock(Graphics2D g2) {
        Block curr = logic.getCurr();
        if (curr == null)
            return;

        int bx = logic.getX();
        int ghostY = move.getGhostY(curr);

        g2.setColor(new Color(200, 200, 200, 120));
        Stroke oldStroke = g2.getStroke();
        g2.setStroke(new BasicStroke(2f));

        for (int j = 0; j < curr.height(); j++) {
            for (int i = 0; i < curr.width(); i++) {
                if (curr.getShape(i, j) == 1) {
                    int x = (bx + i) * CELL_SIZE + CELL_GAP;
                    int y = (ghostY + j) * CELL_SIZE + CELL_GAP;
                    int size = CELL_SIZE - CELL_GAP * 2;
                    g2.drawRect(x, y, size, size);
                }
            }
        }

        g2.setStroke(oldStroke);
    }

    /** 현재 블록 + 아이템 효과 */
    private void drawCurrentBlock(Graphics2D g2, Block block) {
        int bx = logic.getX(), by = logic.getY();

        for (int j = 0; j < block.height(); j++) {
            for (int i = 0; i < block.width(); i++) {
                if (block.getShape(i, j) == 1) {
                    int x = bx + i;
                    int y = by + j;
                    Color color = ColorBlindPalette.convert(block.getColor(), colorMode);
                    drawCell(g2, x, y, color);

                    if (block instanceof ItemBlock item)
                        drawItemSymbol(g2, item, x, y);
                }
            }
        }
    }

    /** 아이템 오버레이 */
    private void drawItemSymbol(Graphics2D g2, ItemBlock item, int gridX, int gridY) {
        int px = gridX * CELL_SIZE + CELL_GAP;
        int py = gridY * CELL_SIZE + CELL_GAP;
        int size = CELL_SIZE - CELL_GAP * 2;
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

        if (item instanceof ColorBombItem) {
            g2.setColor(new Color(255, 220, 100, 120));
            g2.setStroke(new BasicStroke(3f));
            g2.drawOval(px + 4, py + 4, size - 8, size - 8);
        } else if (item instanceof LightningItem) {
            g2.setColor(new Color(100, 180, 255, 140));
            g2.setStroke(new BasicStroke(3f));
            g2.drawOval(px + 4, py + 4, size - 8, size - 8);
        }

        if (symbol != null) {
            g2.setColor(Color.BLACK);
            int tx = px + (size - fm.stringWidth(symbol)) / 2;
            int ty = py + (size + fm.getAscent() - fm.getDescent()) / 2;
            g2.drawString(symbol, tx, ty);
        }
    }

    // 색맹 모드 설정
    public void setColorMode(ColorBlindPalette.Mode mode) {
        this.colorMode = mode;
        repaint();
    }

    public ColorBlindPalette.Mode getColorMode() {
        return colorMode;
    }
}