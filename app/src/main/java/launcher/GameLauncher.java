package launcher;

import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.event.ActionEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.event.WindowListener;

import javax.swing.AbstractAction;
import javax.swing.ActionMap;
import javax.swing.InputMap;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.KeyStroke;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;

import component.BoardPanel;
import component.GameConfig;
import component.GameFrame;
import component.MenuPanel;
import component.config.Settings;
import component.config.SettingsScreen;
import component.score.ScoreBoard;
import component.score.ScoreboardPanel;


public class GameLauncher {

   public static void main(String[] args) {
    System.out.println("[DEBUG] main started");
    SwingUtilities.invokeLater(() -> {
        System.out.println("[DEBUG] creating GameLauncher");
        new GameLauncher().show();
    });
    }

    enum Screen {
        MENU, SETTINGS, SCOREBOARD
    }

    private final JFrame frame = new JFrame("TETRIS");
    private final CardLayout cards = new CardLayout();
    private final JPanel root = new JPanel(cards);

    private final Settings settings = Settings.load();
    private final MenuPanel menuPanel = new MenuPanel(this::onGameConfigSelect, this::onMenuSelect);

    private final JPanel settingsPanel = createSettingsScreen();
    private final ScoreBoard scoreBoard = ScoreBoard.createDefault();
    private final ScoreboardPanel scoreboardPanel = new ScoreboardPanel(scoreBoard, () -> showScreen(Screen.MENU));

    private JPanel createSettingsScreen() {
        return new SettingsScreen(settings,
                applied -> {
                    applyMenuScaleFromSettings();
                    root.revalidate();
                    root.repaint();
                },
                () -> showScreen(Screen.MENU));
    }

    private void show() {
        frame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        frame.setSize(720, 720);
        frame.setLocationRelativeTo(null);

        root.add(menuPanel, Screen.MENU.name());
        root.add(settingsPanel, Screen.SETTINGS.name());
        root.add(scoreboardPanel, Screen.SCOREBOARD.name());

        applyMenuScaleFromSettings();
        frame.setContentPane(root);
        frame.setVisible(true);
        System.out.println("[DEBUG] show() entered");
        showScreen(Screen.MENU);
    }

    private void showScreen(Screen s) {
        cards.show(root, s.name());
        root.requestFocusInWindow();
    }

    private JPanel stubPanel(String text) {
        JPanel p = new JPanel(new BorderLayout());
        JLabel l = new JLabel(text, SwingConstants.CENTER);
        l.setFont(l.getFont().deriveFont(Font.PLAIN, 18f));
        p.add(l, BorderLayout.CENTER);

        InputMap im = p.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW);
        ActionMap am = p.getActionMap();
        im.put(KeyStroke.getKeyStroke("ESCAPE"), "back");
        am.put("back", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                showScreen(Screen.MENU);
            }
        });

        return p;
    }

    /**
     * [1] 게임 모드 선택 시 (CLASSIC / ITEM)
     */
    private void onGameConfigSelect(GameConfig config) {

        boolean p2pMode = (config.mode() == GameConfig.Mode.VERSUS); // 예시: 메뉴에서 VERSUS 모드 선택 시

        boolean isServer = false;
        if (p2pMode) {
            // 🔹 서버 / 클라이언트 선택 창
            int res = JOptionPane.showConfirmDialog(null, "서버로 시작하시겠습니까?", "P2P 대전 모드",
                    JOptionPane.YES_NO_OPTION);
            isServer = (res == JOptionPane.YES_OPTION);
        }

        startGame(config, p2pMode, isServer);
    }

    private void startGame(GameConfig config, boolean p2pMode, boolean isServer) {
        // 메뉴 프레임 가리기
        frame.setVisible(false);

        GameFrame game = new GameFrame(config, p2pMode, isServer);

        // ✅ BoardPanel의 Settings 반영
        try {
            if (game.getActivePanel() instanceof BoardPanel panel) {
                panel.applySettings(settings);
            }
        } catch (Exception ignore) {}

        // ✅ 아이템 모드 활성화 (필요하다면)
        if (config.mode() == GameConfig.Mode.ITEM &&
                game.getActivePanel() instanceof BoardPanel panel) {
            panel.getLogic().setItemMode(true);
        }

        game.setTitle("TETRIS – " + config.mode() + " / " + config.difficulty());
        game.setLocationRelativeTo(null);
        game.setVisible(true);

        SwingUtilities.invokeLater(() -> {
            game.requestFocusInWindow();
            game.toFront();
        });

        // 기존 리스너 제거
        for (WindowListener wl : frame.getWindowListeners()) {
            frame.removeWindowListener(wl);
        }

        // 창이 닫힐 때: RESTART 인지, 그냥 종료인지 구분
        game.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosed(WindowEvent e) {

                JPanel p = game.getActivePanel();

                if (p instanceof BoardPanel bp && bp.isRestarting()) {
                    // 🔁 RESTART로 닫힌 경우 → 메뉴 안 띄우고 게임만 다시 시작
                    startGame(config, p2pMode, isServer);
                    return;
                }

                // 🔚 그냥 종료(EXIТ / X) → 메뉴 복귀
                frame.setVisible(true);
                showScreen(Screen.MENU);
            }

            @Override
            public void windowClosing(WindowEvent e) {
                game.dispose();
            }
        });
    }

    /**
     * [2] 메뉴 하단 버튼 (Settings / Scoreboard / Exit)
     */
    private void onMenuSelect(MenuPanel.MenuItem item) {
        switch (item) {
            case SETTINGS -> showScreen(Screen.SETTINGS);
            case SCOREBOARD -> showScreen(Screen.SCOREBOARD);
            case EXIT -> System.exit(0);
        }
    }

    // 화면 크기 설정 반영
    private void applyMenuScaleFromSettings() {
        Dimension d = switch (settings.screenSize) {
            case SMALL -> new Dimension(600, 600);
            case MEDIUM -> new Dimension(720, 720);
            case LARGE -> new Dimension(840, 840);
        };
        frame.setSize(d);
        frame.setLocationRelativeTo(null);
    }
}
