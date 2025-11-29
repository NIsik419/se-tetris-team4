package launcher;

import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Window;
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
import versus.VersusFrame;

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
        // 메뉴 프레임 종료 리스너 추가 
        frame.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                System.out.println("[MENU] Window closing event...");
            }

            @Override
            public void windowClosed(WindowEvent e) {
                System.out.println("[MENU] Main menu closed");
                
                // // MenuPanel 정리
                // if (menuPanel != null) {
                //     menuPanel.cleanup();
                // }
                
                // 잠시 대기 후 모든 창 확인
                SwingUtilities.invokeLater(() -> {
                    try {
                        Thread.sleep(100);
                    } catch (InterruptedException ex) {
                        ex.printStackTrace();
                    }
                    
                    Window[] windows = Window.getWindows();
                    boolean allClosed = true;
                    
                    for (Window w : windows) {
                        if (w.isVisible()) {
                            allClosed = false;
                            System.out.println("[INFO] Window still visible: " + 
                                            w.getClass().getSimpleName());
                            break;
                        }
                    }
                    
                    if (allClosed) {
                        System.out.println("[EXIT] All windows closed, exiting application...");
                        //  모든 타이머 정리 후 종료
                        System.exit(0);
                    } else {
                        System.out.println("[INFO] Some windows still open, not exiting");
                    }
                });
            }
        });
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
     * 게임 모드 선택 시 호출
     */
    private void onGameConfigSelect(GameConfig config) {
        // AI 모드 체크
        if (config.mode() == GameConfig.Mode.AI) {
            startAIGame(config);
            return;
        }

        // P2P 대전 모드 체크
        boolean p2pMode = (config.mode() == GameConfig.Mode.VERSUS);

        boolean isServer = false;
        String selectedGameRule = "Normal"; // 기본값

        if (p2pMode) {
            // 서버/클라이언트 선택
            int res = JOptionPane.showConfirmDialog(null,
                    "서버로 시작하시겠습니까?",
                    "P2P 대전 모드",
                    JOptionPane.YES_NO_OPTION);
            isServer = (res == JOptionPane.YES_OPTION);

            // ⭐ 서버만 게임 룰 선택
            if (isServer) {
                String[] gameRules = { "Normal", "Item", "Time Limit (3min)" };
                selectedGameRule = (String) JOptionPane.showInputDialog(
                        null,
                        "게임 룰을 선택하세요:",
                        "게임 룰 선택",
                        JOptionPane.QUESTION_MESSAGE,
                        null,
                        gameRules,
                        gameRules[0]);

                if (selectedGameRule == null) {
                    selectedGameRule = "Normal";
                }

                System.out.println("[LAUNCHER] Selected game rule: " + selectedGameRule);
            }
        }

        startGame(config, p2pMode, isServer, selectedGameRule);
    }

    /**
     * AI 대전 시작
     */
    private void startAIGame(GameConfig playerConfig) {
        // 메뉴 프레임 숨김
        frame.setVisible(false);

        // 플레이어 설정
        GameConfig p1Config = new GameConfig(
                GameConfig.Mode.CLASSIC,
                GameConfig.Difficulty.NORMAL,
                false);

        // AI 설정 (난이도는 playerConfig에서 가져옴)
        GameConfig p2Config = new GameConfig(
                GameConfig.Mode.AI,
                playerConfig.difficulty(),
                false);

        // VersusFrame으로 AI 대전 시작
        VersusFrame aiGame = new VersusFrame(p1Config, p2Config);

        // 창 닫힘 리스너
         aiGame.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosed(WindowEvent e) {
                // 🔚 그냥 종료 → 메뉴 복귀
                frame.setVisible(true);
                showScreen(Screen.MENU);
                
                SwingUtilities.invokeLater(() -> {
                    frame.toFront();
                    frame.requestFocusInWindow();
                });
            }

            @Override
            public void windowClosing(WindowEvent e) {
                aiGame.dispose();
            }
        });
    }

    /**
     * 일반 게임 시작 (싱글/멀티)
     */
    private void startGame(GameConfig config, boolean p2pMode, boolean isServer, String gameRule) {
        // 메뉴 프레임 가리기
        frame.setVisible(false);

        GameFrame game = new GameFrame(config, p2pMode, isServer, gameRule);

        // 여기서 미리 BoardPanel 레퍼런스를 잡아둔다
        final BoardPanel mainBoardPanel;
        if (game.getActivePanel() instanceof BoardPanel bp) {
            mainBoardPanel = bp;
            // Settings 적용
            bp.applySettings(settings);

            // 아이템 모드 활성화
            if (config.mode() == GameConfig.Mode.ITEM) {
                bp.getLogic().setItemMode(true);
            }
        } else {
            mainBoardPanel = null;
        }

        game.setTitle("TETRIS – " + config.mode() + " / " + config.difficulty());
        game.setLocationRelativeTo(null);
        game.setVisible(true);

        SwingUtilities.invokeLater(() -> {
            game.requestFocusInWindow();
            game.toFront();
        });

        game.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosed(WindowEvent e) {



                if (mainBoardPanel != null && mainBoardPanel.isRestarting()) {
                    startGame(config, p2pMode, isServer, gameRule);
                    return;
                }

                // 그냥 X를 눌렀거나 EXIT 로 닫힌 경우 → 메뉴 복귀
                frame.setVisible(true);
                showScreen(Screen.MENU);
                SwingUtilities.invokeLater(() -> {
                    frame.toFront();
                    frame.requestFocusInWindow();
                });
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
