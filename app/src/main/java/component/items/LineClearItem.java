package component.items;

import java.awt.Color;
import java.util.Random;

import blocks.Block;
import logic.AnimationManager;
import logic.BoardLogic;
import logic.ClearService;
import logic.ParticleSystem;

/**
 * LineClearItem (부스러기 파티클 버전)
 * - 한 줄 전체 제거
 * - 양끝에서 부스러기가 떨어짐
 * - 즉시 중력 + 연쇄 처리
 */
public class LineClearItem extends ItemBlock {

    private final Block base;
    private int lX;
    private int lY;
    private static final Random rand = new Random();

    public LineClearItem(Block base) {
        super(base.getColor(), base.getShapeArray());
        this.base = base;
        assignRandomL();
    }

    /** L 위치 선택 */
    private void assignRandomL() {
        int h = shape.length, w = shape[0].length;
        int tx, ty;
        while (true) {
            tx = rand.nextInt(w);
            ty = rand.nextInt(h);
            if (shape[ty][tx] == 1)
                break;
        }
        lX = tx;
        lY = ty;
    }

    public int getLX() { return lX; }
    public int getLY() { return lY; }

    /** L 위치 회전 */
    @Override
    public void rotate() {
        super.rotate();
        int newW = shape[0].length;

        int oldLX = lX;
        int oldLY = lY;

        lX = newW - 1 - oldLY;
        lY = oldLX;

        if (shape[lY][lX] == 0)
            assignRandomL();
    }

    @Override
    public void activate(BoardLogic logic, Runnable onComplete) {

        int targetY = logic.getY() + lY;

        if (targetY < 0 || targetY >= BoardLogic.HEIGHT) {
            if (onComplete != null) onComplete.run();
            return;
        }

        var board = logic.getBoard();
        var clear = logic.getClearService();
        var animMgr = logic.getAnimationManager();

        if (animMgr != null)
            animMgr.tryStart(AnimationManager.AnimationType.ITEM_EFFECT);

        /* ===============================
         * TEST MODE
         * =============================== */
        if (testMode) {
            for (int x = 0; x < BoardLogic.WIDTH; x++)
                board[targetY][x] = null;

            clear.applyGravityInstantly();

            int lines = clear.clearLines(logic.getOnFrameUpdate(), null);
            if (lines > 0) logic.addScore(lines * 100);

            if (onComplete != null) onComplete.run();
            if (animMgr != null)
                animMgr.finish(AnimationManager.AnimationType.ITEM_EFFECT);

            return;
        }

        /* ===============================
         * 실제 게임
         * 1) 먼저 일반 줄이 있으면 clearLines() 먼저 처리
         * =============================== */
        if (clear.countFullLines() > 0) {
            clear.clearLines(
                    logic.getOnFrameUpdate(),
                    () -> runLineClearDebris(logic, targetY, clear, animMgr, onComplete)
            );
        } else {
            runLineClearDebris(logic, targetY, clear, animMgr, onComplete);
        }
    }

    /** ----------------------------------------
     *  ⭐ LineClearItem 부스러기 효과
     * ----------------------------------------
     *  실행 순서:
     *  1) 양끝 블록에 부스러기 파티클 생성
     *  2) targetY 줄 전체 삭제
     *  3) 즉시 중력 적용
     *  4) 연쇄 클리어
     *  5) 파티클 애니메이션 (백그라운드)
     */
    private void runLineClearDebris(
            BoardLogic logic,
            int targetY,
            ClearService clear,
            AnimationManager animMgr,
            Runnable onComplete
    ) {
        var board = logic.getBoard();
        ParticleSystem particleSystem = clear.getParticleSystem();

        //  1) 양끝 블록에만 부스러기 파티클 생성
        final int CELL_SIZE = 25;
        if (particleSystem != null) {
            for (int x = 0; x < BoardLogic.WIDTH; x++) {
                if (board[targetY][x] != null) {
                    // 테두리 블록 감지
                    boolean isEdge = (x == 0 || x == BoardLogic.WIDTH - 1 ||
                                     (x > 0 && board[targetY][x - 1] == null) ||
                                     (x < BoardLogic.WIDTH - 1 && board[targetY][x + 1] == null));
                    
                    if (isEdge) {
                        Color blockColor = board[targetY][x];
                        particleSystem.createDebrisParticles(x, targetY, blockColor, CELL_SIZE);
                    }
                }
            }
            
            System.out.println("[LineClearItem] Created " + particleSystem.getParticleCount() + " debris particles");
        }

        //  2) 줄 전체 즉시 삭제
        for (int x = 0; x < BoardLogic.WIDTH; x++) {
            board[targetY][x] = null;
        }

        // 3) 즉시 화면 갱신
        if (logic.getOnFrameUpdate() != null) {
            javax.swing.SwingUtilities.invokeLater(logic.getOnFrameUpdate());
        }

        //  4) 파티클 애니메이션 (백그라운드)
        if (particleSystem != null) {
            startDebrisAnimation(particleSystem, logic);
        }

        //  5) 즉시 중력 적용
        clear.applyGravityInstantly();

        //  6) 화면 갱신
        if (logic.getOnFrameUpdate() != null) {
            javax.swing.SwingUtilities.invokeLater(logic.getOnFrameUpdate());
        }

        //  7) 연쇄 클리어
        clear.clearLines(
            logic.getOnFrameUpdate(),
            () -> {
                if (animMgr != null)
                    animMgr.finish(AnimationManager.AnimationType.ITEM_EFFECT);

                if (onComplete != null)
                    onComplete.run();
            }
        );
    }

    /**
     * ⭐ 부스러기 애니메이션 (백그라운드)
     */
    private void startDebrisAnimation(ParticleSystem particleSystem, BoardLogic logic) {
        javax.swing.Timer particleTimer = new javax.swing.Timer(16, null);
        final int[] frame = { 0 };
        final int MAX_FRAMES = 12; //  20 → 12 (더 빠르게)

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
                System.out.println("[LineClearItem] Debris animation finished");
            }
        });

        particleTimer.setRepeats(true);
        particleTimer.start();
    }
}