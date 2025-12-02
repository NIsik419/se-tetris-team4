package component.network.websocket;

import component.sidebar.HUDSidebar;
import component.BoardView;
import logic.BoardLogic;
import blocks.Block;
import javax.swing.*;

import org.checkerframework.checker.guieffect.qual.UI;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.awt.Color;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;

/**
 * NetworkManager - 네트워크 통신 전담 클래스
 */
public class NetworkManager {

    private static final String IP_SAVE_FILE = "recent_server_ip.txt";
    private static final long PING_INTERVAL = 1000;
    private static final long LAG_THRESHOLD = 200;
    private static final long DISCONNECT_THRESHOLD = 5000;
    private boolean oppRestartReady = false;

    private final GameClient client;
    private BoardSyncAdapter adapter;
    private final boolean isServer;

    private long lastPingTime = 0;
    private long lastPongTime = 0;
    private Timer heartbeatTimer;
    private Timer connectionCheckTimer;

    private boolean isReady = false;
    private boolean oppReady = false;

    private final JLabel lagLabel;
    private final Runnable onConnectionLost;
    private final Runnable onGameOver;

    // 동기화 통계
    private long maxSyncDelay = 0;
    private long avgSyncDelay = 0;
    private int syncCount = 0;

    // UI 참조 (초기화용)
    private JPanel parentPanel;
    private UIOverlayManager overlayManager;

    public NetworkManager(boolean isServer,
            java.util.function.Consumer<Message> messageHandler,
            BoardLogic myLogic,
            BoardLogic oppLogic,
            JLabel lagLabel,
            Runnable onConnectionLost,
            Runnable onGameOver) {
        this.isServer = isServer;
        this.lagLabel = lagLabel;
        this.onConnectionLost = onConnectionLost;
        this.onGameOver = onGameOver;

        client = new GameClient(messageHandler);
        adapter = new BoardSyncAdapter(myLogic, oppLogic, client);

        myLogic.setBeforeSpawnHook(() -> {
            adapter.sendBoardStateImmediate();
            System.out.println("[SYNC] Immediate sync after garbage applied");
        });

        myLogic.setOnGarbageApplied(() -> {
            adapter.sendBoardStateImmediate();
        });

        setupTimers();
    }

    /**
     * UI 초기화 후 호출되어야 함
     */
    public void initialize(JPanel parentPanel, UIOverlayManager overlayManager) {
        this.parentPanel = parentPanel;
        this.overlayManager = overlayManager;

        client.setOnConnected(() -> {
            System.out.println("[DEBUG] onConnected callback!");
            isReady = true;
            lastPongTime = System.currentTimeMillis();
            client.send(new Message(MessageType.PLAYER_READY, "ready"));
            overlayManager.updateStatus("Connected! Waiting for opponent...");
            checkReadyState();
        });

        client.setOnDisconnected(() -> {
            System.out.println("[DEBUG] onDisconnected callback!");
            if (onConnectionLost != null) {
                onConnectionLost.run();
            }
        });

        connectToServer();
    }

    private void checkReadyState() {
        if (isReady && oppReady) {
            overlayManager.enableStartButton();
            if (isServer) {
                // 서버가 모드 전송
                overlayManager.updateStatus("Mode: " + getSelectedMode());
                client.send(new Message(MessageType.MODE_SELECT, getSelectedMode()));
            }
        }
    }

    private String getSelectedMode() {
        // UIOverlayManager에서 현재 선택된 모드 가져오기
        return overlayManager.getSelectedMode();
    }

    private void connectToServer() {
        try {
            if (isServer) {
                GameServer.startServer(8081);
                Thread.sleep(1000);
                client.connect("ws://localhost:8081/game");
            } else {
                // ⭐ 오버레이가 이미 떠 있는 상태에서 IP 입력
                SwingUtilities.invokeLater(() -> {
                    String recentIp = loadRecentServerIp();
                    String prompt = recentIp != null
                            ? "Enter server IP: (Recent: " + recentIp + ")"
                            : "Enter server IP:";

                    String ip = JOptionPane.showInputDialog(
                            SwingUtilities.getWindowAncestor(parentPanel),
                            prompt,
                            recentIp != null ? recentIp : "localhost");

                    if (ip == null || ip.trim().isEmpty()) {
                        ip = recentIp != null ? recentIp : "localhost";
                    }

                    final String finalIp = ip;
                    saveRecentServerIp(ip);

                    // 백그라운드 스레드에서 연결
                    new Thread(() -> {
                        try {
                            client.connect("ws://" + finalIp + ":8081/game");
                        } catch (Exception e) {
                            SwingUtilities.invokeLater(() -> {
                                JOptionPane.showMessageDialog(parentPanel,
                                        "Connection failed: " + e.getMessage());
                            });
                        }
                    }).start();
                });
                return;
            }
        } catch (Exception e) {
            JOptionPane.showMessageDialog(null, "Connection failed: " + e.getMessage());
            throw new RuntimeException(e);
        }
    }

    private void setupTimers() {
        heartbeatTimer = new Timer((int) PING_INTERVAL, e -> sendPing());

        connectionCheckTimer = new Timer(1000, e -> checkConnection());
        connectionCheckTimer.start();
    }

    public void startHeartbeat() {
        if (heartbeatTimer != null && !heartbeatTimer.isRunning()) {
            heartbeatTimer.start();
        }
    }

    private void sendPing() {
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
        if (!oppReady)
            return;

        long now = System.currentTimeMillis();
        long timeSinceLastPong = now - lastPongTime;

        if (timeSinceLastPong > DISCONNECT_THRESHOLD) {
            if (onConnectionLost != null) {
                onConnectionLost.run();
            }
        }
    }

    public void handleMessage(Message msg, BoardView oppView, HUDSidebar oppSidebar,
            BoardLogic myLogic, BoardView myView) {
        switch (msg.type) {
            case PLAYER_READY:
                oppReady = true;
                lastPongTime = System.currentTimeMillis();
                if (overlayManager != null) {
                    overlayManager.updateStatus("Opponent ready!");
                    checkReadyState();
                }
                break;

            case MODE_SELECT:
                lastPongTime = System.currentTimeMillis();
                String mode = (String) msg.data;
                if (overlayManager != null) {
                    overlayManager.updateStatus("Mode: " + mode);
                }
                break;

            case GAME_START:
                lastPongTime = System.currentTimeMillis();
                if (overlayManager != null) {
                    overlayManager.triggerGameStart();
                }
                break;
            case TIME_LIMIT_START:
                lastPongTime = System.currentTimeMillis();
                break;

            case PING:
                handlePing();
                break;

            case PONG:
                handlePong();
                break;

            case GAME_OVER:
                if (onGameOver != null) {
                    onGameOver.run();
                }
                break;

            case BOARD_DELTA:
            case BOARD_DELTA_COMPRESSED:
            case BOARD_FULL_SYNC:
                handleSyncMessage(msg, oppView);
                SwingUtilities.invokeLater(oppView::repaint);
                break;

            case NEXT_BLOCKS:
                handleNextBlocks(msg, oppSidebar);
                break;

            case PLAYER_STATS:
                handlePlayerStats(msg, oppSidebar);
                break;

            case LINE_ATTACK:
                int[] masks = WebSocketUtil.fromJson(msg.data, int[].class);
                myLogic.addGarbageMasks(masks);
                adapter.sendBoardStateImmediate();
                myView.repaint();
                break;

            case RESTART_READY:
                oppRestartReady = true;
                if (overlayManager != null) {
                    overlayManager.updateRestartStatus("Opponent ready! Starting...");
                    if (isServer) {
                        Timer restartTimer = new Timer(1000, e -> {
                            client.send(new Message(MessageType.RESTART_START, null));

                            // ⭐ 서버도 자기 자신에게 RESTART_START 처리!
                            SwingUtilities.invokeLater(() -> {
                                overlayManager.triggerRestart();
                            });

                            ((Timer) e.getSource()).stop();
                        });
                        restartTimer.setRepeats(false);
                        restartTimer.start();
                    }
                }
                break;
            case RESTART_START:
                if (overlayManager != null) {
                    overlayManager.triggerRestart();
                }
                break;

            default:
                adapter.handleIncoming(msg);
                SwingUtilities.invokeLater(oppView::repaint);
                break;
        }
    }

    private void handleSyncMessage(Message msg, BoardView oppView) {
        long receiveTime = System.currentTimeMillis();
        adapter.handleIncoming(msg);

        try {
            String jsonData = msg.data.toString();
            if (jsonData.contains("\"timestamp\"")) {
                int tsIndex = jsonData.indexOf("\"timestamp\":");
                if (tsIndex > 0) {
                    String tsStr = jsonData.substring(tsIndex + 12);
                    tsStr = tsStr.substring(0,
                            tsStr.indexOf(",") > 0 ? tsStr.indexOf(",") : tsStr.indexOf("}"));
                    long sendTime = Long.parseLong(tsStr);
                    long delay = receiveTime - sendTime;

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

        SwingUtilities.invokeLater(oppView::repaint);
    }

    private void handleNextBlocks(Message msg, HUDSidebar oppSidebar) {
        try {
            String rawData = msg.data.toString();
            com.google.gson.Gson gson = new com.google.gson.Gson();
            String jsonData = gson.fromJson(rawData, String.class);

            BoardSyncAdapter.BlockData[] blockDataArray = gson.fromJson(jsonData,
                    BoardSyncAdapter.BlockData[].class);

            List<BoardSyncAdapter.BlockData> blockDataList = Arrays.asList(blockDataArray);

            SwingUtilities.invokeLater(() -> {
                oppSidebar.setNextBlocks(convertToBlocks(blockDataList));
            });
        } catch (Exception e) {
            System.err.println("[ERROR] NEXT_BLOCKS: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void handlePlayerStats(Message msg, HUDSidebar oppSidebar) {
        try {
            String rawData = msg.data.toString();
            com.google.gson.Gson gson = new com.google.gson.Gson();
            String jsonData = gson.fromJson(rawData, String.class);

            BoardSyncAdapter.PlayerStats stats = gson.fromJson(jsonData,
                    BoardSyncAdapter.PlayerStats.class);

            SwingUtilities.invokeLater(() -> {
                oppSidebar.setScore(stats.score);
                oppSidebar.setLevel(stats.level);
            });
        } catch (Exception e) {
            System.err.println("[ERROR] PLAYER_STATS: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private List<Block> convertToBlocks(List<BoardSyncAdapter.BlockData> blockDataList) {
        if (blockDataList == null || blockDataList.isEmpty()) {
            return List.of();
        }

        List<Block> blocks = new ArrayList<>();

        for (BoardSyncAdapter.BlockData data : blockDataList) {
            try {
                if (data != null && data.shape != null && data.shape.length > 0) {
                    Color color = new Color(data.rgb, true);
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

    public void resetAdapter() {
        if (adapter != null) {
            adapter.reset();
        }
    }

    public void reconnect() throws Exception {
        // 기존 연결 정리
        if (client != null) {
            client.disconnect();
        }

        Thread.sleep(1000);

        // 재연결
        if (isServer) {
            client.connect("ws://localhost:8081/game");
        } else {
            String lastIp = loadRecentServerIp();
            if (lastIp == null)
                lastIp = "localhost";
            client.connect("ws://" + lastIp + ":8081/game");
        }

        // 재연결 후 상태 업데이트
        isReady = true;
        lastPongTime = System.currentTimeMillis();
        client.send(new Message(MessageType.PLAYER_READY, "ready"));

        System.out.println("[RECONNECT] Success!");
    }

    public void sendBoardState() {
        adapter.sendBoardState();
    }

    public void sendGameOver() {
        client.send(new Message(MessageType.GAME_OVER, null));
    }

    public void sendModeSelect(String mode) {
        client.send(new Message(MessageType.MODE_SELECT, mode));
    }

    public void sendRestartReady() {
        client.send(new Message(MessageType.RESTART_READY, null));
    }

    public void sendLineAttack(int[] masks) {
        client.send(new Message(MessageType.LINE_ATTACK, masks));
    }

    public void sendTimeLimitStart(long startTime) {
        client.send(new Message(MessageType.TIME_LIMIT_START, startTime));
    }

    public String getStatsString() {
        return adapter.getStatsString();
    }

    public void printStats() {
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
    }

    public GameClient getClient() {
        return client;
    }

    public void resetForRestart() {
        oppRestartReady = false;
        maxSyncDelay = 0;
        avgSyncDelay = 0;
        syncCount = 0;
    }

    public void cleanup() {
        if (connectionCheckTimer != null && connectionCheckTimer.isRunning()) {
            connectionCheckTimer.stop();
        }
        if (heartbeatTimer != null && heartbeatTimer.isRunning()) {
            heartbeatTimer.stop();
        }
        if (client != null) {
            client.disconnect();
        }
        if (isServer) {
            GameServer.stopServer();
        }
    }
}