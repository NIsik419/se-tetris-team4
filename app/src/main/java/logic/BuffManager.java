package logic;

public class BuffManager {
    private boolean doubleScore = false;
    private long doubleScoreEndTime = 0;
    private boolean slowed = false;
    private long slowEndTime = 0;

    public void enableDoubleScore(long ms) {
        doubleScore = true;
        doubleScoreEndTime = System.currentTimeMillis() + ms;
    }

    public boolean isDoubleScoreActive() {
        if (doubleScore && System.currentTimeMillis() < doubleScoreEndTime)
            return true;
        else { doubleScore = false; return false; }
    }

    public void slowDown(long ms) {
        slowed = true;
        slowEndTime = System.currentTimeMillis() + ms;
    }

    public boolean isSlowed() {
        if (slowed && System.currentTimeMillis() < slowEndTime)
            return true;
        else { slowed = false; return false; }
    }
}
