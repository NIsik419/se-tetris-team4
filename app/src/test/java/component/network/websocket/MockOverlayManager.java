package component.network.websocket;

import javax.swing.*;
import java.util.function.Consumer;

public class MockOverlayManager {

    // ---- 테스트에서 참조되는 플래그들 ----
    public boolean overlayCreated = false;
    public boolean overlayHidden = false;
    public boolean startButtonEnabled = false;

    public boolean startGameTriggered = false;
    public boolean restartTriggered = false;
    public boolean cleanupTriggered = false;

    public String selectedMode = "Normal";
    public String lastStatus = "";

    // 콜백 저장
    private Runnable onStartGame;
    private Consumer<String> onModeChanged;
    private Runnable onRestart;
    private Runnable onCleanup;
    private Runnable onExecuteRestart;

    public MockOverlayManager() {
        // 테스트에서는 초기값만 있어도 충분
    }

    // NetworkManager.initialize(parentPanel, overlayManager) 에서 호출됨
    public void initializeCallbacks(
            Runnable onStartGame,
            Consumer<String> onModeChanged,
            Runnable onRestart,
            Runnable onCleanup) {

        this.onStartGame = onStartGame;
        this.onModeChanged = onModeChanged;
        this.onRestart = onRestart;
        this.onCleanup = onCleanup;
    }

    public void setOnExecuteRestart(Runnable r) {
        this.onExecuteRestart = r;
    }

    // 실제 UI 없이 플래그만 세팅
    public void createOverlay() {
        overlayCreated = true;
    }

    public void hideOverlay() {
        overlayHidden = true;
    }

    public void enableStartButton() {
        startButtonEnabled = true;
    }

    public void updateStatus(String msg) {
        lastStatus = msg;
    }

    public void updateGameOverStatus(String msg) {
        lastStatus = msg;
    }

    public void triggerGameStart() {
        startGameTriggered = true;
        if (onStartGame != null) onStartGame.run();
    }

    public void triggerRestart() {
        restartTriggered = true;
        if (onExecuteRestart != null) onExecuteRestart.run();
    }

    public void showGameOverOverlay(boolean iLost, int myScore, int oppScore, int myLines, long startTime) {
        // UI 필요 없음
    }

    public void showStartOverlay() {
        overlayCreated = true;
    }

    // 모드 변경 테스트
    public void setMode(String mode) {
        selectedMode = mode;
        if (onModeChanged != null) onModeChanged.accept(mode);
    }

    public String getSelectedMode() {
        return selectedMode;
    }

    public void callRestart() {
        restartTriggered = true;
        if (onRestart != null) onRestart.run();
    }

    public void callCleanup() {
        cleanupTriggered = true;
        if (onCleanup != null) onCleanup.run();
    }
}
