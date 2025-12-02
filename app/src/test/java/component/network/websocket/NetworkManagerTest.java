// package component.network.websocket;

// import logic.BoardLogic;
// import component.GameConfig.Difficulty;
// import org.junit.Test;

// import component.sidebar.HUDSidebar;

// import javax.swing.*;
// import java.util.concurrent.atomic.AtomicBoolean;

// import static org.junit.Assert.*;

// public class NetworkManagerTest {

//     // 헬퍼
//     private NetworkManager nm(MockGameClient client, JLabel label, Runnable disc) {
//         return NetworkTestHelper.createNetworkManager(client, label, disc);
//     }

//     @Test
//     public void testSaveLoadRecentIp() {
//         NetworkManager nm = nm(new MockGameClient(), new JLabel(), () -> {});
//         nm.test_saveRecentServerIp("192.168.0.10");

//         String loaded = nm.test_loadRecentServerIp();
//         assertEquals("192.168.0.10", loaded);
//     }

//     @Test
//     public void testPingPongUpdatesLabel() {
//         JLabel lag = new JLabel();
//         MockGameClient client = new MockGameClient();
//         NetworkManager nm = nm(client, lag, () -> {});

//         nm.test_setLastPingTime(System.currentTimeMillis() - 150);

//         nm.test_handlePong();

//         //assertTrue(lag.getText().contains("ms"));
//     }

//     @Test
//     public void testDisconnectTimeout() {
//         AtomicBoolean flag = new AtomicBoolean(false);
//         NetworkManager nm = nm(new MockGameClient(), new JLabel(), () -> flag.set(true));

//         nm.test_setReady(true);
//         nm.test_setOppReady(true);

//         nm.test_setLastPongTime(System.currentTimeMillis() - 6000);

//         nm.test_checkConnection();

//         assertTrue(flag.get());
//     }

//     @Test
//     public void testRestartReadyCallback() {
//         AtomicBoolean flag = new AtomicBoolean(false);

//         NetworkManager nm = nm(new MockGameClient(), new JLabel(), () -> {});
//         nm.setOnOpponentRestartReady(() -> flag.set(true));

//         nm.handleMessage(new Message(MessageType.RESTART_READY, null),
//                 null, null, null, null);

//         assertTrue(flag.get());
//     }

//     @Test
//     public void testNextBlocksParsing() {
//         HUDSidebar sidebar = new HUDSidebar();
//         NetworkManager nm = nm(new MockGameClient(), new JLabel(), () -> {});

//         BoardSyncAdapter.BlockData[] arr = {
//                 new BoardSyncAdapter.BlockData(0xFF0000, new int[][]{{1,1},{1,1}} )
//         };

//         String json = new com.google.gson.Gson().toJson(arr);
//         Message msg = new Message(MessageType.NEXT_BLOCKS, json);

//         nm.handleMessage(msg, null, sidebar, null, null);

//         //assertFalse(sidebar.getNextBlocks().isEmpty());
//     }

//     @Test
//     public void testStatsStringNotNull() {
//         NetworkManager nm = nm(new MockGameClient(), new JLabel(), () -> {});
//         assertNotNull(nm.getStatsString());
//     }
// }
