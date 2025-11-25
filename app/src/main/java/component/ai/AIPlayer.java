package component.ai;

import logic.BoardLogic;

/**
 * AIPlayer - TetrisAI를 래핑하여 VersusGameManager에서 사용
 */
public class AIPlayer {
    
    private final TetrisAI ai;
    private final BoardLogic logic;
    
    /**
     * 생성자
     * @param logic 제어할 보드 로직
     */
    public AIPlayer(BoardLogic logic) {
        this.logic = logic;
        this.ai = new TetrisAI(logic);
    }
    
    /**
     * 난이도 설정
     * @param difficulty "easy", "normal", "hard"
     */
    public void setDifficulty(String difficulty) {
        ai.setDifficulty(difficulty);
    }
    
    /**
     * 다음 행동 가져오기
     * @return "LEFT", "RIGHT", "ROTATE", "DROP", "DOWN" 또는 null
     */
    public String getNextAction() {
        return ai.getNextAction();
    }
    
    /**
     * AI 사고 지연 시간 가져오기
     * @return 밀리초 단위 지연 시간
     */
    public int getThinkingDelay() {
        return ai.getThinkingDelay();
    }
    
    /**
     * 게임 오버 확인
     * @return 게임 오버 여부
     */
    public boolean isGameOver() {
        return logic.isGameOver();
    }
}