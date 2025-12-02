package component;

public class GameSettings {

    // 기본 난이도 NORMAL
    private static GameConfig.Difficulty currentDifficulty = GameConfig.Difficulty.NORMAL;

    public static void setDifficulty(GameConfig.Difficulty diff) {
        currentDifficulty = diff;
    }

    public static GameConfig.Difficulty getDifficulty() {
        return currentDifficulty;
    }
}
