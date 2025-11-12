package component;

public final class GameConfig {
    public enum Mode { CLASSIC, ITEM, TIME_ATTACK }
    public enum Difficulty { EASY, NORMAL, HARD }
    public enum ScreenSize { SMALL, MEDIUM, LARGE }

    private final Mode mode;
    private final Difficulty difficulty;
    private final boolean colorBlindMode;

    public GameConfig(Mode mode, Difficulty difficulty, boolean colorBlindMode) {
        this.mode = mode;
        this.difficulty = difficulty;
        this.colorBlindMode = colorBlindMode;
    }

    public Mode mode() { return mode; }
    public Difficulty difficulty() { return difficulty; }
    public boolean colorBlindMode() { return colorBlindMode; }

    @Override public String toString() {
        return "GameConfig{mode=" + mode + ", diff=" + difficulty + ", cb=" + colorBlindMode + "}";
    }
}
