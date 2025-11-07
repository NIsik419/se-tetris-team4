package component.logic;

import javax.swing.Timer;

/** Swing Timer를 감싼 간단한 게임 루프 */
public class GameLoop {
    private final Timer timer;

    public GameLoop(Runnable tick, int intervalMs) {
        this.timer = new Timer(intervalMs, e -> tick.run());
    }

    public void start() { timer.start(); }
    public void stop()  { timer.stop(); }
    /** 드롭 속도가 변하면 호출 */
    public void setInterval(int ms) { timer.setDelay(ms); }
}