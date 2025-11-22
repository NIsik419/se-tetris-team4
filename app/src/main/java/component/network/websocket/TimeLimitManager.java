package component.network.websocket;

import javax.swing.*;
import java.awt.*;

/**
 * 타임 리밋 모드 관리자
 * - 제한 시간 카운트다운
 * - 서버 시간 기준 동기화
 * - 타임아웃 시 승부 결정
 */
public class TimeLimitManager {
    
    private final JLabel timerLabel;
    private final GameClient client;
    private final boolean isServer;
    
    private Timer countdownTimer;
    private long startTime;
    private long limitMillis;
    private boolean isRunning;
    private Runnable onTimeoutCallback;
    
    // 기본 제한 시간 (3분)
    public static final long DEFAULT_LIMIT_SECONDS = 180;
    
    public TimeLimitManager(JLabel timerLabel, GameClient client, boolean isServer) {
        this.timerLabel = timerLabel;
        this.client = client;
        this.isServer = isServer;
        this.isRunning = false;
    }
    
    /**
     * 타임 리밋 시작
     * @param limitSeconds 제한 시간 (초)
     * @param callback 타임아웃 시 호출될 콜백
     */
    public void start(long limitSeconds, Runnable callback) {
        this.limitMillis = limitSeconds * 1000;
        this.onTimeoutCallback = callback;
        this.isRunning = true;
        
        if (isServer) {
            // 서버는 현재 시간을 기준으로 시작
            this.startTime = System.currentTimeMillis();
            
            // 클라이언트에게 시작 시간 전송
            client.send(new Message(MessageType.TIME_LIMIT_START, startTime));
        }
        
        startCountdown();
    }
    
    /**
     * 클라이언트가 서버로부터 받은 시작 시간으로 동기화
     */
    public void syncStart(long serverStartTime, long limitSeconds) {
        this.startTime = serverStartTime;
        this.limitMillis = limitSeconds * 1000;
        this.isRunning = true;
        
        startCountdown();
    }
    
    /**
     * 카운트다운 타이머 시작
     */
    private void startCountdown() {
        if (countdownTimer != null && countdownTimer.isRunning()) {
            countdownTimer.stop();
        }
        
        countdownTimer = new Timer(100, e -> {
            if (!isRunning) {
                return;
            }
            
            long elapsed = System.currentTimeMillis() - startTime;
            long remaining = limitMillis - elapsed;
            
            if (remaining <= 0) {
                onTimeout();
            } else {
                updateTimerDisplay(remaining);
            }
        });
        
        countdownTimer.start();
    }
    
    /**
     * 타이머 UI 업데이트
     */
    private void updateTimerDisplay(long remainingMillis) {
        long totalSeconds = remainingMillis / 1000;
        long minutes = totalSeconds / 60;
        long seconds = totalSeconds % 60;
        
        String timeText = String.format("%02d:%02d", minutes, seconds);
        
        SwingUtilities.invokeLater(() -> {
            timerLabel.setText("Time: " + timeText);
            
            // 30초 이하면 빨간색 경고
            if (remainingMillis < 30000) {
                timerLabel.setForeground(Color.RED);
            } else if (remainingMillis < 60000) {
                timerLabel.setForeground(Color.ORANGE);
            } else {
                timerLabel.setForeground(Color.WHITE);
            }
        });
    }
    
    /**
     * 타임아웃 처리
     */
    private void onTimeout() {
        isRunning = false;
        
        if (countdownTimer != null) {
            countdownTimer.stop();
        }
        
        SwingUtilities.invokeLater(() -> {
            timerLabel.setText("Time: 00:00");
            timerLabel.setForeground(Color.RED);
            
            if (onTimeoutCallback != null) {
                onTimeoutCallback.run();
            }
        });
    }
    
    /**
     * 타이머 정지
     */
    public void stop() {
        isRunning = false;
        
        if (countdownTimer != null && countdownTimer.isRunning()) {
            countdownTimer.stop();
        }
        
        SwingUtilities.invokeLater(() -> {
            timerLabel.setText("");
            timerLabel.setForeground(Color.WHITE);
        });
    }
    
    /**
     * 타이머 리셋 (재시작용)
     */
    public void reset() {
        stop();
        this.startTime = 0;
        this.isRunning = false;
    }
    
    /**
     * 타이머 일시정지
     */
    public void pause() {
        if (countdownTimer != null && countdownTimer.isRunning()) {
            countdownTimer.stop();
        }
    }
    
    /**
     * 타이머 재개
     */
    public void resume() {
        if (isRunning && countdownTimer != null) {
            countdownTimer.start();
        }
    }
    
    /**
     * 남은 시간 반환 (밀리초)
     */
    public long getRemainingTime() {
        if (!isRunning) {
            return 0;
        }
        
        long elapsed = System.currentTimeMillis() - startTime;
        long remaining = limitMillis - elapsed;
        
        return Math.max(0, remaining);
    }
    
    /**
     * 타이머 실행 중인지 확인
     */
    public boolean isRunning() {
        return isRunning;
    }
    
    /**
     * 정리 (패널 종료 시)
     */
    public void cleanup() {
        stop();
        countdownTimer = null;
        onTimeoutCallback = null;
    }
}