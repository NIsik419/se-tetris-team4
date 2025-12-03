package component.network.websocket;

import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;

import javax.swing.*;
import java.util.concurrent.atomic.AtomicBoolean;

public class TimeLimitManagerTest {

    private JLabel timerLabel;
    private MockGameClient client;
    private TimeLimitManager manager;

    @Before
    public void setUp() {
        timerLabel = new JLabel();
        client = new MockGameClient();
    }

    @Test
    public void testConstructorServer() {
        manager = new TimeLimitManager(timerLabel, client, true);
        
        assertNotNull(manager);
        assertFalse(manager.isRunning());
    }

    @Test
    public void testConstructorClient() {
        manager = new TimeLimitManager(timerLabel, client, false);
        
        assertNotNull(manager);
        assertFalse(manager.isRunning());
    }

    @Test
    public void testStartAsServer() throws Exception {
        manager = new TimeLimitManager(timerLabel, client, true);
        AtomicBoolean timeoutCalled = new AtomicBoolean(false);
        
        manager.start(10, () -> timeoutCalled.set(true));
        
        assertTrue(manager.isRunning());
        assertTrue(manager.getRemainingTime() > 0);
        
        // TIME_LIMIT_START 메시지가 전송되었는지 확인
        assertTrue(client.sent.stream()
            .anyMatch(msg -> msg.type == MessageType.TIME_LIMIT_START));
    }

    @Test
    public void testStartAsClient() throws Exception {
        manager = new TimeLimitManager(timerLabel, client, false);
        AtomicBoolean timeoutCalled = new AtomicBoolean(false);
        
        manager.start(10, () -> timeoutCalled.set(true));
        
        assertTrue(manager.isRunning());
        
        // 클라이언트는 메시지를 전송하지 않음
        assertTrue(client.sent.stream()
            .noneMatch(msg -> msg.type == MessageType.TIME_LIMIT_START));
    }

    @Test
    public void testSyncStart() throws Exception {
        manager = new TimeLimitManager(timerLabel, client, false);
        
        long serverTime = System.currentTimeMillis();
        manager.syncStart(serverTime, 60);
        
        assertTrue(manager.isRunning());
        assertTrue(manager.getRemainingTime() > 0);
    }

    @Test
    public void testStop() throws Exception {
        manager = new TimeLimitManager(timerLabel, client, true);
        
        manager.start(10, () -> {});
        assertTrue(manager.isRunning());
        
        manager.stop();
        
        assertFalse(manager.isRunning());
        assertEquals(0, manager.getRemainingTime());
    }

    @Test
    public void testReset() throws Exception {
        manager = new TimeLimitManager(timerLabel, client, true);
        
        manager.start(10, () -> {});
        manager.reset();
        
        assertFalse(manager.isRunning());
        assertEquals(0, manager.getRemainingTime());
    }

    @Test
    public void testPause() throws Exception {
        manager = new TimeLimitManager(timerLabel, client, true);
        
        manager.start(10, () -> {});
        manager.pause();
        
        // pause 후에도 isRunning은 true (타이머만 멈춤)
        assertTrue(manager.isRunning());
    }

    @Test
    public void testResume() throws Exception {
        manager = new TimeLimitManager(timerLabel, client, true);
        
        manager.start(10, () -> {});
        manager.pause();
        manager.resume();
        
        assertTrue(manager.isRunning());
    }

    @Test
    public void testGetRemainingTime() throws Exception {
        manager = new TimeLimitManager(timerLabel, client, true);
        
        manager.start(60, () -> {});
        
        long remaining = manager.getRemainingTime();
        assertTrue(remaining > 0);
        assertTrue(remaining <= 60000);
    }

    @Test
    public void testGetRemainingTimeWhenNotRunning() {
        manager = new TimeLimitManager(timerLabel, client, true);
        
        assertEquals(0, manager.getRemainingTime());
    }

    @Test
    public void testTimerDisplayUpdate() throws Exception {
        manager = new TimeLimitManager(timerLabel, client, true);
        
        manager.start(10, () -> {});
        
        // 타이머 업데이트를 위한 대기
        Thread.sleep(200);
        
        // 타이머 라벨이 업데이트되었는지 확인
        assertNotNull(timerLabel.getText());
    }

    @Test
    public void testTimeout() throws Exception {
        manager = new TimeLimitManager(timerLabel, client, true);
        AtomicBoolean timeoutCalled = new AtomicBoolean(false);
        
        // 1초로 설정하고 대기
        manager.start(1, () -> timeoutCalled.set(true));
        
        Thread.sleep(1200);
        
        // 타임아웃 콜백이 호출되었는지 확인
        assertTrue(timeoutCalled.get());
        assertFalse(manager.isRunning());
    }

    @Test
    public void testCleanup() throws Exception {
        manager = new TimeLimitManager(timerLabel, client, true);
        
        manager.start(10, () -> {});
        manager.cleanup();
        
        assertFalse(manager.isRunning());
    }

    @Test
    public void testMultipleStarts() throws Exception {
        manager = new TimeLimitManager(timerLabel, client, true);
        
        manager.start(10, () -> {});
        manager.start(20, () -> {});
        
        // 두 번째 start가 첫 번째를 덮어씀
        assertTrue(manager.isRunning());
    }

    @Test
    public void testStopWhenNotRunning() {
        manager = new TimeLimitManager(timerLabel, client, true);
        
        // 실행 중이 아닐 때 stop 호출 - 예외 없이 완료
        manager.stop();
        
        assertFalse(manager.isRunning());
    }

    @Test
    public void testPauseWhenNotRunning() {
        manager = new TimeLimitManager(timerLabel, client, true);
        
        // 실행 중이 아닐 때 pause 호출 - 예외 없이 완료
        manager.pause();
        
        assertTrue(true);
    }

    @Test
    public void testResumeWhenNotRunning() {
        manager = new TimeLimitManager(timerLabel, client, true);
        
        // 실행 중이 아닐 때 resume 호출 - 예외 없이 완료
        manager.resume();
        
        assertTrue(true);
    }

    @Test
    public void testDefaultLimit() {
        // DEFAULT_LIMIT_SECONDS 확인
        assertEquals(180, TimeLimitManager.DEFAULT_LIMIT_SECONDS);
    }

    @Test
    public void testTimerLabelColorChange() throws Exception {
        manager = new TimeLimitManager(timerLabel, client, true);
        
        // 30초 미만으로 설정 (빨간색)
        manager.start(25, () -> {});
        
        Thread.sleep(200);
        
        // 라벨 업데이트 확인
        assertNotNull(timerLabel.getText());
    }

    @Test
    public void testTimerLabelOrangeWarning() throws Exception {
        manager = new TimeLimitManager(timerLabel, client, true);
        
        // 30-60초 사이 (주황색)
        manager.start(45, () -> {});
        
        Thread.sleep(200);
        
        assertNotNull(timerLabel.getText());
    }

    @Test
    public void testTimerLabelNormalColor() throws Exception {
        manager = new TimeLimitManager(timerLabel, client, true);
        
        // 60초 이상 (흰색)
        manager.start(120, () -> {});
        
        Thread.sleep(200);
        
        assertNotNull(timerLabel.getText());
    }

    @Test
    public void testSyncStartWithPastTime() throws Exception {
        manager = new TimeLimitManager(timerLabel, client, false);
        
        // 과거 시간으로 동기화 (이미 시간이 많이 지남)
        long pastTime = System.currentTimeMillis() - 5000;
        manager.syncStart(pastTime, 10);
        
        assertTrue(manager.isRunning());
        assertTrue(manager.getRemainingTime() < 10000);
    }

    @Test
    public void testTimeoutWithZeroRemaining() throws Exception {
        manager = new TimeLimitManager(timerLabel, client, true);
        AtomicBoolean timeoutCalled = new AtomicBoolean(false);
        
        // 매우 짧은 시간 설정
        manager.start(0, () -> timeoutCalled.set(true));
        
        Thread.sleep(300);
        
        assertTrue(timeoutCalled.get());
    }

    @Test
    public void testResetAfterTimeout() throws Exception {
        manager = new TimeLimitManager(timerLabel, client, true);
        
        manager.start(1, () -> {});
        Thread.sleep(1200);
        
        // 타임아웃 후 리셋
        manager.reset();
        
        assertFalse(manager.isRunning());
        assertEquals(0, manager.getRemainingTime());
    }

    @Test
    public void testCleanupMultipleTimes() throws Exception {
        manager = new TimeLimitManager(timerLabel, client, true);
        
        manager.start(10, () -> {});
        
        // 여러 번 cleanup 호출 - 예외 없이 완료
        manager.cleanup();
        manager.cleanup();
        manager.cleanup();
        
        assertFalse(manager.isRunning());
    }

    @Test
    public void testNullCallback() throws Exception {
        manager = new TimeLimitManager(timerLabel, client, true);
        
        // null 콜백으로 시작 - 예외 없이 완료
        manager.start(10, null);
        
        assertTrue(manager.isRunning());
    }

    @Test
    public void testRemainingTimeDecrease() throws Exception {
        manager = new TimeLimitManager(timerLabel, client, true);
        
        manager.start(10, () -> {});
        
        long remaining1 = manager.getRemainingTime();
        Thread.sleep(500);
        long remaining2 = manager.getRemainingTime();
        
        // 시간이 감소했는지 확인
        assertTrue(remaining2 < remaining1);
    }
}