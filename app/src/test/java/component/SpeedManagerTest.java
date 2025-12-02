package component;

import component.GameConfig;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.*;

public class SpeedManagerTest {

    private SpeedManager sm;

    @Before
    public void setup() {
        // 기본은 NORMAL 기준으로 생성
        sm = new SpeedManager(GameConfig.Difficulty.NORMAL);
    }

    @Test
    public void testInitialValues() {
        assertEquals(1, sm.getLevel());
        assertEquals(1000, sm.getDropInterval());
        assertNotNull(sm.toString());
    }

    @Test
    public void testSetDifficultyEasyNormalHard() {
        // EASY
        sm.setDifficulty(GameConfig.Difficulty.EASY);
        sm.increaseLevel();
        int easyDrop = sm.getDropInterval();

        // NORMAL
        sm.setDifficulty(GameConfig.Difficulty.NORMAL);
        sm.increaseLevel();
        int normalDrop = sm.getDropInterval();

        // HARD
        sm.setDifficulty(GameConfig.Difficulty.HARD);
        sm.increaseLevel();
        int hardDrop = sm.getDropInterval();

        // HARD는 더 많이 줄어들어야 함 (더 빠름)
        assertTrue("HARD should be faster than NORMAL", hardDrop < normalDrop);
        // EASY는 덜 줄어들어야 함 (더 느림)
        assertTrue("EASY should be slower than NORMAL", easyDrop > normalDrop);
    }

    @Test
    public void testIncreaseLevelCapsAt10() {
        // 레벨 9로 맞춰두고
        sm.setDifficulty(GameConfig.Difficulty.NORMAL);
        sm.setLevel(9);
        int before = sm.getDropInterval();

        sm.increaseLevel(); // -> 레벨 10, 속도 증가 (interval 감소)
        sm.increaseLevel(); // -> 레벨 10에서 더 이상 변화 없음

        assertTrue(sm.getLevel() <= 10);
        assertTrue(sm.getDropInterval() <= before);
    }

    @Test
    public void testToStringFormat() {
        String s = sm.toString();
        assertTrue(s.contains("SpeedManager"));
    }
}
