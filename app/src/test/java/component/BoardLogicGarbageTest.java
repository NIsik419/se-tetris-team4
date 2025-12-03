package component;

import component.GameConfig;
import logic.BoardLogic;  
import org.junit.Test;

import java.awt.Color;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.*;

/**
 * BoardLogic 의 가비지(공격 줄) 관련 로직 테스트.
 *
 * - addGarbageMasks() 로 incoming 큐 / HUD / 프리뷰가 어떻게 바뀌는지
 * - testApplyIncomingGarbage() 로 실제 보드에 가비지가 적용되는지
 *   (incoming 큐가 비워지고, HUD/프리뷰가 0으로 갱신되는지)
 */
public class BoardLogicGarbageTest {

    /** NORMAL 난이도로 BoardLogic 하나 생성하는 헬퍼 */
    private BoardLogic createLogic() {
        return new BoardLogic(score -> {
            // 테스트에서는 게임오버 콜백 사용 안 함
        }, GameConfig.Difficulty.NORMAL);
    }

    /**
     * addGarbageMasks() 호출 시
     * - incoming 큐 길이가 늘어나고
     * - onIncomingChanged / onGarbagePreviewChanged 콜백이 올바르게 불리는지 확인.
     */
    @Test
    public void testAddGarbageMasks_enqueuesAndBuildsPreview() {
        BoardLogic logic = createLogic();

        AtomicInteger lastIncoming = new AtomicInteger(-1);
        AtomicReference<List<boolean[]>> previewRef = new AtomicReference<>();

        logic.setOnIncomingChanged(lastIncoming::set);
        logic.setOnGarbagePreviewChanged(previewRef::set);

        int[] masks = new int[] {
                0b0000000011,  // index 0,1 이 1
                0b0000001010   // index 1,3 이 1
        };

        // when
        logic.addGarbageMasks(masks);

        // then - 큐 크기
        assertEquals("incoming 큐 길이는 2여야 한다", 2, logic.getIncomingQueueSize());
        assertEquals("onIncomingChanged 콜백도 2를 받아야 한다", 2, lastIncoming.get());

        // then - 프리뷰
        List<boolean[]> preview = previewRef.get();
        assertNotNull("가비지 프리뷰 리스트는 null 이 아니어야 한다", preview);
        assertEquals("프리뷰 라인 수는 2여야 한다", 2, preview.size());

        boolean[] row0 = preview.get(0);
        boolean[] row1 = preview.get(1);

        assertEquals("보드 너비와 동일해야 한다", BoardLogic.WIDTH, row0.length);
        assertEquals(BoardLogic.WIDTH, row1.length);

        // row0: ...0011  → index 0,1 이 true
        assertTrue(row0[0]);
        assertTrue(row0[1]);
        for (int x = 2; x < BoardLogic.WIDTH; x++) {
            assertFalse("row0 나머지는 false 여야 한다", row0[x]);
        }

        // row1: ...1010 → index 1,3 이 true
        assertFalse(row1[0]);
        assertTrue(row1[1]);
        assertFalse(row1[2]);
        assertTrue(row1[3]);
        for (int x = 4; x < BoardLogic.WIDTH; x++) {
            assertFalse("row1 나머지는 false 여야 한다", row1[x]);
        }
    }

    /**
     * addGarbageMasks() 로 큐에 쌓은 뒤
     * testApplyIncomingGarbage() 를 호출하면
     * - 보드 맨 아래 줄에 가비지 색이 칠해지고
     * - incoming 큐가 비워지며
     * - HUD/프리뷰 콜백이 0으로 갱신되는지 확인.
     */
    @Test
    public void testApplyIncomingGarbage_appliesToBoardAndClearsQueue() {
        BoardLogic logic = createLogic();

        AtomicInteger lastIncoming = new AtomicInteger(-1);
        AtomicReference<List<boolean[]>> previewRef = new AtomicReference<>();

        logic.setOnIncomingChanged(lastIncoming::set);
        logic.setOnGarbagePreviewChanged(previewRef::set);

        // 하나의 가비지 마스크만 사용 (index 0,1 칸 채움)
        int mask = 0b0000000011;
        logic.addGarbageMasks(new int[] { mask });

        assertEquals(1, logic.getIncomingQueueSize());

        // when: 실제 보드에 가비지를 적용
        logic.testApplyIncomingGarbage();

        // then - 큐/프리뷰/HUD
        assertEquals("적용 후 incoming 큐는 0이어야 한다", 0, logic.getIncomingQueueSize());
        assertEquals("onIncomingChanged 콜백도 0을 받아야 한다", 0, lastIncoming.get());

        List<boolean[]> preview = previewRef.get();
        assertNotNull(preview);
        assertTrue("적용 후 프리뷰는 비어 있어야 한다", preview.isEmpty());

        // then - 보드 맨 아래 줄 확인
        Color[][] board = logic.getBoard();
        Color garbageColor = new Color(80, 80, 80); // BoardLogic 의 GARBAGE_COLOR 와 동일

        int bottomY = BoardLogic.HEIGHT - 1;
        for (int x = 0; x < BoardLogic.WIDTH; x++) {
            boolean filled = ((mask >> x) & 1) != 0;
            if (filled) {
                assertEquals("가비지 칸은 GARBAGE_COLOR 이어야 한다",
                        garbageColor, board[bottomY][x]);
            } else {
                assertNull("구멍 칸은 null 이어야 한다", board[bottomY][x]);
            }
        }
    }

    /**
     * reset() 호출 시
     * - incoming 큐와 가비지 프리뷰가 모두 초기화되는지
     * - 점수와 클리어 라인 수가 0이 되는지 확인.
     */
    @Test
    public void testReset_clearsGarbageAndScore() {
        BoardLogic logic = createLogic();

        AtomicInteger lastIncoming = new AtomicInteger(-1);
        AtomicReference<List<boolean[]>> previewRef = new AtomicReference<>();

        logic.setOnIncomingChanged(lastIncoming::set);
        logic.setOnGarbagePreviewChanged(previewRef::set);

        // 가비지/점수/라인을 임의로 올려둠
        logic.addGarbageMasks(new int[] { 0b1111111111 });
        logic.setScore(1234);
        logic.addScore(100);
        // 일부러 한 번 적용해서 보드에도 가비지 만들어두기
        logic.testApplyIncomingGarbage();

        assertTrue("reset 전에는 incoming 큐가 0 이상이어야 한다",
                logic.getIncomingQueueSize() >= 0);

        // when
        logic.reset();

        // then
        assertEquals("reset 후 incoming 큐는 0이어야 한다", 0, logic.getIncomingQueueSize());
        assertEquals("HUD 콜백도 0을 받아야 한다", 0, lastIncoming.get());

        List<boolean[]> preview = previewRef.get();
        assertNotNull(preview);
        assertTrue("reset 후 프리뷰는 비어 있어야 한다", preview.isEmpty());

        assertEquals("점수도 0으로 초기화되어야 한다", 0, logic.getScore());
        assertEquals("클리어한 라인 수도 0이어야 한다", 0, logic.getLinesCleared());
    }
}
