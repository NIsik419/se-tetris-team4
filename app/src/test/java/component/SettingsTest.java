package component;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import component.config.Settings;
import component.score.ScoreBoard;

public class SettingsTest {
    private static final String TEST_PATH = "config/settings.properties";

    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

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

    @Test
    public void testResetScoreBoard() throws Exception {
        // 임시 폴더에 ScoreBoard 파일 생성 및 데이터 추가
        Path tempFile = tmp.newFile("scores.csv").toPath();
        ScoreBoard board = new ScoreBoard(tempFile, 5);
        board.addScore("Alice", 100, GameConfig.Mode.CLASSIC, GameConfig.Difficulty.NORMAL);
        board.addScore("Bob", 200, GameConfig.Mode.CLASSIC, GameConfig.Difficulty.NORMAL);
        assertFalse(board.getEntries().isEmpty());
        assertTrue(Files.readString(tempFile, StandardCharsets.UTF_8).length() > 0);

        // Settings 생성 후 ScoreBoard 주입
        Settings settings = new Settings();
        settings.setScoreBoard(board);

        // resetScoreBoard() 실행
        settings.resetScoreBoard(GameConfig.Mode.CLASSIC, GameConfig.Difficulty.NORMAL);

        // 메모리 및 파일이 모두 비어 있는지
        assertTrue(board.getEntries().isEmpty());
        String content = Files.readString(tempFile, StandardCharsets.UTF_8);
        assertEquals("", content.trim());
    }
}
