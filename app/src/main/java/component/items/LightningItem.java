package component.items;

import java.awt.Color;
import java.awt.Point;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Random;

import blocks.Block;
import logic.BoardLogic;
import logic.ClearService;
import logic.GameState;
import logic.AnimationManager;
import logic.ParticleSystem;

/**
 * ⚡ LightningItem (번개 파티클 버전)
 */
public class LightningItem extends ItemBlock {

    private static final Random R = new Random();

    public LightningItem() {
        super(new Color(255, 240, 80), new int[][] {
                { 1, 1 },
                { 1, 1 }
        });
        this.canRotate = false;
    }

    @Override
    public void activate(BoardLogic logic, Runnable onComplete) {
        var board = logic.getBoard();
        var fade = logic.getFadeLayer();
        var clear = logic.getClearService();
        var animMgr = logic.getAnimationManager();
        
        // ⭐ 파티클 시스템
        ParticleSystem particleSystem = clear.getParticleSystem();

        if (animMgr != null) {
            animMgr.tryStart(AnimationManager.AnimationType.ITEM_EFFECT);
        }

        clear.setSkipDuringItem(true);

        // === [TEST MODE] ===
        if (testMode) {
            List<Point> filled = new ArrayList<>();
            for (int y = 0; y < GameState.HEIGHT; y++) {
                for (int x = 0; x < GameState.WIDTH; x++) {
                    if (board[y][x] != null)
                        filled.add(new Point(x, y));
                }
            }

            if (filled.isEmpty()) {
                clear.setSkipDuringItem(false);
                if (onComplete != null)
                    onComplete.run();
                return;
            }

            int removeCount = Math.min(10, filled.size());
            for (int i = 0; i < removeCount; i++) {
                Point p = filled.get(i);
                board[p.y][p.x] = null;
            }

            clear.applyGravityInstantly();
            logic.addScore(removeCount * 30);

            clear.setSkipDuringItem(false);
            int combo = clear.clearLines(safeGetFrameUpdate(logic), null);
            if (combo > 0)
                logic.addScore(combo * 100);

            safeCallFrameUpdate(logic);
            if (onComplete != null)
                onComplete.run();

            return;
        }

        // === 실제 모드 ===
        List<Point> filled = new ArrayList<>();
        for (int y = 0; y < GameState.HEIGHT; y++) {
            for (int x = 0; x < GameState.WIDTH; x++) {
                if (board[y][x] != null)
                    filled.add(new Point(x, y));
            }
        }

        if (filled.isEmpty()) {
            clear.setSkipDuringItem(false);
            if (onComplete != null)
                onComplete.run();
            return;
        }

        // 랜덤하게 10개 선택 및 정렬
        Collections.shuffle(filled);
        int removeCount = Math.min(10, filled.size());
        List<Point> targets = filled.subList(0, removeCount);

        Point start = targets.get(0);
        List<Point> ordered = new ArrayList<>();
        ordered.add(start);
        List<Point> remaining = new ArrayList<>(targets);
        remaining.remove(start);

        while (!remaining.isEmpty()) {
            Point last = ordered.get(ordered.size() - 1);
            Point next = remaining.stream()
                    .min(Comparator.comparingDouble(p -> Math.pow(p.x - last.x, 2) + Math.pow(p.y - last.y, 2)))
                    .orElse(null);
            ordered.add(next);
            remaining.remove(next);
        }

        // ⚡ 비동기 전류 + 파티클 애니메이션
        new Thread(() -> {
            try {
                Color[][] fadeLayer = logic.getFadeLayer();
                final int CELL_SIZE = 25;

                for (int i = 0; i < ordered.size(); i++) {
                    Point p = ordered.get(i);
                    
                    // ⭐ 번개 파티클 생성
                    if (particleSystem != null) {
                        Color blockColor = board[p.y][p.x];
                        if (blockColor != null) {
                            particleSystem.createLightningParticles(p.x, p.y, blockColor, CELL_SIZE);
                        }
                    }
                    
                    board[p.y][p.x] = null;
                    fadeLayer[p.y][p.x] = new Color(200, 240, 255, 255);

                    // 곡선 연결
                    if (i > 0) {
                        Point prev = ordered.get(i - 1);
                        int dx = p.x - prev.x;
                        int dy = p.y - prev.y;
                        int midX = prev.x + dx / 2 + R.nextInt(3) - 1;
                        int midY = prev.y + dy / 2 + R.nextInt(3) - 1;

                        if (midX >= 0 && midX < BoardLogic.WIDTH && midY >= 0 && midY < BoardLogic.HEIGHT)
                            fadeLayer[midY][midX] = new Color(180, 220, 255, 180);
                    }

                    // 주변 잔광
                    for (int dy = -1; dy <= 1; dy++) {
                        for (int dx = -1; dx <= 1; dx++) {
                            int nx = p.x + dx, ny = p.y + dy;
                            if (nx >= 0 && nx < BoardLogic.WIDTH && ny >= 0 && ny < BoardLogic.HEIGHT
                                    && !(dx == 0 && dy == 0))
                                fadeLayer[ny][nx] = new Color(150, 200, 255, 100);
                        }
                    }

                    safeCallFrameUpdate(logic);
                    Thread.sleep(10);
                }

                // ⭐ 파티클 애니메이션 시작 (백그라운드)
                if (particleSystem != null) {
                    startLightningParticleAnimation(particleSystem, logic);
                }

                // 페이드아웃
                for (int alpha = 180; alpha >= 0; alpha -= 180) {
                    for (Point p : ordered) {
                        fade[p.y][p.x] = alpha > 0 
                            ? new Color(150, 220, 255, Math.max(alpha, 0))
                            : null;
                    }
                    safeCallFrameUpdate(logic);
                    Thread.sleep(15);
                }

                // fadeLayer 클리어
                for (int y = 0; y < GameState.HEIGHT; y++)
                    for (int x = 0; x < GameState.WIDTH; x++)
                        fade[y][x] = null;

                safeCallFrameUpdate(logic);

                // 흔들림
                shakeGamePanel(logic);

                // 중력 애니메이션
                applyCellGravityFast(logic, clear, () -> {
                    logic.addScore(removeCount * 30);
                    clear.setSkipDuringItem(false);

                    int combo = clear.clearLines(safeGetFrameUpdate(logic), null);
                    if (combo > 0)
                        logic.addScore(combo * 100);

                    safeCallFrameUpdate(logic);
                    
                    if (animMgr != null) {
                        animMgr.finish(AnimationManager.AnimationType.ITEM_EFFECT);
                    }
                    
                    if (onComplete != null)
                        onComplete.run();
                });

            } catch (InterruptedException ignored) {
            }
        }).start();
    }

    /**
     * ⚡ 번개 파티클 애니메이션 (백그라운드)
     */
    private void startLightningParticleAnimation(ParticleSystem particleSystem, BoardLogic logic) {
        javax.swing.Timer particleTimer = new javax.swing.Timer(16, null);
        final int[] frame = { 0 };
        final int MAX_FRAMES = 15; // 약 240ms (짧게)

        particleTimer.addActionListener(e -> {
            frame[0]++;
            particleSystem.update();

            if (logic.getOnFrameUpdate() != null)
                logic.getOnFrameUpdate().run();

            int remainingParticles = particleSystem.getParticleCount();
            if (frame[0] >= MAX_FRAMES || remainingParticles == 0) {
                ((javax.swing.Timer) e.getSource()).stop();
                particleSystem.clear();
                if (logic.getOnFrameUpdate() != null)
                    logic.getOnFrameUpdate().run();
                System.out.println("[LightningItem] Particle animation finished");
            }
        });

        particleTimer.setRepeats(true);
        particleTimer.start();
    }

    /** ⚡ 빠른 셀 단위 중력 */
    private void applyCellGravityFast(BoardLogic logic, ClearService clear, Runnable onComplete) {
        new Thread(() -> {
            try {
                Color[][] board = logic.getBoard();
                Color[][] fade = logic.getFadeLayer();
                boolean moved = true;

                while (moved) {
                    moved = false;

                    for (int y = 0; y < GameState.HEIGHT; y++)
                        for (int x = 0; x < GameState.WIDTH; x++)
                            fade[y][x] = null;

                    for (int y = GameState.HEIGHT - 2; y >= 0; y--) {
                        for (int x = 0; x < GameState.WIDTH; x++) {
                            if (board[y][x] != null && board[y + 1][x] == null) {
                                fade[y + 1][x] = new Color(
                                    board[y][x].getRed(),
                                    board[y][x].getGreen(),
                                    board[y][x].getBlue(), 100
                                );

                                board[y + 1][x] = board[y][x];
                                board[y][x] = null;
                                moved = true;
                            }
                        }
                    }

                    safeCallFrameUpdate(logic);
                    Thread.sleep(20);
                }

                for (int y = 0; y < GameState.HEIGHT; y++)
                    for (int x = 0; x < GameState.WIDTH; x++)
                        fade[y][x] = null;

                safeCallFrameUpdate(logic);

                if (onComplete != null)
                    onComplete.run();

            } catch (InterruptedException ignored) {
            }
        }).start();
    }

    /** 💥 부드러운 진동 */
    private void shakeGamePanel(BoardLogic logic) {
        new Thread(() -> {
            try {
                for (int i = 0; i < 2; i++) {
                    logic.setShakeOffset((i % 2 == 0) ? 2 : -2);
                    safeCallFrameUpdate(logic);
                    Thread.sleep(10);
                }
                logic.setShakeOffset(0);
                safeCallFrameUpdate(logic);
            } catch (InterruptedException ignored) {
            }
        }).start();
    }

    private void safeCallFrameUpdate(BoardLogic logic) {
        Runnable update = logic.getOnFrameUpdate();
        if (update != null) {
            update.run();
        }
    }

    private Runnable safeGetFrameUpdate(BoardLogic logic) {
        return () -> {
            Runnable update = logic.getOnFrameUpdate();
            if (update != null) {
                update.run();
            }
        };
    }

    public void runPostGravityTestHook(BoardLogic logic, ClearService clear, int removeCount, Runnable onComplete) {
        logic.addScore(removeCount * 30);
        clear.setSkipDuringItem(false);

        int combo = clear.clearLines(safeGetFrameUpdate(logic), null);
        if (combo > 0)
            logic.addScore(combo * 100);

        safeCallFrameUpdate(logic);
        if (onComplete != null)
            onComplete.run();
    }

    public static String getSymbol() {
        return "⚡";
    }
}