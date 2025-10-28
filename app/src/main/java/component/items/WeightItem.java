package component.items;

import java.awt.Color;
import logic.BoardLogic;
import logic.ClearService;

/**
 * WeightItem (무게추형 아이템)
 *
 * - 폭 4칸, 높이 2줄
 * - 착지 시 자신의 폭 아래 모든 블록을 즉시 파괴
 * - 즉시 바닥까지 낙하
 * - 착지 후 일반 블록처럼 남음
 * - 회전 불가, 좌우 이동만 가능
 */
public class WeightItem extends ItemBlock {

    public WeightItem() {
        super(Color.ORANGE, new int[][] {
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
        int startY = logic.getY();
        int w = width();
        int h = height();

        // ✅ 1️⃣ 중력/라인정리 잠시 비활성화
        if (clearService != null)
            clearService.setSkipDuringItem(true);

        // ✅ 2️⃣ 자신 포함, 폭 기준 아래 전부 제거
        for (int dx = 0; dx < w; dx++) {
            int bx = startX + dx;
            if (bx < 0 || bx >= BoardLogic.WIDTH) continue;
            for (int by = 0; by < BoardLogic.HEIGHT; by++) {
                board[by][bx] = null;
            }
        }

        // ✅ 3️⃣ 바닥 위치 계산 (맨 아래 두 줄)
        int dropTo = BoardLogic.HEIGHT - h;

        // ✅ 4️⃣ 본체 블록을 바닥에 바로 그리기
        for (int dy = 0; dy < h; dy++) {
            for (int dx = 0; dx < w; dx++) {
                int bx = startX + dx;
                int by = dropTo + dy;
                if (bx >= 0 && bx < BoardLogic.WIDTH && by >= 0 && by < BoardLogic.HEIGHT)
                    board[by][bx] = getColor();
            }
        }

        // ✅ 5️⃣ 즉시 프레임 갱신
        if (logic.getOnFrameUpdate() != null)
            logic.getOnFrameUpdate().run();

        // ✅ 6️⃣ 바로 라인 정리 호출
        if (clearService != null) {
            clearService.setSkipDuringItem(false);
            clearService.clearLines(logic.getOnFrameUpdate(), () -> {
                if (onComplete != null)
                    onComplete.run();
            });
        }
    }
}
