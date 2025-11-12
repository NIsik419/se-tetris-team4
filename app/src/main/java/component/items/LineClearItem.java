package component.items;

import java.awt.Color;
import java.util.Random;
import javax.swing.SwingUtilities;

import blocks.Block;
import logic.AnimationManager;
import logic.BoardLogic;
import logic.ClearService;

/**
 * LineClearItem (최적화 버전)
 * ------------------
 * - 블록 내 한 칸에 'L'이 붙음 (무작위)
 * - 회전 시에도 L 위치가 함께 회전
 * - 착지 시 L이 위치한 줄 삭제 (꽉 차지 않아도)
 * - 삭제 후 즉시 중력 적용 + 연쇄 클리어
 * - AnimationManager 통합 + NPE 방지
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

    public int getLX() {
        return lX;
    }

    public int getLY() {
        return lY;
    }

    /** 회전 시 L 위치도 함께 회전 */
    @Override
    public void rotate() {
        super.rotate();
        int newH = shape.length;
        int newW = shape[0].length;
        int oldLX = lX;
        int oldLY = lY;
        this.lX = newW - 1 - oldLY;
        this.lY = oldLX;
        if (shape[lY][lX] == 0)
            assignRandomL();
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
        var animMgr = logic.getAnimationManager();

        // ✅ 애니메이션 등록
        if (animMgr != null) {
            animMgr.tryStart(AnimationManager.AnimationType.ITEM_EFFECT);
        }

        // === [TEST MODE] ===
        if (testMode) {
            // ✅ 타겟 줄 즉시 삭제
            for (int x = 0; x < BoardLogic.WIDTH; x++)
                board[targetY][x] = null;
            
            // 중력 적용
            clear.applyLineGravity();
            logic.addScore(100);
            
            // 연쇄 클리어 체크
            int lines = clear.clearLines(safeGetFrameUpdate(logic), null);
            if (lines > 0)
                logic.addScore(lines * 100);
            
            safeCallFrameUpdate(logic);
            
            if (animMgr != null) {
                animMgr.finish(AnimationManager.AnimationType.ITEM_EFFECT);
            }
            
            if (onComplete != null)
                onComplete.run();
            return;
        }

        // === 실제 게임 모드 ===
        // ✅ 먼저 일반 라인 클리어가 있는지 확인
        int normalLines = clear.countFullLines();
        
        // 1️⃣ 일반 라인이 있으면 먼저 처리
        if (normalLines > 0) {
            // skipDuringItem을 false로 두고 일반 클리어 먼저 실행
            clear.clearLines(safeGetFrameUpdate(logic), () -> {
                // 일반 클리어 완료 후 LineClear 아이템 효과 실행
                executeLineClearEffect(logic, targetY, board, clear, animMgr, onComplete);
            });
        } else {
            // 일반 라인이 없으면 바로 LineClear 효과 실행
            executeLineClearEffect(logic, targetY, board, clear, animMgr, onComplete);
        }
    }
    
    /** LineClear 아이템 효과 실행 (분리) */
    private void executeLineClearEffect(BoardLogic logic, int targetY, Color[][] board, 
                                       ClearService clear, AnimationManager animMgr, Runnable onComplete) {
        // ✅ 애니메이션 시작 전에 먼저 줄 삭제
        for (int x = 0; x < BoardLogic.WIDTH; x++) {
            board[targetY][x] = null;
        }
        
        // 화면 갱신
        safeCallFrameUpdate(logic);
        
        SwingUtilities.invokeLater(() -> {
            // 삭제 애니메이션
            clear.animateSingleLineClear(targetY, safeGetFrameUpdate(logic), () -> {
                
                // 중력 적용
                clear.applyLineGravity();
                safeCallFrameUpdate(logic);
                
                // 점수 추가
                logic.addScore(100);
                
                // 연쇄 클리어 체크
                new javax.swing.Timer(50, ev -> {
                    ((javax.swing.Timer) ev.getSource()).stop();
                    
                    int lines = clear.clearLines(safeGetFrameUpdate(logic), null);
                    if (lines > 0) {
                        logic.addScore(lines * 100);
                    }
                    
                    safeCallFrameUpdate(logic);
                    
                    if (animMgr != null) {
                        animMgr.finish(AnimationManager.AnimationType.ITEM_EFFECT);
                    }
                    
                    if (onComplete != null)
                        onComplete.run();
                }).start();
            });
        });
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
}