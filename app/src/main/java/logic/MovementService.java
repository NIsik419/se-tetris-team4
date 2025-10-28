package logic;

import blocks.Block;
import logic.GameState;

public class MovementService {
    private final GameState state;

    public MovementService(GameState state) {
        this.state = state;
    }

        /**
     * 현재 블록이 주어진 좌표(newX, newY)에 이동할 수 있는지 검사
     */
    public boolean canMove(Block b, int newX, int newY) {
        var board = state.getBoard();

        for (int j = 0; j < b.height(); j++) {
            for (int i = 0; i < b.width(); i++) {
                if (b.getShape(i, j) == 1) {
                    int bx = newX + i;
                    int by = newY + j;

                    // 경계 검사
                    if (bx < 0 || bx >= GameState.WIDTH || by < 0 || by >= GameState.HEIGHT)
                        return false;

                    // 쌓인 블록과 충돌하면 이동 불가 (단, 자기 자신은 예외)
                    if (board[by][bx] != null && !isPartOfCurrentBlock(bx, by))
                        return false;
                }
            }
        }
        return true;
    }

    /**
     * 현재 조작 중 블록의 셀인지 확인 (자기 자신은 충돌로 보지 않게 예외 처리)
     */
    private boolean isPartOfCurrentBlock(int x, int y) {
        Block curr = state.getCurr();
        int bx = state.getX();
        int by = state.getY();

        if (curr == null) return false;

        for (int j = 0; j < curr.height(); j++) {
            for (int i = 0; i < curr.width(); i++) {
                if (curr.getShape(i, j) == 1) {
                    int cx = bx + i;
                    int cy = by + j;
                    if (x == cx && y == cy)
                        return true;
                }
            }
        }
        return false;
    }

    /**
     *    현재 블록이 "어디까지 떨어질 수 있는지" 계산해서 Y좌표 반환
     *  - 바닥 또는 쌓인 블록 위에서 멈춤
     *  - 고스트 블록 표시용
     */
    public int getGhostY(Block block) {
        if (block == null) return -1;

        int ghostY = state.getY();

        // 아래로 한 칸씩 내리며, 더 이상 이동할 수 없을 때 직전 Y값에서 멈춤
        while (canMove(block, state.getX(), ghostY + 1)) {
            ghostY++;
        }
        System.out.println("currY=" + state.getY() + ", ghostY=" + ghostY);

        return ghostY;
    }

    public void moveDown() {
        state.setPosition(state.getX(), state.getY() + 1);
    }

    public void moveLeft() {
        state.setPosition(state.getX() - 1, state.getY());
    }

    public void moveRight() {
        state.setPosition(state.getX() + 1, state.getY());
    }
}
