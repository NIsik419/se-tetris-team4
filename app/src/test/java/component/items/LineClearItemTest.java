package component.items;

import blocks.Block;
import logic.BoardLogic;
import logic.GameState;
import org.junit.Before;
import org.junit.Test;

import java.awt.Color;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.Assert.*;

public class LineClearItemTest {

    /** DummyBlock → Block이 abstract라서 테스트용 */
    private static class DummyBlock extends Block {
        public DummyBlock(Color color, int[][] shape) {
            super(color, shape);
        }
    }

    private BoardLogic logic;
    private LineClearItem item;

    @Before
    public void setUp() {
        logic = new BoardLogic(score -> {});
        logic.setItemMode(true);
        logic.setOnFrameUpdate(() -> {});

        Block base = new DummyBlock(Color.GREEN, new int[][]{
                {1, 1},
                {1, 1}
        });

        item = new LineClearItem(base);
        item.setTestMode(true);
        
    }

    // ================================
    // TestMode = true (동기 처리)
    // ================================
    @Test
    public void testActivate_SyncMode() {
        Color[][] board = logic.getBoard();
        int targetY = logic.getY() + item.getLY();

        for (int x = 0; x < GameState.WIDTH; x++) {
            board[targetY][x] = Color.GREEN;
        }

        item.activate(logic, null);

        for (int x = 0; x < GameState.WIDTH; x++) {
            assertNull(board[targetY][x]);
        }
    }

    // ================================
    // 비동기 + debris + gravity + clearLines
    // ================================
    @Test
    public void testActivate_AsyncMode() throws Exception {
        Block b = new DummyBlock(Color.BLUE, new int[][]{{1}});
        LineClearItem asyncItem = new LineClearItem(b);
        asyncItem.setTestMode(false);

       

        int ty = logic.getY() + asyncItem.getLY();

        Color[][] board = logic.getBoard();
        for (int x = 0; x < GameState.WIDTH; x++) {
            board[ty][x] = Color.BLUE;
        }

        AtomicBoolean done = new AtomicBoolean(false);
        Thread t = new Thread(() -> asyncItem.activate(logic, () -> done.set(true)));
        t.start();

        long start = System.currentTimeMillis();
        while (!done.get() && System.currentTimeMillis() - start < 3000) {
            Thread.sleep(20);
        }

        assertTrue(done.get());
    }

    // ================================
    // 빈 보드에서도 문제 없어야 함
    // ================================
    @Test
    public void testEmptyBoard_NoError() {
        Color[][] board = logic.getBoard();
        for (int y = 0; y < GameState.HEIGHT; y++)
            for (int x = 0; x < GameState.WIDTH; x++)
                board[y][x] = null;

        AtomicBoolean done = new AtomicBoolean(false);
        item.activate(logic, () -> done.set(true));

        assertTrue(done.get());
    }
}
