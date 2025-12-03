package component;

import org.junit.Test;
import static org.junit.Assert.*;

public class GameConfigTest {

    @Test
    public void testConstructorAndGetters() {
        GameConfig cfg = new GameConfig(GameConfig.Mode.ITEM, GameConfig.Difficulty.HARD, true);
        assertEquals(GameConfig.Mode.ITEM, cfg.mode());
        assertEquals(GameConfig.Difficulty.HARD, cfg.difficulty());
        assertTrue(cfg.colorBlindMode());
    }

    @Test
    public void testToStringContainsAllFields() {
        GameConfig cfg = new GameConfig(GameConfig.Mode.CLASSIC, GameConfig.Difficulty.EASY, false);
        String s = cfg.toString();
        assertTrue(s.contains("CLASSIC"));
        assertTrue(s.contains("EASY"));
        assertTrue(s.contains("cb=false"));
    }
}
