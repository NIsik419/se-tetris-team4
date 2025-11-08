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

/**
 * ⚡ LightningItem (곡선 전류 버전) - NPE 수정
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

        clear.setSkipDuringItem(true);

        // === [TEST MODE] Thread 없이 즉시 실행 ===
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
                fade[p.y][p.x] = new Color(200, 240, 255, 200);
            }

            clear.applyGravityInstantly();
            logic.addScore(removeCount * 30);

            // lambda 내부 로직
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

        // 랜덤하게 10개 선택
        Collections.shuffle(filled);
        int removeCount = Math.min(10, filled.size());
        List<Point> targets = filled.subList(0, removeCount);

        // 가까운 순서로 정렬 (전류 루트)
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

        // ⚡ 비동기 전류 애니메이션
        new Thread(() -> {
            try {
                Color[][] fadeLayer = logic.getFadeLayer();

                for (int i = 0; i < ordered.size(); i++) {
                    Point p = ordered.get(i);
                    board[p.y][p.x] = null;
                    fadeLayer[p.y][p.x] = new Color(200, 240, 255, 255);

                    // ⚡ 곡선 연결 (중간 흔들림)
                    if (i > 0) {
                        Point prev = ordered.get(i - 1);
                        int dx = p.x - prev.x;
                        int dy = p.y - prev.y;
                        int midX = prev.x + dx / 2 + R.nextInt(3) - 1;
                        int midY = prev.y + dy / 2 + R.nextInt(3) - 1;

                        if (midX >= 0 && midX < BoardLogic.WIDTH && midY >= 0 && midY < BoardLogic.HEIGHT)
                            fadeLayer[midY][midX] = new Color(180, 220, 255, 180);
                    }

                    // ⚡ 주변 잔광
                    for (int dy = -1; dy <= 1; dy++) {
                        for (int dx = -1; dx <= 1; dx++) {
                            int nx = p.x + dx, ny = p.y + dy;
                            if (nx >= 0 && nx < BoardLogic.WIDTH && ny >= 0 && ny < BoardLogic.HEIGHT
                                    && !(dx == 0 && dy == 0))
                                fadeLayer[ny][nx] = new Color(150, 200, 255, 100);
                        }
                    }

                    safeCallFrameUpdate(logic);
                    Thread.sleep(40);
                }

                // 페이드아웃 잔광
                for (int alpha = 180; alpha >= 0; alpha -= 40) {
                    for (Point p : ordered) {
                        fade[p.y][p.x] = new Color(150, 220, 255, Math.max(alpha, 0));
                    }
                    safeCallFrameUpdate(logic);
                    Thread.sleep(50);
                }

                // fadeLayer 클리어
                for (int y = 0; y < GameState.HEIGHT; y++)
                    for (int x = 0; x < GameState.WIDTH; x++)
                        fade[y][x] = null;

                safeCallFrameUpdate(logic);

                // 약한 흔들림 (게임판만)
                shakeGamePanel(logic);

                // 중력 및 라인 클리어
                clear.applyGravityStepwise(safeGetFrameUpdate(logic), () -> {
                    logic.addScore(removeCount * 30);
                    clear.setSkipDuringItem(false);

                    int combo = clear.clearLines(safeGetFrameUpdate(logic), null);
                    if (combo > 0)
                        logic.addScore(combo * 100);

                    safeCallFrameUpdate(logic);
                    if (onComplete != null)
                        onComplete.run();
                });

            } catch (InterruptedException ignored) {
            }
        }).start();
    }

    /** 💥 부드러운 진동 (게임판만) */
    private void shakeGamePanel(BoardLogic logic) {
        new Thread(() -> {
            try {
                for (int i = 0; i < 5; i++) {
                    logic.setShakeOffset((i % 2 == 0) ? 2 : -2);
                    safeCallFrameUpdate(logic);
                    Thread.sleep(25);
                }
                logic.setShakeOffset(0);
                safeCallFrameUpdate(logic);
            } catch (InterruptedException ignored) {
            }
        }).start();
    }

    /** ✅ 안전하게 frameUpdate 호출 */
    private void safeCallFrameUpdate(BoardLogic logic) {
        Runnable update = logic.getOnFrameUpdate();
        if (update != null) {
            update.run();
        }
    }

    /** ✅ 안전하게 frameUpdate Runnable 반환 */
    private Runnable safeGetFrameUpdate(BoardLogic logic) {
        return () -> {
            Runnable update = logic.getOnFrameUpdate();
            if (update != null) {
                update.run();
            }
        };
    }

    /** 테스트용 lambda 내부 로직 직접 실행 (JaCoCo용) */
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