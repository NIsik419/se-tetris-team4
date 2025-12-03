package component.items;

import blocks.Block;
import logic.BoardLogic;
import logic.GameState;
import org.junit.Before;
import org.junit.Test;

import java.awt.Color;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.Assert.*;

class DummyBlock extends Block {
    public DummyBlock(Color color, int[][] shape) {
        super(color, shape);
    }
}


public class ColorBombItemTest {

    private BoardLogic logic;
    private ColorBombItem item;

    @Before
    public void setUp() {
        logic = new BoardLogic(score -> {});
        logic.setItemMode(true);
        logic.setOnFrameUpdate(() -> {});

        Block base = new DummyBlock(Color.RED, new int[][]{
                {1, 1},
                {1, 1}
        });

        item = new ColorBombItem(base);
        item.setTestMode(true);
    }

    // ============================================================
    // 1) testMode = true (모든 로직 즉시 실행)
    // ============================================================
    @Test
    public void testActivate_Synchronous_TestMode() {
        Color[][] board = logic.getBoard();

        // RED만 깔기
        for (int y = GameState.HEIGHT - 5; y < GameState.HEIGHT; y++)
            for (int x = 0; x < GameState.WIDTH; x++)
                board[y][x] = Color.RED;

        int before = logic.getScore();
        item.activate(logic, null);
        int after = logic.getScore();

        assertTrue("Score should increase when blocks are removed", after > before);

        for (int y = GameState.HEIGHT - 5; y < GameState.HEIGHT; y++)
            for (int x = 0; x < GameState.WIDTH; x++)
                assertNull("All red blocks should be removed", board[y][x]);
    }

    // ============================================================
    // 2) 비동기 모드 + shake + gravity + clearLines 콜백 기다리기
    // ============================================================
    @Test
    public void testActivate_AsyncMode_Complete() throws Exception {
        Block base = new DummyBlock(Color.BLUE, new int[][]{
                {1}
        });
        ColorBombItem asyncItem = new ColorBombItem(base);
        asyncItem.setTestMode(false);

        Color[][] board = logic.getBoard();

        // BLUE로 채우기
        for (int y = GameState.HEIGHT - 3; y < GameState.HEIGHT; y++)
            for (int x = 0; x < GameState.WIDTH; x++)
                board[y][x] = Color.BLUE;

        AtomicBoolean done = new AtomicBoolean(false);
        Thread t = new Thread(() -> asyncItem.activate(logic, () -> done.set(true)));
        t.start();

        long start = System.currentTimeMillis();
        while (!done.get() && System.currentTimeMillis() - start < 3000) {
            Thread.sleep(20);
        }

        assertTrue("Callback should be called within 3 seconds", done.get());
        assertTrue("Score should increase", logic.getScore() > 0);
    }

    // ============================================================
    // 3) 특정 색만 제거되는지 테스트
    // ============================================================
    @Test
    public void testColorSelectiveRemoval() {
        Color[][] board = logic.getBoard();

        // RED + BLUE 섞어 놓기
        board[19][0] = Color.RED;
        board[19][1] = Color.BLUE;
        board[19][2] = Color.RED;
        board[19][3] = Color.BLUE;

        // bombColor = RED
        Block base = new DummyBlock(Color.RED, new int[][]{{1}});
        ColorBombItem testItem = new ColorBombItem(base);
        testItem.setTestMode(true);

        testItem.activate(logic, null);

        assertNull("RED block removed", board[19][0]);
        assertEquals("BLUE block preserved", Color.BLUE, board[19][1]);
        assertNull("RED block removed", board[19][2]);
        assertEquals("BLUE block preserved", Color.BLUE, board[19][3]);
    }

    // ============================================================
    // 4) Empty board 안정성 테스트
    // ============================================================
    @Test
    public void testActivate_EmptyBoard_NoError() {
        Color[][] board = logic.getBoard();

        for (int y = 0; y < GameState.HEIGHT; y++)
            for (int x = 0; x < GameState.WIDTH; x++)
                board[y][x] = null;

        AtomicBoolean done = new AtomicBoolean(false);
        item.activate(logic, () -> done.set(true));

        assertTrue("Should complete safely even if board is empty", done.get());
    }

    // ============================================================
    // 5) onComplete가 null이어도 NPE 없이 동작해야 함
    // ============================================================
    @Test
    public void testActivate_NoCallback_NoError() {
        item.activate(logic, null);

        assertTrue("Execution should not crash even if onComplete is null", true);
    }
}
