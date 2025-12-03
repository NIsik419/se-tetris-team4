package component.items;

import logic.BoardLogic;
import logic.ClearService;
import logic.GameState;
import org.junit.Before;
import org.junit.Test;

import java.awt.Color;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.Assert.*;

/**
 * ⚡ LightningItemTest – 비동기 로직까지 커버 포함 버전
 */
public class LightningItemTest {

    private BoardLogic logic;
    private LightningItem item;

    @Before
    public void setUp() {
        logic = new BoardLogic(score -> {
        });
        item = new LightningItem();
        item.setTestMode(true);
        logic.setItemMode(true);
        logic.setOnFrameUpdate(() -> {
        });
    }

    /** ✅ testMode = true 경로 테스트 */
    @Test
    public void testActivate_Synchronous_TestMode() {
        Color[][] board = logic.getBoard();
        for (int y = GameState.HEIGHT - 5; y < GameState.HEIGHT; y++)
            for (int x = 0; x < GameState.WIDTH; x++)
                board[y][x] = Color.CYAN;

        int beforeScore = logic.getScore();
        item.setTestMode(true);
        item.activate(logic, null);
        int afterScore = logic.getScore();

        assertTrue("Score should increase after activation", afterScore > beforeScore);
    }

    /** ✅ testMode=false (비동기 모드) – Thread.join()으로 기다리기 */
    @Test
    public void testActivate_AsyncMode_WaitsForThreads() throws Exception {
        LightningItem asyncItem = new LightningItem();
        asyncItem.setTestMode(false);

        Color[][] board = logic.getBoard();
        for (int y = GameState.HEIGHT - 3; y < GameState.HEIGHT; y++)
            for (int x = 0; x < GameState.WIDTH; x++)
                board[y][x] = Color.YELLOW;

        AtomicBoolean done = new AtomicBoolean(false);
        Thread mainThread = new Thread(() -> asyncItem.activate(logic, () -> done.set(true)));
        mainThread.start();

        // ✅ 최대 3초 동안 polling (0.05초 단위)
        long start = System.currentTimeMillis();
        while (!done.get() && System.currentTimeMillis() - start < 3000) {
            Thread.sleep(50);
        }

        assertTrue("Activation should complete", done.get());
        assertTrue("Score should increase", logic.getScore() > 0);
    }

    /** ✅ runPostGravityTestHook() 별도 경로 커버 */
    // @Test
    // public void testRunPostGravityHook_CoversScoringAndCallbacks() {
    //     int before = logic.getScore();
    //     AtomicBoolean callback = new AtomicBoolean(false);

    //     item.runPostGravityTestHook(logic, logic.getClearService(), 4, () -> callback.set(true));

    //     assertTrue("Callback should trigger", callback.get());
    //     assertTrue("Score should increase", logic.getScore() > before);
    // }

    /** ✅ Empty board 안전성 테스트 */
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
}
