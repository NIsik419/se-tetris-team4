package component;

import logic.BoardLogic;

import javax.swing.SwingUtilities;
import javax.swing.Timer;

/**
 * GameLoop (Swing Timer 버전)
 * - pause 중에는 tick 자체가 스킵됨
 * - drop 속도 변화는 매 tick에서 delay 동기화
 * - 기존 API(startLoop/stopLoop/pauseLoop/resumeLoop)와 호환
 */
public class GameLoop {
    private final BoardLogic logic;
    private final Runnable repaint;
    private final Timer timer;

    private volatile boolean running = false;
    private volatile boolean paused  = false;

    /** 표준 생성자 */
    public GameLoop(BoardLogic logic, Runnable repaint) {
        this.logic = logic;
        this.repaint = (repaint != null) ? repaint : () -> {};

        int initialDelay = Math.max(1, (logic != null) ? logic.getDropInterval() : 1000);
        this.timer = new Timer(initialDelay, e -> {
            if (paused) return;
            if (logic != null) {
                // 드롭 인터벌 변동에 맞춰 Timer 딜레이 동기화
                int newDelay = Math.max(1, logic.getDropInterval());
                Timer t = (Timer) e.getSource();
                if (newDelay != t.getDelay()) t.setDelay(newDelay);

                // 한 틱 진행
                logic.moveDown();

                // 게임 종료 체크
                if (logic.isGameOver()) {
                    stop();
                    return;
                }
            }
            // 그리기는 EDT에서
            SwingUtilities.invokeLater(this.repaint);
        });
        this.timer.setRepeats(true);
    }

    /* ===== 기존/호환 API ===== */
    public synchronized void startLoop() { start(); }
    public synchronized void stopLoop()  { stop();  }
    public void pauseLoop()  { pause();  }
    public void resumeLoop() { resume(); }

    /* ===== 메인 제어 ===== */
    public synchronized void start() {
        if (running) return;
        running = true;
        paused  = false;
        timer.start();
    }

    public synchronized void stop() {
        running = false;
        paused  = false;
        timer.stop();
    }

    public void pause()  { paused = true;  }
    public void resume() { paused = false; }

    /* ===== 유틸 ===== */
    public void setInterval(int ms) { timer.setDelay(Math.max(1, ms)); }
    public boolean isRunning() { return running; }
    public boolean isPaused()  { return paused;  }
    public synchronized void cleanup() {
        stop();
        System.out.println("[GameLoop] Cleanup completed");
    } 
}
