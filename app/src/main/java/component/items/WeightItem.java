package component.items;

import java.awt.Color;
import logic.BoardLogic;
import logic.ClearService;
import logic.ParticleSystem;

/**
 * WeightItem (무게추형 아이템)
 *
 * - 폭 4칸, 높이 2줄
 * - 착지 시 자신의 폭 아래 모든 블록을 즉시 파괴
 * - 즉시 바닥까지 낙하
 * - 착지 후 일반 블록처럼 남음
 * - 회전 불가, 좌우 이동만 가능
 * - ⭐ 파괴된 블록마다 파티클 효과
 *
 * ✅ testMode: 테스트 환경일 때 비동기 Thread 없이 즉시 처리
 */
public class WeightItem extends ItemBlock {

    private boolean testMode = false;

    /** 테스트 시 즉시 처리 모드 설정 */
    public void setTestMode(boolean testMode) {
        this.testMode = testMode;
    }

    public WeightItem() {
        super(Color.ORANGE, new int[][]{
            {1, 1, 1, 1},
            {1, 1, 1, 1}
        });
        this.canRotate = false; // 회전 금지
    }

    @Override
    public void activate(BoardLogic logic, Runnable onComplete) {
        var board = logic.getBoard();
        var clearService = logic.getClearService();

        int startX = logic.getX();
        int w = width();
        int h = height();

        if (clearService != null)
            clearService.setSkipDuringItem(true);

        //  파티클 시스템 가져오기
        ParticleSystem particleSystem = clearService != null ? clearService.getParticleSystem() : null;

        // 폭 기준으로 아래 전부 제거 + 파티클 생성
        final int CELL_SIZE = 25; // BoardView와 동일
        for (int dx = 0; dx < w; dx++) {
            int bx = startX + dx;
            if (bx < 0 || bx >= BoardLogic.WIDTH) continue;
            
            for (int by = 0; by < BoardLogic.HEIGHT; by++) {
                // ⭐ 블록이 있으면 파티클 생성
                if (board[by][bx] != null && particleSystem != null) {
                    Color blockColor = board[by][bx];
                    // 테두리 블록만 파티클 생성 (옆이 비었거나 맨 끝)
                    boolean isEdge = (bx == 0 || bx == BoardLogic.WIDTH - 1 ||
                                     (bx > 0 && board[by][bx - 1] == null) ||
                                     (bx < BoardLogic.WIDTH - 1 && board[by][bx + 1] == null));
                    
                    if (isEdge) {
                        particleSystem.createExplosionParticles(bx, by, blockColor, CELL_SIZE);
                    }
                }
                
                // 블록 제거
                board[by][bx] = null;
            }
        }

        // 바닥 위치 계산
        int dropTo = BoardLogic.HEIGHT - h;

        // 본체를 바닥에 바로 그림
        for (int dy = 0; dy < h; dy++) {
            for (int dx = 0; dx < w; dx++) {
                int bx = startX + dx;
                int by = dropTo + dy;
                if (bx >= 0 && bx < BoardLogic.WIDTH && by >= 0 && by < BoardLogic.HEIGHT)
                    board[by][bx] = getColor();
            }
        }

        // === testMode일 경우 즉시 정리 및 완료 처리 ===
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

        // === 실제 게임용: 파티클 애니메이션 + 흔들림 ===
        
        // ⭐ 파티클 애니메이션 시작
        if (particleSystem != null && logic.getOnFrameUpdate() != null) {
            startParticleAnimation(particleSystem, logic);
        }

        // 흔들림 애니메이션
        if (logic.getOnFrameUpdate() != null) {
            new Thread(() -> {
                try {
                    for (int i = 0; i < 4; i++) {
                        logic.setShakeOffset((i % 2 == 0) ? 3 : -3);
                        logic.getOnFrameUpdate().run();
                        Thread.sleep(50);
                    }
                    logic.setShakeOffset(0);
                    logic.getOnFrameUpdate().run();
                } catch (InterruptedException ignored) {}
            }).start();
        }

        // 라인 정리
        if (clearService != null) {
            clearService.setSkipDuringItem(false);
            clearService.clearLines(logic.getOnFrameUpdate(), () -> {
                if (onComplete != null)
                    onComplete.run();
            });
        }
    }

    /**
     * ⭐ 파티클 애니메이션 시작 (백그라운드)
     */
    private void startParticleAnimation(ParticleSystem particleSystem, BoardLogic logic) {
        javax.swing.Timer particleTimer = new javax.swing.Timer(16, null);
        final int[] frame = { 0 };
        final int MAX_FRAMES = 20; // 약 320ms

        particleTimer.addActionListener(e -> {
            frame[0]++;
            particleSystem.update();

            if (logic.getOnFrameUpdate() != null)
                logic.getOnFrameUpdate().run();

            if (frame[0] >= MAX_FRAMES || particleSystem.getParticles().isEmpty()) {
                ((javax.swing.Timer) e.getSource()).stop();
                System.out.println("[WeightItem] Particle animation finished");
            }
        });

        particleTimer.setRepeats(true);
        particleTimer.start();
    }
}