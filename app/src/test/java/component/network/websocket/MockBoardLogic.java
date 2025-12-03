package component.network.websocket;

import logic.BoardLogic;
import component.SpeedManager;
import component.config.Settings;

public class MockBoardLogic extends BoardLogic {
    public boolean addGarbageCalled = false;
    public int score = 0; // 추가
    public int level = 1;

    public MockBoardLogic() {
        super(score -> {
        });
    }

    @Override
    public void addGarbageMasks(int[] masks) {
        addGarbageCalled = true;
    }

    @Override
    public void setScore(int score) {
        super.setScore(score); // BoardLogic.score 도 같이 반영
        this.score = score; // Mock 변수도 업데이트
    }

    @Override
    public int getScore() { // 추가
        return score;
    }

    @Override
    public void setLevel(int level) {
        this.level = level; // Mock 전용 변수

        try {
            // BoardLogic 내부 speedManager 레벨 업데이트
            var field = BoardLogic.class.getDeclaredField("speedManager");
            field.setAccessible(true);
            SpeedManager mgr = (SpeedManager) field.get(this);

            // SpeedManager 의 level 도 반영
            var levField = SpeedManager.class.getDeclaredField("level");
            levField.setAccessible(true);
            levField.set(mgr, level);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

}
