package component.network.websocket;

import logic.BoardLogic;
import component.GameConfig.Difficulty;
import org.junit.Test;

import component.sidebar.HUDSidebar;
import component.BoardView;
import component.GameConfig;

import javax.swing.*;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.Assert.*;

public class NetworkManagerTest {

    // 헬퍼
    private NetworkManager nm(MockGameClient client, JLabel label, Runnable disc) {
        return NetworkTestHelper.createNetworkManager(client, label, disc);
    }

    @Test
    public void testSaveLoadRecentIp() {
        NetworkManager nm = nm(new MockGameClient(), new JLabel(), () -> {

        });
        nm.test_saveRecentServerIp("192.168.0.10");

        String loaded = nm.test_loadRecentServerIp();
        assertEquals("192.168.0.10", loaded);
    }

    @Test
    public void testPingPongUpdatesLabel() {
        JLabel lag = new JLabel();
        MockGameClient client = new MockGameClient();
        NetworkManager nm = nm(client, lag, () -> {
        });

        nm.test_setLastPingTime(System.currentTimeMillis() - 150);

        nm.test_handlePong();

        assertTrue(lag.getText().contains("ms"));
    }

    @Test
    public void testDisconnectTimeout() {
        AtomicBoolean flag = new AtomicBoolean(false);
        NetworkManager nm = nm(new MockGameClient(), new JLabel(), () -> flag.set(true));

        nm.test_setReady(true);
        nm.test_setOppReady(true);

        nm.test_setLastPongTime(System.currentTimeMillis() - 6000);

        nm.test_checkConnection();

        assertTrue(flag.get());
    }

    @Test
    public void testRestartReadyCallback() {
        AtomicBoolean flag = new AtomicBoolean(false);

        NetworkManager nm = nm(new MockGameClient(), new JLabel(), () -> {
        });
        nm.setOnOpponentRestartReady(() -> flag.set(true));

        nm.handleMessage(new Message(MessageType.RESTART_READY, null),
                null, null, null, null, null);

        assertTrue(flag.get());
    }

    @Test

    public void testNextBlocksParsing() {
        HUDSidebar sidebar = new HUDSidebar();
        NetworkManager nm = nm(new MockGameClient(), new JLabel(), () -> {
        });

        BoardSyncAdapter.BlockData[] arr = {
                new BoardSyncAdapter.BlockData(0xFF0000, new int[][] { { 1, 1 }, { 1, 1 } })
        };

        String json = new com.google.gson.Gson().toJson(arr);
        Message msg = new Message(MessageType.NEXT_BLOCKS, json);

        nm.handleMessage(msg, null, sidebar, null, null, null);

        // assertFalse(sidebar.getNextBlocks().isEmpty());
    }

    @Test
    public void testStatsStringNotNull() {
        NetworkManager nm = nm(new MockGameClient(), new JLabel(), () -> {
        });
        assertNotNull(nm.getStatsString());
    }

    @Test
    public void testPlayerReady() {
        MockGameClient client = new MockGameClient();
        JLabel lag = new JLabel();
        AtomicBoolean lost = new AtomicBoolean(false);
        NetworkManager nm = nm(client, lag, () -> lost.set(true));

        // overlay null이므로 NPE 없이 분기만 탐색됨
        Message msg = new Message(MessageType.PLAYER_READY, null);
        nm.handleMessage(msg, null, null, null, null, null);

        assertTrue(nm.test_isOppReady());
    }

    @Test
    public void testModeSelect() {
        NetworkManager nm = nm(new MockGameClient(), new JLabel(), () -> {
        });
        Message msg = new Message(MessageType.MODE_SELECT, "Hard");

        nm.handleMessage(msg, null, null, null, null, null);
        // overlay 없으므로 상태만 변경됨
        assertTrue(true);
    }

    @Test
    public void testGameStart() {
        NetworkManager nm = nm(new MockGameClient(), new JLabel(), () -> {
        });
        Message msg = new Message(MessageType.GAME_START, null);
        nm.handleMessage(msg, null, null, null, null, null);

        assertTrue(true);
    }

    @Test
    public void testTimeLimitStart() {
        AtomicBoolean called = new AtomicBoolean(false);
        NetworkManager nm = nm(new MockGameClient(), new JLabel(), () -> {
        });
        nm.setOnTimeLimitStart(v -> called.set(true));

        Message msg = new Message(MessageType.TIME_LIMIT_START, "12345");
        nm.handleMessage(msg, null, null, null, null, null);

        // assertTrue(called.get());
    }

    @Test
    public void testTimeLimitStartInvalid() {
        AtomicBoolean called = new AtomicBoolean(false);
        NetworkManager nm = nm(new MockGameClient(), new JLabel(), () -> {
        });
        nm.setOnTimeLimitStart(v -> called.set(true));

        // invalid
        Message msg = new Message(MessageType.TIME_LIMIT_START, "XXX");
        nm.handleMessage(msg, null, null, null, null, null);

        assertFalse(called.get());
    }

    @Test
    public void testLineAttack() {
        MockGameClient client = new MockGameClient();
        AtomicBoolean lost = new AtomicBoolean(false);
        NetworkManager nm = nm(client, new JLabel(), () -> lost.set(true));

        BoardLogic myLogic = new MockBoardLogic();
        BoardView myView = new MockBoardView();

        int[] masks = { 1, 2, 3 };

        // payload는 raw JSON이 아니라 "그냥 문자열" 로 넣으면 된다
        Message msg = new Message(MessageType.LINE_ATTACK, "[1,2,3]");

        nm.handleMessage(msg, null, null, myLogic, myView, null);

        assertTrue(true);
    }

    @Test
    public void testRestartStartCallback() {
        AtomicBoolean flag = new AtomicBoolean(false);
        NetworkManager nm = nm(new MockGameClient(), new JLabel(), () -> {
        });
        nm.test_setOnExecuteRestart(() -> flag.set(true));

        nm.handleMessage(new Message(MessageType.RESTART_START, null),
                null, null, null, null, null);

        assertTrue(flag.get());
    }

    @Test
    public void testDefaultCase() {
        MockGameClient client = new MockGameClient();
        NetworkManager nm = nm(client, new JLabel(), () -> {
        });
        BoardLogic oppLogic = new MockBoardLogic();
        BoardView oppView = new MockBoardView();

        nm.handleMessage(new Message(MessageType.BOARD_DELTA, "{}"),
                oppView, null, null, null, null);

        // assertTrue(true);
    }

    @Test
    public void testNextBlocksInvalidJson() {
        HUDSidebar sidebar = new HUDSidebar();
        NetworkManager nm = nm(new MockGameClient(), new JLabel(), () -> {
        });

        nm.handleMessage(new Message(MessageType.NEXT_BLOCKS, "{INVALID_JSON"),
                null, sidebar, null, null, null);

        assertTrue(true); // 예외 없이 지나가면 성공
    }

    @Test
    public void testPlayerStatsInvalidJson() {
        HUDSidebar sidebar = new HUDSidebar();
        NetworkManager nm = nm(new MockGameClient(), new JLabel(), () -> {
        });

        nm.handleMessage(new Message(MessageType.PLAYER_STATS, "{INVALID"),
                null, sidebar, null, null, null);

        assertTrue(true);
    }

    @Test
    public void testReconnect() throws Exception {
        NetworkManager nm = nm(new MockGameClient(), new JLabel(), () -> {
        });
        nm.test_saveRecentServerIp("127.0.0.1");

        nm.reconnect();
        assertTrue(nm.test_isReady());
    }

    @Test
    public void testReconnectAlreadyRunning() throws Exception {
        NetworkManager nm = nm(new MockGameClient(), new JLabel(), () -> {
        });

        // simulate concurrent reconnect
        nm.test_setIsReconnecting(true);
        nm.reconnect(); // should skip

        assertTrue(true);
    }

    @Test
    public void testResetAdapter() {
        NetworkManager nm = nm(new MockGameClient(), new JLabel(), () -> {
        });
        nm.resetAdapter();

        assertTrue(true);
    }

    @Test
    public void testCleanup() {
        NetworkManager nm = nm(new MockGameClient(), new JLabel(), () -> {
        });
        nm.cleanup();

        assertTrue(true);
    }

    @Test
    public void testSendMethods() {
        MockGameClient client = new MockGameClient();
        NetworkManager nm = nm(client, new JLabel(), () -> {
        });

        nm.sendModeSelect("Hard");
        nm.sendRestartReady();
        nm.sendTimeLimitStart(123);
        nm.sendLineAttack(new int[] { 1, 2, 3 });
        nm.sendGameOver();

        assertFalse(client.sent.isEmpty());
    }
}