package component.items;

import java.awt.Color;

import blocks.Block;
import logic.BoardLogic;
import logic.ClearService;
import logic.ParticleSystem;

/**
 * 💥 ColorBombItem (색 폭탄)
 *
 * - 자기와 동일한 색상인 모든 블록을 제거
 * - 테두리 블록만 폭발 파티클 생성
 * - 점수(삭제 개수 × 10)
 * - 중력 적용 + 라인 정리
 *
 * ✅ testMode: 테스트 환경에서 즉시 실행
 */
public class ColorBombItem extends ItemBlock {

    private boolean testMode = false;

    public void setTestMode(boolean testMode) {
        this.testMode = testMode;
    }

    public ColorBombItem(Block base) {
        super(base.getColor(), base.getShapeArray());
    }

    @Override
    public void activate(BoardLogic logic, Runnable onComplete) {
        var board = logic.getBoard();
        var clearService = logic.getClearService();
        if (clearService != null)
            clearService.setSkipDuringItem(true);

        ParticleSystem particleSystem = clearService != null ? clearService.getParticleSystem() : null;

        Color targetColor = this.color;
        int removed = 0;
        final int CELL_SIZE = 25;

        // 같은 색상 모두 제거 + 파티클
        for (int y = 0; y < BoardLogic.HEIGHT; y++) {
            for (int x = 0; x < BoardLogic.WIDTH; x++) {
                if (board[y][x] != null && board[y][x].equals(targetColor)) {

                    boolean isEdge =
                            (x == 0 || x == BoardLogic.WIDTH - 1
                                    || (x > 0 && !targetColor.equals(board[y][x - 1]))
                                    || (x < BoardLogic.WIDTH - 1 && !targetColor.equals(board[y][x + 1])));

                    if (isEdge && particleSystem != null) {
                        particleSystem.createExplosionParticles(x, y, targetColor, CELL_SIZE);
                    }

                    board[y][x] = null;
                    removed++;
                }
            }
        }

        // 점수 반영
        if (removed > 0) {
            logic.addScore(removed * 10);
        }

        // === testMode 즉시 처리 ===
        if (testMode) {
            if (clearService != null) {
                clearService.setSkipDuringItem(false);
                clearService.applyGravityInstantly();
                clearService.clearLines(() -> {}, onComplete);
            } else if (onComplete != null) {
                onComplete.run();
            }
            return;
        }

        // === 실제 게임: 파티클 애니메이션 ===
        if (particleSystem != null && logic.getOnFrameUpdate() != null) {
            startParticleAnimation(particleSystem, logic);
        }

        // === 흔들림 효과 ===
        if (logic.getOnFrameUpdate() != null) {
            new Thread(() -> {
                try {
                    for (int i = 0; i < 4; i++) {
                        logic.setShakeOffset((i % 2 == 0) ? 2 : -2);
                        logic.getOnFrameUpdate().run();
                        Thread.sleep(50);
                    }
                    logic.setShakeOffset(0);
                    logic.getOnFrameUpdate().run();
                } catch (InterruptedException ignored) {}
            }).start();
        }

        // === 라인 클리어 + 완료 ===
        if (clearService != null) {
            clearService.setSkipDuringItem(false);
            clearService.applyCellGravity();
            clearService.clearLines(logic.getOnFrameUpdate(), () -> {
                if (onComplete != null)
                    onComplete.run();
            });
        }
    }

    /** 파티클 애니메이션 */
    private void startParticleAnimation(ParticleSystem ps, BoardLogic logic) {
        javax.swing.Timer timer = new javax.swing.Timer(16, null);
        final int[] frame = {0};
        final int MAX_FRAMES = 20;

        timer.addActionListener(e -> {
            frame[0]++;
            ps.update();

            if (logic.getOnFrameUpdate() != null)
                logic.getOnFrameUpdate().run();

            if (frame[0] >= MAX_FRAMES || ps.getParticles().isEmpty()) {
                ((javax.swing.Timer) e.getSource()).stop();
            }
        });

        timer.setRepeats(true);
        timer.start();
    }
}
