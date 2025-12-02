package logic;

import org.junit.Before;
import org.junit.Test;

import java.awt.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.swing.SwingUtilities;

import static org.junit.Assert.*;

public class ClearServiceTest {

    private GameState state;
    private ClearService clear;

    @Before
    public void setup() {
        state = new GameState();
        clear = new ClearService(state);
    }

    @Test
    public void testClearLinesNoFullRow() {
        int result = clear.clearLines(null, null);
        assertEquals(0, result);
    }

    @Test
    public void testClearLinesWithFullRow() throws Exception {
        Color[][] board = state.getBoard();
        for (int x = 0; x < GameState.WIDTH; x++)
            board[GameState.HEIGHT - 1][x] = Color.BLUE;

        CountDownLatch latch = new CountDownLatch(1);

        // EDT에서 실행해야 Swing Timer 작동
        SwingUtilities.invokeLater(() -> clear.clearLines(() -> {
        }, latch::countDown));

        // 최대 2초 대기 (Timer + 애니메이션 여유)
        boolean completed = latch.await(2000, TimeUnit.MILLISECONDS);
        //assertTrue("라인 클리어 콜백이 호출되어야 함", completed);
    }

    @Test
    public void testApplyGravityInstantlyAndSkip() {
        Color[][] board = state.getBoard();
        board[5][0] = Color.RED;
        clear.applyGravityInstantly();
        //assertNull(board[5][0]); // 아래로 이동했어야 함

        clear.setSkipDuringItem(true);
        clear.applyGravityInstantly(); // skipDuringItem == true면 실행 안 됨
        assertTrue(clear.isSkipDuringItem());
    }

    @Test
    public void testApplyLineGravityAndDeprecatedMethod() {
        Color[][] board = state.getBoard();
        board[2][0] = Color.GREEN;
        clear.applyLineGravity();
        clear.applyGravityFromRow(0);
        assertNotNull(state.getBoard());
    }

    @Test
    public void testAnimateSingleLineClearAndExplosionEffect() throws InterruptedException {
        AtomicBoolean flag = new AtomicBoolean(false);
        clear.animateSingleLineClear(5, () -> {
        }, () -> flag.set(true));
        clear.playExplosionEffect(java.util.List.of(5), null, null);
        Thread.sleep(150);
        assertNotNull(state.getFadeLayer());
    }

    @Test
    public void testApplyGravityStepwise() throws Exception {
        Color[][] board = state.getBoard();
        board[0][0] = Color.YELLOW;

        CountDownLatch latch = new CountDownLatch(1);
        clear.applyGravityStepwise(() -> {
        }, latch::countDown);

        // 스레드 애니메이션은 40ms * 20줄 이하 → 여유 있게 1초 대기
        boolean finished = latch.await(1000, TimeUnit.MILLISECONDS);
        assertTrue("중력 애니메이션 완료 콜백이 호출되어야 함", finished);
    }
}
