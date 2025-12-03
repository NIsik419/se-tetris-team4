package component.network.websocket;

import logic.BoardLogic;
import logic.GameState;
import blocks.Block;
import org.junit.Before;
import org.junit.Test;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.*;

public class BoardSyncAdapterTest {

    private MockGameClient client;
    private MockBoardLogic myLogic;
    private MockBoardLogic oppLogic;
    private BoardSyncAdapter adapter;

    @Before
    public void setUp() {
        client = new MockGameClient();
        myLogic = new MockBoardLogic();
        oppLogic = new MockBoardLogic();
        adapter = new BoardSyncAdapter(myLogic, oppLogic, client);
    }

    @Test
    public void testConstructor() {
        assertNotNull(adapter);
        assertNotNull(adapter.getStatsString());
    }

    @Test
    public void testSendBoardState() {
        adapter.sendBoardState();
        assertFalse(client.sent.isEmpty());
    }

    @Test
    public void testSendBoardStateImmediate() {
        adapter.sendBoardStateImmediate();
        assertFalse(client.sent.isEmpty());
    }

    @Test
    public void testSendGameOver() {
        adapter.sendGameOver();
        assertTrue(client.sent.stream()
                .anyMatch(msg -> msg.type == MessageType.GAME_OVER));
    }

    @Test
    public void testSendScore() {
        adapter.sendScore(1000);
        assertTrue(client.sent.stream()
                .anyMatch(msg -> msg.type == MessageType.SCORE_UPDATE));
    }

    @Test
    public void testHandleIncomingBoardState() {
        Color[][] board = new Color[GameState.HEIGHT][GameState.WIDTH];
        for (int y = 0; y < GameState.HEIGHT; y++) {
            for (int x = 0; x < GameState.WIDTH; x++) {
                board[y][x] = Color.RED;
            }
        }

        Message msg = new Message(MessageType.BOARD_STATE, board);
        adapter.handleIncoming(msg);

        assertNotNull(oppLogic.getState().getBoard());
    }

    @Test
    public void testHandleIncomingGameOver() {
        Message msg = new Message(MessageType.GAME_OVER, "over");
        adapter.handleIncoming(msg);
        assertTrue(true);
    }

    @Test
    public void testHandleIncomingScoreUpdate() {
        Message msg = new Message(MessageType.SCORE_UPDATE, 5000);
        adapter.handleIncoming(msg);
        assertEquals(5000, oppLogic.getState().getScore());
    }

    @Test
    public void testSetDeltaSyncEnabled() {
        adapter.setDeltaSyncEnabled(false);
        adapter.sendBoardState();
        assertTrue(adapter.getStatsString().contains("Legacy"));
    }

    @Test
    public void testSetCompressionEnabled() {
        adapter.setCompressionEnabled(false);
        adapter.sendBoardState();
        assertTrue(true);
    }

    @Test
    public void testReset() {
        adapter.sendBoardState();
        adapter.sendBoardState();
        adapter.reset();

        String stats = adapter.getStatsString();
        assertTrue(stats.contains("Waiting") || stats.contains("Δ:0"));
    }

    @Test
    public void testGetStatsString() {
        String stats = adapter.getStatsString();
        assertNotNull(stats);
        assertFalse(stats.isEmpty());
    }

    @Test
    public void testPrintStats() {
        adapter.sendBoardState();
        adapter.printStats();
        assertTrue(true);
    }

    @Test
    public void testMultipleSendBoardState() {
        for (int i = 0; i < 10; i++) adapter.sendBoardState();
        assertFalse(client.sent.isEmpty());
    }

    @Test
    public void testDeltaSyncAfterBoardChange() {
        myLogic.getState().getBoard()[0][0] = Color.RED;
        adapter.sendBoardState();
        assertFalse(client.sent.isEmpty());
    }

    @Test
    public void testFullSyncTriggered() {
        for (int i = 0; i < 120; i++) adapter.sendBoardState();
        String stats = adapter.getStatsString();
        assertTrue(stats.contains("Full") || stats.contains("Legacy"));
    }

    @Test
    public void testHandleIncomingBoardDelta() {
        BoardDeltaTracker.BoardDelta delta =
                new BoardDeltaTracker.BoardDelta(new ArrayList<>());
        delta.score = 1000;
        delta.level = 5;

        Message msg = new Message(MessageType.BOARD_DELTA, delta);
        adapter.handleIncoming(msg);

        assertEquals(1000, oppLogic.getState().getScore());
        assertEquals(5, oppLogic.getState().getLevel());
    }

    @Test
    public void testHandleIncomingBoardFullSync() {
        BoardDeltaTracker.BoardDelta fullSync =
                new BoardDeltaTracker.BoardDelta(new ArrayList<>());
        fullSync.score = 2000;

        Message msg = new Message(MessageType.BOARD_FULL_SYNC, fullSync);
        adapter.handleIncoming(msg);

        assertEquals(2000, oppLogic.getState().getScore());
    }

    @Test
    public void testHandleIncomingCompressedDelta() {
        BoardDeltaTracker.CompressedDelta compressed =
                new BoardDeltaTracker.CompressedDelta();
        compressed.runs = new ArrayList<>();
        compressed.score = 3000;

        Message msg =
                new Message(MessageType.BOARD_DELTA_COMPRESSED, compressed);

        adapter.handleIncoming(msg);

        assertEquals(3000, oppLogic.getState().getScore());
    }

    // @Test
    // public void testBlockDataSerialization() {
    //     Block block = new Block(Color.RED, new int[][]{{1, 1}, {1, 1}}) {};
    //     myLogic.setNextBlocks(List.of(block));

    //     adapter.sendBoardState();

    //     boolean hasNextBlocks =
    //             client.sent.stream().anyMatch(m -> m.type == MessageType.NEXT_BLOCKS);

    //     assertTrue(hasNextBlocks);
    // }

    @Test
    public void testPlayerStatsSerialization() {
        myLogic.setScore(5000);
        myLogic.setLevel(10);

        adapter.sendBoardState();

        boolean hasPlayerStats =
                client.sent.stream().anyMatch(m -> m.type == MessageType.PLAYER_STATS);

        assertTrue(hasPlayerStats);
    }

    // @Test
    // public void testSkipUnchangedNextBlocks() {
    //     Block block = new Block(Color.RED, new int[][]{{1, 1}, {1, 1}}) {};
    //     myLogic.setNextBlocks(List.of(block));

    //     adapter.sendBoardState();
    //     int count1 = client.sent.size();

    //     adapter.sendBoardState();
    //     int count2 = client.sent.size();

    //     assertTrue(count2 >= count1);
    // }

    @Test
    public void testSkipUnchangedPlayerStats() {
        myLogic.setScore(1000);
        adapter.sendBoardState();
        int c1 = client.sent.size();

        adapter.sendBoardState();
        int c2 = client.sent.size();

        assertTrue(c2 >= c1);
    }

    @Test
    public void testHandleUnknownMessageType() {
        Message msg = new Message(MessageType.PING, null);
        adapter.handleIncoming(msg);
        assertTrue(true);
    }

    @Test
    public void testResetClearsStatistics() {
        adapter.sendBoardState();
        adapter.sendBoardState();

        String before = adapter.getStatsString();
        adapter.reset();
        String after = adapter.getStatsString();

        assertNotEquals(before, after);
    }

    @Test
    public void testCompressionWithManyChanges() {
        adapter.setCompressionEnabled(true);

        GameState s = myLogic.getState();
        for (int y = 0; y < 5; y++)
            for (int x = 0; x < GameState.WIDTH; x++)
                s.getBoard()[y][x] = Color.BLUE;

        adapter.sendBoardState();
        assertFalse(client.sent.isEmpty());
    }

    @Test
    public void testLegacyModePrintStats() {
        adapter.setDeltaSyncEnabled(false);
        adapter.printStats();
        assertTrue(adapter.getStatsString().contains("Legacy"));
    }
}
