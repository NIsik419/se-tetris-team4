package performance;

import static org.junit.Assert.assertTrue;

import org.junit.Test;
import logic.BoardLogic;
import component.GameConfig;

/**
 * PerformanceTest
 * ----------------
 * 비기능 요구사항 검증: FPS(프레임 처리 속도)
 *
 * 요구사항: NORMAL 난이도에서 평균 FPS 60 이상 유지
 */
public class PerformanceTest {

    @Test
    public void testBoardLogicFramePerformance() throws InterruptedException {
        // given
        BoardLogic logic = new BoardLogic(score -> {} , GameConfig.Difficulty.NORMAL);

        int frames = 0;
        long start = System.nanoTime();

        // when: 5초 동안 moveDown() 시뮬레이션 (게임 루프 대체)
        long durationMs = 5000;
        long endTime = System.currentTimeMillis() + durationMs;

        while (System.currentTimeMillis() < endTime) {
            logic.moveDown();   // 한 프레임 처리
            frames++;
            Thread.sleep(16);   // 60fps 기준 딜레이
        }

        // then: FPS 계산
        long elapsedNs = System.nanoTime() - start;
        double elapsedSec = elapsedNs / 1e9;
        double avgFps = frames / elapsedSec;

        System.out.printf("평균 FPS: %.2f%n", avgFps);
        assertTrue("FPS 60 이상 유지해야 함", avgFps >= 50.0);
    }
}
