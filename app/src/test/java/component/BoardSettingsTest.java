// package component;

// import component.config.Settings;
// import org.junit.Before;
// import org.junit.Test;

// import javax.swing.*;
// import java.awt.event.KeyEvent;
// import java.util.Map;

// import static org.junit.Assert.*;

// public class BoardSettingsTest {

//     private Board board;
//     private Settings settings;

//     @Before
//     public void setup() throws Exception {
//         SwingUtilities.invokeAndWait(() -> {
//             GameConfig config = new GameConfig(GameConfig.Mode.CLASSIC, GameConfig.Difficulty.EASY,false);
//             board = new Board(config);
//             settings = new Settings();
//         });
//     }

//     @Test
//     public void testApplySettingsChangesColorMode() throws Exception {
//         settings.colorBlindMode = ColorBlindPalette.Mode.DEUTER;
//         SwingUtilities.invokeAndWait(() -> board.setSettings(settings));
//         var field = Board.class.getDeclaredField("colorMode");
//         field.setAccessible(true);
//         Object mode = field.get(board);
//         assertEquals(ColorBlindPalette.Mode.DEUTER, mode);
//     }

//     @Test
//     public void testRebindKeymapReflectsNewKeys() throws Exception {
//         settings.keymap.put(Settings.Action.Left, KeyEvent.VK_A);
//         settings.keymap.put(Settings.Action.Right, KeyEvent.VK_D);
//         SwingUtilities.invokeAndWait(() -> board.setSettings(settings));

//         var mapField = Board.class.getDeclaredField("boundKeys");
//         mapField.setAccessible(true);
//         Map<?, ?> map = (Map<?, ?>) mapField.get(board);
//         assertTrue(map.containsKey(Settings.Action.Left));
//     }
// }
