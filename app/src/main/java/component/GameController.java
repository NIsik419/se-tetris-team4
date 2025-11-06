package component;

import logic.BoardLogic;
import javax.swing.*;
import java.awt.event.ActionEvent;
import java.util.Map;
import component.config.Settings;

public class GameController {
    private final BoardLogic logic;
    private final JComponent target;
    private final Runnable onPause;
    private final Runnable onExit;

    public GameController(BoardLogic logic, JComponent target, Runnable onPause, Runnable onExit) {
        this.logic = logic;
        this.target = target;
        this.onPause = onPause;
        this.onExit = onExit;
        setupKeys();
    }

    private void setupKeys() {
        InputMap im = target.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW);
        ActionMap am = target.getActionMap();

        bind(im, am, "LEFT", "left", logic::moveLeft);
        bind(im, am, "RIGHT", "right", logic::moveRight);
        bind(im, am, "DOWN", "down", logic::moveDown);
        bind(im, am, "UP", "rotate", logic::rotateBlock);
        bind(im, am, "SPACE", "drop", logic::hardDrop);
        bind(im, am, "P", "pause", () -> {
            if (onPause != null)
                onPause.run();
        });
        bind(im, am, "ESCAPE", "exit", () -> {
            if (onExit != null)
                onExit.run();
        });


        bind(im, am, "F11", "fullscreen", () -> {
            JFrame frame = (JFrame) SwingUtilities.getWindowAncestor(target);
            if (frame instanceof GameFrame gf) {
                gf.toggleFullScreen();
                System.out.println("[DEBUG] Fullscreen toggled");
            } else {
                System.out.println("[WARN] F11 pressed but no GameFrame ancestor found");
            }
        });

        System.out.println("[DEBUG] GameController: Key bindings set (F11 included)");
    }

    private void bind(InputMap im, ActionMap am, String key, String name, Runnable action) {
        im.put(KeyStroke.getKeyStroke(key), name);
        am.put(name, new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                if (!logic.isGameOver()) {
                    System.out.println("[DEBUG] Key pressed: " + key + " → action=" + name);
                    action.run();
                    target.repaint();
                } else {
                    System.out.println("[DEBUG] Ignored key " + key + " (Game Over)");
                }
            }
        });
    }

    /** 🔧 Settings 재바인딩용 */
    public void updateKeyBindings(Map<Settings.Action, Integer> keymap) {
        InputMap im = target.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW);
        ActionMap am = target.getActionMap();
        im.clear();
        am.clear();

        keymap.forEach((action, keyCode) -> {
            String actionName = action.name().toLowerCase();
            im.put(KeyStroke.getKeyStroke(keyCode, 0), actionName);
            am.put(actionName, new AbstractAction() {
                @Override
                public void actionPerformed(ActionEvent e) {
                    switch (action) {
                        case Left -> logic.moveLeft();
                        case Right -> logic.moveRight();
                        case SoftDrop -> logic.moveDown();
                        case HardDrop -> logic.hardDrop();
                        case Rotate -> logic.rotateBlock();
                    }
                    target.repaint();
                }
            });
        });

        // Pause / Exit / C키는 유지
        im.put(KeyStroke.getKeyStroke("P"), "pause");
        im.put(KeyStroke.getKeyStroke("ESCAPE"), "exit");
        im.put(KeyStroke.getKeyStroke("C"), "toggleColorMode");

        am.put("pause", new AbstractAction() {
            public void actionPerformed(ActionEvent e) {
                System.out.println("[DEBUG] P key → Pause triggered");
                if (onPause != null)
                    onPause.run();
            }
        });

        am.put("exit", new AbstractAction() {
            public void actionPerformed(ActionEvent e) {
                System.out.println("[DEBUG] ESC key → Exit triggered");
                if (onExit != null)
                    onExit.run();
            }
        });

        am.put("toggleColorMode", new AbstractAction() {
            public void actionPerformed(ActionEvent e) {
                System.out.println("[DEBUG] C key → toggleColorMode() triggered (rebinding)");
                toggleColorMode();
            }
        });

        System.out.println("[DEBUG] GameController: Key bindings updated (custom map applied)");
    }

    /** 색맹모드 전환 (C키) */
    private void toggleColorMode() {
        System.out.println("[DEBUG] toggleColorMode() called");

        if (!(target instanceof BoardView view)) {
            System.out.println("[ERROR] target is not BoardView → " + target.getClass().getSimpleName());
            return;
        }

        ColorBlindPalette.Mode current = view.getColorMode();
        System.out.println("[DEBUG] Current color mode = " + current);

        ColorBlindPalette.Mode[] values = ColorBlindPalette.Mode.values();
        int next = (current.ordinal() + 1) % values.length;
        view.setColorMode(values[next]);

        System.out.println("[DEBUG] Changed color mode → " + values[next]);
    }
}
