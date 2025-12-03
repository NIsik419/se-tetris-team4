package component.network.websocket;

import logic.BoardLogic;
import logic.GameState;
import java.awt.Color;
import java.util.ArrayList;
import java.util.List;

import blocks.Block;

/**
 * BoardSyncAdapter (델타 전송 방식)
 * --------------------
 * 주요 변경:
 * - 전체 보드 전송 → 델타(변경사항만) 전송으로 75% 네트워크 절약
 * - GameState 기반으로 동작
 * - 기존 기능 유지: LINE_ATTACK, SCORE_UPDATE, GAME_OVER
 * 
 * 기존 기능:
 * - 내 보드 상태 / 공격 / 게임오버 동기화
 * - 상대 보드 수신 시 oppLogic에 반영
 */
public class BoardSyncAdapter {

    private final BoardLogic myLogic;
    private final BoardLogic oppLogic;
    private final GameClient client;
    private final BoardDeltaTracker tracker;

    // 델타 전송 설정
    private boolean enableDeltaSync = true;
    private boolean enableCompression = true;
    private long lastFullSyncTime = 0;
    private static final long FULL_SYNC_INTERVAL = 30000; // 10초 → 30초로 변경
    private int deltasWithoutFullSync = 0;
    private static final int MAX_DELTAS_BEFORE_FULL_SYNC = 100;

    // 통계
    private int deltasSent = 0;
    private int fullSyncsSent = 0;
    private int skippedSyncs = 0;
    private long totalBytesSaved = 0;
    private long totalDeltaBytes = 0;
    private long totalFullBytes = 0;

    private List<Block> lastSentNextBlocks = null;
    private int lastSentScore = -1;
    private int lastSentLevel = -1;
    private int lastSentLines = -1;

    public BoardSyncAdapter(BoardLogic myLogic, BoardLogic oppLogic, GameClient client) {
        this.myLogic = myLogic;
        this.oppLogic = oppLogic;
        this.client = client;

        // 델타 추적기 초기화 (GameState의 고정 크기 사용)
        this.tracker = new BoardDeltaTracker(GameState.WIDTH, GameState.HEIGHT);

        // 라인 클리어 시 공격 마스크 전송 + 즉시 보드 동기화
        myLogic.setOnLinesClearedWithMasks(masks -> {
            client.send(new Message(MessageType.LINE_ATTACK, masks));
            sendBoardStateImmediate();

        });

        // 🔹 게임오버 시 알림 전송
        myLogic.setOnGameOverCallback(this::sendGameOver);
    }

    /**
     * 🟦 주기적으로 내 보드 상태를 상대에게 전송
     * 델타 모드: 변경사항만 전송
     * 레거시 모드: 전체 보드 전송
     */
    public void sendBoardState() {
        if (enableDeltaSync) {
            sendBoardStateDelta();
        } else {
            sendBoardStateLegacy();
        }
        // Next 블록 전송 (변경된 경우만)
        sendNextBlocks();

        // 플레이어 통계 전송 (변경된 경우만)
        sendPlayerStats();
    }

    /**
     * 즉시 보드 상태 전송 (라인 클리어 등 중요 이벤트용)
     */
    public void sendBoardStateImmediate() {
        if (enableDeltaSync) {
            GameState currentState = myLogic.getState();
            sendFullSync(currentState);

            // 1. 델타 추적기 강제 업데이트
            tracker.forceUpdate(currentState);

            // 2. 델타 전송
            sendDelta(currentState);

            // 3. Full Sync 타이머 리셋
            lastFullSyncTime = System.currentTimeMillis();
            deltasWithoutFullSync = 0; // 횟수도 리셋

            System.out.println("[SYNC] Immediate sync completed");
        } else {
            sendBoardStateLegacy();
        }
    }

    /**
     * 델타 방식으로 보드 상태 전송 (변경사항만)
     */
    private void sendBoardStateDelta() {
        GameState myState = myLogic.getState();
        long now = System.currentTimeMillis();

        deltasWithoutFullSync++;

        // 시간 OR 델타 횟수 기준으로 Full Sync
        boolean shouldFullSync = (now - lastFullSyncTime) > FULL_SYNC_INTERVAL ||
                deltasWithoutFullSync >= MAX_DELTAS_BEFORE_FULL_SYNC;

        if (shouldFullSync) {
            sendFullSync(myState);
            lastFullSyncTime = now;
            deltasWithoutFullSync = 0;
            fullSyncsSent++;
        } else {
            sendDelta(myState);
        }
    }

    /**
     * Next 블록 전송 (변경된 경우만)
     */
    private void sendNextBlocks() {
        List<Block> nextBlocks = myLogic.getNextBlocks();

        // 변경되지 않았으면 전송하지 않음
        if (nextBlocks.equals(lastSentNextBlocks)) {
            return;
        }

        lastSentNextBlocks = List.copyOf(nextBlocks);

        try {
            // Block을 직렬화 가능한 형태로 변환
            List<BlockData> blockDataList = nextBlocks.stream()
                    .map(this::blockToData)
                    .toList();

            client.send(new Message(MessageType.NEXT_BLOCKS,
                    WebSocketUtil.toJson(blockDataList)));
        } catch (Exception e) {
            System.err.println("[ERROR] Failed to send NEXT_BLOCKS: " + e.getMessage());
        }
    }

    /**
     * 플레이어 통계 전송 (점수, 레벨, 라인수)
     */
    private void sendPlayerStats() {
        int score = myLogic.getScore();
        int level = myLogic.getLevel();
        int lines = myLogic.getLinesCleared();

        // 변경되지 않았으면 전송하지 않음
        if (score == lastSentScore && level == lastSentLevel && lines == lastSentLines) {
            return;
        }

        lastSentScore = score;
        lastSentLevel = level;
        lastSentLines = lines;

        PlayerStats stats = new PlayerStats(score, level, lines);
        client.send(new Message(MessageType.PLAYER_STATS,
                WebSocketUtil.toJson(stats)));
    }

    /**
     * 델타 전송
     */
    private void sendDelta(GameState state) {
        BoardDeltaTracker.BoardDelta delta = tracker.computeDelta(state);

        // 변경사항이 없으면 전송하지 않음
        if (delta == null) {
            skippedSyncs++;
            return;
        }

        deltasSent++;
        int changeCount = delta.changes.size();

        if (enableCompression && changeCount > 10) {
            // 변경사항이 많으면 압축해서 전송
            BoardDeltaTracker.CompressedDelta compressed = tracker.compressDelta(delta);
            client.send(new Message(MessageType.BOARD_DELTA_COMPRESSED, compressed));

            int uncompressedSize = changeCount * 12;
            int compressedSize = compressed.runs.size() * 16;
            int saved = uncompressedSize - compressedSize;

            totalBytesSaved += Math.max(0, saved);
            totalDeltaBytes += compressedSize;
        } else {
            // 변경사항이 적으면 그냥 전송
            client.send(new Message(MessageType.BOARD_DELTA, delta));
            totalDeltaBytes += changeCount * 12 + 20;
        }
    }

    /**
     * 전체 동기화 전송
     */
    private void sendFullSync(GameState state) {
        BoardDeltaTracker.BoardDelta fullDelta = tracker.createFullSync(state);
        client.send(new Message(MessageType.BOARD_FULL_SYNC, fullDelta));

        int boardSize = GameState.HEIGHT * GameState.WIDTH;
        totalFullBytes += boardSize * 2;

    }

    /**
     * 레거시 방식으로 보드 상태 전송 (전체 보드)
     */
    private void sendBoardStateLegacy() {
        client.send(new Message(MessageType.BOARD_STATE, myLogic.getBoard()));
    }

    /** 🟥 내 게임이 끝났음을 상대에게 통보 */
    public void sendGameOver() {
        System.out.println("[SEND] GAME_OVER");
        client.send(new Message(MessageType.GAME_OVER, "over"));
    }

    /**
     * Block을 직렬화 가능한 데이터로 변환
     */
    private BlockData blockToData(Block block) {
        return new BlockData(
                block.getColor().getRGB(),
                block.getShapeArray());
    }

    /** 🟨 수신 메시지 처리 */
    public void handleIncoming(Message msg) {
        switch (msg.type) {
            case BOARD_STATE -> {
                // 레거시: 상대방 보드 데이터를 oppLogic에 반영
                Color[][] board = WebSocketUtil.fromJson(msg.data, Color[][].class);
                oppLogic.setBoard(board);
            }

            case BOARD_DELTA -> {
                // 델타: 변경사항만 적용
                BoardDeltaTracker.BoardDelta delta = WebSocketUtil.fromJson(msg.data,
                        BoardDeltaTracker.BoardDelta.class);
                applyDeltaToOppLogic(delta);
            }

            case BOARD_DELTA_COMPRESSED -> {
                // 압축된 델타 적용
                BoardDeltaTracker.CompressedDelta compressed = WebSocketUtil.fromJson(msg.data,
                        BoardDeltaTracker.CompressedDelta.class);
                applyCompressedDeltaToOppLogic(compressed);
            }

            case BOARD_FULL_SYNC -> {
                // 전체 동기화 적용
                BoardDeltaTracker.BoardDelta fullDelta = WebSocketUtil.fromJson(msg.data,
                        BoardDeltaTracker.BoardDelta.class);
                applyDeltaToOppLogic(fullDelta);
            }

            // case LINE_ATTACK -> {
            //     // 상대의 공격을 내 보드에 반영
            //     int[] masks = WebSocketUtil.fromJson(msg.data, int[].class);
            //     myLogic.addGarbageMasks(masks);
            // }

            

            case GAME_OVER -> {
                System.out.println("[RECV] GAME_OVER");
                myLogic.onOpponentGameOver();
            }

            case SCORE_UPDATE -> {
                // 점수 업데이트 수신
                int score = WebSocketUtil.fromJson(msg.data, Integer.class);
                oppLogic.getState().setScore(score);
            }

            // 이 케이스들은 OnlineVersusPanel에서 처리해야 함
            // 여기서는 무시
            case NEXT_BLOCKS, PLAYER_STATS -> {
                // OnlineVersusPanel에서 처리
            }

            default -> {
                // PING, PONG 등 무시
            }
        }
    }

    /**
     * 델타를 oppLogic의 GameState에 적용
     */
    private void applyDeltaToOppLogic(BoardDeltaTracker.BoardDelta delta) {
        if (delta == null)
            return;

        GameState oppState = oppLogic.getState();
        Color[][] oppBoard = oppState.getBoard();

        // 셀 변경사항 적용
        for (BoardDeltaTracker.CellDelta change : delta.changes) {
            if (change.x >= 0 && change.x < GameState.WIDTH &&
                    change.y >= 0 && change.y < GameState.HEIGHT) {
                oppBoard[change.y][change.x] = rgbToColor(change.rgb);
            }
        }

        // 메타데이터 적용
        if (delta.score != null) {
            oppState.setScore(delta.score);
        }
        if (delta.level != null) {
            oppState.setLevel(delta.level);
        }
        if (delta.incomingLines != null) {
            oppState.setIncomingLines(delta.incomingLines);
        }
    }

    
    /**
     * 압축된 델타를 oppLogic의 GameState에 적용
     */
    private void applyCompressedDeltaToOppLogic(BoardDeltaTracker.CompressedDelta compressed) {
        GameState oppState = oppLogic.getState();
        Color[][] oppBoard = oppState.getBoard();

        // RLE 압축 해제하며 적용
        for (BoardDeltaTracker.CompressedDelta.CellRun run : compressed.runs) {
            for (int i = 0; i < run.count; i++) {
                int x = run.startX + i;
                int y = run.startY;

                if (x >= 0 && x < GameState.WIDTH &&
                        y >= 0 && y < GameState.HEIGHT) {
                    oppBoard[y][x] = rgbToColor(run.rgb);
                }
            }
        }

        // 메타데이터 적용
        if (compressed.score != null) {
            oppState.setScore(compressed.score);
        }
        if (compressed.level != null) {
            oppState.setLevel(compressed.level);
        }
        if (compressed.incomingLines != null) {
            oppState.setIncomingLines(compressed.incomingLines);
        }
    }

    /**
     * RGB 정수값을 Color 객체로 변환
     */
    private Color rgbToColor(Integer rgb) {
        return rgb == null ? null : new Color(rgb, true);
    }

    /** 점수 전송 */
    public void sendScore(int score) {
        client.send(new Message(MessageType.SCORE_UPDATE, score));
    }

    /**
     * 델타 모드 활성화/비활성화
     */
    public void setDeltaSyncEnabled(boolean enabled) {
        this.enableDeltaSync = enabled;
    }

    /**
     * 압축 활성화/비활성화
     */
    public void setCompressionEnabled(boolean enabled) {
        this.enableCompression = enabled;
    }

    /**
     * 리셋 (게임 재시작시)
     */
    public void reset() {
        tracker.reset();
        deltasSent = 0;
        fullSyncsSent = 0;
        skippedSyncs = 0;
        totalBytesSaved = 0;
        totalDeltaBytes = 0;
        totalFullBytes = 0;
        lastFullSyncTime = 0;
        deltasWithoutFullSync = 0;
    }

    /**
     * UI 표시용 간단한 통계 문자열
     */
    public String getStatsString() {
        if (!enableDeltaSync)
            return "Sync: Legacy";

        long totalSyncs = deltasSent + fullSyncsSent + skippedSyncs;
        if (totalSyncs == 0)
            return "Sync: Waiting...";

        int skipPercentage = (int) ((skippedSyncs * 100.0) / totalSyncs);
        long avgDeltaSize = deltasSent > 0 ? totalDeltaBytes / deltasSent : 0;

        return String.format("Δ:%d Full:%d Skip:%d%% (~%dB)",
                deltasSent, fullSyncsSent, skipPercentage, avgDeltaSize);
    }

    /**
     * 동기화 통계 상세 출력
     */
    public void printStats() {
        if (!enableDeltaSync) {
            System.out.println("[SYNC] Running in legacy mode (full board sync)");
            return;
        }

        System.out.println("\n╔════════════════════════════════════════╗");
        System.out.println("║     Delta Sync Statistics              ║");
        System.out.println("╠════════════════════════════════════════╣");

        long totalSyncs = deltasSent + fullSyncsSent + skippedSyncs;

        System.out.println("║ Total sync attempts: " + totalSyncs);
        System.out.println("║ - Deltas sent: " + deltasSent);
        System.out.println("║ - Full syncs sent: " + fullSyncsSent);
        System.out.println("║ - Skipped (no changes): " + skippedSyncs);

        if (totalSyncs > 0) {
            int deltaPercent = (int) ((deltasSent * 100.0) / totalSyncs);
            int skipPercent = (int) ((skippedSyncs * 100.0) / totalSyncs);
            System.out.println("║ - Delta percentage: " + deltaPercent + "%");
            System.out.println("║ - Skip percentage: " + skipPercent + "%");
        }

        System.out.println("║");
        System.out.println("║ Network usage:");
        System.out.println("║ - Delta traffic: " + formatBytes(totalDeltaBytes));
        System.out.println("║ - Full sync traffic: " + formatBytes(totalFullBytes));
        System.out.println("║ - Total traffic: " + formatBytes(totalDeltaBytes + totalFullBytes));

        // 기존 방식과 비교
        long estimatedOldMethod = totalSyncs * 500;
        long actualUsage = totalDeltaBytes + totalFullBytes;
        long saved = estimatedOldMethod - actualUsage;

        if (saved > 0) {
            int savePercent = (int) ((saved * 100.0) / estimatedOldMethod);
            System.out.println("║");
            System.out.println("║ Savings vs full-board sync:");
            System.out.println("║ - Estimated old method: " + formatBytes(estimatedOldMethod));
            System.out.println("║ - Bytes saved: " + formatBytes(saved) + " (" + savePercent + "%)");
        }

        System.out.println("║");
        System.out.println("║ Compression: " + (enableCompression ? "ENABLED" : "DISABLED"));
        if (enableCompression && totalBytesSaved > 0) {
            System.out.println("║ - Compression savings: " + formatBytes(totalBytesSaved));
        }

        System.out.println("╚════════════════════════════════════════╝\n");
    }

    private String formatBytes(long bytes) {
        if (bytes < 1024) {
            return bytes + " B";
        } else if (bytes < 1024 * 1024) {
            return String.format("%.2f KB", bytes / 1024.0);
        } else {
            return String.format("%.2f MB", bytes / (1024.0 * 1024.0));
        }
    }

    /**
     * 직렬화 가능한 Block 데이터
     */
    public static class BlockData {
        public int rgb;
        public int[][] shape;

        public BlockData() {
        }

        public BlockData(int rgb, int[][] shape) {
            this.rgb = rgb;
            this.shape = shape;
        }
    }

    /**
     * 플레이어 통계
     */
    public static class PlayerStats {
        public int score;
        public int level;
        public int lines;

        public PlayerStats() {
        }

        public PlayerStats(int score, int level, int lines) {
            this.score = score;
            this.level = level;
            this.lines = lines;
        }
    }

}



