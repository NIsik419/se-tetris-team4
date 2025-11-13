package component.network.websocket;

import component.GameConfig;
import component.GameLoop;
import component.board.KeyBindingInstaller;
import component.BoardView;
import component.ColorBlindPalette;
import logic.BoardLogic;

import javax.swing.*;
import java.awt.*;
import java.net.InetAddress;

/**
 * OnlineVersusPanel (수정 버전)
 * 수정 사항:
 * 1. 중복 메서드 제거 (restartGame vs startRestartGame)
 * 2. Incoming 라벨 업데이트 로직 수정
 * 3. 타이머 리소스 정리 추가
 * 4. 연결 상태 초기화 개선
 * 5. Game Over 오버레이 참조 수정
 */
public class OnlineVersusPanel extends JPanel {

    private final JLabel myIncoming = new JLabel("0");
    private final JLabel oppIncoming = new JLabel("0");
    private final JLabel lagLabel = new JLabel("Connection: OK");

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

        /* 상단 HUD */
        JPanel topHud = new JPanel(new GridLayout(1, 3, 20, 0));
        topHud.setBackground(new Color(18, 22, 30));
        topHud.setBorder(BorderFactory.createEmptyBorder(10, 20, 10, 20));
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

        topHud.add(buildHud("Opponent Incoming", oppIncoming));
        add(topHud, BorderLayout.NORTH);

        /* 보드 패널 */
        myLogic = new BoardLogic(score -> adapter.sendGameOver());
        oppLogic = new BoardLogic(score -> {});
        oppLogic.getState().setCurr(null);

        // 수정: 내 보드와 상대 보드 모두 Incoming 업데이트
        myLogic.setOnIncomingChanged(
                count -> SwingUtilities.invokeLater(() -> myIncoming.setText(String.valueOf(count))));
        
        oppLogic.setOnIncomingChanged(
                count -> SwingUtilities.invokeLater(() -> oppIncoming.setText(String.valueOf(count))));

        myView = new BoardView(myLogic);
        oppView = new BoardView(oppLogic);

        JPanel boardsPanel = new JPanel(new GridLayout(1, 2, 20, 0));
        boardsPanel.setBackground(new Color(18, 22, 30));
        boardsPanel.setBorder(BorderFactory.createEmptyBorder(10, 20, 10, 20));
        boardsPanel.add(myView);
        boardsPanel.add(oppView);
        add(boardsPanel, BorderLayout.CENTER);

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
                String ip = JOptionPane.showInputDialog(this, "Enter server IP:", "localhost");
                if (ip == null || ip.trim().isEmpty()) {
                    ip = "localhost";
                }
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
                () -> {}, () -> {}, () -> false,
                () -> {}, () -> {},
                loop::startLoop, loop::stopLoop, t -> {},
                () -> ColorBlindPalette.Mode.NORMAL,
                m -> {}, m -> {});
        new KeyBindingInstaller().install(myView, deps, KeyBindingInstaller.KeySet.ARROWS, false);

        myView.setFocusable(true);
        SwingUtilities.invokeLater(myView::requestFocusInWindow);

        /* 동기화 타이머 */
        syncTimer = new Timer(300, e -> {
            if (gameStarted) {
                adapter.sendBoardState();
            }
        });
        syncTimer.start();

        /* Heartbeat 타이머 */
        heartbeatTimer = new Timer((int) PING_INTERVAL, e -> sendPing());

        /* 연결 체크 타이머 */
        connectionCheckTimer = new Timer(1000, e -> checkConnection());
        connectionCheckTimer.start();

        myLogic.setOnGameOverCallback(() -> {
            SwingUtilities.invokeLater(() -> {
                loop.stopLoop();
                client.send(new Message(MessageType.GAME_OVER, null));
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
            if (root == null) return;

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

            default -> {
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

    // 수정: Game Over 오버레이 - gameOverPanel 참조 저장
    private void showGameOverOverlay(boolean iLost) {
        JRootPane root = SwingUtilities.getRootPane(this);
        if (root == null) return;

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
            cleanup(); // 리소스 정리 추가
            Window w = SwingUtilities.getWindowAncestor(this);
            if (w != null) w.dispose();
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

    // 수정: updateGameOverOverlay - null 체크 추가
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
        if (gameStarted) return;
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
            System.out.println("[GAME] Started with mode: " + selectedMode);
        });
    }

    /* ===== 네트워크 안정성 메서드 ===== */

    private void sendPing() {
        if (!gameStarted) return;

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
        if (!oppReady || !gameStarted) return;

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

    // 추가: 리소스 정리 메서드
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

    // 수정: 중복 메서드 통합 (restartGame 제거, performRestart로 통일)
    private void performRestart() {
        SwingUtilities.invokeLater(() -> {
            myRestartReady = false;
            oppRestartReady = false;
            gameStarted = false; // 상태 초기화 추가

            JRootPane root = SwingUtilities.getRootPane(this);
            if (root != null) {
                root.getGlassPane().setVisible(false);
            }

            // 게임 루프 정지 후 리셋
            loop.stopLoop();
            
            myLogic.reset();
            oppLogic.reset();
            
            // 상대방 보드는 curr를 null로 유지 (초기화 때와 동일)
            oppLogic.getState().setCurr(null);
            
            // Incoming 카운트 초기화
            myIncoming.setText("0");
            oppIncoming.setText("0");
            
            // 화면 갱신
            myView.repaint();
            oppView.repaint();
            
            gameStarted = true; // 게임 재시작
            loop.startLoop();
            myView.requestFocusInWindow();
            
            System.out.println("[GAME] Restarted");
        });
    }

    @Override
    public Dimension getPreferredSize() {
        return new Dimension(800, 750);
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            JFrame f = new JFrame("Online Versus - Network Stability");
            f.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            boolean isServer = JOptionPane.showConfirmDialog(f, "Start as server?", "P2P Setup",
                    JOptionPane.YES_NO_OPTION) == JOptionPane.YES_OPTION;
            OnlineVersusPanel panel = new OnlineVersusPanel(isServer);
            f.setContentPane(panel);
            f.pack();
            f.setLocationRelativeTo(null);
            f.setVisible(true);
        });
    }
}