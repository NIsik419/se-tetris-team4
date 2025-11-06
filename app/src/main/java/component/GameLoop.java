package component;

import logic.BoardLogic;

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

    public void startLoop() {
        if (loopThread == null || !loopThread.isAlive()) {
            running = true;
            paused = false;
            loopThread = new Thread(this::runLoop, "GameLoopThread");
            loopThread.start();
        }
    }

    private void runLoop() {
        while (running) {
            try {
                Thread.sleep(logic.getDropInterval());
                if (paused) continue;

                logic.moveDown();
                repaint.run();

                // 🔥 게임오버 즉시 종료
                if (logic.isGameOver()) {
                    System.err.println("[GameLoop] Detected Game Over. Stopping loop." );
                    running = false;
                    break;
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
    }

    public void pauseLoop() { paused = true; }

    public void resumeLoop() { paused = false; }

    public void stopLoop() {
        running = false;
        loopThread = null;
    }
}
