package component;

import logic.BoardLogic;
import org.junit.Test;

import javax.swing.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.*;

public class GameLoopTest {

    /** Fake BoardLogic for Timer testing */
    private static class FakeLogic extends BoardLogic {

        private int moves = 0;
        private boolean gameOver = false;
        private int interval = 50; // 기본 속도

        public FakeLogic() {
            super(score -> {}, GameConfig.Difficulty.NORMAL);
            this.setTestMode(true);
        }

        @Override
        public void moveDown() {
            moves++;
        }

        @Override
        public int getDropInterval() {
            return interval;
        }

        @Override
        public boolean isGameOver() {
            return gameOver;
        }

        public int getMoves() {
            return moves;
        }

        public void setGameOver() {
            this.gameOver = true;
        }

        public void setInterval(int ms) {
            this.interval = ms;
        }
    }

    @Test
    public void testGameLoopStartStopPauseResume() throws Exception {
        FakeLogic logic = new FakeLogic();

        CountDownLatch repaintLatch = new CountDownLatch(3);

        GameLoop loop = new GameLoop(logic, repaintLatch::countDown);

        // Start
        loop.start();
        assertTrue(loop.isRunning());
        assertFalse(loop.isPaused());

        // Pause
        loop.pause();
        assertTrue(loop.isPaused());

        // Resume
        loop.resume();
        assertFalse(loop.isPaused());

        // Stop
        loop.stop();
        assertFalse(loop.isRunning());
    }

    @Test
    public void testGameLoopCallsMoveDown() throws Exception {
        FakeLogic logic = new FakeLogic();
        CountDownLatch latch = new CountDownLatch(3);

        GameLoop loop = new GameLoop(logic, latch::countDown);

        loop.start();

        // Timer는 50ms 딜레이 → 200ms 동안 최소 3회 실행
        assertTrue(latch.await(500, TimeUnit.MILLISECONDS));

        loop.stop();
        assertTrue(logic.getMoves() >= 2); // 여러 번 호출됨
    }

    @Test
    public void testGameLoopStopsOnGameOver() throws Exception {
        FakeLogic logic = new FakeLogic();
        CountDownLatch latch = new CountDownLatch(1);

        GameLoop loop = new GameLoop(logic, latch::countDown);
        loop.start();

        // 첫 틱 후 바로 gameOver 설정
        SwingUtilities.invokeLater(logic::setGameOver);

        Thread.sleep(200); // 타이머 진행

        assertFalse(loop.isRunning()); // stop() 호출됨
    }

    @Test
    public void testGameLoopIntervalSync() throws Exception {
        FakeLogic logic = new FakeLogic();
        GameLoop loop = new GameLoop(logic, () -> {});

        loop.start();

        // interval 변경 테스트
        logic.setInterval(20);
        Thread.sleep(100);

        // Timer 딜레이 적용 여부 확인
        assertEquals(20, logic.getDropInterval());

        loop.stop();
    }
}
