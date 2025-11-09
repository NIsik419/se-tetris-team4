package component.board;

import component.ColorBlindPalette;
import component.items.*;
import logic.BoardLogic;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;

/**
 * 키 바인딩 전담 설치기.
 * install()로 키셋을 선택해 바인딩
 */
public class KeyBindingInstaller {

    /** 사용할 키셋 */
    public enum KeySet { ARROWS, WASD }

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

    /** 공통 액션 등록 (키 매핑은 install()에서 설정) */
    private void registerCoreActions(ActionMap am, Deps d) {
        am.put(ACT_LEFT,  new AbstractAction(){ public void actionPerformed(ActionEvent e){ d.logic.moveLeft();   d.drawBoard.run(); }});
        am.put(ACT_RIGHT, new AbstractAction(){ public void actionPerformed(ActionEvent e){ d.logic.moveRight();  d.drawBoard.run(); }});
        am.put(ACT_DOWN,  new AbstractAction(){ public void actionPerformed(ActionEvent e){ d.logic.moveDown();   d.drawBoard.run(); }});
        am.put(ACT_ROT,   new AbstractAction(){ public void actionPerformed(ActionEvent e){ d.logic.rotateBlock(); d.drawBoard.run(); }});
        am.put(ACT_DROP,  new AbstractAction(){ public void actionPerformed(ActionEvent e){ d.logic.hardDrop();   d.drawBoard.run(); }});

        // 일시정지 토글 (BoardPanel에서 startLoop=resume, stopLoop=pause로 주입되어 있음)
        am.put("pause", new AbstractAction() {
            public void actionPerformed(ActionEvent e) {
                if (d.isPauseVisible.get()) {
                    d.hidePause.run();
                    d.startLoop.run(); // resume
                    d.setTitle.accept("TETRIS");
                } else {
                    d.stopLoop.run();  // pause
                    d.setTitle.accept("TETRIS (PAUSED)");
                    d.showPause.run();
                }
            }
        });

        // 색맹 모드 토글 
        am.put("toggleColorBlind_pressed", new AbstractAction() {
            public void actionPerformed(ActionEvent e) {
                ColorBlindPalette.Mode mode = d.getColorMode.get();
                switch (mode) {
                    case NORMAL -> mode = ColorBlindPalette.Mode.PROTAN;
                    case PROTAN -> mode = ColorBlindPalette.Mode.DEUTER;
                    case DEUTER -> mode = ColorBlindPalette.Mode.TRITAN;
                    case TRITAN -> mode = ColorBlindPalette.Mode.NORMAL;
                }
                d.setColorMode.accept(mode);
                d.onColorModeChanged.accept(mode);
                d.drawBoard.run();
                d.setTitle.accept("TETRIS - " + mode.name());
                System.out.println("[DEBUG] Color mode switched → " + mode.name());
            }
        });
        am.put("toggleColorBlind_released", new AbstractAction() {
            public void actionPerformed(ActionEvent e) { /* no-op */ }
        });

        // 디버그 아이템 (바인드 여부는 install()에서 결정)
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

        // 기타
        am.put("fullscreen", new AbstractAction() { public void actionPerformed(ActionEvent e) { d.toggleFullScreen.run(); }});
        am.put("exit",       new AbstractAction() { public void actionPerformed(ActionEvent e) { d.disposeWindow.run(); }});
    }

    /* ====================== 통합 API ====================== */

    /**
     * 통합 설치기
     * @param comp 바인딩 대상 컴포넌트(보드 뷰)
     * @param d    의존성
     * @param set  키셋 (ARROWS or WASD)
     * @param enableDebug 디버그키(1~5) 바인딩 여부
     */
    public void install(JComponent comp, Deps d, KeySet set, boolean enableDebug) {
        InputMap im = comp.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW);
        ActionMap am = comp.getActionMap();

        // 이동/회전/드랍
        if (set == KeySet.ARROWS) {
            im.put(KeyStroke.getKeyStroke("LEFT"),  ACT_LEFT);
            im.put(KeyStroke.getKeyStroke("RIGHT"), ACT_RIGHT);
            im.put(KeyStroke.getKeyStroke("DOWN"),  ACT_DOWN);
            im.put(KeyStroke.getKeyStroke("UP"),    ACT_ROT);
            im.put(KeyStroke.getKeyStroke("SPACE"), ACT_DROP);
            im.put(KeyStroke.getKeyStroke("P"),     "pause");
        } else { // WASD
            im.put(KeyStroke.getKeyStroke("A"), ACT_LEFT);
            im.put(KeyStroke.getKeyStroke("D"), ACT_RIGHT);
            im.put(KeyStroke.getKeyStroke("S"), ACT_DOWN);
            im.put(KeyStroke.getKeyStroke("W"), ACT_ROT);
            im.put(KeyStroke.getKeyStroke("F"), ACT_DROP);
            im.put(KeyStroke.getKeyStroke("R"), "pause");
        }

        // 공통 유틸
        im.put(KeyStroke.getKeyStroke(KeyEvent.VK_F11, 0), "fullscreen");
        im.put(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), "exit");
        im.put(KeyStroke.getKeyStroke("pressed C"),  "toggleColorBlind_pressed");
        im.put(KeyStroke.getKeyStroke("released C"), "toggleColorBlind_released");

        // 디버그키는 필요할 때만
        if (enableDebug) {
            im.put(KeyStroke.getKeyStroke("1"), "debugLineClear");
            im.put(KeyStroke.getKeyStroke("2"), "debugWeight");
            im.put(KeyStroke.getKeyStroke("3"), "debugSpinLock");
            im.put(KeyStroke.getKeyStroke("4"), "debugColorBomb");
            im.put(KeyStroke.getKeyStroke("5"), "debugLightning");
        }

        // 액션 공통 등록
        registerCoreActions(am, d);
    }

    /* =================== 하위호환용 래퍼 =================== */

    /** (호환) ARROWS 키셋 설치 — 예전의 install() 의미 */
    public void install(JComponent comp, Deps d) {
        install(comp, d, KeySet.ARROWS, /*enableDebug=*/true);
    }

    /** (호환) WASD 키셋 설치 — 예전의 installForP2() 의미 */
    public void installForP2(JComponent comp, Deps d) {
        install(comp, d, KeySet.WASD, /*enableDebug=*/true);
    }
}
