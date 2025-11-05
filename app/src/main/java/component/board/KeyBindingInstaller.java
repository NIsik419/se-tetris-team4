package component.board;

import component.ColorBlindPalette;
import component.items.*;
import logic.BoardLogic;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.util.Objects;

/**
 * 키 바인딩 전담 설치기.
 * Board가 가진 동작들을 Deps로 주입받아 ActionMap/InputMap에 연결한다.
 */
public class KeyBindingInstaller {

    /** Board 쪽에서 제공해야 하는 의존성(콜백/상태) */
    public static class Deps {
        public final BoardLogic logic;

        // 상태/콜백
        public final Runnable drawBoard;
        public final Runnable toggleFullScreen;
        public final Runnable disposeWindow;

        // 일시정지 관련
        public final JPanel pausePanel; // PausePanel은 JPanel 상속 가정
        public final javax.swing.Timer timer;
        public final java.util.function.Consumer<String> setTitle;

        // 색약 모드
        public final java.util.function.Supplier<ColorBlindPalette.Mode> getColorMode;
        public final java.util.function.Consumer<ColorBlindPalette.Mode> setColorMode;
        public final java.util.function.Consumer<ColorBlindPalette.Mode> onColorModeChanged; // 예: nextPanel.setColorMode

        public Deps(BoardLogic logic,
                    Runnable drawBoard,
                    Runnable toggleFullScreen,
                    Runnable disposeWindow,
                    JPanel pausePanel,
                    javax.swing.Timer timer,
                    java.util.function.Consumer<String> setTitle,
                    java.util.function.Supplier<ColorBlindPalette.Mode> getColorMode,
                    java.util.function.Consumer<ColorBlindPalette.Mode> setColorMode,
                    java.util.function.Consumer<ColorBlindPalette.Mode> onColorModeChanged) {

            this.logic = Objects.requireNonNull(logic);
            this.drawBoard = Objects.requireNonNull(drawBoard);
            this.toggleFullScreen = Objects.requireNonNull(toggleFullScreen);
            this.disposeWindow = Objects.requireNonNull(disposeWindow);
            this.pausePanel = Objects.requireNonNull(pausePanel);
            this.timer = Objects.requireNonNull(timer);
            this.setTitle = Objects.requireNonNull(setTitle);
            this.getColorMode = Objects.requireNonNull(getColorMode);
            this.setColorMode = Objects.requireNonNull(setColorMode);
            this.onColorModeChanged = Objects.requireNonNull(onColorModeChanged);
        }
    }

    // 액션명 상수
    private static final String ACT_LEFT  = "left";
    private static final String ACT_RIGHT = "right";
    private static final String ACT_DOWN  = "down";
    private static final String ACT_ROT   = "rotate";
    private static final String ACT_DROP  = "drop";

    public void install(JComponent comp, Deps d) {
        // 포커스 계층 / 전역 맵
        InputMap imPlay   = comp.getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT);
        InputMap imGlobal = comp.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW);
        ActionMap am      = comp.getActionMap();

        // ── 이동/회전/드랍 ─────────────────────────────────────
        imPlay.put(KeyStroke.getKeyStroke("LEFT"),  ACT_LEFT);
        imPlay.put(KeyStroke.getKeyStroke("RIGHT"), ACT_RIGHT);
        imPlay.put(KeyStroke.getKeyStroke("DOWN"),  ACT_DOWN);
        imPlay.put(KeyStroke.getKeyStroke("UP"),    ACT_ROT);
        imPlay.put(KeyStroke.getKeyStroke("SPACE"), ACT_DROP);

        am.put(ACT_LEFT,  new AbstractAction(){ public void actionPerformed(ActionEvent e){ d.logic.moveLeft();  d.drawBoard.run(); }});
        am.put(ACT_RIGHT, new AbstractAction(){ public void actionPerformed(ActionEvent e){ d.logic.moveRight(); d.drawBoard.run(); }});
        am.put(ACT_DOWN,  new AbstractAction(){ public void actionPerformed(ActionEvent e){ d.logic.moveDown();  d.drawBoard.run(); }});
        am.put(ACT_ROT,   new AbstractAction(){ public void actionPerformed(ActionEvent e){ d.logic.rotateBlock(); d.drawBoard.run(); }});
        am.put(ACT_DROP,  new AbstractAction(){ public void actionPerformed(ActionEvent e){ d.logic.hardDrop();  d.drawBoard.run(); }});

        // ── 전역 기능 ────────────────────────────────────────
        imGlobal.put(KeyStroke.getKeyStroke("P"),                     "pause");
        imGlobal.put(KeyStroke.getKeyStroke(KeyEvent.VK_F11, 0),      "fullscreen");
        imGlobal.put(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0),   "exit");
        imGlobal.put(KeyStroke.getKeyStroke("C"),                     "toggleColorBlind");
        imGlobal.put(KeyStroke.getKeyStroke("1"),                     "debugLineClear");
        imGlobal.put(KeyStroke.getKeyStroke("2"),                     "debugWeight");
        imGlobal.put(KeyStroke.getKeyStroke("3"),                     "debugSpinLock");
        imGlobal.put(KeyStroke.getKeyStroke("4"),                     "debugColorBomb");
        imGlobal.put(KeyStroke.getKeyStroke("5"),                     "debugLightning");

        am.put("pause", new AbstractAction() {
            public void actionPerformed(ActionEvent e) {
                if (d.pausePanel.isVisible()) {
                    d.pausePanel.setVisible(false);
                    d.timer.start();
                    d.setTitle.accept("TETRIS");
                } else {
                    d.timer.stop();
                    d.setTitle.accept("TETRIS (PAUSED)");
                    d.pausePanel.setVisible(true);
                }
            }
        });

        am.put("toggleColorBlind", new AbstractAction() {
            public void actionPerformed(ActionEvent e) {
                ColorBlindPalette.Mode mode = d.getColorMode.get();
                switch (mode) {
                    case NORMAL -> mode = ColorBlindPalette.Mode.PROTAN;
                    case PROTAN -> mode = ColorBlindPalette.Mode.DEUTER;
                    case DEUTER -> mode = ColorBlindPalette.Mode.TRITAN;
                    case TRITAN -> mode = ColorBlindPalette.Mode.NORMAL;
                }
                d.setColorMode.accept(mode);
                d.setTitle.accept("TETRIS - " + mode.name() + " mode");
                d.onColorModeChanged.accept(mode); // nextPanel.setColorMode 등
                d.drawBoard.run();
            }
        });

        // ── 디버그 아이템 ────────────────────────────────────
        am.put("debugLineClear", new AbstractAction() {
            public void actionPerformed(ActionEvent e) {
                if (!d.logic.isItemMode()) return;
                d.logic.debugSetNextItem(new LineClearItem(d.logic.getCurr()));
                System.out.println("🧪 Debug: 다음 블록 = LineClearItem");
                d.drawBoard.run();
            }
        });
        am.put("debugWeight", new AbstractAction() {
            public void actionPerformed(ActionEvent e) {
                if (!d.logic.isItemMode()) return;
                d.logic.debugSetNextItem(new WeightItem());
                System.out.println("🧪 Debug: 다음 블록 = WeightItem");
                d.drawBoard.run();
            }
        });
        am.put("debugSpinLock", new AbstractAction() {
            public void actionPerformed(ActionEvent e) {
                if (!d.logic.isItemMode()) return;
                d.logic.debugSetNextItem(new SpinLockItem(d.logic.getCurr()));
                System.out.println("🧪 Debug: 다음 블록 = SpinLockItem (회전금지)");
                d.drawBoard.run();
            }
        });
        am.put("debugColorBomb", new AbstractAction() {
            public void actionPerformed(ActionEvent e) {
                if (!d.logic.isItemMode()) return;
                d.logic.debugSetNextItem(new ColorBombItem(d.logic.getCurr()));
                System.out.println("🧪 Debug: 다음 블록 = ColorBombItem (색상 폭탄)");
                d.drawBoard.run();
            }
        });
        am.put("debugLightning", new AbstractAction() {
            public void actionPerformed(ActionEvent e) {
                if (!d.logic.isItemMode()) return;
                d.logic.debugSetNextItem(new LightningItem());
                System.out.println("🧪 Debug: 다음 블록 = LightningItem (번개)");
                d.drawBoard.run();
            }
        });

        am.put("fullscreen", new AbstractAction(){ public void actionPerformed(ActionEvent e){ d.toggleFullScreen.run(); }});
        am.put("exit",       new AbstractAction(){ public void actionPerformed(ActionEvent e){ d.disposeWindow.run(); }});
    }
}
