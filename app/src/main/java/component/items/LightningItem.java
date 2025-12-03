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
 * - 랜덤 블록 10개 제거
 * - 번개 체인 효과
 * - 즉시 중력 + 연쇄 클리어
 */
public class LightningItem extends ItemBlock {

    private static final Random R = new Random();
    private static final int CELL_SIZE = 25;
    private static final int MAX_REMOVE_COUNT = 10;
    private static final int POINTS_PER_BLOCK = 30;
    
    // 애니메이션 설정
    private static final int CHAIN_DELAY_MS = 5;
    private static final int PARTICLE_FRAMES = 10;
    private static final int SHAKE_ITERATIONS = 2;
    private static final int SHAKE_OFFSET = 2;

    public LightningItem() {
        super(new Color(255, 240, 80), new int[][] {
                { 1, 1 },
                { 1, 1 }
        });
        this.canRotate = false;
    }

    @Override
    public void activate(BoardLogic logic, Runnable onComplete) {
        if (logic == null) {
            if (onComplete != null) onComplete.run();
            return;
        }

        var board = logic.getBoard();
        var clear = logic.getClearService();
        var animMgr = logic.getAnimationManager();
        
        if (board == null || clear == null) {
            if (onComplete != null) onComplete.run();
            return;
        }

        ParticleSystem particleSystem = clear.getParticleSystem();

        if (animMgr != null) {
            animMgr.tryStart(AnimationManager.AnimationType.ITEM_EFFECT);
        }

        // 테스트 모드
        if (testMode) {
            activateTestMode(logic, board, clear, onComplete);
            return;
        }

        // 실제 모드
        activateRealMode(logic, board, clear, animMgr, particleSystem, onComplete);
    }

    /**
     * 테스트 모드 활성화
     */
    private void activateTestMode(BoardLogic logic, Color[][] board, ClearService clear, Runnable onComplete) {
        List<Point> targets = findTargetBlocks(board, MAX_REMOVE_COUNT);

        if (targets.isEmpty()) {
            if (onComplete != null) onComplete.run();
            return;
        }

        removeBlocks(board, targets);
        clear.applyGravityInstantly();
        logic.addScore(targets.size() * POINTS_PER_BLOCK);

        if (onComplete != null) onComplete.run();
    }

    /**
     * 실제 모드 활성화
     */
    private void activateRealMode(BoardLogic logic, Color[][] board, ClearService clear, 
                                  AnimationManager animMgr, ParticleSystem particleSystem, 
                                  Runnable onComplete) {
        List<Point> targets = findTargetBlocks(board, MAX_REMOVE_COUNT);

        if (targets.isEmpty()) {
            finishAnimation(animMgr, onComplete);
            return;
        }

        List<Point> orderedTargets = orderTargetsByDistance(targets);
        
        startChainAnimation(logic, board, orderedTargets, particleSystem, animMgr, onComplete);
    }

    /**
     * 타겟 블록 찾기
     */
    private List<Point> findTargetBlocks(Color[][] board, int maxCount) {
        List<Point> filled = new ArrayList<>();
        
        for (int y = 0; y < GameState.HEIGHT; y++) {
            for (int x = 0; x < GameState.WIDTH; x++) {
                if (board[y][x] != null) {
                    filled.add(new Point(x, y));
                }
            }
        }

        if (filled.isEmpty()) {
            return filled;
        }

        Collections.shuffle(filled);
        int removeCount = Math.min(maxCount, filled.size());
        return new ArrayList<>(filled.subList(0, removeCount));
    }

    /**
     * 타겟을 거리 기준으로 정렬 (번개 체인 효과)
     */
    private List<Point> orderTargetsByDistance(List<Point> targets) {
        if (targets.isEmpty()) {
            return new ArrayList<>();
        }

        Point start = targets.get(0);
        List<Point> ordered = new ArrayList<>();
        ordered.add(start);
        List<Point> remaining = new ArrayList<>(targets);
        remaining.remove(start);

        while (!remaining.isEmpty()) {
            Point last = ordered.get(ordered.size() - 1);
            Point next = findNearestPoint(last, remaining);
            if (next == null) break;
            
            ordered.add(next);
            remaining.remove(next);
        }

        return ordered;
    }

    /**
     * 가장 가까운 점 찾기
     */
    private Point findNearestPoint(Point from, List<Point> candidates) {
        return candidates.stream()
                .min(Comparator.comparingDouble(p -> 
                    Math.pow(p.x - from.x, 2) + Math.pow(p.y - from.y, 2)))
                .orElse(null);
    }

    /**
     * 블록 제거
     */
    private void removeBlocks(Color[][] board, List<Point> targets) {
        for (Point p : targets) {
            if (isValidPosition(p)) {
                board[p.y][p.x] = null;
            }
        }
    }

    /**
     * 번개 체인 애니메이션 시작
     */
    private void startChainAnimation(BoardLogic logic, Color[][] board, 
                                     List<Point> orderedTargets, ParticleSystem particleSystem,
                                     AnimationManager animMgr, Runnable onComplete) {
        new Thread(() -> {
            try {
                Color[][] fadeLayer = logic.getFadeLayer();
                int removeCount = orderedTargets.size();

                // 1. 번개 체인 애니메이션
                performChainAnimation(board, fadeLayer, orderedTargets, particleSystem, logic);

                // 2. 페이드아웃
                clearFadeLayer(fadeLayer, orderedTargets);
                safeCallFrameUpdate(logic);

                // 3. 전체 페이드 클리어
                clearAllFade(fadeLayer);
                safeCallFrameUpdate(logic);

                // 4. 파티클 애니메이션 (백그라운드)
                if (particleSystem != null) {
                    startParticleAnimation(particleSystem, logic);
                }

                // 5. 화면 흔들림
                shakeGamePanel(logic);

                // 6. 중력 적용 및 완료
                javax.swing.SwingUtilities.invokeLater(() -> {
                    applyGravityAndFinish(logic, animMgr, removeCount, onComplete);
                });

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                finishAnimation(animMgr, onComplete);
            }
        }).start();
    }

    /**
     * 체인 애니메이션 수행
     */
    private void performChainAnimation(Color[][] board, Color[][] fadeLayer, 
                                       List<Point> ordered, ParticleSystem particleSystem,
                                       BoardLogic logic) throws InterruptedException {
        for (int i = 0; i < ordered.size(); i++) {
            Point p = ordered.get(i);
            
            // 번개 파티클 생성
            createLightningEffect(board, p, particleSystem);
            
            // 블록 제거
            if (isValidPosition(p)) {
                board[p.y][p.x] = null;
                fadeLayer[p.y][p.x] = new Color(200, 240, 255, 255);
            }

            // 체인 연결 효과
            if (i > 0) {
                drawChainConnection(ordered.get(i - 1), p, fadeLayer);
            }

            // 주변 잔광
            drawSurroundingGlow(p, fadeLayer);

            safeCallFrameUpdate(logic);
            Thread.sleep(CHAIN_DELAY_MS);
        }
    }

    /**
     * 번개 효과 생성
     */
    private void createLightningEffect(Color[][] board, Point p, ParticleSystem particleSystem) {
        if (particleSystem != null && isValidPosition(p)) {
            Color blockColor = board[p.y][p.x];
            if (blockColor != null) {
                particleSystem.createLightningParticles(p.x, p.y, blockColor, CELL_SIZE);
            }
        }
    }

    /**
     * 체인 연결 그리기
     */
    private void drawChainConnection(Point prev, Point curr, Color[][] fadeLayer) {
        int dx = curr.x - prev.x;
        int dy = curr.y - prev.y;
        int midX = prev.x + dx / 2 + R.nextInt(3) - 1;
        int midY = prev.y + dy / 2 + R.nextInt(3) - 1;

        if (isValidPosition(new Point(midX, midY))) {
            fadeLayer[midY][midX] = new Color(180, 220, 255, 180);
        }
    }

    /**
     * 주변 잔광 그리기
     */
    private void drawSurroundingGlow(Point p, Color[][] fadeLayer) {
        for (int dy = -1; dy <= 1; dy++) {
            for (int dx = -1; dx <= 1; dx++) {
                if (dx == 0 && dy == 0) continue;
                
                int nx = p.x + dx;
                int ny = p.y + dy;
                
                if (isValidPosition(new Point(nx, ny))) {
                    fadeLayer[ny][nx] = new Color(150, 200, 255, 100);
                }
            }
        }
    }

    /**
     * 페이드 레이어 클리어 (특정 포인트)
     */
    private void clearFadeLayer(Color[][] fadeLayer, List<Point> points) {
        for (Point p : points) {
            if (isValidPosition(p)) {
                fadeLayer[p.y][p.x] = null;
            }
        }
    }

    /**
     * 전체 페이드 레이어 클리어
     */
    private void clearAllFade(Color[][] fadeLayer) {
        for (int y = 0; y < GameState.HEIGHT; y++) {
            for (int x = 0; x < GameState.WIDTH; x++) {
                fadeLayer[y][x] = null;
            }
        }
    }

    /**
     * 파티클 애니메이션 시작
     */
    private void startParticleAnimation(ParticleSystem particleSystem, BoardLogic logic) {
        javax.swing.Timer particleTimer = new javax.swing.Timer(16, null);
        final int[] frame = { 0 };

        particleTimer.addActionListener(e -> {
            frame[0]++;
            particleSystem.update();

            safeCallFrameUpdate(logic);

            if (frame[0] >= PARTICLE_FRAMES || particleSystem.getParticleCount() == 0) {
                ((javax.swing.Timer) e.getSource()).stop();
                particleSystem.clear();
                safeCallFrameUpdate(logic);
            }
        });

        particleTimer.setRepeats(true);
        particleTimer.start();
    }

    /**
     * 화면 흔들림 효과
     */
    private void shakeGamePanel(BoardLogic logic) {
        new Thread(() -> {
            try {
                for (int i = 0; i < SHAKE_ITERATIONS; i++) {
                    logic.setShakeOffset((i % 2 == 0) ? SHAKE_OFFSET : -SHAKE_OFFSET);
                    safeCallFrameUpdate(logic);
                    Thread.sleep(10);
                }
                logic.setShakeOffset(0);
                safeCallFrameUpdate(logic);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }).start();
    }

    /**
     * 중력 적용 및 완료
     */
    private void applyGravityAndFinish(BoardLogic logic, AnimationManager animMgr, 
                                       int removeCount, Runnable onComplete) {
        logic.applySimpleCellGravity();
        logic.addScore(removeCount * POINTS_PER_BLOCK);
        
        finishAnimation(animMgr, onComplete);
        
        logic.checkAndClearLinesAfterItem(onComplete);
    }

    /**
     * 애니메이션 종료
     */
    private void finishAnimation(AnimationManager animMgr, Runnable onComplete) {
        if (animMgr != null) {
            animMgr.finish(AnimationManager.AnimationType.ITEM_EFFECT);
        }
        if (onComplete != null) {
            onComplete.run();
        }
    }

    /**
     * 유효한 위치인지 확인
     */
    private boolean isValidPosition(Point p) {
        return p != null && 
               p.x >= 0 && p.x < GameState.WIDTH && 
               p.y >= 0 && p.y < GameState.HEIGHT;
    }

    /**
     * 안전한 프레임 업데이트 호출
     */
    private void safeCallFrameUpdate(BoardLogic logic) {
        if (logic != null) {
            Runnable update = logic.getOnFrameUpdate();
            if (update != null) {
                update.run();
            }
        }
    }

    public static String getSymbol() {
        return "⚡";
    }

    // 테스트용 getter
    int getMaxRemoveCount() {
        return MAX_REMOVE_COUNT;
    }

    int getPointsPerBlock() {
        return POINTS_PER_BLOCK;
    }
}