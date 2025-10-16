package component;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import org.junit.Before;
import org.junit.Test;

public class SpeedManagerTest {

    private SpeedManager speedManager;

    @Before
    public void setUp() {
        speedManager = new SpeedManager();
    }

    @Test
    public void increaseLevel() {
        speedManager.increaseLevel();
        assertEquals(2, speedManager.getLevel());
    }

    @Test
    public void dropInterval() {
        for (int i = 0; i < 15; i++) {
            speedManager.increaseLevel();
        }
        assertTrue(speedManager.getDropInterval() >= 200);
    }
}
