package component;

import component.GameConfig;

public class SpeedManager {
    private int level;
    private int dropInterval; // 현재 블럭 낙하 간격(ms)
    private double increaseFactor; // 난이도별 속도 증가 계수 (EASY=0.8, HARD=1.2)

    public SpeedManager() {
        setDifficulty(GameConfig.Difficulty.NORMAL);
    }

    /** 현재 레벨 반환 */
    public int getLevel() {
        return level;
    }

    public int getDropInterval() {
        return dropInterval;
    }

    public void setLevel(int level) {
        this.level = level;
    }

    /**
     * 난이도 설정
     * - 기본 속도는 동일하게 시작
     * - 증가율(가속 비율)만 다름
     */
    public void setDifficulty(GameConfig.Difficulty difficulty) {
        this.level = 1;
        this.dropInterval = 1000; // 모든 난이도 동일하게 시작 (요구사항 반영)

        switch (difficulty) {
            case EASY -> increaseFactor = 0.8;   // 20% 덜 증가
            case NORMAL -> increaseFactor = 1.0; // 기준
            case HARD -> increaseFactor = 1.2;   // 20% 더 증가
        }
    }

    /**
     * 레벨업 시 낙하속도 증가
     * - EASY: 완만하게 감소
     * - HARD: 더 급격히 감소
     */
    public void increaseLevel() {
        if (level < 10) {
            level++;
            // 기본 10% 감소를 기준으로 난이도 계수 적용
            double decreaseRate = 0.10 * increaseFactor;  // 예: EASY → 8%, HARD → 12%
            dropInterval = Math.max(250, (int) (dropInterval * (1.0 - decreaseRate)));
        }
    }

    @Override
    public String toString() {
        return "SpeedManager{" +
                "level=" + level +
                ", dropInterval=" + dropInterval +
                ", factor=" + increaseFactor +
                '}';
    }

    public void resetLevel() {
    this.level = 1;
    switch ((int) increaseFactor) {
        case 1:  // NORMAL
            this.dropInterval = 1000;
            break;
        case 0:  // EASY
            this.dropInterval = 1200; 
            break;
        case 2:  // HARD
            this.dropInterval = 800; 
            break;
    }
}

}
