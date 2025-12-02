package component;

import component.GameConfig;

public class SpeedManager {
    private int level;
    private int dropInterval; // 현재 블럭 낙하 간격(ms)
    private double increaseFactor;

    public SpeedManager(GameConfig.Difficulty difficulty) {
        setDifficulty(difficulty);
    }

    public int getLevel() {
        return level;
    }

    public int getDropInterval() {
        return dropInterval;
    }

    public void setLevel(int level) {
        this.level = level;
    }

    /** dropInterval을 변경 */
    private void setDropInterval(int newInterval) {
        this.dropInterval = newInterval;
        // System.out.println("⭐[Speed] Level=" + level +
        //         " → DropInterval=" + dropInterval + "ms");
    }

    /**
     * 난이도 설정
     */
    public void setDifficulty(GameConfig.Difficulty difficulty) {
        this.level = 1;
        setDropInterval(1000); //  이제 이 함수만 사용

        switch (difficulty) {
            case EASY -> increaseFactor = 0.8;
            case NORMAL -> increaseFactor = 1.0;
            case HARD -> increaseFactor = 1.2;
        }
    }

    /**
     * 레벨업 시 낙하속도 증가
     */
    public void increaseLevel() {
        if (level < 10) {
            level++;
            double decreaseRate = 0.10 * increaseFactor;
            int next = Math.max(250, (int) (dropInterval * (1.0 - decreaseRate)));

            setDropInterval(next); 
        }
    }

    /**
     * 레벨/속도 리셋
     */
    public void resetLevel() {
        this.level = 1;
        setDropInterval(1000); 
    }
}
