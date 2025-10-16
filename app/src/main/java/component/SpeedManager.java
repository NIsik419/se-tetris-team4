package component;

public class SpeedManager {
    private int level = 1;
    private int dropInterval = 1000;

    public void increaseLevel() {
        if (level < 10) {
            level++;
            dropInterval = Math.max(200, dropInterval-100);
        }
    }

    public int getDropInterval() {
        return dropInterval;
    }
    
    public int getLevel() {
        return level;
    }
}
