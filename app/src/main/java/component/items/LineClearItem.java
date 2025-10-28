package component.items;

import java.awt.Color;
import java.util.Random;
import javax.swing.SwingUtilities;
import javax.swing.Timer;

import blocks.Block;
import logic.BoardLogic;
import logic.ClearService;

/**
 * LineClearItem
 * - 블록 내 한 칸에 'L'이 붙음 (무작위)
 * - 회전 시에도 L 위치가 함께 회전
 * - 착지 시 L이 위치한 줄 삭제 (꽉 차지 않아도)
 * - 삭제 후 위쪽 블록 중력 낙하
 */
public class LineClearItem extends ItemBlock {

    private final Block base;
    private int lX; // L 문자 위치 (shape 내 좌표)
    private int lY;
    private static final Random rand = new Random();

    public LineClearItem(Block base) {
        super(base.getColor(), base.getShapeArray());
        this.base = base;
        assignRandomL();
    }

    /** 무작위 L 위치 지정 */
    private void assignRandomL() {
        int h = shape.length, w = shape[0].length;
        int tx, ty;
        while (true) {
            tx = rand.nextInt(w);
            ty = rand.nextInt(h);
            if (shape[ty][tx] == 1)
                break;
        }
        this.lX = tx;
        this.lY = ty;
    }

    public int getLX() { return lX; }
    public int getLY() { return lY; }

    /** 회전 시 L 위치도 함께 회전 (시계 방향 기준) */
    @Override
    public void rotate() {
        super.rotate();

        int newH = shape.length;
        int newW = shape[0].length;
        int oldLX = lX;
        int oldLY = lY;

        // 회전 좌표 변환 (시계 방향)
        this.lX = newW - 1 - oldLY;
        this.lY = oldLX;

        // 혹시 비활성 칸(0)에 들어가면 다시 랜덤 배치
        if (shape[lY][lX] == 0) {
            assignRandomL();
        }
    }

    @Override
    public void activate(BoardLogic logic, Runnable onComplete) {
        int targetY = logic.getY() + lY;
        if (targetY < 0 || targetY >= BoardLogic.HEIGHT) {
            if (onComplete != null)
                onComplete.run();
            return;
        }

        var board = logic.getBoard();
        var clear = logic.getClearService();

        // 🧪 테스트 모드: 즉시 삭제 + 중력 적용
        if (testMode) {
            for (int x = 0; x < BoardLogic.WIDTH; x++)
                board[targetY][x] = null;

            safeApplyGravity(clear, targetY);
            logic.addScore(100);

            if (logic.getOnFrameUpdate() != null)
                logic.getOnFrameUpdate().run();
            if (onComplete != null)
                onComplete.run();
            return;
        }

        // 🎮 실제 게임 모드: 번쩍임 애니메이션 + 중력
        SwingUtilities.invokeLater(() -> {
            clear.animateSingleLineClear(targetY, logic.getOnFrameUpdate(), () -> {
                safeApplyGravity(clear, targetY);
                logic.addScore(100);
                if (logic.getOnFrameUpdate() != null)
                    logic.getOnFrameUpdate().run();
                if (onComplete != null)
                    onComplete.run();
            });
        });
    }

    /**
     * 안전한 중력 적용:
     * - 해당 줄만 삭제된 상태에서 위쪽 블록을 아래로 내림
     */
    private void safeApplyGravity(ClearService clear, int targetY) {
        if (clear == null) return;

        // 이미 다른 아이템이 중력 처리 중이면 → 잠시 후 재시도
        if (clear.isSkipDuringItem()) {
            new Timer(80, e -> {
                ((Timer) e.getSource()).stop();
                safeApplyGravity(clear, targetY);
            }).start();
            return;
        }

        // 중력 처리 시작
        clear.setSkipDuringItem(true);
        
        // 즉시 중력 적용 (targetY는 이미 삭제된 상태)
        clear.applyGravityFromRow(targetY);
        
        // 중력 완료
        clear.setSkipDuringItem(false);
    }
}