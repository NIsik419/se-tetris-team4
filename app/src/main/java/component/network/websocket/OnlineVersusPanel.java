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
 * ✅ OnlineVersusPanel (중앙 오버레이)
 * --------------------------------------
 * - 보드 2개 나란히 표시
 * - 연결/준비 UI는 중앙에 오버레이
 * - 게임 시작하면 오버레이 숨김
 */
public class OnlineVersusPanel extends JPanel {

    private final JLabel myIncoming = new JLabel("0");
    private final JLabel oppIncoming = new JLabel("0");

    private final BoardLogic myLogic;
    private final BoardLogic oppLogic;
    private final BoardView myView;
    private final BoardView oppView;

    private final GameClient client;
    private BoardSyncAdapter adapter;
    private final GameLoop loop;
    private final Timer syncTimer;

    private boolean isReady = false;
    private boolean oppReady = false;
    private boolean gameStarted = false;
    private boolean isServer;

    // 오버레이 UI
    private JPanel overlayPanel;
    private JLabel statusLabel;
    private JLabel ipLabel;
    private JComboBox<String> modeSelector;
    private JButton startButton;
    private String selectedMode = "Normal";

    public OnlineVersusPanel(boolean isServer) {
        this.isServer = isServer;
        setLayout(new BorderLayout(0, 0)); // ✅ BorderLayout으로 변경
        setBackground(new Color(18, 22, 30));

        /* 🎛 상단 HUD */
        JPanel topHud = new JPanel(new GridLayout(1, 2, 20, 0));
        topHud.setBackground(new Color(18, 22, 30));
        topHud.setBorder(BorderFactory.createEmptyBorder(10, 20, 10, 20));
        topHud.add(buildHud("My Incoming", myIncoming));
        topHud.add(buildHud("Opponent Incoming", oppIncoming));
        add(topHud, BorderLayout.NORTH);

        /* 🎮 보드 패널 */
        myLogic = new BoardLogic(score -> adapter.sendGameOver());
        oppLogic = new BoardLogic(score -> {
        });
        oppLogic.getState().setCurr(null);

        myLogic.setOnIncomingChanged(
                count -> SwingUtilities.invokeLater(() -> myIncoming.setText(String.valueOf(count))));

        myView = new BoardView(myLogic);
        oppView = new BoardView(oppLogic);

        JPanel boardsPanel = new JPanel(new GridLayout(1, 2, 20, 0));
        boardsPanel.setBackground(new Color(18, 22, 30));
        boardsPanel.setBorder(BorderFactory.createEmptyBorder(10, 20, 10, 20));
        boardsPanel.add(myView);
        boardsPanel.add(oppView);
        add(boardsPanel, BorderLayout.CENTER); // ✅ CENTER로 배치

        /* 🌐 네트워크 초기화 */
        client = new GameClient(this::onNetworkMessage);
        adapter = new BoardSyncAdapter(myLogic, oppLogic, client);

        client.setOnConnected(() -> {
            System.out.println("[DEBUG] onConnected callback!");
            isReady = true;
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

        /* ⏳ 게임 루프 */
        loop = new GameLoop(myLogic, myView::repaint);
        myLogic.setLoopControl(loop::pause, loop::resume);

        /* 🎹 키 입력 */
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

        /* 🔁 동기화 타이머 */
        syncTimer = new Timer(300, e -> {
            if (gameStarted) {
                adapter.sendBoardState();
            }
        });
        syncTimer.start();

        /* 🎭 오버레이 생성 */
        createOverlay();

        // ✅ 레이아웃 완료 후 오버레이 위치 조정
        addComponentListener(new java.awt.event.ComponentAdapter() {
            @Override
            public void componentResized(java.awt.event.ComponentEvent e) {
                centerOverlay();
            }
        });
    }

    /** HUD 박스 */
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

    /** 🔧 오버레이 중앙 정렬 */
    private void centerOverlay() {
        if (overlayPanel != null && getWidth() > 0 && getHeight() > 0) {
            int x = (getWidth() - overlayPanel.getWidth()) / 2;
            int y = (getHeight() - overlayPanel.getHeight()) / 2;
            overlayPanel.setLocation(x, y);
        }
    }

    /** 중앙 오버레이 생성 (보드 위에 떠 있는 구조) */
    private void createOverlay() {
        // ✅ 1. LayeredPane 생성
        JLayeredPane layeredPane = new JLayeredPane();
        layeredPane.setLayout(null);
        add(layeredPane, BorderLayout.CENTER);

        // ✅ 2. 보드 패널 추가 (기존 보드뷰 2개)
        JPanel boardsPanel = new JPanel(new GridLayout(1, 2, 20, 0));
        boardsPanel.setBackground(new Color(18, 22, 30));
        boardsPanel.setBorder(BorderFactory.createEmptyBorder(10, 20, 10, 20));
        boardsPanel.add(myView);
        boardsPanel.add(oppView);
        boardsPanel.setBounds(0, 0, getPreferredSize().width, getPreferredSize().height);
        layeredPane.add(boardsPanel, Integer.valueOf(0)); // 👈 아래 레이어(0)

        // ✅ 3. 오버레이 패널 생성 (위 레이어)
        overlayPanel = new JPanel();
        overlayPanel.setLayout(new BoxLayout(overlayPanel, BoxLayout.Y_AXIS));
        overlayPanel.setBackground(new Color(30, 35, 45, 230)); // 반투명 어두운 배경
        overlayPanel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(100, 150, 200), 2),
                BorderFactory.createEmptyBorder(30, 40, 30, 40)));
        overlayPanel.setSize(400, 300);

        // === 상태 라벨 ===
        statusLabel = new JLabel("Connecting...");
        statusLabel.setForeground(new Color(255, 200, 100));
        statusLabel.setFont(new Font("Arial", Font.BOLD, 16));
        statusLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        overlayPanel.add(statusLabel);
        overlayPanel.add(Box.createVerticalStrut(15));

        // === IP 라벨 + 모드 선택 (서버 전용) ===
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

        // === 시작 버튼 ===
        startButton = new JButton("Start Game");
        startButton.setEnabled(false);
        startButton.setFont(new Font("Arial", Font.BOLD, 16));
        startButton.setPreferredSize(new Dimension(180, 45));
        startButton.setMaximumSize(new Dimension(180, 45));
        startButton.setAlignmentX(Component.CENTER_ALIGNMENT);
        startButton.setBackground(new Color(70, 70, 70));
        startButton.setForeground(Color.WHITE);
        startButton.setFocusPainted(false);
        startButton.addActionListener(e -> onStartButtonClick());
        overlayPanel.add(startButton);

        // ✅ 4. 오버레이 중앙 배치
        int x = (getPreferredSize().width - overlayPanel.getWidth()) / 2;
        int y = (getPreferredSize().height - overlayPanel.getHeight()) / 2;
        overlayPanel.setLocation(x, y);
        layeredPane.add(overlayPanel, Integer.valueOf(1)); // 👈 위쪽 레이어(1)

        overlayPanel.setVisible(true);
    }

    /** 오버레이 상태 업데이트 */
    private void updateOverlay(String message) {
        SwingUtilities.invokeLater(() -> statusLabel.setText(message));
    }

    /** 로컬 IP */
    private String getLocalIP() {
        try {
            return InetAddress.getLocalHost().getHostAddress();
        } catch (Exception e) {
            return "localhost";
        }
    }

    /** 메시지 수신 */
    private void onNetworkMessage(Message msg) {
        switch (msg.type) {
            case PLAYER_READY -> {
                oppReady = true;
                updateOverlay("Opponent ready!");
                checkReadyState();
            }
            case MODE_SELECT -> {
                selectedMode = (String) msg.data;
                updateOverlay("Mode: " + selectedMode);
            }
            case GAME_START -> startGame();
            default -> {
                adapter.handleIncoming(msg);
                SwingUtilities.invokeLater(oppView::repaint);
            }
        }
    }

    /** 모드 변경 */
    private void onModeChanged() {
        selectedMode = (String) modeSelector.getSelectedItem();
        if (oppReady) {
            client.send(new Message(MessageType.MODE_SELECT, selectedMode));
        }
    }

    /** 준비 확인 */
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

    /** 시작 버튼 */
    private void onStartButtonClick() {
        if (!gameStarted) {
            client.send(new Message(MessageType.GAME_START, "start"));
            startGame();
        }
    }

    /** 게임 시작 */
    private void startGame() {
        if (gameStarted)
            return;
        gameStarted = true;

        SwingUtilities.invokeLater(() -> {
            applyGameMode(selectedMode);

            // 오버레이 숨김
            overlayPanel.setVisible(false);

            loop.startLoop();
            myView.requestFocusInWindow();
            System.out.println("[GAME] Started with mode: " + selectedMode);
        });
    }

    /** 모드 적용 */
    private void applyGameMode(String mode) {
        switch (mode) {
            case "Normal" -> myLogic.setItemMode(false);
            case "Item" -> myLogic.setItemMode(true);
            case "Time Limit" -> myLogic.setItemMode(false);
        }
    }

    @Override
    public Dimension getPreferredSize() {
        return new Dimension(1100, 750);
    }

    /** 테스트 */
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            JFrame f = new JFrame("Online Versus - Center Overlay");
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