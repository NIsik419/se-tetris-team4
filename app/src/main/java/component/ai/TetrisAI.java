package component.ai;

import logic.BoardLogic;
import logic.GameState;
import blocks.Block;
import java.awt.Color;
import java.util.*;

/**
 * 테트리스 AI - 난이도별 전략
 * 
 * EASY: 단순 줄 클리어 (1줄도 OK)
 * NORMAL: 효율적인 줄 클리어 (2줄 선호)
 * HARD: 공격 우선 + 생존 밸런스
 */
public class TetrisAI {
    
    private final BoardLogic logic;
    private Queue<String> actionQueue = new LinkedList<>();
    
    // AI 설정
    private String difficulty = "normal";
    private int thinkingDelay = 100;
    private double randomMistakeChance = 0.05;
    
    public TetrisAI(BoardLogic logic) {
        this.logic = logic;
    }
    
    /**
     * 다음 액션 반환
     */
    public String getNextAction() {
        if (!actionQueue.isEmpty()) {
            return actionQueue.poll();
        }
        
        Block current = logic.getCurr();
        if (current == null) {
            return null;
        }
        
        BestMove best = findBestMove();
        if (best != null) {
            generateActionSequence(best);
        }
        
        return actionQueue.isEmpty() ? null : actionQueue.poll();
    }
    
    /**
     * 최적 배치 찾기
     */
    private BestMove findBestMove() {
        Block current = logic.getCurr();
        if (current == null) return null;
        
        GameState state = logic.getState();
        Color[][] board = state.getBoard();
        
        BestMove best = null;
        double bestScore = Double.NEGATIVE_INFINITY;
        
        // 모든 회전 시도
        for (int rotation = 0; rotation < 4; rotation++) {
            Block testBlock = current.clone();
            
            for (int r = 0; r < rotation; r++) {
                testBlock.rotate();
            }
            
            // 모든 X 위치 시도
            for (int x = -2; x < GameState.WIDTH + 2; x++) {
                int finalY = dropBlock(board, testBlock, x, 0);
                
                if (finalY < 0) continue;
                
                double score = evaluateMove(board, testBlock, x, finalY);
                
                if (score > bestScore) {
                    bestScore = score;
                    best = new BestMove(x, rotation, score);
                }
            }
        }
        
        return best;
    }
    
    /**
     * 블록 드롭 시뮬레이션
     */
    private int dropBlock(Color[][] board, Block block, int x, int startY) {
        int y = startY;
        
        while (canPlace(board, block, x, y + 1)) {
            y++;
        }
        
        if (!canPlace(board, block, x, y)) {
            return -1;
        }
        
        return y;
    }
    
    /**
     * 배치 가능 여부
     */
    private boolean canPlace(Color[][] board, Block block, int x, int y) {
        for (int by = 0; by < block.height(); by++) {
            for (int bx = 0; bx < block.width(); bx++) {
                if (block.getShape(bx, by) == 1) {
                    int boardX = x + bx;
                    int boardY = y + by;
                    
                    if (boardX < 0 || boardX >= GameState.WIDTH || 
                        boardY < 0 || boardY >= GameState.HEIGHT) {
                        return false;
                    }
                    
                    if (board[boardY][boardX] != null) {
                        return false;
                    }
                }
            }
        }
        return true;
    }
    
    /**
     * 배치 평가 (난이도별 전략)
     */
    private double evaluateMove(Color[][] board, Block block, int x, int y) {
        Color[][] simBoard = copyBoard(board);
        
        // 블록 배치
        for (int by = 0; by < block.height(); by++) {
            for (int bx = 0; bx < block.width(); bx++) {
                if (block.getShape(bx, by) == 1) {
                    int boardX = x + bx;
                    int boardY = y + by;
                    if (boardX >= 0 && boardX < GameState.WIDTH && 
                        boardY >= 0 && boardY < GameState.HEIGHT) {
                        simBoard[boardY][boardX] = block.getColor();
                    }
                }
            }
        }
        
        // 평가 지표
        int completedLines = countCompletedLines(simBoard);
        int holes = countHoles(simBoard);
        int bumpiness = calculateBumpiness(simBoard);
        int maxHeight = getMaxHeight(simBoard);
        int blockades = countBlockades(simBoard);
        int aggregateHeight = getAggregateHeight(simBoard);
        
        double score;
        
        switch (difficulty.toLowerCase()) {
            case "easy":
                // EASY: 단순 클리어 (1줄도 좋음)
                score = 
                    completedLines * 100.0 +      // 줄 클리어 보너스
                    holes * -30.0 +                // 구멍 페널티 (약함)
                    bumpiness * -5.0 +             // 울퉁불퉁 페널티 (약함)
                    maxHeight * -3.0;              // 높이 페널티 (약함)
                break;
                
            case "hard":
                // HARD: 공격 + 생존 밸런스 (고도화)
                // 3단계 위험도 평가
                boolean isCritical = maxHeight >= 16;     // 🔴 매우 위험 (16줄 이상)
                boolean isDangerous = maxHeight >= 12;    // 🟡 위험 (12~15줄)
                boolean isSafe = maxHeight < 10;          // 🟢 안전 (10줄 미만)
                
                if (isCritical) {
                    // 🔴 매우 위험: 무조건 생존! (어떤 클리어든 환영)
                    score = 
                        completedLines * 350.0 +           // 모든 클리어 큰 보너스
                        (completedLines >= 2 ? 300.0 : 0) + // 2줄+ 추가
                        (completedLines >= 3 ? 400.0 : 0) + // 3줄+ 추가
                        holes * -120.0 +                   // 구멍 매우 큰 페널티
                        bumpiness * -30.0 +                // 평평하게 필수
                        maxHeight * -80.0 +                // 높이 감소 최우선
                        aggregateHeight * -5.0 +           // 전체 높이 큰 페널티
                        blockades * -100.0;                // 막힌 공간 절대 안됨
                } else if (isDangerous) {
                    // 🟡 위험: 생존 우선, 2줄 이상 선호
                    if (completedLines >= 2) {
                        score = 
                            completedLines * 280.0 +       // 2줄+ 큰 보너스
                            (completedLines >= 3 ? 350.0 : 0) +
                            holes * -90.0 +
                            bumpiness * -20.0 +
                            maxHeight * -60.0 +
                            aggregateHeight * -3.0 +
                            blockades * -80.0;
                    } else {
                        // 1줄도 괜찮지만 페널티 있음
                        score = 
                            completedLines * 150.0 +
                            holes * -90.0 +
                            bumpiness * -20.0 +
                            maxHeight * -60.0 +
                            aggregateHeight * -3.0 +
                            blockades * -80.0;
                    }
                } else if (isSafe) {
                    // 🟢 안전 (10줄 미만): 공격적 플레이 (2줄 이상 필수)
                    if (completedLines >= 2) {
                        score = 
                            completedLines * 300.0 +       // 2줄+ 큰 보너스
                            (completedLines >= 3 ? 500.0 : 0) + // 3줄 특별 보너스
                            (completedLines >= 4 ? 800.0 : 0) + // 4줄 엄청난 보너스
                            holes * -60.0 +                // 구멍 관리
                            bumpiness * -12.0 +            // 약간의 페널티
                            maxHeight * -15.0 +            // 높이는 덜 중요
                            aggregateHeight * -0.5 +       // 전체 높이 약간만 관리
                            blockades * -50.0;             // 막힌 공간 중간 페널티
                    } else {
                        // 1줄은 큰 페널티
                        score = -600.0 + (completedLines * 80.0);
                    }
                } else {
                    // 🔵 중간 (10~11줄): 균형 잡힌 플레이
                    if (completedLines >= 2) {
                        score = 
                            completedLines * 280.0 +
                            (completedLines >= 3 ? 400.0 : 0) +
                            (completedLines >= 4 ? 700.0 : 0) +
                            holes * -70.0 +
                            bumpiness * -15.0 +
                            maxHeight * -30.0 +
                            aggregateHeight * -2.0 +
                            blockades * -65.0;
                    } else {
                        // 1줄은 페널티 (하지만 안전할 때보단 약함)
                        score = -300.0 + (completedLines * 100.0);
                    }
                }
                break;
                
            default: // "normal"
                // NORMAL: 효율 중시 (2줄 선호, 1줄도 괜찮)
                double lineBonus = completedLines * 100.0;
                if (completedLines >= 2) {
                    lineBonus += 100.0; // 2줄 이상 보너스
                }
                
                score = 
                    lineBonus +
                    holes * -50.0 +
                    bumpiness * -10.0 +
                    maxHeight * -5.0 +
                    blockades * -30.0;
                break;
        }
        
        // 실수 확률 적용
        if (Math.random() < randomMistakeChance) {
            score += (Math.random() - 0.5) * 100;
        }
        
        return score;
    }
    
    /**
     * 완성된 라인 수
     */
    private int countCompletedLines(Color[][] board) {
        int count = 0;
        for (int y = 0; y < GameState.HEIGHT; y++) {
            boolean full = true;
            for (int x = 0; x < GameState.WIDTH; x++) {
                if (board[y][x] == null) {
                    full = false;
                    break;
                }
            }
            if (full) count++;
        }
        return count;
    }
    
    /**
     * 구멍 개수
     */
    private int countHoles(Color[][] board) {
        int holes = 0;
        for (int x = 0; x < GameState.WIDTH; x++) {
            boolean blockFound = false;
            for (int y = 0; y < GameState.HEIGHT; y++) {
                if (board[y][x] != null) {
                    blockFound = true;
                } else if (blockFound) {
                    holes++;
                }
            }
        }
        return holes;
    }
    
    /**
     * 막힌 공간 (구멍 위에 2개 이상 블록)
     */
    private int countBlockades(Color[][] board) {
        int blockades = 0;
        for (int x = 0; x < GameState.WIDTH; x++) {
            int blocksAboveHole = 0;
            boolean holeFound = false;
            
            for (int y = GameState.HEIGHT - 1; y >= 0; y--) {
                if (board[y][x] == null) {
                    if (blocksAboveHole > 0) {
                        holeFound = true;
                    }
                } else {
                    if (holeFound) {
                        blockades++;
                    }
                    blocksAboveHole++;
                }
            }
        }
        return blockades;
    }
    
    /**
     * 울퉁불퉁함
     */
    private int calculateBumpiness(Color[][] board) {
        int[] heights = getColumnHeights(board);
        int bumpiness = 0;
        for (int i = 0; i < heights.length - 1; i++) {
            bumpiness += Math.abs(heights[i] - heights[i + 1]);
        }
        return bumpiness;
    }
    
    /**
     * 최대 높이
     */
    private int getMaxHeight(Color[][] board) {
        int[] heights = getColumnHeights(board);
        int max = 0;
        for (int h : heights) {
            max = Math.max(max, h);
        }
        return max;
    }
    
    /**
     * 전체 높이 합 (평균 높이 계산용)
     */
    private int getAggregateHeight(Color[][] board) {
        int[] heights = getColumnHeights(board);
        int sum = 0;
        for (int h : heights) {
            sum += h;
        }
        return sum;
    }
    
    /**
     * 각 열 높이
     */
    private int[] getColumnHeights(Color[][] board) {
        int[] heights = new int[GameState.WIDTH];
        for (int x = 0; x < GameState.WIDTH; x++) {
            for (int y = 0; y < GameState.HEIGHT; y++) {
                if (board[y][x] != null) {
                    heights[x] = GameState.HEIGHT - y;
                    break;
                }
            }
        }
        return heights;
    }
    
    /**
     * 보드 복사
     */
    private Color[][] copyBoard(Color[][] board) {
        Color[][] copy = new Color[GameState.HEIGHT][GameState.WIDTH];
        for (int y = 0; y < GameState.HEIGHT; y++) {
            System.arraycopy(board[y], 0, copy[y], 0, GameState.WIDTH);
        }
        return copy;
    }
    
    /**
     * 액션 시퀀스 생성
     */
    private void generateActionSequence(BestMove best) {
        actionQueue.clear();
        
        int currentX = logic.getX();
        int targetX = best.x;
        int targetRotation = best.rotation;
        
        // 1. 회전
        for (int i = 0; i < targetRotation; i++) {
            actionQueue.add("ROTATE");
        }
        
        // 2. 좌우 이동
        int dx = targetX - currentX;
        if (dx < 0) {
            for (int i = 0; i < -dx; i++) {
                actionQueue.add("LEFT");
            }
        } else {
            for (int i = 0; i < dx; i++) {
                actionQueue.add("RIGHT");
            }
        }
        
        // 3. 하드 드롭
        actionQueue.add("DROP");
    }
    
    /**
     * 난이도 설정
     */
    public void setDifficulty(String difficulty) {
        this.difficulty = difficulty;
        
        switch (difficulty.toLowerCase()) {
            case "easy":
                thinkingDelay = 300;
                randomMistakeChance = 0.15;
                break;
            case "normal":
                thinkingDelay = 150;
                randomMistakeChance = 0.05;
                break;
            case "hard":
                thinkingDelay = 50;
                randomMistakeChance = 0.01;
                break;
        }
    }
    
    public int getThinkingDelay() {
        return thinkingDelay;
    }
    
    /**
     * 최적 배치 정보
     */
    private static class BestMove {
        int x;
        int rotation;
        double score;
        
        BestMove(int x, int rotation, double score) {
            this.x = x;
            this.rotation = rotation;
            this.score = score;
        }
    }
}