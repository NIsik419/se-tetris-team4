package component;

import logic.BoardLogic;
import logic.ParticleSystem;
import blocks.Block;
import component.items.*;
import component.config.Settings;
import javax.swing.*;
import javax.swing.border.EmptyBorder;

import java.awt.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import logic.MovementService;

public class BoardView extends JPanel {
    private final BoardLogic logic;
    private final MovementService move;
    private ColorBlindPalette.Mode colorMode = ColorBlindPalette.Mode.NORMAL;
    private Settings settings;

    private boolean showGameOverScreen = false;
    private int gameOverScore = 0;
    private int gameOverLines = 0;
    private int gameOverLevel = 0;
    private float gameOverAlpha = 0f;
    private Rectangle confirmButtonBounds = null;
    private boolean confirmButtonHovered = false;
    private Runnable gameOverConfirmAction = null;

    // === 상수 통일 (Board 기준) ===
    private static final int CELL_SIZE = 25;
    private static final int CELL_GAP = 2;
    private static final int ARC = 6;
    private static final int MAX_HEIGHT = 500;
    public static final int WIDTH = BoardLogic.WIDTH;
    public static final int HEIGHT = BoardLogic.HEIGHT;
    private static final Color GRID_LINE = new Color(50, 55, 70);
    private static final Color BG_GAME = new Color(25, 30, 42);
    public Timer renderTimer;

    //  생성자에 Settings 추가
    public BoardView(BoardLogic logic, Settings settings) {
        this.logic = logic;
        this.move = new MovementService(logic.getState());
        this.settings = settings;

        // 렌더링 60fps 전용 타이머
        renderTimer = new Timer(16, e -> {
            logic.getClearService().getParticleSystem().update();
            repaint();
        });
        renderTimer.start();
        setBackground(BG_GAME);
        setBorder(BorderFactory.createLineBorder(GRID_LINE, 3));
    }

    //  getPreferredSize를 Settings 기반으로 수정 (null 안전)
    @Override
    public Dimension getPreferredSize() {
        int cellSize;
        if (settings != null) {
            cellSize = switch (settings.screenSize) {
                case SMALL -> 20;
                case MEDIUM -> 25;
                case LARGE -> 30;
            };
        } else {
            // settings가 null이면 기본값 사용 (MEDIUM)
            cellSize = 25;
        }
        return new Dimension(WIDTH * cellSize, HEIGHT * cellSize);
    }

    @Override
    protected void paintComponent(Graphics g) {
        boolean clearing = logic.isLineClearing();
        super.paintComponent(g);

        if (!visibleDuringStandby)
            return;
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        Color[][] grid = logic.getBoard();

        // 현재 셀 크기 계산 (Settings 기반)
        int currentCellSize = CELL_SIZE; // 기본값
        if (settings != null) {
            currentCellSize = switch (settings.screenSize) {
                case SMALL -> 20;
                case MEDIUM -> 25;
                case LARGE -> 30;
            };
        }

        // ===배경 격자 ===
        g2.setColor(GRID_LINE);
        for (int r = 0; r <= BoardLogic.HEIGHT; r++)
            g2.drawLine(0, r * currentCellSize, BoardLogic.WIDTH * currentCellSize, r * currentCellSize);
        for (int c = 0; c <= BoardLogic.WIDTH; c++)
            g2.drawLine(c * currentCellSize, 0, c * currentCellSize, BoardLogic.HEIGHT * currentCellSize);

        //  게임오버 화면 표시 (보드 위에 직접 그리기)
        if (showGameOverScreen) {
            drawGameOverScreen(g2);
            g2.dispose();
            return;
        }

        // === 고정 블록 먼저 그리기 ===
        for (int y = 0; y < BoardLogic.HEIGHT; y++) {
            for (int x = 0; x < BoardLogic.WIDTH; x++) {
                if (grid[y][x] != null) {
                    drawCell(g2, x, y, ColorBlindPalette.convert(grid[y][x], colorMode), currentCellSize);
                }
            }
        }

        // === Ghost 블록 ===
        drawGhostBlock(g2, currentCellSize);

        // === 현재 블록 ===
        Block curr = logic.getCurr();
        if (curr != null)
            drawCurrentBlock(g2, curr, currentCellSize);

        // === 파티클 렌더링 (맨 위에) ===
        drawParticles(g2);

        g2.dispose();
    }

    /** 파티클 렌더링 */
    private void drawParticles(Graphics2D g2) {
        ParticleSystem particles = logic.getClearService().getParticleSystem();
        List<ParticleSystem.Particle> particleList = particles.getParticles();

        if (particleList.isEmpty()) {
            return;
        }

        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_SPEED);

        for (ParticleSystem.Particle p : particleList) {
            if (p.life <= 0)
                continue;
            float alpha = p.getAlpha();
            if (alpha <= 0)
                continue;

            Color c = new Color(
                    p.color.getRed() / 255f,
                    p.color.getGreen() / 255f,
                    p.color.getBlue() / 255f,
                    alpha);

            int px = (int) p.x;
            int py = (int) p.y;

            g2.setColor(c);
            g2.fillOval(px - p.size / 2, py - p.size / 2, p.size, p.size);

            if (alpha > 0.6f) {
                Color glow = new Color(1f, 1f, 1f, alpha * 0.4f);
                g2.setColor(glow);
                int glowSize = p.size + 2;
                g2.fillOval(px - glowSize / 2, py - glowSize / 2, glowSize, glowSize);
            }

            double speed = Math.sqrt(p.vx * p.vx + p.vy * p.vy);
            if (speed > 2.0 && alpha > 0.5f) {
                g2.setColor(new Color(
                        p.color.getRed() / 255f,
                        p.color.getGreen() / 255f,
                        p.color.getBlue() / 255f,
                        alpha * 0.3f));

                int tailX = (int) (px - p.vx * 2);
                int tailY = (int) (py - p.vy * 2);

                g2.setStroke(new BasicStroke(Math.max(1, p.size / 2)));
                g2.drawLine(px, py, tailX, tailY);
            }
        }
    }

    /** 기본 셀 렌더링 - cellSize 파라미터 추가 */
    private void drawCell(Graphics2D g2, int x, int y, Color color, int cellSize) {
        int px = x * cellSize + CELL_GAP;
        int py = y * cellSize + CELL_GAP;
        int size = cellSize - CELL_GAP * 2;

        g2.setColor(color);
        g2.fillRoundRect(px, py, size, size, ARC, ARC);

        // 하이라이트
        g2.setColor(new Color(255, 255, 255, 60));
        g2.fillRoundRect(px, py, size, size / 3, ARC, ARC);

        // 그림자
        g2.setColor(new Color(0, 0, 0, 40));
        g2.fillRoundRect(px, py + size * 2 / 3, size, size / 3, ARC, ARC);
    }

    /** 유령 블록 (Ghost) - cellSize 파라미터 추가 */
    private void drawGhostBlock(Graphics2D g2, int cellSize) {
        if (logic.getClearService().isClearing())
            return;

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
                    int x = (bx + i) * cellSize + CELL_GAP;
                    int y = (ghostY + j) * cellSize + CELL_GAP;
                    int size = cellSize - CELL_GAP * 2;
                    g2.drawRect(x, y, size, size);
                }
            }
        }

        g2.setStroke(oldStroke);
    }

    /** 현재 블록 + 아이템 효과 - cellSize 파라미터 추가 */
    private void drawCurrentBlock(Graphics2D g2, Block block, int cellSize) {
        int bx = logic.getX(), by = logic.getY();

        for (int j = 0; j < block.height(); j++) {
            for (int i = 0; i < block.width(); i++) {
                if (block.getShape(i, j) == 1) {
                    int x = bx + i;
                    int y = by + j;
                    Color color = ColorBlindPalette.convert(block.getColor(), colorMode);
                    drawCell(g2, x, y, color, cellSize);

                    if (block instanceof LineClearItem lci) {
                        if (i == lci.getLX() && j == lci.getLY()) {
                            drawItemSymbol(g2, lci, x, y, cellSize);
                        }
                    } else if (block instanceof ItemBlock item) {
                        drawItemSymbol(g2, item, x, y, cellSize);
                    }
                }
            }
        }
    }

    /** 아이템 오버레이 - cellSize 파라미터 추가 */
    private void drawItemSymbol(Graphics2D g2, ItemBlock item, int gridX, int gridY, int cellSize) {
        int px = gridX * cellSize + CELL_GAP;
        int py = gridY * cellSize + CELL_GAP;
        int size = cellSize - CELL_GAP * 2;
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
    
    //  Settings 업데이트 메서드 추가
    public void updateSettings(Settings settings) {
        this.settings = settings;
        revalidate();
        repaint();
    }

    private boolean visibleDuringStandby = true;

    public void setVisibleDuringStandby(boolean visible) {
        this.visibleDuringStandby = visible;
        repaint();
    }

    // 렌더링 제어
    public void pauseRendering() {
        if (renderTimer != null && renderTimer.isRunning()) {
            renderTimer.stop();
        }
    }

    public void resumeRendering() {
        if (renderTimer != null && !renderTimer.isRunning()) {
            renderTimer.start();
        }
    }

    public void stopRendering() {
        if (renderTimer != null) {
            renderTimer.stop();
        }
    }

    public void cleanup() {
        if (renderTimer != null) {
            renderTimer.stop();
            renderTimer = null;
        }
        System.out.println("[CLEANUP] BoardView resources released");
    }

    public void triggerGameOverAnimation(Runnable afterAnimation) {
        Color[][] board = logic.getBoard();
        Color[][] boardCopy = new Color[BoardLogic.HEIGHT][BoardLogic.WIDTH];

        for (int y = 0; y < BoardLogic.HEIGHT; y++) {
            for (int x = 0; x < BoardLogic.WIDTH; x++) {
                boardCopy[y][x] = board[y][x];
                board[y][x] = null;
            }
        }

        int[][] pid = logic.getState().getPieceId();
        for (int y = 0; y < BoardLogic.HEIGHT; y++) {
            Arrays.fill(pid[y], 0);
        }

        Color[][] fade = logic.getFadeLayer();
        if (fade != null) {
            for (int y = 0; y < BoardLogic.HEIGHT; y++) {
                Arrays.fill(fade[y], null);
            }
        }

        repaint();

        JPanel glassPane = new JPanel(null);
        glassPane.setOpaque(false);

        JFrame parentFrame = (JFrame) SwingUtilities.getWindowAncestor(this);
        if (parentFrame == null) {
            if (afterAnimation != null)
                afterAnimation.run();
            return;
        }

        setFocusable(false);
        setEnabled(false);

        parentFrame.setGlassPane(glassPane);
        glassPane.setVisible(true);

        List<JPanel> blocks = new ArrayList<>();

        //  현재 셀 크기 사용
        int currentCellSize = Math.min(getWidth() / WIDTH, getHeight() / HEIGHT);

        for (int y = 0; y < BoardLogic.HEIGHT; y++) {
            for (int x = 0; x < BoardLogic.WIDTH; x++) {
                if (boardCopy[y][x] != null) {
                    JPanel block = new JPanel();
                    block.setBackground(boardCopy[y][x]);
                    block.setBorder(BorderFactory.createLineBorder(boardCopy[y][x].darker(), 1));

                    Point screenPos = SwingUtilities.convertPoint(
                            this,
                            x * currentCellSize + CELL_GAP,
                            y * currentCellSize + CELL_GAP,
                            glassPane);

                    block.setBounds(
                            screenPos.x,
                            screenPos.y,
                            currentCellSize - CELL_GAP * 2,
                            currentCellSize - CELL_GAP * 2);

                    glassPane.add(block);
                    blocks.add(block);
                }
            }
        }

        Timer explosionTimer = new Timer(12, null);
        final int[] frameCount = { 0 };
        final int maxFrames = 30;

        List<double[]> velocities = new ArrayList<>();
        for (int i = 0; i < blocks.size(); i++) {
            velocities.add(new double[] {
                    (Math.random() - 0.5) * 60,
                    -(Math.random() * 25 + 15),
                    (Math.random() - 0.5) * 30
            });
        }

        explosionTimer.addActionListener(e -> {
            frameCount[0]++;

            for (int i = 0; i < blocks.size(); i++) {
                JPanel block = blocks.get(i);
                double[] vel = velocities.get(i);

                Rectangle bounds = block.getBounds();
                bounds.x += (int) vel[0];
                bounds.y += (int) vel[1];
                vel[1] += 10;

                block.setBounds(bounds);

                float alpha = 1.0f - (frameCount[0] / (float) maxFrames);
                alpha = Math.max(0, alpha);

                Color originalColor = block.getBackground();
                block.setBackground(new Color(
                        originalColor.getRed(),
                        originalColor.getGreen(),
                        originalColor.getBlue(),
                        (int) (255 * alpha)));
            }

            glassPane.repaint();

            if (frameCount[0] >= maxFrames) {
                glassPane.setVisible(false);
                ((Timer) e.getSource()).stop();

                if (afterAnimation != null) {
                    SwingUtilities.invokeLater(afterAnimation);
                }
            }
        });

        explosionTimer.start();
    }

    public void showGameOverStats(int score, int lines, int level, Runnable onComplete) {
        this.gameOverScore = score;
        this.gameOverLines = lines;
        this.gameOverLevel = level;
        this.showGameOverScreen = true;
        this.gameOverAlpha = 0f;
        this.gameOverConfirmAction = onComplete;

        addGameOverMouseListener();

        Timer fadeTimer = new Timer(5, null);
        fadeTimer.addActionListener(e -> {
            gameOverAlpha += 0.05f;
            if (gameOverAlpha >= 1.0f) {
                gameOverAlpha = 1.0f;
                ((Timer) e.getSource()).stop();
            }
            repaint();
        });
        fadeTimer.start();
    }

    private java.awt.event.MouseAdapter gameOverMouseListener = null;

    private void addGameOverMouseListener() {
        if (gameOverMouseListener != null) {
            removeMouseListener(gameOverMouseListener);
            removeMouseMotionListener(gameOverMouseListener);
        }

        gameOverMouseListener = new java.awt.event.MouseAdapter() {
            @Override
            public void mouseClicked(java.awt.event.MouseEvent e) {
                if (showGameOverScreen && confirmButtonBounds != null &&
                        confirmButtonBounds.contains(e.getPoint())) {
                    showGameOverScreen = false;
                    removeMouseListener(this);
                    removeMouseMotionListener(this);
                    repaint();

                    if (gameOverConfirmAction != null) {
                        gameOverConfirmAction.run();
                    }
                }
            }

            @Override
            public void mouseMoved(java.awt.event.MouseEvent e) {
                if (showGameOverScreen && confirmButtonBounds != null) {
                    boolean wasHovered = confirmButtonHovered;
                    confirmButtonHovered = confirmButtonBounds.contains(e.getPoint());

                    if (wasHovered != confirmButtonHovered) {
                        repaint();
                    }

                    if (confirmButtonHovered) {
                        setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
                    } else {
                        setCursor(Cursor.getDefaultCursor());
                    }
                }
            }
        };

        addMouseListener(gameOverMouseListener);
        addMouseMotionListener(gameOverMouseListener);
    }

    private void drawGameOverScreen(Graphics2D g2) {
        int width = getWidth();
        int height = getHeight();

        g2.setColor(new Color(0, 0, 0, (int) (180 * gameOverAlpha)));
        g2.fillRect(0, 0, width, height);

        g2.setColor(new Color(255, 100, 100, (int) (255 * gameOverAlpha)));
        g2.setFont(new Font("Arial", Font.BOLD, Math.min(36, width / 7)));
        String gameOverText = "GAME OVER";
        FontMetrics fm1 = g2.getFontMetrics();
        int x1 = (width - fm1.stringWidth(gameOverText)) / 2;
        int y1 = height / 3;
        g2.drawString(gameOverText, x1, y1);

        g2.setColor(new Color(255, 255, 255, (int) (255 * gameOverAlpha)));
        g2.setFont(new Font("Arial", Font.BOLD, Math.min(24, width / 10)));
        String scoreText = "Score: " + gameOverScore;
        FontMetrics fm2 = g2.getFontMetrics();
        int x2 = (width - fm2.stringWidth(scoreText)) / 2;
        int y2 = height / 2 - 10;
        g2.drawString(scoreText, x2, y2);

        g2.setFont(new Font("Arial", Font.PLAIN, Math.min(18, width / 14)));
        g2.setColor(new Color(200, 200, 200, (int) (255 * gameOverAlpha)));
        String linesText = "Lines: " + gameOverLines;
        FontMetrics fm3 = g2.getFontMetrics();
        int x3 = (width - fm3.stringWidth(linesText)) / 2;
        int y3 = y2 + 30;
        g2.drawString(linesText, x3, y3);

        String levelText = "Level: " + gameOverLevel;
        FontMetrics fm4 = g2.getFontMetrics();
        int x4 = (width - fm4.stringWidth(levelText)) / 2;
        int y4 = y3 + 25;
        g2.drawString(levelText, x4, y4);

        g2.setColor(new Color(100, 255, 218, (int) (200 * gameOverAlpha)));
        g2.setStroke(new BasicStroke(2));
        int lineWidth = Math.min(150, width - 60);
        g2.drawLine((width - lineWidth) / 2, y2 + 8, (width + lineWidth) / 2, y2 + 8);

        int buttonWidth = Math.min(100, width - 60);
        int buttonHeight = 35;
        int buttonX = (width - buttonWidth) / 2;
        int buttonY = y4 + 40;

        confirmButtonBounds = new Rectangle(buttonX, buttonY, buttonWidth, buttonHeight);

        if (confirmButtonHovered) {
            g2.setColor(new Color(100, 255, 218, (int) (255 * gameOverAlpha)));
        } else {
            g2.setColor(new Color(100, 255, 218, (int) (200 * gameOverAlpha)));
        }
        g2.fillRoundRect(buttonX, buttonY, buttonWidth, buttonHeight, 8, 8);

        g2.setColor(new Color(255, 255, 255, (int) (255 * gameOverAlpha)));
        g2.setStroke(new BasicStroke(2));
        g2.drawRoundRect(buttonX, buttonY, buttonWidth, buttonHeight, 8, 8);

        g2.setFont(new Font("Arial,맑은 고딕", Font.BOLD, Math.min(16, width / 16)));
        g2.setColor(new Color(20, 25, 35, (int) (255 * gameOverAlpha)));
        String buttonText = "확인";
        FontMetrics fmBtn = g2.getFontMetrics();
        int textX = buttonX + (buttonWidth - fmBtn.stringWidth(buttonText)) / 2;
        int textY = buttonY + (buttonHeight + fmBtn.getAscent() - fmBtn.getDescent()) / 2;
        g2.drawString(buttonText, textX, textY);
    }
}