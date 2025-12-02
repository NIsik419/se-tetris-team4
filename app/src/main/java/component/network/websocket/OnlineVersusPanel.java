package component.network.websocket;

import component.GameLoop;
import component.board.KeyBindingInstaller;
import component.sidebar.HUDSidebar;
import component.BoardView;
import component.ColorBlindPalette;
import logic.BoardLogic;
import blocks.Block;
import javax.swing.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.awt.*;

/**
 * OnlineVersusPanel (리팩토링 버전)
 * - 메인 게임 UI 및 로직
 */
public class OnlineVersusPanel extends JPanel {

    private static final int CELL_SIZE = 25;
    private static final int CELL_GAP = 0;
    private boolean opponentGameOver = false;

    private final JLabel myIncoming = new JLabel("0");
    private final JLabel oppIncoming = new JLabel("0");
    private final JLabel lagLabel = new JLabel("Connection: OK");
    private final JLabel syncStatsLabel = new JLabel("");

    private HUDSidebar mySidebar;
    private HUDSidebar oppSidebar;

    private final BoardLogic myLogic;
    private final BoardLogic oppLogic;
    private final BoardView myView;
    private final BoardView oppView;

    private final GameLoop loop;
    private Timer syncTimer;
    private Timer hudTimer;
    private Timer statsTimer;

    private boolean gameStarted = false;
    private final boolean isServer;
    private String selectedMode = "Normal";

    private TimeLimitManager timeLimitManager;
    private JLabel timerLabel;
    private static final long TIME_LIMIT_SECONDS = 180;

    // 외부 매니저들
    private NetworkManager networkManager;
    private UIOverlayManager overlayManager;

    // 게임 통계
    private long gameStartTime = 0;
    private int myTotalLines = 0;
    private int oppTotalLines = 0;

    public OnlineVersusPanel(boolean isServer, String gameRule) {
        this.isServer = isServer;
        if (gameRule != null && !gameRule.isEmpty()) {
            this.selectedMode = gameRule;
        }

        setLayout(new BorderLayout(0, 0));
        setBackground(new Color(18, 22, 30));

        /* 상단 HUD */
        add(createTopHUD(), BorderLayout.NORTH);

        /* 보드 로직 초기화 */
        myLogic = new BoardLogic(score -> networkManager.sendGameOver());
        oppLogic = new BoardLogic(score -> {
        });
        oppLogic.getState().setCurr(null);

        myLogic.setOnIncomingChanged(
                count -> SwingUtilities.invokeLater(() -> myIncoming.setText(String.valueOf(count))));
        oppLogic.setOnIncomingChanged(
                count -> SwingUtilities.invokeLater(() -> oppIncoming.setText(String.valueOf(count))));

        myView = new BoardView(myLogic, null);
        oppView = new BoardView(oppLogic, null);

        /* 중앙 컨테이너 */
        add(createCenterContainer(), BorderLayout.CENTER);

        /* 게임 루프 */
        loop = new GameLoop(myLogic, myView::repaint);
        myLogic.setLoopControl(loop::pause, loop::resume);

        /* 네트워크 초기화 */
        networkManager = new NetworkManager(
                isServer,
                this::onNetworkMessage,
                myLogic,
                oppLogic,
                lagLabel,
                this::onConnectionLost,
                this::onGameOver);
        myLogic.setOnLinesClearedWithMasks(masks -> {
            if (masks != null && masks.length > 0) {
                myTotalLines += masks.length; // masks 개수가 라인 수
                networkManager.sendLineAttack(masks);
            }
        });

        timeLimitManager = new TimeLimitManager(timerLabel, networkManager.getClient(), isServer);

        /* 키 입력 */
        setupKeyBindings();

        /* 타이머들 */
        setupTimers();

        /* 게임 오버 콜백 */
        myLogic.setOnGameOverCallback(() -> {
            SwingUtilities.invokeLater(() -> {
                loop.stopLoop();
                networkManager.sendGameOver();
                networkManager.printStats();
                triggerGameOverAnimation(myView, myLogic, () -> {
                    overlayManager.showGameOverOverlay(true, myLogic.getScore(),
                            oppLogic.getScore(), myTotalLines, gameStartTime);
                });
            });
        });

        /* 오버레이 생성 */
        overlayManager = new UIOverlayManager(
                this,
                isServer,
                selectedMode,
                this::onStartGame,
                this::onModeChanged,
                this::onRestart,
                this::cleanup);
        overlayManager.setOnExecuteRestart(this::executeRestart);
        SwingUtilities.invokeLater(() -> {
            overlayManager.createOverlay();
            // 오버레이가 표시된 후 네트워크 초기화
            SwingUtilities.invokeLater(() -> {
                networkManager.initialize(this, overlayManager);
            });
        });
    }

    private JPanel createTopHUD() {
        JPanel topHud = new JPanel(new GridLayout(1, 5, 15, 0));
        topHud.setBackground(new Color(18, 22, 30));
        topHud.setBorder(BorderFactory.createEmptyBorder(10, 20, 10, 20));
        topHud.setPreferredSize(new Dimension(0, 80));

        topHud.add(buildHud("My Incoming", myIncoming));
        topHud.add(createLagPanel());
        topHud.add(createTimerPanel());
        topHud.add(createSyncStatsPanel());
        topHud.add(buildHud("Opponent Incoming", oppIncoming));

        return topHud;
    }

    private JPanel createLagPanel() {
        JPanel panel = new JPanel();
        panel.setBackground(new Color(24, 28, 38));
        panel.setBorder(BorderFactory.createEmptyBorder(8, 12, 8, 12));
        lagLabel.setForeground(new Color(100, 255, 100));
        lagLabel.setFont(new Font("Arial", Font.BOLD, 12));
        lagLabel.setHorizontalAlignment(SwingConstants.CENTER);
        panel.add(lagLabel);
        return panel;
    }

    private JPanel createTimerPanel() {
        JPanel panel = new JPanel();
        panel.setBackground(new Color(24, 28, 38));
        panel.setBorder(BorderFactory.createEmptyBorder(8, 12, 8, 12));
        timerLabel = new JLabel("");
        timerLabel.setForeground(Color.WHITE);
        timerLabel.setFont(new Font("Arial", Font.BOLD, 16));
        timerLabel.setHorizontalAlignment(SwingConstants.CENTER);
        panel.add(timerLabel);
        return panel;
    }

    private JPanel createSyncStatsPanel() {
        JPanel panel = new JPanel();
        panel.setBackground(new Color(24, 28, 38));
        panel.setBorder(BorderFactory.createEmptyBorder(8, 12, 8, 12));
        syncStatsLabel.setForeground(new Color(150, 200, 255));
        syncStatsLabel.setFont(new Font("Arial", Font.PLAIN, 11));
        syncStatsLabel.setHorizontalAlignment(SwingConstants.CENTER);
        panel.add(syncStatsLabel);
        return panel;
    }

    private JPanel createCenterContainer() {
        JPanel centerContainer = new JPanel(new BorderLayout(0, 0));
        centerContainer.setBackground(new Color(18, 22, 30));

        mySidebar = new HUDSidebar();
        mySidebar.setPreferredSize(new Dimension(160, 0));
        centerContainer.add(mySidebar, BorderLayout.WEST);

        JPanel boardsContainer = new JPanel(new GridBagLayout());
        boardsContainer.setBackground(new Color(18, 22, 30));
        boardsContainer.setBorder(BorderFactory.createEmptyBorder(10, 20, 10, 20));

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.weightx = 1.0;
        gbc.weighty = 1.0;
        gbc.anchor = GridBagConstraints.CENTER;
        gbc.insets = new Insets(0, 10, 0, 10);

        JPanel boardsPanel = new JPanel(new GridLayout(1, 2, 30, 0));
        boardsPanel.setBackground(new Color(18, 22, 30));
        boardsPanel.add(myView);
        boardsPanel.add(oppView);

        boardsContainer.add(boardsPanel, gbc);
        centerContainer.add(boardsContainer, BorderLayout.CENTER);

        oppSidebar = new HUDSidebar();
        oppSidebar.setPreferredSize(new Dimension(160, 0));
        centerContainer.add(oppSidebar, BorderLayout.EAST);

        return centerContainer;
    }

    private void setupKeyBindings() {
        KeyBindingInstaller.Deps deps = new KeyBindingInstaller.Deps(
                myLogic, myView::repaint,
                () -> {
                }, () -> {
                }, () -> false,
                () -> {
                }, () -> {
                },
                loop::startLoop, loop::stopLoop, t -> {
                },
                () -> ColorBlindPalette.Mode.NORMAL,
                m -> {
                }, m -> {
                });
        new KeyBindingInstaller().install(myView, deps, KeyBindingInstaller.KeySet.ARROWS, false);
        myView.setFocusable(true);
        SwingUtilities.invokeLater(myView::requestFocusInWindow);
    }

    private void setupTimers() {
        hudTimer = new Timer(100, e -> {
            if (gameStarted) {
                mySidebar.setScore(myLogic.getScore());
                mySidebar.setLevel(myLogic.getLevel());
                mySidebar.setNextBlocks(myLogic.getNextBlocks());
            }
        });
        hudTimer.start();

        syncTimer = new Timer(50, e -> {
            if (gameStarted) {
                networkManager.sendBoardState();
            }
        });
        syncTimer.start();

        statsTimer = new Timer(2000, e -> updateSyncStats());
        statsTimer.start();
    }

    private void updateSyncStats() {
        if (!gameStarted)
            return;
        String stats = networkManager.getStatsString();
        SwingUtilities.invokeLater(() -> syncStatsLabel.setText(stats));
    }

    private JPanel buildHud(String title, JLabel label) {
        JPanel p = new JPanel();
        p.setBackground(new Color(24, 28, 38));
        p.setBorder(BorderFactory.createEmptyBorder(8, 12, 8, 12));
        p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));

        JLabel t = new JLabel(title);
        t.setForeground(new Color(160, 180, 200));
        t.setFont(new Font("Arial", Font.PLAIN, 12));
        t.setAlignmentX(Component.CENTER_ALIGNMENT);

        label.setForeground(Color.WHITE);
        label.setFont(new Font("Arial", Font.BOLD, 20));
        label.setAlignmentX(Component.CENTER_ALIGNMENT);

        p.add(t);
        p.add(Box.createVerticalStrut(4));
        p.add(label);
        return p;
    }

    private void onNetworkMessage(Message msg) {
        networkManager.handleMessage(msg, oppView, oppSidebar, myLogic, myView);
    }

    private void onStartGame() {
        System.out.println("[GAME] onStartGame called, gameStarted=" + gameStarted);
        if (gameStarted) {
            System.out.println("[GAME] Already started, returning");
            return;
        }
        if (isServer) {
            networkManager.getClient().send(new Message(MessageType.GAME_START, null));
        }
        gameStarted = true;
        gameStartTime = System.currentTimeMillis();
        myTotalLines = 0;
        oppTotalLines = 0;

        SwingUtilities.invokeLater(() -> {
            applyGameMode(selectedMode);
            overlayManager.hideOverlay();

            if (selectedMode.startsWith("Time Limit")) {
                long startTime = System.currentTimeMillis();
                if (isServer) {
                    networkManager.sendTimeLimitStart(startTime);
                }
                timeLimitManager.start(TIME_LIMIT_SECONDS, this::onTimeLimitTimeout);
            }

            loop.startLoop();
            myView.requestFocusInWindow();
            networkManager.startHeartbeat();
        });
    }

    private void onModeChanged(String mode) {
        selectedMode = mode;
        networkManager.sendModeSelect(mode);
    }

    private void onRestart() {
        networkManager.sendRestartReady();
        overlayManager.updateRestartStatus("Waiting for opponent...");
    }

    public void executeRestart() {
        System.out.println("[RESTART] executeRestart called");
        gameStarted = false;
        System.out.println("[RESTART] gameStarted set to false");

        gameStartTime = 0;
        myTotalLines = 0;
        oppTotalLines = 0;

        // 보드 리셋
        myLogic.reset();
        oppLogic.reset();
        oppLogic.getState().setCurr(null);

        // 타이머 리셋
        if (timeLimitManager != null) {
            timeLimitManager.stop();
            timeLimitManager.reset();
        }

        // 네트워크 리셋
        networkManager.resetForRestart();
        networkManager.resetAdapter();

        // UI 리셋
        myIncoming.setText("0");
        oppIncoming.setText("0");
        mySidebar.setScore(0);
        mySidebar.setLevel(1);
        oppSidebar.setScore(0);
        oppSidebar.setLevel(1);

        myView.repaint();
        oppView.repaint();

        System.out.println("[RESTART] About to call onStartGame");
        onStartGame();
    }

    private void applyGameMode(String mode) {
        myLogic.setItemMode(mode.equals("Item"));
    }

    private void onTimeLimitTimeout() {
        loop.stopLoop();
        int myScore = myLogic.getScore();
        int oppScore = oppLogic.getScore();
        boolean iWon = myScore > oppScore;

        networkManager.sendGameOver();
        triggerGameOverAnimation(myView, myLogic, () -> {
            overlayManager.showTimeLimitGameOverOverlay(iWon, myScore, oppScore,
                    myTotalLines, TIME_LIMIT_SECONDS);
        });
    }

    private void onConnectionLost() {
        if (gameStarted && loop != null) {
            loop.pause();
        }

        SwingUtilities.invokeLater(() -> {
            lagLabel.setText("DISCONNECTED");
            lagLabel.setForeground(Color.RED);

            int choice = JOptionPane.showConfirmDialog(
                    this,
                    "Connection to opponent lost.\nAttempt to reconnect?", // 수정
                    "Connection Lost",
                    JOptionPane.YES_NO_OPTION,
                    JOptionPane.WARNING_MESSAGE);

            if (choice == JOptionPane.YES_OPTION) {
                autoReconnect(); // 추가
            } else {
                returnToMainMenu();
            }
        });
    }

    private void onGameOver() {
        System.out.println("[GAMEOVER] onGameOver called");
        opponentGameOver = true;

        SwingUtilities.invokeLater(() -> {
            System.out.println("[GAMEOVER] Inside SwingUtilities.invokeLater");
            loop.stopLoop(); // 내 게임도 멈춤
            networkManager.printStats();

            // 상대 보드 클리어
            Color[][] oppBoard = oppLogic.getBoard();
            for (int y = 0; y < BoardLogic.HEIGHT; y++) {
                for (int x = 0; x < BoardLogic.WIDTH; x++) {
                    oppBoard[y][x] = null;
                }
            }
            oppView.repaint();

            // 상대가 먼저 죽음
            System.out.println("[GAMEOVER] About to trigger animation");
            triggerGameOverAnimation(myView, myLogic, () -> {
                System.out.println("[GAMEOVER] Showing victory overlay");
                overlayManager.showGameOverOverlay(false, myLogic.getScore(),
                        oppLogic.getScore(), myTotalLines, gameStartTime);
            });
        });
    }

    private void returnToMainMenu() {
        cleanup();
        SwingUtilities.invokeLater(() -> {
            Window window = SwingUtilities.getWindowAncestor(this);
            if (window != null) {
                window.dispose();
            }
        });
    }

    private void cleanup() {
        try {
            if (myLogic != null) {
                myLogic.setOnLinesClearedWithMasks(null);
                myLogic.setOnGameOverCallback(null);
                myLogic.setOnIncomingChanged(null);
            }
            if (oppLogic != null) {
                oppLogic.setOnIncomingChanged(null);
            }
            if (syncTimer != null)
                syncTimer.stop();
            if (hudTimer != null)
                hudTimer.stop();
            if (statsTimer != null)
                statsTimer.stop();
            if (gameStarted) {
                loop.stopLoop();
            }
            if (networkManager != null) {
                networkManager.cleanup();
            }
        } catch (Exception e) {
            System.err.println("[CLEANUP] Error: " + e.getMessage());
        }
    }

    /**
     * 자동 재연결 (최대 3회 시도)
     */
    private void autoReconnect() {
        final int MAX_RETRIES = 3;
        final int RETRY_DELAY = 2000; // 2초

        new Thread(() -> {
            for (int attempt = 1; attempt <= MAX_RETRIES; attempt++) {
                try {
                    System.out.println("[RECONNECT] Attempt " + attempt + "/" + MAX_RETRIES);

                    final int currentAttempt = attempt;
                    SwingUtilities.invokeLater(() -> lagLabel.setText("RECONNECTING... (" + currentAttempt + "/3)"));

                    Thread.sleep(RETRY_DELAY);

                    // 재연결 시도
                    networkManager.reconnect();

                    // 성공 시
                    SwingUtilities.invokeLater(() -> {
                        lagLabel.setText("RECONNECTED");
                        lagLabel.setForeground(new Color(100, 255, 100));
                        if (gameStarted) {
                            loop.resume();
                        }
                    });
                    return;

                } catch (Exception e) {
                    System.err.println("[RECONNECT] Attempt " + attempt + " failed: " + e.getMessage());

                    if (attempt == MAX_RETRIES) {
                        SwingUtilities.invokeLater(() -> {
                            lagLabel.setText("DISCONNECTED");
                            lagLabel.setForeground(Color.RED);

                            int choice = JOptionPane.showConfirmDialog(
                                    OnlineVersusPanel.this,
                                    "Failed to reconnect after 3 attempts.\nReturn to main menu?",
                                    "Connection Failed",
                                    JOptionPane.YES_NO_OPTION);

                            if (choice == JOptionPane.YES_OPTION) {
                                returnToMainMenu();
                            } else {
                                lagLabel.setText("OFFLINE MODE");
                            }
                        });
                    }
                }
            }
        }).start();
    }

    private void triggerGameOverAnimation(BoardView view, BoardLogic logic, Runnable afterAnimation) {
        Color[][] board = logic.getBoard();
        Color[][] boardCopy = new Color[BoardLogic.HEIGHT][BoardLogic.WIDTH];

        for (int y = 0; y < BoardLogic.HEIGHT; y++) {
            for (int x = 0; x < BoardLogic.WIDTH; x++) {
                boardCopy[y][x] = board[y][x];
                board[y][x] = null;
            }
        }

        view.repaint();

        JPanel glassPane = new JPanel(null);
        glassPane.setOpaque(false);

        JFrame parentFrame = (JFrame) SwingUtilities.getWindowAncestor(this);
        if (parentFrame == null) {
            if (afterAnimation != null)
                afterAnimation.run();
            return;
        }

        parentFrame.setGlassPane(glassPane);
        glassPane.setVisible(true);

        List<JPanel> blocks = new ArrayList<>();

        for (int y = 0; y < BoardLogic.HEIGHT; y++) {
            for (int x = 0; x < BoardLogic.WIDTH; x++) {
                if (boardCopy[y][x] != null) {
                    JPanel block = new JPanel();
                    block.setBackground(boardCopy[y][x]);
                    block.setBorder(BorderFactory.createLineBorder(boardCopy[y][x].darker(), 1));

                    Point screenPos = SwingUtilities.convertPoint(
                            view,
                            x * CELL_SIZE + CELL_GAP,
                            y * CELL_SIZE + CELL_GAP,
                            glassPane);

                    block.setBounds(
                            screenPos.x,
                            screenPos.y,
                            CELL_SIZE - CELL_GAP * 2,
                            CELL_SIZE - CELL_GAP * 2);

                    glassPane.add(block);
                    blocks.add(block);
                }
            }
        }

        Timer explosionTimer = new Timer(12, null);
        final int[] frameCount = { 0 };
        final int maxFrames = 30;

        List<double[]> velocities = new ArrayList<>();
        for (int i = 0; i < blocks.size(); i++) {
            velocities.add(new double[] {
                    (Math.random() - 0.5) * 60,
                    -(Math.random() * 25 + 15),
                    (Math.random() - 0.5) * 30
            });
        }

        explosionTimer.addActionListener(e -> {
            frameCount[0]++;

            for (int i = 0; i < blocks.size(); i++) {
                JPanel block = blocks.get(i);
                double[] vel = velocities.get(i);

                Rectangle bounds = block.getBounds();
                bounds.x += (int) vel[0];
                bounds.y += (int) vel[1];
                vel[1] += 10;

                block.setBounds(bounds);

                float alpha = 1.0f - (frameCount[0] / (float) maxFrames);
                alpha = Math.max(0, alpha);

                Color originalColor = block.getBackground();
                block.setBackground(new Color(
                        originalColor.getRed(),
                        originalColor.getGreen(),
                        originalColor.getBlue(),
                        (int) (255 * alpha)));
            }

            glassPane.repaint();

            if (frameCount[0] >= maxFrames) {
                // 모든 블록 제거
                glassPane.removeAll(); // 전체 제거
                glassPane.revalidate();
                glassPane.repaint();

                ((Timer) e.getSource()).stop();
                if (afterAnimation != null) {
                    // 약간의 딜레이 후 콜백 실행
                    Timer callbackTimer = new Timer(100, evt -> {
                        SwingUtilities.invokeLater(afterAnimation);
                        ((Timer) evt.getSource()).stop();
                    });
                    callbackTimer.setRepeats(false);
                    callbackTimer.start();
                }
            }
        });

        explosionTimer.start();
    }

    @Override
    public Dimension getPreferredSize() {
        int boardWidth = myView.getPreferredSize().width;
        int boardHeight = myView.getPreferredSize().height;
        int totalWidth = (boardWidth * 2) + (160 * 2) + 100;
        int totalHeight = boardHeight + 180;
        return new Dimension(totalWidth, totalHeight);
    }
}