// package component;

// import org.junit.Before;
// import org.junit.Test;
// import java.awt.Graphics2D;
// import java.awt.event.MouseEvent;
// import java.awt.image.BufferedImage;
// import java.awt.event.MouseListener;

// import javax.swing.*;
// import java.awt.*;
// import java.awt.event.ActionEvent;
// import java.util.concurrent.atomic.AtomicBoolean;

// import static org.junit.Assert.*;

// public class MenuPanelTest {

//     private MenuPanel panel;
//     private AtomicBoolean startCalled;
//     private AtomicBoolean selectCalled;
//     private GameConfig capturedConfig;
//     private MenuPanel.MenuItem capturedItem;

//     @Before
//     public void setUp() {
//         startCalled = new AtomicBoolean(false);
//         selectCalled = new AtomicBoolean(false);

//         panel = new MenuPanel(
//                 config -> {
//                     startCalled.set(true);
//                     capturedConfig = config;
//                 },
//                 item -> {
//                     selectCalled.set(true);
//                     capturedItem = item;
//                 });

//         // 실제 Swing 환경에서도 컴포넌트 초기화 필요
//         JFrame frame = new JFrame();
//         frame.add(panel);
//         frame.pack();
//     }

//     @Test
//     public void testInitialLayout_NotNull() {
//         assertNotNull(panel);
//         assertTrue(panel.isOpaque());
//         assertEquals(new Color(0x0A0F18), panel.getBackground());
//     }

//     @Test
//     public void testResizeChangesLayoutMode() throws Exception {
//         // 초기 크기
//         panel.setSize(1200, 800);
//         invokeHandleResize(panel);
//         assertFalse(getPrivateCompactMode(panel));

//         // 작게 만든 뒤 다시 호출
//         panel.setSize(400, 300);
//         invokeHandleResize(panel);
//         assertTrue(getPrivateCompactMode(panel));
//     }

//     // 리플렉션으로 private 메서드 직접 호출
//     private void invokeHandleResize(MenuPanel p) throws Exception {
//         var m = MenuPanel.class.getDeclaredMethod("handleResize");
//         m.setAccessible(true);
//         m.invoke(p);
//     }

//     @Test
//     public void testPaintComponent_RendersWithoutError() {
//         // 실제 그리기 테스트
//         panel.setSize(800, 600);

//         // title JLabel과 cardsContainer의 paintComponent 강제 호출
//         for (Component c : panel.getComponents()) {
//             if (c instanceof JComponent jc) {
//                 BufferedImage img = new BufferedImage(400, 300, BufferedImage.TYPE_INT_ARGB);
//                 Graphics2D g2 = img.createGraphics();
//                 jc.paint(g2); // ✅ paintComponent 강제 실행
//                 g2.dispose();
//             }
//         }
//     }

//     @Test
//     public void testCardHoverAnimation() throws Exception {
//         JPanel card = findCard(panel);
//         assertNotNull(card);

//         // Mouse enter → hover true → repaint 호출 유도
//         for (MouseListener ml : card.getMouseListeners()) {
//             ml.mouseEntered(new MouseEvent(card, 0, 0, 0, 10, 10, 1, false));
//         }

//         // paint 호출 (hoverProgress 증가)
//         BufferedImage img = new BufferedImage(300, 200, BufferedImage.TYPE_INT_ARGB);
//         Graphics2D g2 = img.createGraphics();
//         card.paint(g2);
//         g2.dispose();

//         // Mouse exit → hover false
//         for (MouseListener ml : card.getMouseListeners()) {
//             ml.mouseExited(new MouseEvent(card, 0, 0, 0, 10, 10, 1, false));
//         }
//     }

//     private JPanel findCard(Container parent) {
//         for (Component c : parent.getComponents()) {
//             if (c instanceof JPanel jp && jp.getLayout() instanceof BorderLayout)
//                 return jp;
//             if (c instanceof Container child) {
//                 JPanel found = findCard(child);
//                 if (found != null)
//                     return found;
//             }
//         }
//         return null;
//     }

//     @Test
//     public void testLiftButtonAndLinkBtnPaints() {
//         JButton start = findButton(panel, "Normal Game Start");
//         JButton settings = findButton(panel, "Settings (T)");
//         assertNotNull(start);
//         assertNotNull(settings);

//         BufferedImage img = new BufferedImage(300, 100, BufferedImage.TYPE_INT_ARGB);
//         Graphics2D g2 = img.createGraphics();

//         start.getModel().setRollover(true);
//         start.paint(g2); // hover true

//         settings.getModel().setRollover(true);
//         settings.paint(g2);

//         g2.dispose();
//     }

//     @Test
//     public void testKeyboardShortcut_EscapeTriggersExit() {
//         KeyStroke esc = KeyStroke.getKeyStroke("ESCAPE");
//         ActionMap am = panel.getActionMap();
//         InputMap im = panel.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW);

//         Object key = im.get(esc);
//         assertNotNull("ESC key should be mapped", key);

//         Action action = am.get(key);
//         assertNotNull("ESC action should exist", action);

//         // 직접 실행
//         action.actionPerformed(new ActionEvent(this, ActionEvent.ACTION_PERFORMED, "exit"));
//         assertTrue(selectCalled.get());
//         assertEquals(MenuPanel.MenuItem.EXIT, capturedItem);
//     }

//     @Test
//     public void testKeyboardShortcut_SettingsTriggersSelect() {
//         KeyStroke key = KeyStroke.getKeyStroke('T');
//         ActionMap am = panel.getActionMap();
//         InputMap im = panel.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW);

//         Object actionKey = im.get(key);
//         assertNotNull(actionKey);
//         Action action = am.get(actionKey);
//         assertNotNull(action);

//         action.actionPerformed(new ActionEvent(this, ActionEvent.ACTION_PERFORMED, "settings"));
//         assertTrue(selectCalled.get());
//         assertEquals(MenuPanel.MenuItem.SETTINGS, capturedItem);
//     }

//     @Test
//     public void testOnStart_ClassicMode() {
//         JButton target = findButton(panel, "Normal Game Start");
//         assertNotNull("Start button should exist", target);

//         target.doClick();

//         assertTrue("Start callback should be called", startCalled.get());
//         assertNotNull(capturedConfig);
//         assertEquals(GameConfig.Mode.CLASSIC, capturedConfig.mode());
//     }

//     // ================================
//     // 🔧 유틸리티 메서드
//     // ================================

//     private boolean getPrivateCompactMode(MenuPanel panel) {
//         try {
//             var field = MenuPanel.class.getDeclaredField("isCompactMode");
//             field.setAccessible(true);
//             return field.getBoolean(panel);
//         } catch (Exception e) {
//             throw new AssertionError(e);
//         }
//     }

//     private JButton findButton(Container parent, String text) {
//         for (Component c : parent.getComponents()) {
//             if (c instanceof JButton b && text.equals(b.getText())) {
//                 return b;
//             }
//             if (c instanceof Container child) {
//                 JButton found = findButton(child, text);
//                 if (found != null)
//                     return found;
//             }
//         }
//         return null;
//     }

// }
