package component.items;

import java.awt.Color;

import blocks.Block;
import logic.BoardLogic;
import logic.ClearService;
import logic.ParticleSystem;

/**
 * 💥 ColorBombItem (색 폭탄)
 *
 * - 자기와 같은 색의 모든 블록 제거
 * - 테두리 부분만 폭발 파티클
 * - 점수 증가 (삭제 블록 × 10)
 * - **새 중력 시스템 적용: applyGravityInstantly() → clearLines()**
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
        var clear = logic.getClearService();

        if (clear != null) {
            clear.setSkipDuringItem(true); // 아이템 중력 스킵 활성화
        }

        ParticleSystem ps = (clear != null ? clear.getParticleSystem() : null);
        Color targetColor = this.color;
        int removed = 0;
        final int CELL_SIZE = 25;

        // ===========================================
        // 1) 색상 일치하는 모든 블록 삭제 + 파티클 생성
        // ===========================================
        for (int y = 0; y < BoardLogic.HEIGHT; y++) {
            for (int x = 0; x < BoardLogic.WIDTH; x++) {
                if (board[y][x] != null && board[y][x].equals(targetColor)) {

                    boolean isEdge =
                        (x == 0 || x == BoardLogic.WIDTH - 1
                         || (x > 0 && !targetColor.equals(board[y][x - 1]))
                         || (x < BoardLogic.WIDTH - 1 && !targetColor.equals(board[y][x + 1])));

                    if (isEdge && ps != null) {
                        ps.createExplosionParticles(x, y, targetColor, CELL_SIZE);
                    }

                    board[y][x] = null;
                    removed++;
                }
            }
        }

        // 점수 증가
        if (removed > 0) {
            logic.addScore(removed * 10);
        }

        // ===========================================
        // 2) testMode: 모든 것을 즉시 처리
        // ===========================================
        if (testMode) {
            if (clear != null) {
                clear.setSkipDuringItem(false);
                clear.applyGravityInstantly();
                clear.clearLines(() -> {}, onComplete);
            } else if (onComplete != null) {
                onComplete.run();
            }
            return;
        }

        // ===========================================
        // 3) 파티클 애니메이션 (있으면)
        // ===========================================
        if (ps != null && logic.getOnFrameUpdate() != null) {
            startParticleAnimation(ps, logic);
        }

        // ===========================================
        // 4) 흔들림 효과 (Shake)
        // ===========================================
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

      
        if (clear != null) {
            clear.setSkipDuringItem(false);

            // 1) 즉시 중력 (클러스터 중력 + 라인 압축)
            clear.applyGravityInstantly();

            // 2) 추가로 만들어진 줄이 있으면 지우기
            clear.clearLines(
                logic.getOnFrameUpdate(),
                () -> {
                    if (onComplete != null)
                        onComplete.run();
                }
            );
        }
    }

    // ==================================================
    // 파티클 애니메이션
    // ==================================================
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

        timer.start();
    }
}
