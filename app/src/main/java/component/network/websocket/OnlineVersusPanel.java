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
import com.google.gson.Gson;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.awt.*;
import java.awt.event.WindowListener;
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

    // 시간제한 모드 용
    private TimeLimitManager timeLimitManager;
    private JLabel timerLabel; // 타이머 표시용
    private static final long TIME_LIMIT_SECONDS = 180; // 3분

    // 오버레이 UI
    private JPanel gameOverPanel;
    private JPanel overlayPanel;
    private JLabel statusLabel;
    private JLabel ipLabel;
    private JComboBox<String> modeSelector;
    private JButton startButton;
    private String selectedMode = "Normal";
    private String preSelectedGameRule = null;

    // 동기화 지연 측정
    private long maxSyncDelay = 0;
    private long avgSyncDelay = 0;
    private int syncCount = 0;

    // 게임 통계
    private long gameStartTime = 0;
    private int myTotalLines = 0;
    private int oppTotalLines = 0;

    public OnlineVersusPanel(boolean isServer, String gameRule) {
        this.isServer = isServer;
        this.preSelectedGameRule = gameRule;
        // 미리 선택된 모드가 있으면 사용
        if (gameRule != null && !gameRule.isEmpty()) {
            this.selectedMode = gameRule;
            System.out.println("[P2P] Pre-selected mode: " + gameRule);
        } else {
            this.selectedMode = "Normal"; // 기본값
        }
        setLayout(new BorderLayout(0, 0));
        setBackground(new Color(18, 22, 30));

        /* 상단 HUD - 타이머 추가 */
        JPanel topHud = new JPanel(new GridLayout(1, 5, 15, 0)); // 4→5칸으로 변경
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

        // 타이머 표시 추가
        JPanel timerPanel = new JPanel();
        timerPanel.setBackground(new Color(24, 28, 38));
        timerPanel.setBorder(BorderFactory.createEmptyBorder(8, 12, 8, 12));
        timerLabel = new JLabel("");
        timerLabel.setForeground(Color.WHITE);
        timerLabel.setFont(new Font("Arial", Font.BOLD, 16));
        timerLabel.setHorizontalAlignment(SwingConstants.CENTER);
        timerPanel.add(timerLabel);
        topHud.add(timerPanel);

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

        myView = new BoardView(myLogic,null);
        oppView = new BoardView(oppLogic,null);

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

        // 초기 더미 데이터 설정 (표시 확인용)
        oppSidebar.setScore(0);
        oppSidebar.setLevel(1);
        System.out.println("[DEBUG] Opponent sidebar initialized");

        add(centerContainer, BorderLayout.CENTER);

        /* 네트워크 초기화 */
        client = new GameClient(this::onNetworkMessage);
        adapter = new BoardSyncAdapter(myLogic, oppLogic, client);
        // 가비지 적용 후 즉시 동기화
        myLogic.setBeforeSpawnHook(() -> {
            adapter.sendBoardStateImmediate();
            System.out.println("[SYNC] Immediate sync after garbage applied");
        });

        myLogic.setOnGarbageApplied(() -> {
            adapter.sendBoardStateImmediate();
        });

        timeLimitManager = new TimeLimitManager(timerLabel, client, isServer);

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

                // oppSidebar.setScore(oppLogic.getScore());
                // oppSidebar.setLevel(oppLogic.getLevel());
            }
        });
        hudTimer.start();

        /* 델타 동기화 타이머 - 변경사항만 전송 */
        syncTimer = new Timer(50, e -> {
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
        if (!gameStarted)
            return;

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

            // 모드 셀렉터 스타일 개선
            modeSelector = new JComboBox<>(new String[] {
                    "Normal",
                    "Item",
                    "Time Limit (3min)"
            });
            modeSelector.setLightWeightPopupEnabled(false);
            modeSelector.setMaximumSize(new Dimension(220, 40));
            modeSelector.setAlignmentX(Component.CENTER_ALIGNMENT);

            // 메인 콤보박스 스타일
            modeSelector.setBackground(new Color(70, 80, 95));
            modeSelector.setForeground(Color.WHITE);
            modeSelector.setFont(new Font("Arial", Font.BOLD, 15));
            modeSelector.setOpaque(true);
            modeSelector.setFocusable(false);
            modeSelector.setBorder(BorderFactory.createLineBorder(new Color(120, 180, 255), 3));

            // 팝업 메뉴 불투명하게 만들기 - 이벤트 리스너로 처리
            modeSelector.addPopupMenuListener(new javax.swing.event.PopupMenuListener() {
                @Override
                public void popupMenuWillBecomeVisible(javax.swing.event.PopupMenuEvent e) {
                    // 팝업이 열릴 때 불투명하게 설정
                    SwingUtilities.invokeLater(() -> {
                        Object popup = modeSelector.getUI().getAccessibleChild(modeSelector, 0);
                        if (popup instanceof javax.swing.plaf.basic.ComboPopup) {
                            JComponent popupComponent = (JComponent) popup;
                            popupComponent.setOpaque(true);
                            popupComponent.setBackground(Color.WHITE);

                            // 최상위로 올리기 (핵심!)
                            if (popupComponent instanceof JPopupMenu) {
                                JPopupMenu menu = (JPopupMenu) popupComponent;
                                menu.setLightWeightPopupEnabled(false); // 무거운 팝업으로 변경
                            }

                            JList<?> list = ((javax.swing.plaf.basic.ComboPopup) popup).getList();
                            list.setOpaque(true);
                            list.setBackground(Color.WHITE);
                        }
                    });
                }

                @Override
                public void popupMenuWillBecomeInvisible(javax.swing.event.PopupMenuEvent e) {
                }

                @Override
                public void popupMenuCanceled(javax.swing.event.PopupMenuEvent e) {
                }
            });

            // 렌더러 설정
            modeSelector.setRenderer(new DefaultListCellRenderer() {
                @Override
                public Component getListCellRendererComponent(JList<?> list, Object value,
                        int index, boolean isSelected, boolean cellHasFocus) {
                    JLabel label = (JLabel) super.getListCellRendererComponent(
                            list, value, index, isSelected, cellHasFocus);

                    label.setFont(new Font("Arial", Font.BOLD, 15));
                    label.setOpaque(true);

                    if (isSelected) {
                        label.setBackground(new Color(80, 140, 220));
                        label.setForeground(Color.WHITE);
                    } else if (index == -1) {
                        label.setBackground(new Color(70, 80, 95));
                        label.setForeground(Color.WHITE);
                    } else {
                        label.setBackground(Color.WHITE);
                        label.setForeground(Color.BLACK);
                    }

                    label.setBorder(BorderFactory.createCompoundBorder(
                            BorderFactory.createMatteBorder(0, 0, 1, 0, new Color(200, 200, 200)),
                            BorderFactory.createEmptyBorder(8, 12, 8, 12)));

                    return label;
                }
            });

            modeSelector.addActionListener(e -> onModeChanged());

            // 미리 선택된 모드로 초기화 (필드 사용!)
            if (preSelectedGameRule != null) {
                modeSelector.setSelectedItem(preSelectedGameRule);
                System.out.println("[OVERLAY] Set mode selector to: " + preSelectedGameRule);
            }
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
            case PLAYER_READY:
                oppReady = true;
                lastPongTime = System.currentTimeMillis();
                updateOverlay(
                        "Opponent ready!");
                checkReadyState();
                break;

            case MODE_SELECT:
                selectedMode = (String) msg.data;
                lastPongTime = System.currentTimeMillis();
                updateOverlay(
                        "Mode: " + selectedMode);
                break;

            case GAME_START:
                lastPongTime = System.currentTimeMillis();
                startGame();
                break;

            case TIME_LIMIT_START:
                // 클라이언트가 서버의 시작 시간 수신
                if (!isServer) {
                    long serverStartTime = WebSocketUtil.fromJson(msg.data, Long.class);
                    timeLimitManager.syncStart(serverStartTime, TIME_LIMIT_SECONDS);
                    System.out.println("[TIME] Synced with server start time");
                }
                break;

            case PING:
                handlePing();
                break;

            case PONG:
                handlePong();
                break;

            case GAME_OVER:
                SwingUtilities.invokeLater(() -> {
                    loop.stopLoop();
                    adapter.printStats();
                    showGameOverOverlay(false);
                });
                break;

            case RESTART_READY:
                oppRestartReady = true;
                updateGameOverOverlay(
                        "Opponent is ready!");
                checkRestartState();
                break;

            case RESTART_START:
                performRestart();
                break;

            // 델타 메시지 처리
            case BOARD_DELTA:
            case BOARD_DELTA_COMPRESSED:
            case BOARD_FULL_SYNC:
                // 동기화 지연 측정
                long receiveTime = System.currentTimeMillis();
                adapter.handleIncoming(msg);

                // 타임스탬프가 있으면 지연 계산
                if (msg.type == MessageType.BOARD_DELTA ||
                        msg.type == MessageType.BOARD_DELTA_COMPRESSED ||
                        msg.type == MessageType.BOARD_FULL_SYNC) {

                    // BoardDelta에서 타임스탬프 추출 (JSON 파싱)
                    try {
                        String jsonData = msg.data.toString();
                        if (jsonData.contains("\"timestamp\"")) {
                            // 간단한 타임스탬프 추출
                            int tsIndex = jsonData.indexOf("\"timestamp\":");
                            if (tsIndex > 0) {
                                String tsStr = jsonData.substring(tsIndex + 12);
                                tsStr = tsStr.substring(0,
                                        tsStr.indexOf(",") > 0 ? tsStr.indexOf(",") : tsStr.indexOf("}"));
                                long sendTime = Long.parseLong(tsStr);
                                long delay = receiveTime - sendTime;

                                // 통계 업데이트
                                syncCount++;
                                avgSyncDelay = (avgSyncDelay * (syncCount - 1) + delay) / syncCount;
                                maxSyncDelay = Math.max(maxSyncDelay, delay);

                                if (delay > 200) {
                                    System.err.println("[WARNING] Sync delay exceeded 200ms: " + delay + "ms");
                                }
                            }
                        }
                    } catch (Exception e) {
                        // 파싱 실패 시 무시
                    }
                }

                SwingUtilities.invokeLater(oppView::repaint);
                break;

            case NEXT_BLOCKS:
                System.out.println("[DEBUG] Received NEXT_BLOCKS message");
                try {
                    // msg.data를 String으로 변환 후 직접 파싱
                    String rawData = msg.data.toString();
                    System.out.println("[DEBUG] NEXT_BLOCKS raw: " + rawData);

                    // Gson이 자동으로 escape 처리
                    com.google.gson.Gson gson = new com.google.gson.Gson();
                    String jsonData = gson.fromJson(rawData, String.class);

                    BoardSyncAdapter.BlockData[] blockDataArray = gson.fromJson(jsonData,
                            BoardSyncAdapter.BlockData[].class);

                    List<BoardSyncAdapter.BlockData> blockDataList = Arrays.asList(blockDataArray);

                    System.out.println("[DEBUG] Parsed " + blockDataList.size() + " blocks");

                    SwingUtilities.invokeLater(() -> {
                        oppSidebar.setNextBlocks(convertToBlocks(blockDataList));
                    });
                } catch (Exception e) {
                    System.err.println("[ERROR] NEXT_BLOCKS: " + e.getMessage());
                    e.printStackTrace();
                }
                break;

            case PLAYER_STATS:
                System.out.println("[DEBUG] Received PLAYER_STATS message");
                try {
                    String rawData = msg.data.toString();
                    System.out.println("[DEBUG] PLAYER_STATS raw: " + rawData);

                    com.google.gson.Gson gson = new com.google.gson.Gson();
                    String jsonData = gson.fromJson(rawData, String.class);

                    BoardSyncAdapter.PlayerStats stats = gson.fromJson(jsonData, BoardSyncAdapter.PlayerStats.class);

                    System.out.println("[DEBUG] Stats: score=" + stats.score + " level=" + stats.level);

                    SwingUtilities.invokeLater(() -> {
                        oppSidebar.setScore(stats.score);
                        oppSidebar.setLevel(stats.level);
                    });
                } catch (Exception e) {
                    System.err.println("[ERROR] PLAYER_STATS: " + e.getMessage());
                    e.printStackTrace();
                }
                break;
            case LINE_ATTACK: {
                // 상대의 공격을 내 보드에 반영
                int[] masks = WebSocketUtil.fromJson(msg.data, int[].class);
                myLogic.addGarbageMasks(masks);

                // 가비지 추가 후 즉시 보드 상태 전송!
               
                adapter.sendBoardStateImmediate(); // Full Sync 강제
                myView.repaint();
                
                break; 
            }

            default:
                // 기타 메시지도 adapter에 위임
                adapter.handleIncoming(msg);
                SwingUtilities.invokeLater(oppView::repaint);
                break;
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

        // 게임 시간 계산
        long gameDuration = System.currentTimeMillis() - gameStartTime;
        int minutes = (int) (gameDuration / 60000);
        int seconds = (int) ((gameDuration % 60000) / 1000);

        int myScore = myLogic.getScore();
        int oppScore = oppLogic.getScore();

        JPanel glass = (JPanel) root.getGlassPane();
        glass.removeAll();
        glass.setLayout(null);
        glass.setVisible(true);

        gameOverPanel = new JPanel();
        gameOverPanel.setLayout(new BoxLayout(gameOverPanel, BoxLayout.Y_AXIS));
        gameOverPanel.setBackground(new Color(20, 20, 25, 230));
        gameOverPanel.setBorder(BorderFactory.createEmptyBorder(40, 40, 40, 40));

        // 승패 타이틀
        JLabel title = new JLabel(iLost ? "YOU LOSE" : "YOU WIN");
        title.setFont(new Font("Arial", Font.BOLD, 32));
        title.setForeground(iLost ? new Color(255, 80, 80) : new Color(80, 255, 80));
        title.setAlignmentX(Component.CENTER_ALIGNMENT);
        gameOverPanel.add(title);
        gameOverPanel.add(Box.createVerticalStrut(30));

        // 통계 패널
        JPanel statsPanel = createStatsPanel(myScore, oppScore, myTotalLines, minutes, seconds);
        statsPanel.setAlignmentX(Component.CENTER_ALIGNMENT);
        gameOverPanel.add(statsPanel);

        gameOverPanel.add(Box.createVerticalStrut(30));

        // 버튼 패널
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 15, 0));
        buttonPanel.setOpaque(false);

        JButton restartBtn = new JButton("Restart");
        restartBtn.setFont(new Font("Arial", Font.BOLD, 16));
        restartBtn.setPreferredSize(new Dimension(120, 45));
        restartBtn.setBackground(new Color(70, 150, 70));
        restartBtn.setForeground(Color.WHITE);
        restartBtn.setFocusPainted(false);
        restartBtn.addActionListener(e -> {
            myRestartReady = true;
            client.send(new Message(MessageType.RESTART_READY, null));
            updateGameOverOverlay("Waiting for opponent...");
        });
        buttonPanel.add(restartBtn);

        JButton exitBtn = new JButton("Exit");
        exitBtn.setFont(new Font("Arial", Font.BOLD, 16));
        exitBtn.setPreferredSize(new Dimension(120, 45));
        exitBtn.setBackground(new Color(150, 70, 70));
        exitBtn.setForeground(Color.WHITE);
        exitBtn.setFocusPainted(false);
        exitBtn.addActionListener(e -> {
            cleanupAll();
            Window w = SwingUtilities.getWindowAncestor(this);
            if (w != null)
                w.dispose();
        });
        buttonPanel.add(exitBtn);

        gameOverPanel.add(buttonPanel);

        int w = 450, h = 420;
        gameOverPanel.setBounds(
                (getWidth() - w) / 2,
                (getHeight() - h) / 2,
                w, h);

        glass.add(gameOverPanel);
        glass.repaint();
        glass.revalidate();
    }

    /**
     * 게임 통계 패널 생성
     */
    private JPanel createStatsPanel(int myScore, int oppScore, int myLines, int minutes, int seconds) {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setOpaque(false);

        // 점수 비교
        addStatRow(panel, "Your Score", String.valueOf(myScore), new Color(100, 200, 255));
        addStatRow(panel, "Opponent Score", String.valueOf(oppScore), new Color(255, 150, 100));

        panel.add(Box.createVerticalStrut(10));

        // 라인 수
        addStatRow(panel, "Lines Cleared", String.valueOf(myLines), Color.WHITE);

        // 플레이 시간
        String timeStr = String.format("%d:%02d", minutes, seconds);
        addStatRow(panel, "Time Played", timeStr, Color.WHITE);

        return panel;
    }

    /**
     * 통계 행 추가 헬퍼 메서드
     */
    private void addStatRow(JPanel panel, String label, String value, Color valueColor) {
        JPanel row = new JPanel(new FlowLayout(FlowLayout.CENTER, 10, 5));
        row.setOpaque(false);

        JLabel labelText = new JLabel(label + ":");
        labelText.setFont(new Font("Arial", Font.PLAIN, 16));
        labelText.setForeground(new Color(180, 180, 180));
        row.add(labelText);

        JLabel valueText = new JLabel(value);
        valueText.setFont(new Font("Arial", Font.BOLD, 20));
        valueText.setForeground(valueColor);
        row.add(valueText);

        panel.add(row);
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

        // 게임 통계 초기화
        gameStartTime = System.currentTimeMillis();
        myTotalLines = 0;
        oppTotalLines = 0;

        lastPongTime = System.currentTimeMillis();
        heartbeatTimer.start();

        SwingUtilities.invokeLater(() -> {
            applyGameMode(selectedMode);

            JRootPane rootPane = SwingUtilities.getRootPane(this);
            if (rootPane != null) {
                rootPane.getGlassPane().setVisible(false);
            }

            // 시간제한 모드
            if (selectedMode.startsWith("Time Limit")) { // ✅ 이제 작동!
                long startTime = System.currentTimeMillis();

                // 서버가 클라이언트에게 시작 시간 전송
                if (isServer) {
                    client.send(new Message(MessageType.TIME_LIMIT_START, startTime));
                }

                // 서버도 타이머 시작
                timeLimitManager.start(TIME_LIMIT_SECONDS, this::onTimeLimitTimeout);
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
            lagLabel.setText("RECONNECTING...");
            lagLabel.setForeground(Color.YELLOW);

            // 자동 재연결 (사용자 개입 없이)
            autoReconnect();
        });
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

                    // 기존 연결 정리
                    if (client != null) {
                        client.disconnect();
                    }

                    Thread.sleep(RETRY_DELAY);

                    // 재연결 시도 (같은 역할 유지)
                    GameClient newClient = new GameClient(this::onNetworkMessage);

                    newClient.setOnConnected(() -> {
                        System.out.println("[RECONNECT] Success!");
                        isReady = true;
                        oppReady = false; // 상대도 재연결 필요
                        lastPongTime = System.currentTimeMillis();
                        newClient.send(new Message(MessageType.PLAYER_READY, "ready"));

                        SwingUtilities.invokeLater(() -> {
                            lagLabel.setText("RECONNECTED");
                            lagLabel.setForeground(new Color(100, 255, 100));

                            // 게임 상태 유지하고 계속 진행
                            if (gameStarted) {
                                loop.resume();
                            }
                        });
                    });

                    // 같은 주소로 재연결
                    if (isServer) {
                        newClient.connect("ws://localhost:8081/game");
                    } else {
                        String lastIp = loadRecentServerIp();
                        if (lastIp == null)
                            lastIp = "localhost";
                        newClient.connect("ws://" + lastIp + ":8081/game");
                    }

                    // adapter 업데이트
                    adapter = new BoardSyncAdapter(myLogic, oppLogic, newClient);

                    // 성공 시 루프 탈출
                    return;

                } catch (Exception e) {
                    System.err.println("[RECONNECT] Attempt " + attempt + " failed: " + e.getMessage());

                    if (attempt == MAX_RETRIES) {
                        // 최종 실패 시에만 게임 종료
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
                                // 게임 계속 (오프라인)
                                lagLabel.setText("OFFLINE MODE");
                            }
                        });
                    }
                }
            }
        }).start();
    }

    /**
     * 메인 메뉴로 돌아갑니다.
     * GameFrame을 닫으면 GameLauncher의 WindowListener가 자동으로 메뉴로 복귀시킵니다.
     */
    private void returnToMainMenu() {
        cleanupAll();

        SwingUtilities.invokeLater(() -> {
            Window window = SwingUtilities.getWindowAncestor(this);

            System.out.println("[DEBUG] returnToMainMenu called");
            System.out.println("[DEBUG] Window: " + window);
            System.out.println("[DEBUG] Window class: " + (window != null ? window.getClass().getName() : "null"));

            if (window != null) {
                WindowListener[] listeners = window.getWindowListeners();
                System.out.println("[DEBUG] WindowListeners count: " + listeners.length);
                for (int i = 0; i < listeners.length; i++) {
                    System.out.println("[DEBUG] Listener " + i + ": " + listeners[i].getClass().getName());
                }

                window.dispose(); // GameFrame 닫기 → GameLauncher가 메뉴 표시
                System.out.println("[DEBUG] window.dispose() called");
            }
        });
    }

    private void cleanup() {
        try {
            // 콜백 제거 (순환 참조 해제)
            if (myLogic != null) {
                myLogic.setOnLinesClearedWithMasks(null);
                myLogic.setOnGameOverCallback(null);
                myLogic.setOnIncomingChanged(null);
            }

            if (oppLogic != null) {
                oppLogic.setOnIncomingChanged(null);
            }

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

            // 참조 명시적 제거
            if (adapter != null) {
                adapter = null;
            }

            if (client != null) {
                client.disconnect();
            }
        } catch (Exception e) {
            System.err.println("[CLEANUP] Error: " + e.getMessage());
        }
    }

    private void applyGameMode(String mode) {
        if (mode.startsWith("Time Limit")) {
            myLogic.setItemMode(false);
        } else if (mode.equals("Item")) {
            myLogic.setItemMode(true);
        } else { // Normal
            myLogic.setItemMode(false);
        }
    }

    private void performRestart() {
        SwingUtilities.invokeLater(() -> {
            myRestartReady = false;
            oppRestartReady = false;
            gameStarted = false;

            // 게임 오버 오버레이 제거
            JRootPane root = SwingUtilities.getRootPane(this);
            if (root != null) {
                root.getGlassPane().setVisible(false);
            }

            loop.stopLoop();

            // 보드 리셋
            myLogic.reset();
            oppLogic.reset();
            oppLogic.getState().setCurr(null);

            // HUD 리셋
            myIncoming.setText("0");
            oppIncoming.setText("0");

            myView.repaint();
            oppView.repaint();

            // 델타 추적기 리셋
            adapter.reset();

            // 타이머 리셋
            timeLimitManager.reset();

            // 통계 리셋
            gameStartTime = System.currentTimeMillis();
            myTotalLines = 0;
            oppTotalLines = 0;

            gameStarted = true;
            loop.startLoop();

            // 타임 리밋 모드면 타이머 재시작 (수정!)
            if (selectedMode.startsWith("Time Limit")) {
                long startTime = System.currentTimeMillis();

                if (isServer) {
                    client.send(new Message(MessageType.TIME_LIMIT_START, startTime));
                }

                timeLimitManager.start(TIME_LIMIT_SECONDS, this::onTimeLimitTimeout);
                System.out.println("[TIME] Timer restarted");
            }

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

    /**
     * 타임 리밋 종료 시 호출
     */
    private void onTimeLimitTimeout() {
        loop.stopLoop();

        int myScore = myLogic.getScore();
        int oppScore = oppLogic.getScore();

        // 점수 비교하여 승부 결정
        boolean iWon = myScore > oppScore;

        // 타임아웃 메시지 전송
        client.send(new Message(MessageType.GAME_OVER, "timeout"));

        SwingUtilities.invokeLater(() -> {
            showTimeLimitGameOverOverlay(iWon, myScore, oppScore);
        });
    }

    /**
     * 타임 리밋 게임 오버 오버레이 (점수 비교 표시)
     */
    private void showTimeLimitGameOverOverlay(boolean iWon, int myScore, int oppScore) {
        JRootPane root = SwingUtilities.getRootPane(this);
        if (root == null)
            return;

        // 게임 시간 = 타임리밋 (3분)
        int minutes = (int) (TIME_LIMIT_SECONDS / 60);
        int seconds = (int) (TIME_LIMIT_SECONDS % 60);

        JPanel glass = (JPanel) root.getGlassPane();
        glass.removeAll();
        glass.setLayout(null);
        glass.setVisible(true);

        gameOverPanel = new JPanel();
        gameOverPanel.setLayout(new BoxLayout(gameOverPanel, BoxLayout.Y_AXIS));
        gameOverPanel.setBackground(new Color(20, 20, 25, 240));
        gameOverPanel.setBorder(BorderFactory.createEmptyBorder(40, 50, 40, 50));

        // 승패 타이틀
        JLabel title = new JLabel(iWon ? "TIME'S UP - YOU WIN!" : "TIME'S UP - YOU LOSE");
        title.setFont(new Font("Arial", Font.BOLD, 28));
        title.setForeground(iWon ? new Color(80, 255, 80) : new Color(255, 80, 80));
        title.setAlignmentX(Component.CENTER_ALIGNMENT);
        gameOverPanel.add(title);
        gameOverPanel.add(Box.createVerticalStrut(30));

        // 통계 패널
        JPanel statsPanel = createStatsPanel(myScore, oppScore, myTotalLines, minutes, seconds);
        statsPanel.setAlignmentX(Component.CENTER_ALIGNMENT);
        gameOverPanel.add(statsPanel);

        gameOverPanel.add(Box.createVerticalStrut(30));

        // 버튼 패널 (가로 배치)
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 15, 0));
        buttonPanel.setOpaque(false);

        JButton restartBtn = new JButton("Restart");
        restartBtn.setFont(new Font("Arial", Font.BOLD, 16));
        restartBtn.setPreferredSize(new Dimension(120, 45));
        restartBtn.setBackground(new Color(70, 150, 70));
        restartBtn.setForeground(Color.WHITE);
        restartBtn.setFocusPainted(false);
        restartBtn.addActionListener(e -> {
            myRestartReady = true;
            client.send(new Message(MessageType.RESTART_READY, null));
            updateGameOverOverlay("Waiting for opponent...");
        });
        buttonPanel.add(restartBtn);

        JButton exitBtn = new JButton("Exit");
        exitBtn.setFont(new Font("Arial", Font.BOLD, 16));
        exitBtn.setPreferredSize(new Dimension(120, 45));
        exitBtn.setBackground(new Color(150, 70, 70));
        exitBtn.setForeground(Color.WHITE);
        exitBtn.setFocusPainted(false);
        exitBtn.addActionListener(e -> {
            cleanupAll();
            Window w = SwingUtilities.getWindowAncestor(this);
            if (w != null)
                w.dispose();
        });
        buttonPanel.add(exitBtn);

        gameOverPanel.add(buttonPanel);

        // 패널 크기 증가 (통계 표시 공간 확보)
        int w = 450, h = 420;
        gameOverPanel.setBounds(
                (getWidth() - w) / 2,
                (getHeight() - h) / 2,
                w, h);

        glass.add(gameOverPanel);
        glass.repaint();
        glass.revalidate();
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            JFrame f = new JFrame("Online Versus - Delta Sync");
            boolean isServer = JOptionPane.showConfirmDialog(f, "Start as server?", "P2P Setup",
                    JOptionPane.YES_NO_OPTION) == JOptionPane.YES_OPTION;
            OnlineVersusPanel panel = new OnlineVersusPanel(isServer, null);
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

            // 동기화 성능 통계 출력
            System.out.println("\n=== Sync Performance Statistics ===");
            System.out.println("Total syncs received: " + syncCount);
            System.out.println("Average delay: " + avgSyncDelay + " ms");
            System.out.println("Max delay: " + maxSyncDelay + " ms");

            if (maxSyncDelay > 200) {
                System.out.println("⚠️ WARNING: Max delay exceeded 200ms requirement!");
            } else {
                System.out.println("✓ All syncs within 200ms requirement");
            }

            System.out.println("\n=== Final Sync Statistics ===");
            adapter.printStats();

            client.disconnect();
            loop.stopLoop();
            GameServer.stopServer();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * BoardSyncAdapter.BlockData를 Block 객체로 변환
     */
    private List<Block> convertToBlocks(List<BoardSyncAdapter.BlockData> blockDataList) {
        if (blockDataList == null || blockDataList.isEmpty()) {
            return List.of(); // 빈 리스트 반환
        }

        List<Block> blocks = new ArrayList<>();

        for (BoardSyncAdapter.BlockData data : blockDataList) {
            try {
                if (data != null && data.shape != null && data.shape.length > 0) {
                    // BlockData → Block 변환
                    Color color = new Color(data.rgb, true);

                    // Block은 추상 클래스이므로 익명 클래스로 생성
                    Block block = new Block(color, data.shape) {
                    };

                    blocks.add(block);
                }
            } catch (Exception e) {
                System.err.println("[ERROR] Failed to convert BlockData to Block: " + e.getMessage());
            }
        }

        return blocks;
    }

}