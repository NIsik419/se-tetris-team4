package component.items;

import java.awt.Color;
import blocks.Block;

public class SpinLockItem extends ItemBlock {

    public SpinLockItem(Block base) {
        super(base.getColor(), base.getShapeArray());
        this.canRotate = false; // 회전 완전 금지
    }

    @Override
    public void activate(logic.BoardLogic logic, Runnable onComplete) {
        // 회전 제한 아이템은 별도 효과 없이 “착지 즉시 일반 블록으로”
        if (onComplete != null)
            onComplete.run();
    }

    /** 시각적 포인트 (자물쇠 테두리용 표시) */
    public static String getSymbol() {
        return "🔒";
    }
}
    