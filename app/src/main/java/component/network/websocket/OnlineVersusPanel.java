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
    private boolean myRestartReady = false;
    private boolean oppRestartReady = false;
    private boolean isTimeLimitMode = false;

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

        // 기존의 myLogic.setOnIncomingLinesChanged 부분을 찾아서 추가
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
        myLogic.setOnGarbagePreviewChanged(
                lines -> {
                    System.out.println("[MY_LOGIC] Garbage preview changed: " + lines.size() + " lines");
                    SwingUtilities.invokeLater(() -> {
                        mySidebar.setGarbageLines(lines);
                        //networkManager.sendGarbagePreview(lines);
                    });
                });

        oppLogic.setOnGarbagePreviewChanged(
                lines -> {
                    System.out.println("[OPP_LOGIC] Garbage preview changed: " + lines.size() + " lines");
                    SwingUtilities.invokeLater(() -> {
                        oppSidebar.setGarbageLines(lines);
                    });
                });

        // 공격 콜백도 여기에
        myLogic.setOnLinesClearedWithMasks(masks -> {
            if (masks != null && masks.length > 0) {
                System.out.println("[ATTACK] Sending " + masks.length + " lines attack");
                myTotalLines += masks.length;
                networkManager.sendLineAttack(masks);

                List<boolean[]> preview = convertMasksToPreview(masks);
                System.out.println("[ATTACK] Showing on oppSidebar: " + preview.size() + " lines");
                SwingUtilities.invokeLater(() -> oppSidebar.setGarbageLines(preview));
            }
        });

        networkManager.setOnTimeLimitStart(startTime -> {
            if (timeLimitManager != null) {
                // syncStart를 사용하여 서버 시간과 동기화
                timeLimitManager.syncStart(startTime, TIME_LIMIT_SECONDS);
            }
        });

        networkManager.setOnModeChanged(mode -> {
            this.selectedMode = mode;
            System.out.println("[MODE] selectedMode updated to: " + mode);
        });

        networkManager.setOnOpponentRestartReady(() -> {
            oppRestartReady = true;

            // 내가 이미 준비됐으면 시작 화면으로
            if (myRestartReady) {
                executeRestart();
                overlayManager.showStartOverlay();
            } else {
                // 상대방만 준비된 상태
                overlayManager.updateGameOverStatus("Opponent ready! Press OK to continue");
            }
        });

        myLogic.setOnLinesClearedWithMasks(masks -> {
            if (masks != null && masks.length > 0) {
                myTotalLines += masks.length;
                networkManager.sendLineAttack(masks);

                // // 상대방에게 프리뷰도 전송
                // List<boolean[]> preview = convertMasksToPreview(masks);
                // networkManager.sendGarbagePreview(preview);
            }
        });

        myLogic.setOnVisualEffect((type, value) -> {
            // 내 보드에 표시
            SwingUtilities.invokeLater(() -> {
                switch (type) {
                    case "combo" -> myView.showCombo(value);
                    case "lineClear" -> myView.showLineClear(value);
                    case "perfectClear" -> myView.showPerfectClear();
                    case "backToBack" -> myView.showBackToBack();
                    case "speedUp" -> myView.showSpeedUp(value);
                }
            });

            // 상대방에게 전송
            networkManager.sendVisualEffect(type, value);
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
                triggerGlassShatterEffect(myView, myLogic, () -> {
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
        mySidebar.showTime(false);
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
        oppSidebar.showTime(false);
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
        networkManager.handleMessage(msg, oppView, oppSidebar, myLogic, myView, oppLogic);
    }

    private void onStartGame() {
        System.out.println("[GAME] onStartGame called, gameStarted=" + gameStarted);
        if (gameStarted) {
            System.out.println("[GAME] Already started, returning");
            return;
        }

        gameStarted = true;
        isTimeLimitMode = selectedMode.startsWith("Time Limit");
        if (isServer) {
            networkManager.getClient().send(new Message(MessageType.GAME_START, null));
        }

        gameStartTime = System.currentTimeMillis();
        myTotalLines = 0;
        oppTotalLines = 0;

        SwingUtilities.invokeLater(() -> {
            applyGameMode(selectedMode);
            overlayManager.hideOverlay();

            if (isTimeLimitMode) {
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
        System.out.println("[RESTART] onRestart called");

        myRestartReady = true;
        networkManager.sendRestartReady();

        // 상대방도 준비됐으면 시작 화면으로
        if (oppRestartReady) {
            executeRestart();
            overlayManager.showStartOverlay();
        } else {
            // 상대방 대기 중 표시
            overlayManager.updateGameOverStatus("Waiting for opponent...");
        }
    }

    public void executeRestart() {
        System.out.println("[RESTART] executeRestart called");

        gameStarted = false;
        isTimeLimitMode = false;
        myRestartReady = false;
        oppRestartReady = false;
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

        // 서버는 현재 모드를 다시 전송
        if (isServer) {
            networkManager.sendModeSelect(selectedMode);
        }

        System.out.println("[RESTART] About to call onStartGame");
        // onStartGame();
    }

    private void applyGameMode(String mode) {
        myLogic.setItemMode(mode.equals("Item"));
    }

    private void onTimeLimitTimeout() {
        System.out.println("[TIME_LIMIT] Timeout reached!");
        loop.stopLoop();

        int myScore = myLogic.getScore();

        // 상대방에게 내 점수 전송
        networkManager.sendMyScore(myScore);

        System.out.println("[TIME_LIMIT] My score: " + myScore + ", waiting for opponent score...");

        // 잠시 대기 후 비교 (상대방 점수 수신 대기)
        Timer waitTimer = new Timer(500, e -> {
            int oppScore = oppLogic.getScore();
            boolean iWon = myScore > oppScore;

            System.out.println(
                    "[TIME_LIMIT] Final comparison - My: " + myScore + ", Opp: " + oppScore + ", iWon: " + iWon);

            // 진 사람 보드에 glass shatter
            BoardView targetView = iWon ? oppView : myView;
            BoardLogic targetLogic = iWon ? oppLogic : myLogic;

            triggerGlassShatterEffect(targetView, targetLogic, () -> {
                overlayManager.showTimeLimitGameOverOverlay(iWon, myScore, oppScore,
                        myTotalLines, TIME_LIMIT_SECONDS);
            });

            ((Timer) e.getSource()).stop();
        });
        waitTimer.setRepeats(false);
        waitTimer.start();
    }

    private void onConnectionLost() {
        if (gameStarted && loop != null) {
            loop.pause();
        }

        SwingUtilities.invokeLater(() -> {
            lagLabel.setText("DISCONNECTED");
            lagLabel.setForeground(Color.RED);

            if (!gameStarted) {
                JOptionPane.showMessageDialog(
                        this,
                        "Waiting for opponent to connect...\nPlease wait.",
                        "No Opponent",
                        JOptionPane.INFORMATION_MESSAGE);
                return; // 재연결 시도하지 않음
            }

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
        System.out.println("[GAMEOVER] isTimeLimitMode=" + isTimeLimitMode);
        System.out.println("[GAMEOVER] selectedMode=" + selectedMode);
        opponentGameOver = true;

        SwingUtilities.invokeLater(() -> {
            System.out.println("[GAMEOVER] Inside SwingUtilities.invokeLater");
            loop.stopLoop();
            networkManager.printStats();

            System.out.println("[GAMEOVER] About to trigger OPPONENT collapse");

            triggerGlassShatterEffect(oppView, oppLogic, () -> {
                System.out.println("[GAMEOVER] Showing victory overlay");
                System.out.println("[GAMEOVER] Final check - isTimeLimitMode=" + isTimeLimitMode);

                // Time Limit 모드면 Time Limit 오버레이 표시
                if (isTimeLimitMode) {
                    int myScore = myLogic.getScore();
                    int oppScore = oppLogic.getScore();
                    boolean iWon = myScore > oppScore;

                    System.out.println("[GAMEOVER] TIME LIMIT overlay: iWon=" + iWon);
                    overlayManager.showTimeLimitGameOverOverlay(iWon, myScore, oppScore,
                            myTotalLines, TIME_LIMIT_SECONDS);
                } else {
                    // 일반 모드 승리 오버레이
                    System.out.println("[GAMEOVER] NORMAL overlay");
                    overlayManager.showGameOverOverlay(false, myLogic.getScore(),
                            oppLogic.getScore(), myTotalLines, gameStartTime);
                }
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
    private boolean isAutoReconnecting = false;

    private void autoReconnect() {
        if (isAutoReconnecting) {
            System.out.println("[RECONNECT] Already reconnecting...");
            return;
        }

        isAutoReconnecting = true;

        final int MAX_RETRIES = 3;
        final int RETRY_DELAY = 2000;

        new Thread(() -> {
            try {
                for (int attempt = 1; attempt <= MAX_RETRIES; attempt++) {
                    try {
                        System.out.println("[RECONNECT] Attempt " + attempt + "/" + MAX_RETRIES);

                        final int currentAttempt = attempt;
                        SwingUtilities
                                .invokeLater(() -> lagLabel.setText("RECONNECTING... (" + currentAttempt + "/3)"));

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
            } finally {
                isAutoReconnecting = false;
            }
        }).start();
    }

    /**
     * 보드 전체가 유리처럼 깨지는 효과
     */
    private void triggerGlassShatterEffect(BoardView view, BoardLogic logic, Runnable afterAnimation) {
        System.out.println("[GLASS] Starting glass shatter effect for " + (view == myView ? "MY" : "OPP"));

        Color[][] board = logic.getBoard();

        // 보드를 격자 조각으로 나누기 (각 조각은 여러 셀)
        List<GlassShard> shards = new ArrayList<>();
        int shardSize = 1; // 2x2 셀 크기의 조각

        for (int sy = 0; sy < BoardLogic.HEIGHT; sy += shardSize) {
            for (int sx = 0; sx < BoardLogic.WIDTH; sx += shardSize) {
                GlassShard shard = new GlassShard();
                shard.startX = sx;
                shard.startY = sy;
                shard.width = Math.min(shardSize, BoardLogic.WIDTH - sx);
                shard.height = Math.min(shardSize, BoardLogic.HEIGHT - sy);

                // 조각의 색상 (있는 블록들의 평균)
                int colorCount = 0;
                int r = 0, g = 0, b = 0;
                for (int y = sy; y < sy + shard.height && y < BoardLogic.HEIGHT; y++) {
                    for (int x = sx; x < sx + shard.width && x < BoardLogic.WIDTH; x++) {
                        if (board[y][x] != null) {
                            r += board[y][x].getRed();
                            g += board[y][x].getGreen();
                            b += board[y][x].getBlue();
                            colorCount++;
                        }
                    }
                }

                if (colorCount > 0) {
                    shard.color = new Color(r / colorCount, g / colorCount, b / colorCount);
                } else {
                    shard.color = new Color(50, 50, 50); // 회색 (빈 칸)
                }

                // 중심에서 바깥으로 날아가는 방향
                double centerX = BoardLogic.WIDTH / 2.0;
                double centerY = BoardLogic.HEIGHT / 2.0;
                double dx = (sx + shard.width / 2.0) - centerX;
                double dy = (sy + shard.height / 2.0) - centerY;
                double distance = Math.sqrt(dx * dx + dy * dy);

                if (distance > 0) {
                    shard.velocityX = (dx / distance) * (3 + Math.random() * 3);
                    shard.velocityY = (dy / distance) * (3 + Math.random() * 3);
                } else {
                    shard.velocityX = (Math.random() - 0.5) * 5;
                    shard.velocityY = (Math.random() - 0.5) * 5;
                }

                shard.rotationSpeed = (Math.random() - 0.5) * 15;

                shards.add(shard);
            }
        }

        // 보드 클리어
        for (int y = 0; y < BoardLogic.HEIGHT; y++) {
            for (int x = 0; x < BoardLogic.WIDTH; x++) {
                board[y][x] = null;
            }
        }
        view.repaint();

        System.out.println("[GLASS] Created " + shards.size() + " shards");

        // glassPane에 조각들을 그리기
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

        // 각 조각을 JPanel로 생성
        List<JPanel> shardPanels = new ArrayList<>();

        for (GlassShard shard : shards) {
            JPanel shardPanel = new JPanel();
            shardPanel.setBackground(shard.color);
            shardPanel.setBorder(BorderFactory.createLineBorder(Color.BLACK, 1));
            shardPanel.setOpaque(true);

            Point screenPos = SwingUtilities.convertPoint(
                    view,
                    shard.startX * CELL_SIZE,
                    shard.startY * CELL_SIZE,
                    glassPane);

            shard.currentX = screenPos.x;
            shard.currentY = screenPos.y;

            shardPanel.setBounds(
                    screenPos.x,
                    screenPos.y,
                    shard.width * CELL_SIZE,
                    shard.height * CELL_SIZE);

            glassPane.add(shardPanel);
            shardPanels.add(shardPanel);
        }

        // 애니메이션
        Timer shatterTimer = new Timer(8, null); // 60fps
        final int[] frameCount = { 0 };
        final int maxFrames = 30;

        shatterTimer.addActionListener(e -> {
            frameCount[0]++;

            for (int i = 0; i < shards.size(); i++) {
                GlassShard shard = shards.get(i);
                JPanel panel = shardPanels.get(i);

                // 물리 시뮬레이션
                shard.velocityY += 0.4; // 중력
                shard.currentX += shard.velocityX;
                shard.currentY += shard.velocityY;
                shard.rotation += shard.rotationSpeed;

                // 페이드 아웃
                float alpha = 1.0f - (frameCount[0] / (float) maxFrames);
                alpha = Math.max(0, alpha);

                Color c = shard.color;
                panel.setBackground(new Color(
                        c.getRed(),
                        c.getGreen(),
                        c.getBlue(),
                        (int) (255 * alpha)));

                panel.setBounds(
                        (int) shard.currentX,
                        (int) shard.currentY,
                        shard.width * CELL_SIZE,
                        shard.height * CELL_SIZE);
            }

            glassPane.repaint();

            if (frameCount[0] >= maxFrames) {
                glassPane.removeAll();
                glassPane.setVisible(false);
                ((Timer) e.getSource()).stop();

                if (afterAnimation != null) {
                    Timer delayTimer = new Timer(100, evt -> {
                        SwingUtilities.invokeLater(afterAnimation);
                        ((Timer) evt.getSource()).stop();
                    });
                    delayTimer.setRepeats(false);
                    delayTimer.start();
                }
            }
        });

        shatterTimer.start();
    }

    // 유리 조각 클래스
    private static class GlassShard {
        int startX, startY;
        int width, height;
        Color color;
        double currentX, currentY;
        double velocityX, velocityY;
        double rotation;
        double rotationSpeed;
    }

    @Override
    public Dimension getPreferredSize() {
        int boardWidth = myView.getPreferredSize().width;
        int boardHeight = myView.getPreferredSize().height;
        int totalWidth = (boardWidth * 2) + (160 * 2) + 100;
        int totalHeight = boardHeight + 180;
        return new Dimension(totalWidth, totalHeight);
    }

    private List<boolean[]> convertMasksToPreview(int[] masks) {
        List<boolean[]> preview = new ArrayList<>();
        for (int mask : masks) {
            boolean[] row = new boolean[BoardLogic.WIDTH];
            for (int x = 0; x < BoardLogic.WIDTH; x++) {
                row[x] = ((mask >> x) & 1) != 0;
            }
            preview.add(row);
        }
        return preview;
    }

}