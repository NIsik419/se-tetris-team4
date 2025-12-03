package component.items;

import logic.BoardLogic;
import logic.GameState;
import org.junit.Before;
import org.junit.Test;

import java.awt.Color;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.Assert.*;

public class WeightItemTest {

    private BoardLogic logic;
    private WeightItem item;

    @Before
    public void setUp() {
        logic = new BoardLogic(score -> {});
        logic.setItemMode(true);
        logic.setOnFrameUpdate(() -> {});

        item = new WeightItem();
        item.setTestMode(true);
        
    }

    // ===========================
    // Sync Mode Test (즉시 처리)
    // ===========================
    @Test
    public void testActivate_SyncMode() {
        Color[][] board = logic.getBoard();

        // weight 아래 색 채우기
        for (int dx = 0; dx < item.width(); dx++) {
            int bx = logic.getX() + dx;
            for (int y = 0; y < GameState.HEIGHT; y++) {
                board[y][bx] = Color.YELLOW;
            }
        }

        item.activate(logic, null);

        // weight 본체가 바닥에 있는지 확인
        int h = item.height();
        int dropY = GameState.HEIGHT - h;

        for (int dy = 0; dy < h; dy++) {
            for (int dx = 0; dx < item.width(); dx++) {
                int bx = logic.getX() + dx;
                assertEquals(item.getColor(), board[dropY + dy][bx]);
            }
        }
    }

    // ===========================
    // 비동기 모드
    // ===========================
    @Test
    public void testActivate_AsyncMode() throws Exception {
        WeightItem asyncItem = new WeightItem();
        asyncItem.setTestMode(false);
        

        Color[][] board = logic.getBoard();

        for (int y = 0; y < GameState.HEIGHT; y++)
            for (int x = 0; x < GameState.WIDTH; x++)
                board[y][x] = Color.CYAN;

        AtomicBoolean done = new AtomicBoolean(false);
        Thread t = new Thread(() -> asyncItem.activate(logic, () -> done.set(true)));
        t.start();

        long start = System.currentTimeMillis();
        while (!done.get() && System.currentTimeMillis() - start < 3000) {
            Thread.sleep(20);
        }

        assertTrue(done.get());
    }

    // ===========================
    // 빈 보드에서도 안정성
    // ===========================
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
