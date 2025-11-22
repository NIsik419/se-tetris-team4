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
 * - ✅ 즉시 중력 + 연쇄 줄 클리어
 */
public class WeightItem extends ItemBlock {

    private boolean testMode = false;

    /** 테스트 시 즉시 처리 모드 설정 */
    public void setTestMode(boolean testMode) {
        this.testMode = testMode;
    }

    public WeightItem() {
        super(Color.ORANGE, new int[][] {
                { 1, 1, 1, 1 },
                { 1, 1, 1, 1 }
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

        // 파티클 시스템 가져오기
        ParticleSystem particleSystem = clearService != null ? clearService.getParticleSystem() : null;

        // ============================================
        // 1) 폭 기준으로 아래 전부 제거 + 파티클 생성
        // ============================================
        final int CELL_SIZE = 25;
        for (int dx = 0; dx < w; dx++) {
            int bx = startX + dx;
            if (bx < 0 || bx >= BoardLogic.WIDTH)
                continue;

            for (int by = 0; by < BoardLogic.HEIGHT; by++) {
                // 블록이 있으면 파티클 생성
                if (board[by][bx] != null && particleSystem != null) {
                    Color blockColor = board[by][bx];
                    // 테두리 블록만 파티클 생성
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

        // ============================================
        // 2) 본체를 바닥에 배치
        // ============================================
        int dropTo = BoardLogic.HEIGHT - h;
        for (int dy = 0; dy < h; dy++) {
            for (int dx = 0; dx < w; dx++) {
                int bx = startX + dx;
                int by = dropTo + dy;
                if (bx >= 0 && bx < BoardLogic.WIDTH && by >= 0 && by < BoardLogic.HEIGHT)
                    board[by][bx] = getColor();
            }
        }

        // 즉시 화면 갱신
        if (logic.getOnFrameUpdate() != null) {
            javax.swing.SwingUtilities.invokeLater(logic.getOnFrameUpdate());
        }

        // ============================================
        // 3) testMode일 경우 즉시 정리
        // ============================================
        if (testMode) {
            if (clearService != null) {
                clearService.applyGravityInstantly();
            }
            if (onComplete != null) {
                onComplete.run();
            }
            return;
        }

        // ============================================
        // 4) 파티클 애니메이션 (백그라운드)
        // ============================================
        if (particleSystem != null && logic.getOnFrameUpdate() != null) {
            startParticleAnimation(particleSystem, logic);
        }

        // ============================================
        // 5) 흔들림 효과
        // ============================================
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
                } catch (InterruptedException ignored) {
                }
            }).start();
        }

        // ============================================
        // 6) 즉시 중력 적용
        // ============================================
        if (clearService != null) {
            clearService.applyGravityInstantly();
        }

        // 화면 갱신
        if (logic.getOnFrameUpdate() != null) {
            javax.swing.SwingUtilities.invokeLater(logic.getOnFrameUpdate());
        }

        java.util.List<Integer> newFullRows = new java.util.ArrayList<>();
        for (int y = 0; y < BoardLogic.HEIGHT; y++) {
            boolean full = true;
            for (int x = 0; x < BoardLogic.WIDTH; x++) {
                if (board[y][x] == null) {
                    full = false;
                    break;
                }
            }
            if (full)
                newFullRows.add(y);
        }

        if (!newFullRows.isEmpty()) {
            System.out.println("[WeightItem] Found " + newFullRows.size() + " lines after gravity");
        }

        if (onComplete != null) {
            javax.swing.SwingUtilities.invokeLater(onComplete);
        }
    }

    /**
     * ⭐ 파티클 애니메이션 시작 (백그라운드)
     */
    private void startParticleAnimation(ParticleSystem particleSystem, BoardLogic logic) {
        javax.swing.Timer particleTimer = new javax.swing.Timer(16, null);
        final int[] frame = { 0 };
        final int MAX_FRAMES = 12;

        particleTimer.addActionListener(e -> {
            frame[0]++;
            particleSystem.update();

            if (logic.getOnFrameUpdate() != null)
                logic.getOnFrameUpdate().run();

            if (frame[0] >= MAX_FRAMES || particleSystem.getParticles().isEmpty()) {
                ((javax.swing.Timer) e.getSource()).stop();
                particleSystem.clear();
                System.out.println("[WeightItem] Particle animation finished");
            }
        });

        particleTimer.setRepeats(true);
        particleTimer.start();
    }
}