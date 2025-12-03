package component;

import logic.BoardLogic;
import logic.ParticleSystem;
import blocks.Block;
import component.items.*;
import component.config.Settings;

import javax.swing.*;
import javax.swing.border.EmptyBorder;

import java.awt.*;
import java.awt.image.BufferedImage;
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
    private List<AwardNotification> awards = new ArrayList<>();

    // === 상수 통일 (Board 기준) ===
    private static final int CELL_SIZE = 25;
    private static final int CELL_GAP = 0;
    private static final int MAX_HEIGHT = 500;
    public static final int WIDTH = BoardLogic.WIDTH;
    public static final int HEIGHT = BoardLogic.HEIGHT;
    private static final Color BG_GAME = new Color(25, 30, 42);

    // 배경 타일 기본색 (블록보다 더 어두운 회색 느낌)
    private static final Color BG_TILE_COLOR = new Color(24, 26, 32);

    public Timer renderTimer;

    // 배경 타일 이미지 재사용
    private BufferedImage backgroundImage;
    private int backgroundCellSize = -1;

    // 생성자에 Settings 추가
    public BoardView(BoardLogic logic, Settings settings) {
        this.logic = logic;
        this.move = new MovementService(logic.getState());
        this.settings = settings;

        // 현재 셀 크기 결정
        int cellSize = CELL_SIZE; // 기본값 25
        if (settings != null) {
            cellSize = switch (settings.screenSize) {
                case SMALL -> 20;
                case MEDIUM -> 25;
                case LARGE -> 30;
            };
        }
        logic.setCellSize(cellSize);
        // 배경 이미지 생성 (셀 크기에 맞춰)
        initBackgroundImage(cellSize);

        // 렌더링 60fps 전용 타이머
        renderTimer = new Timer(16, e -> {
            logic.getClearService().getParticleSystem().update();
            repaint();
        });
        renderTimer.start();

        setBackground(BG_GAME);
        setBorder(BorderFactory.createLineBorder(new Color(50, 55, 70), 3));
    }

    // getPreferredSize를 Settings 기반으로 수정 (null 안전)
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

    /** 배경(빈 칸 타일)을 한 번만 그려두는 메서드 */
    private void initBackgroundImage(int cellSize) {

        this.backgroundCellSize = cellSize; // 현재 셀 크기 저장

        int w = BoardLogic.WIDTH * cellSize;
        int h = BoardLogic.HEIGHT * cellSize;

        backgroundImage = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2 = backgroundImage.createGraphics();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        // 보드 배경 기본 색
        g2.setColor(BG_GAME);
        g2.fillRect(0, 0, w, h);

        // 배경 타일(빈 칸) – 어두운 회색 계열
        Color emptyBase = new Color(40, 42, 52);

        // 배경 빈칸 타일 그리기
        for (int y = 0; y < BoardLogic.HEIGHT; y++) {
            for (int x = 0; x < BoardLogic.WIDTH; x++) {
                drawBackgroundCell(g2, x, y, cellSize); // ← 반드시 cellSize 넘겨서 그리기
            }
        }

        g2.dispose();
    }

    private static class AwardNotification {
        String text;
        Color color;
        long createTime;
        int fontSize;

        // 애니메이션 상태
        float alpha = 0f;
        float scale = 0.4f;
        int offsetY = 0;

        AwardNotification(String text, Color color, int fontSize) {
            this.text = text;
            this.color = color;
            this.fontSize = fontSize;
            this.createTime = System.currentTimeMillis();
        }

        void update() {
            long elapsed = System.currentTimeMillis() - createTime;

            if (elapsed < 200) {
                //
                float t = elapsed / 200f;
                scale = 0.5f + (0.5f * t);
                alpha = t;
            } else if (elapsed < 1800) {
                //
                scale = 1.0f;
                alpha = 1.0f;
                offsetY = (int) ((elapsed - 200) / 6); // 천천히 위로 (5 → 6)
            } else if (elapsed < 2200) {
                //
                float t = (elapsed - 1800) / 400f;
                alpha = 1.0f - t;
                offsetY = (int) ((elapsed - 200) / 6);
            }
        }

        boolean isExpired() {
            return System.currentTimeMillis() - createTime > 1500;
        }
    }

    @Override
    protected void paintComponent(Graphics g) {
        boolean clearing = logic.isLineClearing();
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g.create();

        if (!visibleDuringStandby) {
            g2.dispose(); // ← 리턴 전에 정리
            return;
        }

        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        int shakeOffset = logic.getShakeOffset();
        if (shakeOffset != 0) {
            g2.translate(shakeOffset, 0); // 좌우로 흔들기
        }

        // 1) 현재 셀 크기 계산 (Settings 기반)
        int currentCellSize = CELL_SIZE; // 기본값
        if (settings != null) {
            currentCellSize = switch (settings.screenSize) {
                case SMALL -> 20;
                case MEDIUM -> 25;
                case LARGE -> 30;
            };
        }

        // 2) 배경 타일 이미지 준비 (셀 크기 달라지면 다시 생성)
        if (backgroundImage == null || backgroundCellSize != currentCellSize) {
            initBackgroundImage(currentCellSize);
        }

        // 3) 배경 먼저 그리기
        g2.drawImage(backgroundImage, 0, 0, null);

        renderBeamParticles(g2, currentCellSize);

        // 5) 고정 블록 그리기
        Color[][] grid = logic.getBoard();
        for (int y = 0; y < BoardLogic.HEIGHT; y++) {
            for (int x = 0; x < BoardLogic.WIDTH; x++) {
                if (grid[y][x] != null) {
                    drawCell(g2, x, y,
                            ColorBlindPalette.convert(grid[y][x], colorMode),
                            currentCellSize); // ← 셀 크기 전달
                }
            }
        }

        renderTrailParticles(g2, currentCellSize);

        // === Ghost 블록 ===
        drawGhostBlock(g2, currentCellSize);

        // === 4) 현재 블록 ===
        Block curr = logic.getCurr();
        if (curr != null)
            drawCurrentBlock(g2, curr, currentCellSize);

        // === 5) 파티클 렌더링 ===
        drawParticles(g2);

        // === 6) GAME OVER 오버레이 ===
        if (showGameOverScreen) {
            drawGameOverScreen(g2);
        }
        awards.removeIf(award -> {
            award.update();
            return award.isExpired();
        });

        drawAwards(g2, currentCellSize);
        g2.dispose();
    }

    /**
     * 궤적 파티클 렌더링 - 블록이 떨어진 자리에 반투명 잔상
     */
    private void renderTrailParticles(Graphics2D g2, int cellSize) {
        ParticleSystem particleSystem = logic.getClearService().getParticleSystem();
        List<ParticleSystem.TrailParticle> trails = particleSystem.getTrailParticles();

        if (!trails.isEmpty()) {
            //System.out.println("[RENDER] Trail particles: " + trails.size());
        }

        if (trails == null || trails.isEmpty()) {
            return;
        }

        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        for (ParticleSystem.TrailParticle trail : trails) {
            float alpha = trail.getAlpha();
            if (alpha <= 0)
                continue;

            Color c = trail.color;

            //  궤적 색상 (약간 밝게 + 투명도)
            Color trailColor = new Color(
                    Math.min(255, c.getRed() + 30),
                    Math.min(255, c.getGreen() + 30),
                    Math.min(255, c.getBlue() + 30),
                    (int) (alpha * 255));

            int px = trail.x * cellSize + CELL_GAP;
            int py = trail.y * cellSize + CELL_GAP;
            int size = cellSize - CELL_GAP * 2;

            //  안쪽을 약간 작게 그려서 경계선 효과
            int inset = 3;
            int innerX = px + inset;
            int innerY = py + inset;
            int innerSize = size - inset * 2;

            // 1) 바깥쪽 테두리 (더 진한 색)
            g2.setColor(new Color(
                    c.getRed(),
                    c.getGreen(),
                    c.getBlue(),
                    (int) (alpha * 180)));
            g2.fillRect(px, py, size, size);

            // 2) 안쪽 중심 (밝은 색)
            g2.setColor(trailColor);
            g2.fillRect(innerX, innerY, innerSize, innerSize);

            // 3) 테두리 효과 (선택적)
            if (alpha > 0.3f) {
                g2.setColor(new Color(255, 255, 255, (int) (alpha * 100)));
                g2.setStroke(new BasicStroke(1.5f));
                g2.drawRect(innerX, innerY, innerSize, innerSize);
            }
        }
    }

    /**
     * 빔 파티클 렌더링 (테이퍼 광선 - 아래는 굵고 위는 얇음)
     */
    private void renderBeamParticles(Graphics2D g2, int cellSize) {
        ParticleSystem particleSystem = logic.getClearService().getParticleSystem();
        List<ParticleSystem.BeamParticle> beams = particleSystem.getBeamParticles();

        if (beams == null || beams.isEmpty()) {
            return;
        }

        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);

        for (ParticleSystem.BeamParticle beam : beams) {
            float alpha = beam.getAlpha();
            if (alpha <= 0)
                continue;

            //  제곱 페이드 효과 (더 빠르게 사라짐)
            float fadeAlpha = alpha * alpha;

            int height = beam.endY - beam.startY;

            //  테이퍼 광선 그리기 (아래 → 위로 점점 가늘어짐)
            drawTaperedBeam(g2, beam, fadeAlpha, height, cellSize);
        }
    }

    /**
     * 테이퍼 광선 그리기 (중앙 진한 단색 레이저)
     */
    private void drawTaperedBeam(Graphics2D g2, ParticleSystem.BeamParticle beam,
            float alpha, int height, int cellSize) {
        int centerX = beam.x;

        //  중앙 레이저만 그리기 (진한 단색)
        int beamWidth = (int) (beam.width * 0.8f); // 블록 너비의 80%

        // 사각형 레이저 (위에서 아래까지 같은 굵기)
        int[] xPoints = {
                centerX - beamWidth / 2,
                centerX + beamWidth / 2,
                centerX + beamWidth / 2,
                centerX - beamWidth / 2
        };
        int[] yPoints = {
                beam.startY,
                beam.startY,
                beam.endY,
                beam.endY
        };

        //  블록 색상 그대로 (완전 불투명)
        g2.setColor(new Color(
                beam.color.getRed(),
                beam.color.getGreen(),
                beam.color.getBlue(),
                180 // 완전 불투명
        ));
        g2.fillPolygon(xPoints, yPoints, 4);

        //  테두리 (더 진하게)
        g2.setStroke(new BasicStroke(2f));
        g2.setColor(new Color(
                Math.max(0, beam.color.getRed() - 40),
                Math.max(0, beam.color.getGreen() - 40),
                Math.max(0, beam.color.getBlue() - 40)));
        g2.drawPolygon(xPoints, yPoints, 4);
    }

    /**
     * 광선 레이어 그리기 (진한 단색)
     */
    private void drawBeamLayer(Graphics2D g2, int centerX, int startY, int endY,
            int topWidth, int bottomWidth, Color baseColor, float alpha) {
        if (alpha <= 0)
            return;

        // 그라데이션 없이 진한 단색으로
        Color solidColor = new Color(
                baseColor.getRed(),
                baseColor.getGreen(),
                baseColor.getBlue(),
                (int) (alpha * 180));

        // 사다리꼴 모양
        int[] xPoints = {
                centerX - topWidth / 2,
                centerX + topWidth / 2,
                centerX + bottomWidth / 2,
                centerX - bottomWidth / 2
        };
        int[] yPoints = {
                startY,
                startY,
                endY,
                endY
        };

        //  단색으로 채우기 (그라데이션 제거)
        g2.setColor(solidColor);
        g2.fillPolygon(xPoints, yPoints, 4);
    }

    /**
     * 중심 밝은 라인 (레이저 코어) - 심플하게
     */
    private void drawCoreBeam(Graphics2D g2, int centerX, int startY, int endY,
            int topWidth, int bottomWidth, float alpha) {
        if (alpha <= 0)
            return;

        // 순백색 코어 (적당하게)
        Color coreColor = new Color(255, 255, 255, (int) (alpha * 180));

        int[] xPoints = {
                centerX - topWidth / 2,
                centerX + topWidth / 2,
                centerX + bottomWidth / 2,
                centerX - bottomWidth / 2
        };
        int[] yPoints = {
                startY,
                startY,
                endY,
                endY
        };

        g2.setColor(coreColor);
        g2.fillPolygon(xPoints, yPoints, 4);

        // 중앙 라인 (심플하게)
        g2.setStroke(new BasicStroke(2f));
        g2.setColor(new Color(255, 255, 255, (int) (alpha * 220)));
        g2.drawLine(centerX, startY, centerX, endY);
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
    private void drawCell(Graphics2D g2, int x, int y, Color baseColor, int cellSize) {
        int px = x * cellSize + CELL_GAP;
        int py = y * cellSize + CELL_GAP;
        int size = cellSize - CELL_GAP * 2;

        // 중앙 정사각형 inset 비율 (살짝 줄여서 더 단단한 느낌)
        int inset = (int) (size * 0.22);
        int innerX = px + inset;
        int innerY = py + inset;
        int innerSize = size - inset * 2;

        // 🔹 색 계열: 대비를 확 줄여서 은은하게
        Color topColor = lighten(baseColor, 0.15f); // 0.35f → 0.15f
        Color leftColor = lighten(baseColor, 0.07f); // 0.15f → 0.07f
        Color rightColor = darken(baseColor, 0.12f); // 0.25f → 0.12f
        Color bottomColor = darken(baseColor, 0.20f); // 0.45f → 0.20f
        Color centerColor = darken(baseColor, 0.03f); // 중앙은 아주 조금만 어둡게

        // ===== top facet (위 사다리꼴) =====
        Polygon top = new Polygon();
        top.addPoint(px, py);
        top.addPoint(px + size, py);
        top.addPoint(innerX + innerSize, innerY);
        top.addPoint(innerX, innerY);
        g2.setColor(topColor);
        g2.fillPolygon(top);

        // ===== bottom facet =====
        Polygon bottom = new Polygon();
        bottom.addPoint(innerX, innerY + innerSize);
        bottom.addPoint(innerX + innerSize, innerY + innerSize);
        bottom.addPoint(px + size, py + size);
        bottom.addPoint(px, py + size);
        g2.setColor(bottomColor);
        g2.fillPolygon(bottom);

        // ===== left facet =====
        Polygon left = new Polygon();
        left.addPoint(px, py);
        left.addPoint(innerX, innerY);
        left.addPoint(innerX, innerY + innerSize);
        left.addPoint(px, py + size);
        g2.setColor(leftColor);
        g2.fillPolygon(left);

        // ===== right facet =====
        Polygon right = new Polygon();
        right.addPoint(innerX + innerSize, innerY);
        right.addPoint(px + size, py);
        right.addPoint(px + size, py + size);
        right.addPoint(innerX + innerSize, innerY + innerSize);
        g2.setColor(rightColor);
        g2.fillPolygon(right);

        // ===== 중앙 정사각형 =====
        g2.setColor(centerColor);
        g2.fillRect(innerX, innerY, innerSize, innerSize);

        // 바깥 테두리도 살짝만
        g2.setColor(new Color(0, 0, 0, 120)); // 150 → 120
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

    /** 배경용 타일 (블록보다 훨씬 차분한 3D) */
    private void drawBackgroundCell(Graphics2D g2, int gridX, int gridY, int cellSize) {
        int px = gridX * cellSize + CELL_GAP;
        int py = gridY * cellSize + CELL_GAP;
        int size = cellSize - CELL_GAP * 2;

        // 1) 전체 네모 기본색 (BG_TILE_COLOR)
        g2.setColor(BG_TILE_COLOR);
        g2.fillRect(px, py, size, size);

        // 2) 안쪽 네모 – 블록처럼 입체지만 대비는 약하게
        int innerPad = size / 5;
        int innerX = px + innerPad;
        int innerY = py + innerPad;
        int innerSize = size - innerPad * 2;

        // 중심 색
        Color center = BG_TILE_COLOR;
        g2.setColor(center);
        g2.fillRect(innerX, innerY, innerSize, innerSize);

        // facet 들은 “아주 약한” 하이라이트/그림자만
        // top
        g2.setColor(new Color(230, 230, 240, 10)); // 살짝만 밝게
        g2.fillPolygon(new int[] { px, px + size, innerX + innerSize, innerX },
                new int[] { py, py, innerY, innerY }, 4);

        // bottom
        g2.setColor(new Color(0, 0, 0, 40)); // 살짝만 어둡게
        g2.fillPolygon(new int[] { px, px + size, innerX + innerSize, innerX },
                new int[] { py + size, py + size, innerY + innerSize, innerY + innerSize }, 4);

        // left
        g2.setColor(new Color(220, 220, 230, 5));
        g2.fillPolygon(new int[] { px, px, innerX, innerX },
                new int[] { py, py + size, innerY + innerSize, innerY }, 4);

        // right
        g2.setColor(new Color(0, 0, 0, 35));
        g2.fillPolygon(new int[] { px + size, px + size, innerX + innerSize, innerX + innerSize },
                new int[] { py, py + size, innerY + innerSize, innerY }, 4);
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

    // Settings 업데이트 메서드 추가
    public void updateSettings(Settings settings) {
        this.settings = settings;

        // 셀 크기 변경 시 BoardLogic에도 알려주기
        int cellSize = CELL_SIZE;
        if (settings != null) {
            cellSize = switch (settings.screenSize) {
                case SMALL -> 20;
                case MEDIUM -> 25;
                case LARGE -> 30;
            };
        }
        logic.setCellSize(cellSize);

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

        // 현재 셀 크기 사용
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

                    // 더 이상 버튼은 못 누르게 리스너만 제거
                    removeMouseListener(this);
                    removeMouseMotionListener(this);
                    setCursor(Cursor.getDefaultCursor());

                    // 이름 입력 오버레이 띄우기
                    if (gameOverConfirmAction != null) {
                        gameOverConfirmAction.run();
                    }

                    // 보드 다시 그리기
                    repaint();
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

    private void drawAwards(Graphics2D g2, int cellSize) {
        if (awards.isEmpty())
            return;

        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

        int boardWidth = getWidth();
        int baseY = 100; // 보드 상단에서 100픽셀 아래

        for (AwardNotification award : awards) {
            if (award.alpha <= 0)
                continue;

            // 폰트 설정 (스케일 적용)
            int scaledSize = (int) (award.fontSize * award.scale);
            Font font = new Font("Arial Black", Font.BOLD, scaledSize);
            g2.setFont(font);

            FontMetrics fm = g2.getFontMetrics();
            int textWidth = fm.stringWidth(award.text);
            int x = (boardWidth - textWidth) / 2;
            int y = baseY - award.offsetY;

            // 그림자 효과 (더 진하게)
            g2.setColor(new Color(0, 0, 0, (int) (180 * award.alpha)));
            g2.drawString(award.text, x + 3, y + 3);

            // 외곽선 효과 (더 두껍게)
            g2.setColor(new Color(0, 0, 0, (int) (200 * award.alpha)));
            g2.setStroke(new BasicStroke(4f));
            for (int dx = -1; dx <= 1; dx++) {
                for (int dy = -1; dy <= 1; dy++) {
                    if (dx != 0 || dy != 0) {
                        g2.drawString(award.text, x + dx * 2, y + dy * 2);
                    }
                }
            }

            // 메인 텍스트
            g2.setColor(new Color(
                    award.color.getRed(),
                    award.color.getGreen(),
                    award.color.getBlue(),
                    (int) (255 * award.alpha)));
            g2.drawString(award.text, x, y);

            // 빛나는 효과 (선택적)
            if (award.alpha > 0.7f) {
                g2.setColor(new Color(255, 255, 255, (int) (100 * award.alpha)));
                Font glowFont = new Font("Arial Black", Font.BOLD, scaledSize + 2);
                g2.setFont(glowFont);
                g2.drawString(award.text, x, y);
            }
        }
    }

    /**
     * 어워드 표시 (공개 메서드)
     */
    public void showAward(String text, Color color, int fontSize) {
        awards.add(new AwardNotification(text, color, fontSize));
        repaint();
    }

    /**
     * 콤보 표시
     */
    public void showCombo(int combo) {
        if (combo < 2)
            return;

        Color color;
        if (combo >= 5) {
            color = new Color(255, 100, 255); // 보라색
        } else if (combo >= 3) {
            color = new Color(255, 150, 50); // 주황색
        } else {
            color = new Color(100, 200, 255); // 파란색
        }

        showAward("COMBO x" + combo + "!", color, 32);
    }

    /**
     * Double/Triple/Tetris 표시
     */
    public void showLineClear(int lines) {
        switch (lines) {
            case 2 -> showAward("DOUBLE!", new Color(100, 200, 255), 28);
            case 3 -> showAward("TRIPLE!", new Color(150, 100, 255), 30);
            case 4 -> showAward("TETRIS!!", new Color(255, 100, 100), 36);
        }
    }

    /**
     * Perfect Clear 표시
     */
    public void showPerfectClear() {
        showAward("PERFECT CLEAR!!", new Color(255, 215, 0), 40);
    }

    /**
     * Back-to-Back 표시
     */
    public void showBackToBack() {
        showAward("BACK-TO-BACK!", new Color(255, 50, 150), 34);
    }

    public void showSpeedUp(int level) {
        showAward("SPEED UP! Lv." + level, new Color(255, 165, 0), 30);
    }
}