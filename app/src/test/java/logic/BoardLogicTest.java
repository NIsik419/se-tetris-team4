package logic;

import component.GameConfig;
import org.junit.Before;
import org.junit.Test;

import java.awt.Color;
import java.util.List;

import static org.junit.Assert.*;

public class BoardLogicTest {

    private BoardLogic logic;

    @Before
    public void setUp() {
        // 게임오버 콜백은 더미로 전달
        logic = new BoardLogic(score -> {
        }, GameConfig.Difficulty.NORMAL);

        // UI, 사운드, 애니메이션 끄기
        logic.setAnimatedGravityEnabled(false);
    }

    // -------------------------------
    // 1. addScore
    // -------------------------------
    @Test
    public void testAddScore() {
        int before = logic.getScore();
        logic.addScore(100);
        assertEquals(before + 100, logic.getScore());
    }

    // -------------------------------
    // 2. set/get CellSize
    // -------------------------------
    @Test
    public void testCellSize() {
        logic.setCellSize(40);
        assertEquals(40, logic.getCellSize());
    }

    // -------------------------------
    // 3. addGarbageMasks → 큐에 마스크가 들어가는지
    // -------------------------------
    @Test
    public void testAddGarbageMasks() {
        int[] masks = { 0b11110000, 0b00001111 };

        logic.addGarbageMasks(masks);

        // apply 전에 queue 사이즈만 체크
        assertEquals(2, logic.getIncomingQueueSize());
    }

    // -------------------------------
    // 4. applyIncomingGarbage – 실제 garbage row 적용
    // -------------------------------
    @Test
    public void testApplyIncomingGarbage() {
        logic.addGarbageMasks(new int[] {
                0b1111111111, // full
                0b0000000000 // empty
        });

        logic.testApplyIncomingGarbage();
        Color[][] board = logic.getBoard();

        // 맨 아래는 empty mask → null
        for (int x = 0; x < BoardLogic.WIDTH; x++) {
            assertNull(board[BoardLogic.HEIGHT - 1][x]);
        }

        // 그 위가 full garbage
        for (int x = 0; x < BoardLogic.WIDTH; x++) {
            assertNotNull(board[BoardLogic.HEIGHT - 2][x]);
        }
    }

    // -------------------------------
    // 5. reset() – 완전 초기화 확인
    // -------------------------------
    @Test
    public void testReset() {
        logic.addScore(500);
        logic.addGarbageMasks(new int[] { 0b11110000 });
        logic.testApplyIncomingGarbage();

        logic.reset();

        assertEquals(0, logic.getScore());
        assertEquals(0, logic.getLinesCleared());
        assertNotNull(logic.getCurr());
    }

    // -------------------------------
    // 6. buildAttackMasks – 최근Placed 제외 & 가비지 제외
    // -------------------------------
    @Test
    public void testBuildAttackMasks() throws Exception {
        // 보드 직접 조작
        Color[][] board = logic.getBoard();
        board[19][0] = Color.RED;
        board[19][1] = Color.RED;
        board[19][2] = Color.RED;
        board[19][3] = Color.RED;

        // recentPlaced를 건드려서 제외할 칸 하나 만들기
        logic.getRecentPlacedForTest()[19][2] = true;

        // reflection으로 private 메서드 호출
        var method = BoardLogic.class.getDeclaredMethod("buildAttackMasks", List.class);
        method.setAccessible(true);

        int[] masks = (int[]) method.invoke(logic, List.of(19));

        // 19행의 공격 마스크 → recentPlaced 제외 → bit2 빠져야 함
        // RED RED (skip) RED RED → 11011 (skip middle)
        assertEquals(0b00001011, masks[0]);
    }
}
