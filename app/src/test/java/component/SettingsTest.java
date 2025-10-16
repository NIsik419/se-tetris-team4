package component;

import java.nio.file.Files;
import java.nio.file.Paths;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import org.junit.Before;
import org.junit.Test;

import component.config.Settings;

public class SettingsTest {
    private static final String TEST_PATH = "config/settings.properties";
    
    @Before
    public void setUp() throws Exception {
        Files.deleteIfExists(Paths.get(TEST_PATH));
    }

    @Test
    public void testSaveAndLoad() {
        Settings s = new Settings();
        s.toggleBlindMode();
        s.setScreenScale(Settings.ScreenSize.LARGE);
        s.save();

        Settings loaded = Settings.load();

        assertTrue(loaded.blindMode);
        assertEquals(Settings.ScreenSize.LARGE, loaded.screenSize);
    }

    @Test
    public void testResetToDefaults() {
        Settings s = new Settings();
        s.toggleBlindMode();
        s.setScreenScale(Settings.ScreenSize.LARGE);
        s.save();

        s.resetToDefaults();

        assertFalse(s.blindMode);
        assertEquals(Settings.ScreenSize.MEDIUM, s.screenSize);
    }
}
