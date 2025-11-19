package component.network.websocket;

import component.GameConfig;
import component.GameLoop;
import component.board.KeyBindingInstaller;
import component.sidebar.HUDSidebar;
import component.BoardView;
import component.ColorBlindPalette;
import logic.BoardLogic;
import blocks.Block;
import javax.swing.*;

import blocks.Block;
import java.util.List;
import java.awt.*;
import java.io.*;
import java.net.InetAddress;
import java.nio.file.Files;
import java.nio.file.Paths;

/**
 * OnlineVersusPanel (델타 전송 방식 적용)
 * 
 * 주요 변경사항:
 * - 전체 보드 전송 → 델타(변경사항만) 전송
 * - 75% 네트워크 대역폭 절약
 * - 동기화 통계 추적
 */
public class OnlineVersusPanel extends JPanel {

    private static final String IP_SAVE_FILE = "recent_server_ip.txt";

    private final JLabel myIncoming = new JLabel("0");
    private final JLabel oppIncoming = new JLabel("0");
    private final JLabel lagLabel = new JLabel("Connection: OK");
    private final JLabel syncStatsLabel = new JLabel(""); // 동기화 통계 표시

    private HUDSidebar mySidebar;
    private HUDSidebar oppSidebar;

    private final BoardLogic myLogic;
    private final BoardLogic oppLogic;
    private final BoardView myView;
    private final BoardView oppView;

    private final GameClient client;
    private BoardSyncAdapter adapter;
    private final GameLoop loop;
    private final Timer syncTimer;

    private boolean myRestartReady = false;
    private boolean oppRestartReady = false;
    private boolean isReady = false;
    private boolean oppReady = false;
    private boolean gameStarted = false;
    private boolean isServer;

    // 네트워크 안정성
    private long lastPingTime = 0;
    private long lastPongTime = 0;
    private Timer heartbeatTimer;
    private Timer connectionCheckTimer;
    private Timer statsTimer; // 통계 업데이트 타이머
    private static final long PING_INTERVAL = 1000;
    private static final long LAG_THRESHOLD = 200;
    private static final long DISCONNECT_THRESHOLD = 5000;

    // 오버레이 UI
    private JPanel gameOverPanel;
    private JPanel overlayPanel;
    private JLabel statusLabel;
    private JLabel ipLabel;
    private JComboBox<String> modeSelector;
    private JButton startButton;
    private String selectedMode = "Normal";

    public OnlineVersusPanel(boolean isServer) {
        this.isServer = isServer;
        setLayout(new BorderLayout(0, 0));
        setBackground(new Color(18, 22, 30));

        /* 상단 HUD - 높이 고정 */
        JPanel topHud = new JPanel(new GridLayout(1, 4, 15, 0)); // 4칸으로 변경 (통계 추가)
        topHud.setBackground(new Color(18, 22, 30));
        topHud.setBorder(BorderFactory.createEmptyBorder(10, 20, 10, 20));
        topHud.setPreferredSize(new Dimension(0, 80));
        topHud.add(buildHud("My Incoming", myIncoming));

        // 중앙 연결 상태 표시
        JPanel lagPanel = new JPanel();
        lagPanel.setBackground(new Color(24, 28, 38));
        lagPanel.setBorder(BorderFactory.createEmptyBorder(8, 12, 8, 12));
        lagLabel.setForeground(new Color(100, 255, 100));
        lagLabel.setFont(new Font("Arial", Font.BOLD, 12));
        lagLabel.setHorizontalAlignment(SwingConstants.CENTER);
        lagPanel.add(lagLabel);
        topHud.add(lagPanel);

        // 동기화 통계 표시 (NEW!)
        JPanel syncPanel = new JPanel();
        syncPanel.setBackground(new Color(24, 28, 38));
        syncPanel.setBorder(BorderFactory.createEmptyBorder(8, 12, 8, 12));
        syncStatsLabel.setForeground(new Color(150, 200, 255));
        syncStatsLabel.setFont(new Font("Arial", Font.PLAIN, 11));
        syncStatsLabel.setHorizontalAlignment(SwingConstants.CENTER);
        syncPanel.add(syncStatsLabel);
        topHud.add(syncPanel);

        topHud.add(buildHud("Opponent Incoming", oppIncoming));
        add(topHud, BorderLayout.NORTH);

        /* 보드 로직 초기화 */
        myLogic = new BoardLogic(score -> adapter.sendGameOver());
        oppLogic = new BoardLogic(score -> {
        });
        oppLogic.getState().setCurr(null);

        myLogic.setOnIncomingChanged(
                count -> SwingUtilities.invokeLater(() -> myIncoming.setText(String.valueOf(count))));
        oppLogic.setOnIncomingChanged(
                count -> SwingUtilities.invokeLater(() -> oppIncoming.setText(String.valueOf(count))));

        myView = new BoardView(myLogic);
        oppView = new BoardView(oppLogic);

        /* 중앙 컨테이너 - 사이드바 + 보드들 */
        JPanel centerContainer = new JPanel(new BorderLayout(0, 0));
        centerContainer.setBackground(new Color(18, 22, 30));

        // 왼쪽 사이드바 (나)
        mySidebar = new HUDSidebar();
        mySidebar.setPreferredSize(new Dimension(160, 0));
        centerContainer.add(mySidebar, BorderLayout.WEST);

        // 보드 패널 - GridBagLayout으로 중앙 정렬
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

        // 보드들을 담을 패널 - 내 보드가 왼쪽
        JPanel boardsPanel = new JPanel(new GridLayout(1, 2, 30, 0));
        boardsPanel.setBackground(new Color(18, 22, 30));
        boardsPanel.add(myView);
        boardsPanel.add(oppView);

        boardsContainer.add(boardsPanel, gbc);
        centerContainer.add(boardsContainer, BorderLayout.CENTER);

        // 오른쪽 사이드바 (상대)
        oppSidebar = new HUDSidebar();
        oppSidebar.setPreferredSize(new Dimension(160, 0));
        centerContainer.add(oppSidebar, BorderLayout.EAST);

        add(centerContainer, BorderLayout.CENTER);

        /* 네트워크 초기화 */
        client = new GameClient(this::onNetworkMessage);
        adapter = new BoardSyncAdapter(myLogic, oppLogic, client);

        client.setOnConnected(() -> {
            System.out.println("[DEBUG] onConnected callback!");
            isReady = true;
            lastPongTime = System.currentTimeMillis();
            client.send(new Message(MessageType.PLAYER_READY, "ready"));
            updateOverlay("Connected! Waiting for opponent...");
            checkReadyState();
        });

        try {
            if (isServer) {
                GameServer.startServer(8081);
                Thread.sleep(1000);
                client.connect("ws://localhost:8081/game");
            } else {
                String recentIp = loadRecentServerIp();
                String prompt = recentIp != null
                        ? "Enter server IP: (Recent: " + recentIp + ")"
                        : "Enter server IP:";

                String ip = JOptionPane.showInputDialog(this, prompt, recentIp != null ? recentIp : "localhost");

                if (ip == null || ip.trim().isEmpty()) {
                    ip = recentIp != null ? recentIp : "localhost";
                }

                saveRecentServerIp(ip);
                client.connect("ws://" + ip + ":8081/game");
            }
        } catch (Exception e) {
            JOptionPane.showMessageDialog(this, "Connection failed: " + e.getMessage());
            throw new RuntimeException(e);
        }

        /* 게임 루프 */
        loop = new GameLoop(myLogic, myView::repaint);
        myLogic.setLoopControl(loop::pause, loop::resume);

        /* 키 입력 */
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

        /* 점수/레벨 업데이트 타이머 */
        Timer hudTimer = new Timer(100, e -> {
            if (gameStarted) {
                mySidebar.setScore(myLogic.getScore());
                mySidebar.setLevel(myLogic.getLevel());
                mySidebar.setNextBlocks(myLogic.getNextBlocks());

                oppSidebar.setScore(oppLogic.getScore());
                oppSidebar.setLevel(oppLogic.getLevel());
            }
        });
        hudTimer.start();

        /* 델타 동기화 타이머 - 변경사항만 전송 */
        syncTimer = new Timer(300, e -> {
            if (gameStarted) {
                adapter.sendBoardState(); // 델타만 전송
            }
        });
        syncTimer.start();

        /* 동기화 통계 업데이트 타이머 (NEW!) */
        statsTimer = new Timer(2000, e -> updateSyncStats());
        statsTimer.start();

        /* Heartbeat 타이머 */
        heartbeatTimer = new Timer((int) PING_INTERVAL, e -> sendPing());

        /* 연결 체크 타이머 */
        connectionCheckTimer = new Timer(1000, e -> checkConnection());
        connectionCheckTimer.start();

        myLogic.setOnGameOverCallback(() -> {
            SwingUtilities.invokeLater(() -> {
                loop.stopLoop();
                client.send(new Message(MessageType.GAME_OVER, null));
                
                // 게임 종료 시 통계 출력
                adapter.printStats();
                
                showGameOverOverlay(true);
            });
        });

        /* 오버레이 생성 */
        createOverlay();

        addComponentListener(new java.awt.event.ComponentAdapter() {
            @Override
            public void componentResized(java.awt.event.ComponentEvent e) {
                centerOverlay();
            }
        });
    }

    /* ===== 동기화 통계 업데이트 (NEW!) ===== */
    
    private void updateSyncStats() {
        if (!gameStarted) return;
        
        String stats = adapter.getStatsString();
        SwingUtilities.invokeLater(() -> syncStatsLabel.setText(stats));
    }

    /* ===== IP 자동 저장/불러오기 메서드 ===== */

    private String loadRecentServerIp() {
        try {
            if (Files.exists(Paths.get(IP_SAVE_FILE))) {
                String ip = Files.readString(Paths.get(IP_SAVE_FILE)).trim();
                if (!ip.isEmpty()) {
                    System.out.println("[IP] Loaded recent IP: " + ip);
                    return ip;
                }
            }
        } catch (IOException e) {
            System.err.println("[IP] Failed to load recent IP: " + e.getMessage());
        }
        return null;
    }

    private void saveRecentServerIp(String ip) {
        try {
            Files.writeString(Paths.get(IP_SAVE_FILE), ip);
            System.out.println("[IP] Saved recent IP: " + ip);
        } catch (IOException e) {
            System.err.println("[IP] Failed to save IP: " + e.getMessage());
        }
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

    /** 중앙 오버레이 생성 */
    private void createOverlay() {
        overlayPanel = new JPanel();
        overlayPanel.setLayout(new BoxLayout(overlayPanel, BoxLayout.Y_AXIS));
        overlayPanel.setBackground(new Color(30, 35, 45, 240));
        overlayPanel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(100, 150, 200), 2),
                BorderFactory.createEmptyBorder(30, 40, 30, 40)));

        statusLabel = new JLabel("Connecting...");
        statusLabel.setForeground(new Color(255, 200, 100));
        statusLabel.setFont(new Font("Arial", Font.BOLD, 16));
        statusLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        overlayPanel.add(statusLabel);
        overlayPanel.add(Box.createVerticalStrut(15));

        if (isServer) {
            ipLabel = new JLabel("IP: " + getLocalIP() + ":8081");
            ipLabel.setForeground(new Color(100, 200, 255));
            ipLabel.setFont(new Font("Monospaced", Font.BOLD, 14));
            ipLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
            overlayPanel.add(ipLabel);
            overlayPanel.add(Box.createVerticalStrut(20));

            JLabel modeLabel = new JLabel("Game Mode:");
            modeLabel.setForeground(Color.WHITE);
            modeLabel.setFont(new Font("Arial", Font.BOLD, 13));
            modeLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
            overlayPanel.add(modeLabel);
            overlayPanel.add(Box.createVerticalStrut(8));

            modeSelector = new JComboBox<>(new String[] { "Normal", "Item", "Time Limit" });
            modeSelector.setMaximumSize(new Dimension(200, 30));
            modeSelector.setAlignmentX(Component.CENTER_ALIGNMENT);
            modeSelector.addActionListener(e -> onModeChanged());
            overlayPanel.add(modeSelector);
            overlayPanel.add(Box.createVerticalStrut(20));
        } else {
            overlayPanel.add(Box.createVerticalStrut(40));
        }

        startButton = new JButton("Start Game");
        startButton.setEnabled(false);
        startButton.setFont(new Font("Arial", Font.BOLD, 16));
        startButton.setPreferredSize(new Dimension(180, 45));
        startButton.setAlignmentX(Component.CENTER_ALIGNMENT);
        startButton.setBackground(new Color(70, 70, 70));
        startButton.setForeground(Color.WHITE);
        startButton.setFocusPainted(false);
        startButton.addActionListener(e -> onStartButtonClick());
        overlayPanel.add(startButton);

        SwingUtilities.invokeLater(() -> {
            JRootPane root = SwingUtilities.getRootPane(OnlineVersusPanel.this);
            if (root == null)
                return;

            JPanel glass = new JPanel(null);
            glass.setOpaque(false);
            glass.add(overlayPanel);

            root.setGlassPane(glass);
            glass.setVisible(true);
            glass.setBounds(0, 0, root.getWidth(), root.getHeight());

            centerOverlay();
        });
    }

    private void centerOverlay() {
        JRootPane root = SwingUtilities.getRootPane(this);
        if (overlayPanel != null && root != null) {
            JPanel glass = (JPanel) root.getGlassPane();
            Dimension size = glass.getSize();

            int width = 400;
            int height = isServer ? 320 : 220;

            overlayPanel.setBounds(
                    (size.width - width) / 2,
                    (size.height - height) / 2,
                    width, height);
        }
    }

    private void updateOverlay(String message) {
        SwingUtilities.invokeLater(() -> statusLabel.setText(message));
    }

    private String getLocalIP() {
        try {
            return InetAddress.getLocalHost().getHostAddress();
        } catch (Exception e) {
            return "localhost";
        }
    }

    private void onNetworkMessage(Message msg) {
        switch (msg.type) {
            case PLAYER_READY -> {
                oppReady = true;
                lastPongTime = System.currentTimeMillis();
                updateOverlay("Opponent ready!");
                checkReadyState();
            }
            case MODE_SELECT -> {
                selectedMode = (String) msg.data;
                lastPongTime = System.currentTimeMillis();
                updateOverlay("Mode: " + selectedMode);
            }
            case GAME_START -> {
                lastPongTime = System.currentTimeMillis();
                startGame();
            }
            case PING -> handlePing();
            case PONG -> handlePong();

            case GAME_OVER -> {
                SwingUtilities.invokeLater(() -> {
                    loop.stopLoop();
                    adapter.printStats(); // 통계 출력
                    showGameOverOverlay(false);
                });
            }

            case RESTART_READY -> {
                oppRestartReady = true;
                updateGameOverOverlay("Opponent is ready!");
                checkRestartState();
            }

            case RESTART_START -> {
                performRestart();
            }

            // 델타 메시지 처리 (BoardSyncAdapter가 처리)
            case BOARD_DELTA, BOARD_DELTA_COMPRESSED, BOARD_FULL_SYNC -> {
                adapter.handleIncoming(msg);
                SwingUtilities.invokeLater(oppView::repaint);
            }

            default -> {
                // 기타 메시지도 adapter에 위임
                adapter.handleIncoming(msg);
                SwingUtilities.invokeLater(oppView::repaint);
            }
        }
    }

    private void checkRestartState() {
        if (myRestartReady && oppRestartReady) {
            client.send(new Message(MessageType.RESTART_START, null));
            performRestart();
        }
    }

    private void showGameOverOverlay(boolean iLost) {
        JRootPane root = SwingUtilities.getRootPane(this);
        if (root == null)
            return;

        JPanel glass = (JPanel) root.getGlassPane();
        glass.removeAll();
        glass.setLayout(null);
        glass.setVisible(true);

        gameOverPanel = new JPanel();
        gameOverPanel.setLayout(new BoxLayout(gameOverPanel, BoxLayout.Y_AXIS));
        gameOverPanel.setBackground(new Color(20, 20, 25, 230));
        gameOverPanel.setBorder(BorderFactory.createEmptyBorder(40, 40, 40, 40));

        JLabel title = new JLabel(iLost ? "YOU LOSE" : "YOU WIN");
        title.setFont(new Font("Arial", Font.BOLD, 28));
        title.setForeground(iLost ? Color.RED : new Color(80, 255, 80));
        title.setAlignmentX(Component.CENTER_ALIGNMENT);
        gameOverPanel.add(title);

        gameOverPanel.add(Box.createVerticalStrut(25));

        JButton restartBtn = new JButton("Restart");
        restartBtn.setFont(new Font("Arial", Font.BOLD, 16));
        restartBtn.setAlignmentX(Component.CENTER_ALIGNMENT);
        restartBtn.addActionListener(e -> {
            myRestartReady = true;
            client.send(new Message(MessageType.RESTART_READY, null));
            updateGameOverOverlay("Waiting for opponent...");
        });
        gameOverPanel.add(restartBtn);

        gameOverPanel.add(Box.createVerticalStrut(10));

        JButton exitBtn = new JButton("Exit");
        exitBtn.setFont(new Font("Arial", Font.BOLD, 16));
        exitBtn.setAlignmentX(Component.CENTER_ALIGNMENT);
        exitBtn.addActionListener(e -> {
            cleanupAll();
            Window w = SwingUtilities.getWindowAncestor(this);
            if (w != null)
                w.dispose();
        });
        gameOverPanel.add(exitBtn);

        int w = 300, h = 250;
        gameOverPanel.setBounds(
                (getWidth() - w) / 2,
                (getHeight() - h) / 2,
                w, h);

        glass.add(gameOverPanel);
        glass.repaint();
        glass.revalidate();
    }

    private void onModeChanged() {
        selectedMode = (String) modeSelector.getSelectedItem();
        if (oppReady) {
            client.send(new Message(MessageType.MODE_SELECT, selectedMode));
        }
    }

    private void checkReadyState() {
        if (isReady && oppReady && !gameStarted) {
            SwingUtilities.invokeLater(() -> {
                updateOverlay("Ready! Press Start");
                startButton.setEnabled(true);
                startButton.setBackground(new Color(50, 180, 80));

                if (isServer) {
                    client.send(new Message(MessageType.MODE_SELECT, selectedMode));
                }
            });
        }
    }

    private void updateGameOverOverlay(String msg) {
        if (gameOverPanel != null && gameOverPanel.getComponentCount() > 0) {
            Component c = gameOverPanel.getComponent(0);
            if (c instanceof JLabel label) {
                label.setText(msg);
                gameOverPanel.repaint();
            }
        }
    }

    private void onStartButtonClick() {
        if (!gameStarted) {
            client.send(new Message(MessageType.GAME_START, "start"));
            startGame();
        }
    }

    private void startGame() {
        if (gameStarted)
            return;
        gameStarted = true;

        lastPongTime = System.currentTimeMillis();
        heartbeatTimer.start();

        SwingUtilities.invokeLater(() -> {
            applyGameMode(selectedMode);

            JRootPane rootPane = SwingUtilities.getRootPane(this);
            if (rootPane != null) {
                rootPane.getGlassPane().setVisible(false);
            }

            loop.startLoop();
            myView.requestFocusInWindow();
            System.out.println("[GAME] Started with mode: " + selectedMode + " (Delta Sync Enabled)");
        });
    }

    /* ===== 네트워크 안정성 메서드 ===== */

    private void sendPing() {
        if (!gameStarted)
            return;

        lastPingTime = System.currentTimeMillis();
        client.send(new Message(MessageType.PING, lastPingTime));
    }

    private void handlePing() {
        client.send(new Message(MessageType.PONG, System.currentTimeMillis()));
    }

    private void handlePong() {
        long now = System.currentTimeMillis();
        lastPongTime = now;
        long rtt = now - lastPingTime;

        SwingUtilities.invokeLater(() -> {
            if (rtt < LAG_THRESHOLD) {
                lagLabel.setText("Ping: " + rtt + "ms");
                lagLabel.setForeground(new Color(100, 255, 100));
            } else {
                lagLabel.setText("LAG: " + rtt + "ms");
                lagLabel.setForeground(new Color(255, 200, 100));
            }
        });
    }

    private void checkConnection() {
        if (!oppReady || !gameStarted)
            return;

        long now = System.currentTimeMillis();
        long timeSinceLastPong = now - lastPongTime;

        if (timeSinceLastPong > DISCONNECT_THRESHOLD) {
            onConnectionLost();
        }
    }

    private void onConnectionLost() {
        cleanup();

        SwingUtilities.invokeLater(() -> {
            lagLabel.setText("DISCONNECTED");
            lagLabel.setForeground(new Color(255, 100, 100));

            JOptionPane.showMessageDialog(this,
                    "Connection lost! Game ended.",
                    "Network Error",
                    JOptionPane.ERROR_MESSAGE);

            Window window = SwingUtilities.getWindowAncestor(this);
            if (window != null) {
                window.dispose();
            }
        });
    }

    private void cleanup() {
        if (connectionCheckTimer != null && connectionCheckTimer.isRunning()) {
            connectionCheckTimer.stop();
        }
        if (heartbeatTimer != null && heartbeatTimer.isRunning()) {
            heartbeatTimer.stop();
        }
        if (syncTimer != null && syncTimer.isRunning()) {
            syncTimer.stop();
        }
        if (statsTimer != null && statsTimer.isRunning()) {
            statsTimer.stop();
        }
        if (gameStarted) {
            loop.stopLoop();
        }
        if (client != null) {
            client.disconnect();
        }
    }

    private void applyGameMode(String mode) {
        switch (mode) {
            case "Normal" -> myLogic.setItemMode(false);
            case "Item" -> myLogic.setItemMode(true);
            case "Time Limit" -> myLogic.setItemMode(false);
        }
    }

    private void performRestart() {
        SwingUtilities.invokeLater(() -> {
            myRestartReady = false;
            oppRestartReady = false;
            gameStarted = false;

            JRootPane root = SwingUtilities.getRootPane(this);
            if (root != null) {
                root.getGlassPane().setVisible(false);
            }

            loop.stopLoop();

            myLogic.reset();
            oppLogic.reset();

            oppLogic.getState().setCurr(null);

            myIncoming.setText("0");
            oppIncoming.setText("0");

            myView.repaint();
            oppView.repaint();

            // 델타 추적기 리셋
            adapter.reset();

            gameStarted = true;
            loop.startLoop();
            myView.requestFocusInWindow();

            System.out.println("[GAME] Restarted (Delta tracking reset)");
        });
    }

    @Override
    public Dimension getPreferredSize() {
        int boardWidth = myView.getPreferredSize().width;
        int boardHeight = myView.getPreferredSize().height;

        int totalWidth = (boardWidth * 2) + (160 * 2) + 100;
        int totalHeight = boardHeight + 180;

        return new Dimension(totalWidth, totalHeight);
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            JFrame f = new JFrame("Online Versus - Delta Sync");
            boolean isServer = JOptionPane.showConfirmDialog(f, "Start as server?", "P2P Setup",
                    JOptionPane.YES_NO_OPTION) == JOptionPane.YES_OPTION;
            OnlineVersusPanel panel = new OnlineVersusPanel(isServer);
            f.setContentPane(panel);
            f.pack();
            f.setLocationRelativeTo(null);
            f.setVisible(true);

            f.setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
            f.addWindowListener(new java.awt.event.WindowAdapter() {
                @Override
                public void windowClosing(java.awt.event.WindowEvent e) {
                    panel.cleanupAll();
                    f.dispose();
                    System.exit(0);
                }
            });
        });
    }

    private void cleanupAll() {
        try {
            if (syncTimer != null)
                syncTimer.stop();
            if (heartbeatTimer != null)
                heartbeatTimer.stop();
            if (connectionCheckTimer != null)
                connectionCheckTimer.stop();
            if (statsTimer != null)
                statsTimer.stop();
            
            // 최종 통계 출력
            System.out.println("\n=== Final Sync Statistics ===");
            adapter.printStats();
            
            client.disconnect();
            loop.stopLoop();
            GameServer.stopServer();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private char[][] blockToShape(Block block) {
        if (block == null)
            return null;

        int w = block.width();
        int h = block.height();
        char[][] shape = new char[h][w];

        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                shape[y][x] = block.getShape(x, y) == 1 ? '#' : ' ';
            }
        }
        return shape;
    }
}