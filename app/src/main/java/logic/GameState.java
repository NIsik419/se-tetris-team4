package logic;

import java.awt.Color;
import blocks.Block;

/**
 * GameState
 * -----------------------
 * - 현재 보드 상태, 블록, 좌표, 홀드/넥스트, 페이드 레이어 관리
 * - BoardLogic이 사용하는 핵심 데이터 구조
 */
public class GameState {
    public static final int HEIGHT = 20;
    public static final int WIDTH = 10;

    // === 핵심 필드 ===
    private final Color[][] board = new Color[HEIGHT][WIDTH];
    private final Color[][] fadeLayer = new Color[HEIGHT][WIDTH];

    // 각 칸이 어떤 조각에 속하는지 표시 (0 = 비어 있음)
    private final int[][] pieceId = new int[HEIGHT][WIDTH];

    // 새 조각이 스폰될 때 쓸 ID
    private int nextPieceId = 1;

    private Block curr;     // 현재 블록
    private Block next;     // 다음 블록 (프리뷰용)
    private Block hold;     // 홀드 블록
    private boolean canHold = true;
    private boolean gameOver = false;
    private int score = 0;
    private int level = 1;
    private int incomingLines = 0;

    private int x = 3, y = 0;

    // === Getter / Setter ===
    public Color[][] getBoard() { return board; }
    public Color[][] getFadeLayer() { return fadeLayer; }

    public int[][] getPieceId() { return pieceId; }

    public Block getCurr() { return curr; }
    public void setCurr(Block b) { this.curr = b; }

    public Block getNext() { return next; }
    public void setNext(Block b) { this.next = b; }

    public Block getHold() { return hold; }
    public void setHold(Block b) { this.hold = b; }

    public boolean isCanHold() { return canHold; }
    public void setCanHold(boolean value) { this.canHold = value; }

    public boolean isGameOver() { return gameOver; }
    public void setGameOver(boolean value) { this.gameOver = value; }

    public int getScore() { return score; }
    public void setScore(int score) { this.score = score; }

    public int getLevel() { return level; }
    public void setLevel(int level) { this.level = level; }

    public int getIncomingLines() { return incomingLines; }
    public void setIncomingLines(int v) { this.incomingLines = v; }

    public int getX() { return x; }
    public int getY() { return y; }
    public void setPosition(int x, int y) { this.x = x; this.y = y; }

    public int allocatePieceId() {
        if (nextPieceId == Integer.MAX_VALUE) {
            nextPieceId = 1;
        }
        return nextPieceId++;
    }

    /**
     * 상태 전체 초기화 (대전 재시작 등)
     */
    public void reset() {
        curr = null;
        next = null;
        hold = null;
        canHold = true;
        gameOver = false;

        x = WIDTH / 2 - 2;
        y = 0;

        for (int yy = 0; yy < HEIGHT; yy++) {
            for (int xx = 0; xx < WIDTH; xx++) {
                board[yy][xx] = null;
                fadeLayer[yy][xx] = null;
                pieceId[yy][xx] = 0;   
            }
        }
        nextPieceId = 1; 
    }
}
