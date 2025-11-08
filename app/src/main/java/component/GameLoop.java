package component;

import logic.BoardLogic;

/**
 * GameLoop (수정 버전)
 * ---------------------
 * - pause 중에는 tick 자체를 멈춤
 * - volatile + synchronized로 thread-safe 보장
 */
public class GameLoop {
    private final BoardLogic logic;
    private final Runnable repaint;
    private Thread loopThread;
    private volatile boolean running = false;
    private volatile boolean paused = false;

    public GameLoop(BoardLogic logic, Runnable repaint) {
        this.logic = logic;
        this.repaint = repaint;
    }

    /** 루프 시작 */
    public synchronized void startLoop() {
        if (loopThread == null || !loopThread.isAlive()) {
            running = true;
            paused = false;
            loopThread = new Thread(this::runLoop, "GameLoopThread");
            loopThread.setDaemon(true);
            loopThread.start();
        }
    }

    /** 실제 루프 동작 - 수정 버전 */
    private void runLoop() {
        while (running) {
            try {
                // pause 상태면 busy-waiting으로 대기
                while (paused && running) {
                    Thread.sleep(50); // pause 중엔 짧게 sleep
                    continue;
                }

                // running이 false로 바뀌면 즉시 탈출
                if (!running) break;

                // 실제 게임 로직 실행
                logic.moveDown();
                repaint.run();

                // 게임오버 감지
                if (logic.isGameOver()) {
                    running = false;
                    break;
                }

                // 다음 tick까지 대기
                int interval = logic.getDropInterval();
                Thread.sleep(Math.max(15, interval));

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                
                break;
            }
        }
        
    }

    public synchronized void pauseLoop() {
        paused = true;
        
    }

    public synchronized void resumeLoop() {
        if (!paused) {
            
            return;
        }
        paused = false;
       
    }

    /** 완전 중지 */
    public synchronized void stopLoop() {
        running = false;
        paused = false;
        if (loopThread != null) {
            loopThread.interrupt();
            loopThread = null;
        }
        
    }

    public boolean isRunning() {
        return running;
    }

    public boolean isPaused() {
        return paused;
    }
}