package component.items;

import blocks.Block;
import java.awt.Color;
import logic.BoardLogic;

/**
 * ItemBlock: 아이템 블록의 공통 상위 클래스
 * - 모든 아이템은 착지 시 activate() 호출됨
 */
public abstract class ItemBlock extends Block {

    protected boolean testMode=false;

    public ItemBlock(Color color, int[][] shape) {
        super(color, shape);
    }

    // Block 기반 생성자 추가
    public ItemBlock(Block base) {
        super(base.getColor(), base.getShapeArray());
    }

    public void setTestMode(boolean enabled) {
        this.testMode = enabled;
    }

    /**
     * 아이템 발동 효과 (BoardLogic에서 호출)
     * 
     * @param logic      현재 게임 로직
     * @param onComplete 애니메이션 종료 후 실행할 콜백 (null 가능)
     */
    public abstract void activate(BoardLogic logic, Runnable onComplete);
}
