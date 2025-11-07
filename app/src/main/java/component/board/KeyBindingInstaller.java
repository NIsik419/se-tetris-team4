package component.board;

import component.ColorBlindPalette;
import component.items.*;
import logic.BoardLogic;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;

/**
 * 키 바인딩 전담 설치기.
 * Board가 가진 동작들을 Deps로 주입받아 ActionMap/InputMap에 연결한다.
 */
public class KeyBindingInstaller {

    /** Board 쪽에서 제공해야 하는 의존성(콜백/상태) */
    public static class Deps {
        public final BoardLogic logic;

        public final Runnable drawBoard;
        public final Runnable toggleFullScreen;
        public final Runnable disposeWindow;

        // Pause 제어
        public final java.util.function.Supplier<Boolean> isPauseVisible;
        public final Runnable showPause, hidePause;
        public final Runnable startLoop, stopLoop;
        public final java.util.function.Consumer<String> setTitle;

        // 색약 모드
        public final java.util.function.Supplier<ColorBlindPalette.Mode> getColorMode;
        public final java.util.function.Consumer<ColorBlindPalette.Mode> setColorMode;
        public final java.util.function.Consumer<ColorBlindPalette.Mode> onColorModeChanged;

        /** 모든 의존성을 주입받는 생성자 */
        public Deps(
                BoardLogic logic,
                Runnable drawBoard,
                Runnable toggleFullScreen,
                Runnable disposeWindow,
                java.util.function.Supplier<Boolean> isPauseVisible,
                Runnable showPause,
                Runnable hidePause,
                Runnable startLoop,
                Runnable stopLoop,
                java.util.function.Consumer<String> setTitle,
                java.util.function.Supplier<ColorBlindPalette.Mode> getColorMode,
                java.util.function.Consumer<ColorBlindPalette.Mode> setColorMode,
                java.util.function.Consumer<ColorBlindPalette.Mode> onColorModeChanged
        ) {
            this.logic = logic;
            this.drawBoard = drawBoard;
            this.toggleFullScreen = toggleFullScreen;
            this.disposeWindow = disposeWindow;
            this.isPauseVisible = isPauseVisible;
            this.showPause = showPause;
            this.hidePause = hidePause;
            this.startLoop = startLoop;
            this.stopLoop = stopLoop;
            this.setTitle = setTitle;
            this.getColorMode = getColorMode;
            this.setColorMode = setColorMode;
            this.onColorModeChanged = onColorModeChanged;
        }
    }

    // 액션명 상수
    private static final String ACT_LEFT  = "left";
    private static final String ACT_RIGHT = "right";
    private static final String ACT_DOWN  = "down";
    private static final String ACT_ROT   = "rotate";
    private static final String ACT_DROP  = "drop";

    /** 실제 키 바인딩 설치 */
    public void install(JComponent comp, Deps d) {
        InputMap im = comp.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW);
        ActionMap am = comp.getActionMap();

        // ── 이동/회전/드랍 ─────────────────────────────
        im.put(KeyStroke.getKeyStroke("LEFT"),  ACT_LEFT);
        im.put(KeyStroke.getKeyStroke("RIGHT"), ACT_RIGHT);
        im.put(KeyStroke.getKeyStroke("DOWN"),  ACT_DOWN);
        im.put(KeyStroke.getKeyStroke("UP"),    ACT_ROT);
        im.put(KeyStroke.getKeyStroke("SPACE"), ACT_DROP);

        am.put(ACT_LEFT,  new AbstractAction(){ public void actionPerformed(ActionEvent e){ d.logic.moveLeft();  d.drawBoard.run(); }});
        am.put(ACT_RIGHT, new AbstractAction(){ public void actionPerformed(ActionEvent e){ d.logic.moveRight(); d.drawBoard.run(); }});
        am.put(ACT_DOWN,  new AbstractAction(){ public void actionPerformed(ActionEvent e){ d.logic.moveDown();  d.drawBoard.run(); }});
        am.put(ACT_ROT,   new AbstractAction(){ public void actionPerformed(ActionEvent e){ d.logic.rotateBlock(); d.drawBoard.run(); }});
        am.put(ACT_DROP,  new AbstractAction(){ public void actionPerformed(ActionEvent e){ d.logic.hardDrop();  d.drawBoard.run(); }});

        // ── 전역 기능 ─────────────────────────────
        im.put(KeyStroke.getKeyStroke("P"), "pause");
        im.put(KeyStroke.getKeyStroke(KeyEvent.VK_F11, 0), "fullscreen");
        im.put(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), "exit");

        // 색맹 모드 C키 (pressed/released 모두 등록)
        KeyStroke cPressed  = KeyStroke.getKeyStroke("pressed C");
        KeyStroke cReleased = KeyStroke.getKeyStroke("released C");
        im.put(cPressed,  "toggleColorBlind_pressed");
        im.put(cReleased, "toggleColorBlind_released");

        // 디버그 키
        im.put(KeyStroke.getKeyStroke("1"), "debugLineClear");
        im.put(KeyStroke.getKeyStroke("2"), "debugWeight");
        im.put(KeyStroke.getKeyStroke("3"), "debugSpinLock");
        im.put(KeyStroke.getKeyStroke("4"), "debugColorBomb");
        im.put(KeyStroke.getKeyStroke("5"), "debugLightning");

        // ── 일시정지 ─────────────────────────────
        am.put("pause", new AbstractAction() {
            public void actionPerformed(ActionEvent e) {
                if (d.isPauseVisible.get()) {
                    d.hidePause.run();
                    d.startLoop.run();
                    d.setTitle.accept("TETRIS");
                } else {
                    d.stopLoop.run();
                    d.setTitle.accept("TETRIS (PAUSED)");
                    d.showPause.run();
                }
            }
        });

        // ── 색맹 모드 토글 (연속 입력 가능) ─────────────────────
        final ColorBlindPalette.Mode[] currentMode = { d.getColorMode.get() };

        am.put("toggleColorBlind_pressed", new AbstractAction() {
            public void actionPerformed(ActionEvent e) {
                ColorBlindPalette.Mode mode = currentMode[0];
                switch (mode) {
                    case NORMAL -> mode = ColorBlindPalette.Mode.PROTAN;
                    case PROTAN -> mode = ColorBlindPalette.Mode.DEUTER;
                    case DEUTER -> mode = ColorBlindPalette.Mode.TRITAN;
                    case TRITAN -> mode = ColorBlindPalette.Mode.NORMAL;
                }
                currentMode[0] = mode; // 내부 상태 갱신
                d.setColorMode.accept(mode);
                d.onColorModeChanged.accept(mode);
                d.drawBoard.run();
                d.setTitle.accept("TETRIS - " + mode.name());
                System.out.println("[DEBUG] Color mode switched → " + mode.name());
            }
        });

        // released는 입력 풀림만 감지용 (아무 동작 없음)
        am.put("toggleColorBlind_released", new AbstractAction() {
            public void actionPerformed(ActionEvent e) { }
        });

        // ── 디버그 아이템 ─────────────────────────────
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

        // ── 기타 ─────────────────────────────
        am.put("fullscreen", new AbstractAction() { public void actionPerformed(ActionEvent e) { d.toggleFullScreen.run(); }});
        am.put("exit",       new AbstractAction() { public void actionPerformed(ActionEvent e) { d.disposeWindow.run(); }});
    }
}
