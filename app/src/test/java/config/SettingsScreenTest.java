package config;

import component.ColorBlindPalette;
import component.config.Settings;
import component.config.SettingsScreen;
import org.junit.Before;
import org.junit.Test;

import javax.swing.*;
import java.awt.event.KeyEvent;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.Assert.*;

public class SettingsScreenTest {

    private Settings settings;
    private SettingsScreen screen;

    @Before
    public void setUp() {
        settings = new Settings();
        settings.colorBlindMode = ColorBlindPalette.Mode.NORMAL;
        settings.screenSize = Settings.ScreenSize.MEDIUM;
        settings.keymap.put(Settings.Action.Left, KeyEvent.VK_LEFT);
        settings.keymap.put(Settings.Action.Right, KeyEvent.VK_RIGHT);
        settings.keymap.put(Settings.Action.Rotate, KeyEvent.VK_UP);
        settings.keymap.put(Settings.Action.SoftDrop, KeyEvent.VK_DOWN);
        settings.keymap.put(Settings.Action.HardDrop, KeyEvent.VK_SPACE);

        // 더미 ApplyListener (호출 여부만 확인)
        AtomicBoolean applied = new AtomicBoolean(false);
        screen = new SettingsScreen(settings, s -> applied.set(true), () -> {});
    }

    /** 기존 설정이 UI에 잘 로드되는지 */
    @Test
    public void testLoadFromSettings() {
        JComboBox<ColorBlindPalette.Mode> cbBlind = getCombo(screen, "cbBlindMode");
        assertEquals(ColorBlindPalette.Mode.NORMAL, cbBlind.getSelectedItem());
    }

    /** Apply 시 실제 Settings 객체가 업데이트 되는지 */
    @Test
    public void testSaveToSettingsAndApply() {
        // 콜백 호출 감시
        AtomicBoolean called = new AtomicBoolean(false);
        SettingsScreen screen2 = new SettingsScreen(settings, s -> called.set(true), () -> {});

        // Blind 모드 변경
        JComboBox<ColorBlindPalette.Mode> cbBlind = getCombo(screen2, "cbBlindMode");
        cbBlind.setSelectedItem(ColorBlindPalette.Mode.DEUTER);

        // Apply 버튼 클릭 시 saveToSettings + listener 호출
        JButton btnApply = getButton(screen2, "btnApply");
        btnApply.doClick();

        assertEquals(ColorBlindPalette.Mode.DEUTER, settings.colorBlindMode);
        assertTrue("Apply listener should be called", called.get());
    }

    /** 중복 키 바인딩이 감지되어 오류 메시지가 표시되는지 */
    @Test
    public void testValidateKeys_DuplicateDetection() throws Exception {
        // Left와 Right를 같은 키로 설정
        settings.keymap.put(Settings.Action.Left, KeyEvent.VK_A);
        settings.keymap.put(Settings.Action.Right, KeyEvent.VK_A);

        SettingsScreen s = new SettingsScreen(settings, null, () -> {});
        boolean result = invokeValidateKeys(s);

        assertFalse("Should detect duplicate keys", result);

        JLabel lblError = getLabel(s, "lblError");
        assertTrue("Error label should mention duplicate", 
                   lblError.getText().contains("Duplicate"));
    }

    /** 정상적인 키 구성이면 Apply 버튼이 활성화되는지 */
    @Test
    public void testValidateKeys_NoDuplicate() throws Exception {
        settings.keymap.put(Settings.Action.Left, KeyEvent.VK_A);
        settings.keymap.put(Settings.Action.Right, KeyEvent.VK_D);
        settings.keymap.put(Settings.Action.Rotate, KeyEvent.VK_W);
        settings.keymap.put(Settings.Action.SoftDrop, KeyEvent.VK_S);
        settings.keymap.put(Settings.Action.HardDrop, KeyEvent.VK_SPACE);

        SettingsScreen s = new SettingsScreen(settings, null, () -> {});
        boolean result = invokeValidateKeys(s);

        assertTrue(result);
        JButton btnApply = getButton(s, "btnApply");
        assertTrue(btnApply.isEnabled());
    }

    // ---- private reflection util ----

    @SuppressWarnings("unchecked")
    private <T> JComboBox<T> getCombo(SettingsScreen s, String fieldName) {
        try {
            var f = SettingsScreen.class.getDeclaredField(fieldName);
            f.setAccessible(true);
            return (JComboBox<T>) f.get(s);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private JButton getButton(SettingsScreen s, String fieldName) {
        try {
            var f = SettingsScreen.class.getDeclaredField(fieldName);
            f.setAccessible(true);
            return (JButton) f.get(s);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private JLabel getLabel(SettingsScreen s, String fieldName) {
        try {
            var f = SettingsScreen.class.getDeclaredField(fieldName);
            f.setAccessible(true);
            return (JLabel) f.get(s);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private boolean invokeValidateKeys(SettingsScreen s) throws Exception {
        var m = SettingsScreen.class.getDeclaredMethod("validateKeys");
        m.setAccessible(true);
        return (boolean) m.invoke(s);
    }
}
