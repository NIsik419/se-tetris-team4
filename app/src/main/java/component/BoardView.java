package component;

import logic.BoardLogic;
import logic.ParticleSystem;
import blocks.Block;
import component.items.*;
import javax.swing.*;
import javax.swing.border.EmptyBorder;

import java.awt.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List; // ⭐ 추가
import logic.MovementService;

public class BoardView extends JPanel {
    private final BoardLogic logic;
    private final MovementService move;
    private ColorBlindPalette.Mode colorMode = ColorBlindPalette.Mode.NORMAL;

    private boolean showGameOverScreen = false;
    private int gameOverScore = 0;
    private int gameOverLines = 0;
    private int gameOverLevel = 0;
    private float gameOverAlpha = 0f;
    private Rectangle confirmButtonBounds = null; // 확인 버튼 영역
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

    public BoardView(BoardLogic logic) {
        this.logic = logic;
        this.move = new MovementService(logic.getState());

        // 렌더링 60fps 전용 타이머
        renderTimer = new Timer(16, e -> {
            logic.getClearService().getParticleSystem().update();
            repaint();
        });
        renderTimer.start();
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
        boolean clearing = logic.isLineClearing();
        super.paintComponent(g);

        if (!visibleDuringStandby)
            return;
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        // g2.setComposite(AlphaComposite.SrcOver.derive(0.9f));

        Color[][] grid = logic.getBoard();

        // ===배경 격자 ===
        g2.setColor(GRID_LINE);
        for (int r = 0; r <= BoardLogic.HEIGHT; r++)
            g2.drawLine(0, r * CELL_SIZE, BoardLogic.WIDTH * CELL_SIZE, r * CELL_SIZE);
        for (int c = 0; c <= BoardLogic.WIDTH; c++)
            g2.drawLine(c * CELL_SIZE, 0, c * CELL_SIZE, BoardLogic.HEIGHT * CELL_SIZE);

        // ⭐ 게임오버 화면 표시 (보드 위에 직접 그리기)
        if (showGameOverScreen) {
            drawGameOverScreen(g2);
            g2.dispose();
            return; // 다른 요소는 그리지 않음
        }

        // === 고정 블록 먼저 그리기 ===
        for (int y = 0; y < BoardLogic.HEIGHT; y++) {
            for (int x = 0; x < BoardLogic.WIDTH; x++) {
                if (grid[y][x] != null) {
                    drawCell(g2, x, y, ColorBlindPalette.convert(grid[y][x], colorMode));
                }
            }
        }

        // === fadeLayer 제거 (파티클 사용으로 대체) ===

        // === Ghost 블록 ===
        drawGhostBlock(g2);

        // === 현재 블록 ===
        Block curr = logic.getCurr();
        if (curr != null)
            drawCurrentBlock(g2, curr);

        // === 파티클 렌더링 (맨 위에) ===
        drawParticles(g2);

        g2.dispose();
    }

    /** 파티클 렌더링 */
    private void drawParticles(Graphics2D g2) {
        ParticleSystem particles = logic.getClearService().getParticleSystem();
        List<ParticleSystem.Particle> particleList = particles.getParticles();

        // 파티클이 없으면 바로 리턴
        if (particleList.isEmpty()) {
            return;
        }

        // 안티앨리어싱 강화 (부드러운 파티클)
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_SPEED);

        for (ParticleSystem.Particle p : particleList) {
            if (p.life <= 0)
                continue;
            float alpha = p.getAlpha();
            if (alpha <= 0)
                continue;

            // 파티클 색상 (알파 적용)
            Color c = new Color(
                    p.color.getRed() / 255f,
                    p.color.getGreen() / 255f,
                    p.color.getBlue() / 255f,
                    alpha);

            int px = (int) p.x;
            int py = (int) p.y;

            // 메인 파티클 (원형)
            g2.setColor(c);
            g2.fillOval(px - p.size / 2, py - p.size / 2, p.size, p.size);

            // 빛나는 효과 (높은 알파일 때)
            if (alpha > 0.6f) {
                Color glow = new Color(1f, 1f, 1f, alpha * 0.4f);
                g2.setColor(glow);
                int glowSize = p.size + 2;
                g2.fillOval(px - glowSize / 2, py - glowSize / 2, glowSize, glowSize);
            }

            // 선택: 꼬리 효과 (속도가 빠를 때)
            double speed = Math.sqrt(p.vx * p.vx + p.vy * p.vy);
            if (speed > 2.0 && alpha > 0.5f) {
                g2.setColor(new Color(
                        p.color.getRed() / 255f,
                        p.color.getGreen() / 255f,
                        p.color.getBlue() / 255f,
                        alpha * 0.3f));

                // 꼬리 방향 계산
                int tailX = (int) (px - p.vx * 2);
                int tailY = (int) (py - p.vy * 2);

                g2.setStroke(new BasicStroke(Math.max(1, p.size / 2)));
                g2.drawLine(px, py, tailX, tailY);
            }
        }
    }

    // ⭐ drawFade 메서드 제거 또는 사용 안 함
    // private void drawFade(Graphics2D g2, int x, int y, Color fadeColor) {
    // // 파티클 시스템으로 대체됨
    // }

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

                    if (block instanceof LineClearItem lci) {
                        if (i == lci.getLX() && j == lci.getLY()) {
                            drawItemSymbol(g2, lci, x, y);
                        }
                    } else if (block instanceof ItemBlock item) {
                        drawItemSymbol(g2, item, x, y);
                    }
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
            renderTimer = null; // 참조 해제
        }
        System.out.println("[CLEANUP] BoardView resources released");
    }

    public void triggerGameOverAnimation(Runnable afterAnimation) {
        // 애니메이션 시작 전에 보드 즉시 클리어
        Color[][] board = logic.getBoard();
        Color[][] boardCopy = new Color[BoardLogic.HEIGHT][BoardLogic.WIDTH];

        // 보드 복사 (애니메이션용)
        for (int y = 0; y < BoardLogic.HEIGHT; y++) {
            for (int x = 0; x < BoardLogic.WIDTH; x++) {
                boardCopy[y][x] = board[y][x];
                board[y][x] = null; // 즉시 클리어
            }
        }

        // pieceId도 클리어
        int[][] pid = logic.getState().getPieceId();
        for (int y = 0; y < BoardLogic.HEIGHT; y++) {
            Arrays.fill(pid[y], 0);
        }

        // fadeLayer도 클리어
        Color[][] fade = logic.getFadeLayer();
        if (fade != null) {
            for (int y = 0; y < BoardLogic.HEIGHT; y++) {
                Arrays.fill(fade[y], null);
            }
        }

        repaint(); // 보드 즉시 갱신

        JPanel glassPane = new JPanel(null);
        glassPane.setOpaque(false);

        JFrame parentFrame = (JFrame) SwingUtilities.getWindowAncestor(this);
        if (parentFrame == null) {
            if (afterAnimation != null)
                afterAnimation.run();
            return;
        }

        // 애니메이션 중 입력 차단
        setFocusable(false);
        setEnabled(false);

        parentFrame.setGlassPane(glassPane);
        glassPane.setVisible(true);

        List<JPanel> blocks = new ArrayList<>();

        // 복사한 보드로 블록 생성
        for (int y = 0; y < BoardLogic.HEIGHT; y++) {
            for (int x = 0; x < BoardLogic.WIDTH; x++) {
                if (boardCopy[y][x] != null) {
                    JPanel block = new JPanel();
                    block.setBackground(boardCopy[y][x]);
                    block.setBorder(BorderFactory.createLineBorder(boardCopy[y][x].darker(), 1));

                    Point screenPos = SwingUtilities.convertPoint(
                            this,
                            x * CELL_SIZE + CELL_GAP,
                            y * CELL_SIZE + CELL_GAP,
                            glassPane);

                    block.setBounds(
                            screenPos.x,
                            screenPos.y,
                            CELL_SIZE - CELL_GAP * 2,
                            CELL_SIZE - CELL_GAP * 2);

                    glassPane.add(block);
                    blocks.add(block);
                }
            }
        }

       
        Timer explosionTimer = new Timer(12, null);
        final int[] frameCount = { 0 };
        final int maxFrames = 15; // 30 → 15로 감소 (2배 빠르게)

        List<double[]> velocities = new ArrayList<>();
        for (int i = 0; i < blocks.size(); i++) {
            velocities.add(new double[] {
                    (Math.random() - 0.5) * 60, // vx: 30 → 60 (2배 빠르게)
                    -(Math.random() * 25 + 15), // vy: -(15~23) → -(15~40) (더 높이)
                    (Math.random() - 0.5) * 30 // rotation speed
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
                vel[1] += 10; // 중력: 2 → 3.5 (더 강하게)

                block.setBounds(bounds);

                // 빠른 페이드 아웃
                float alpha = 1.0f - (frameCount[0] / (float) maxFrames);
                alpha = Math.max(0, alpha); // 음수 방지

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

        // 마우스 리스너 추가
        addGameOverMouseListener();

        // 페이드인 애니메이션
        Timer fadeTimer = new Timer(5 , null);
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

    // 마우스 리스너 추가
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
                    // 확인 버튼 클릭
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

                    // 커서 변경
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

    // 게임오버 화면 그리기 수정
    private void drawGameOverScreen(Graphics2D g2) {
        int width = getWidth(); // BoardView의 실제 크기
        int height = getHeight(); // BoardView의 실제 크기

        // 반투명 검은 배경
        g2.setColor(new Color(0, 0, 0, (int) (180 * gameOverAlpha)));
        g2.fillRect(0, 0, width, height);

        // GAME OVER 텍스트 (크기 조정)
        g2.setColor(new Color(255, 100, 100, (int) (255 * gameOverAlpha)));
        g2.setFont(new Font("Arial", Font.BOLD, Math.min(36, width / 7))); // 보드 크기에 맞춤
        String gameOverText = "GAME OVER";
        FontMetrics fm1 = g2.getFontMetrics();
        int x1 = (width - fm1.stringWidth(gameOverText)) / 2;
        int y1 = height / 3;
        g2.drawString(gameOverText, x1, y1);

        // 점수 정보
        g2.setColor(new Color(255, 255, 255, (int) (255 * gameOverAlpha)));
        g2.setFont(new Font("Arial", Font.BOLD, Math.min(24, width / 10)));
        String scoreText = "Score: " + gameOverScore;
        FontMetrics fm2 = g2.getFontMetrics();
        int x2 = (width - fm2.stringWidth(scoreText)) / 2;
        int y2 = height / 2 - 10;
        g2.drawString(scoreText, x2, y2);

        // 라인 수
        g2.setFont(new Font("Arial", Font.PLAIN, Math.min(18, width / 14)));
        g2.setColor(new Color(200, 200, 200, (int) (255 * gameOverAlpha)));
        String linesText = "Lines: " + gameOverLines;
        FontMetrics fm3 = g2.getFontMetrics();
        int x3 = (width - fm3.stringWidth(linesText)) / 2;
        int y3 = y2 + 30;
        g2.drawString(linesText, x3, y3);

        // 레벨
        String levelText = "Level: " + gameOverLevel;
        FontMetrics fm4 = g2.getFontMetrics();
        int x4 = (width - fm4.stringWidth(levelText)) / 2;
        int y4 = y3 + 25;
        g2.drawString(levelText, x4, y4);

        // 구분선
        g2.setColor(new Color(100, 255, 218, (int) (200 * gameOverAlpha)));
        g2.setStroke(new BasicStroke(2));
        int lineWidth = Math.min(150, width - 60);
        g2.drawLine((width - lineWidth) / 2, y2 + 8, (width + lineWidth) / 2, y2 + 8);

        //  확인 버튼 (보드 크기에 맞춤)
        int buttonWidth = Math.min(100, width - 60);
        int buttonHeight = 35;
        int buttonX = (width - buttonWidth) / 2;
        int buttonY = y4 + 40;

        confirmButtonBounds = new Rectangle(buttonX, buttonY, buttonWidth, buttonHeight);

        // 버튼 배경 (호버 효과)
        if (confirmButtonHovered) {
            g2.setColor(new Color(100, 255, 218, (int) (255 * gameOverAlpha)));
        } else {
            g2.setColor(new Color(100, 255, 218, (int) (200 * gameOverAlpha)));
        }
        g2.fillRoundRect(buttonX, buttonY, buttonWidth, buttonHeight, 8, 8);

        // 버튼 테두리
        g2.setColor(new Color(255, 255, 255, (int) (255 * gameOverAlpha)));
        g2.setStroke(new BasicStroke(2));
        g2.drawRoundRect(buttonX, buttonY, buttonWidth, buttonHeight, 8, 8);

        // 버튼 텍스트
        g2.setFont(new Font("Arial,맑은 고딕", Font.BOLD, Math.min(16, width / 16)));
        g2.setColor(new Color(20, 25, 35, (int) (255 * gameOverAlpha)));
        String buttonText = "확인";
        FontMetrics fmBtn = g2.getFontMetrics();
        int textX = buttonX + (buttonWidth - fmBtn.stringWidth(buttonText)) / 2;
        int textY = buttonY + (buttonHeight + fmBtn.getAscent() - fmBtn.getDescent()) / 2;
        g2.drawString(buttonText, textX, textY);
    }
}