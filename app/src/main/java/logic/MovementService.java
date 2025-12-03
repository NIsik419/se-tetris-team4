package logic;

import blocks.Block;
import logic.GameState;
import java.awt.Color;

public class MovementService {
    private final GameState state;

    public MovementService(GameState state) {
        this.state = state;
    }

    /**
     * 현재 블록이 주어진 좌표(newX, newY)에 이동할 수 있는지 검사
     */
    public boolean canMove(Block b, int newX, int newY) {
        //  null 체크 추가
        if (b == null) {
            return false;
        }
        
        var board = state.getBoard();
        for (int j = 0; j < b.height(); j++) {
            for (int i = 0; i < b.width(); i++) {
                if (b.getShape(i, j) == 1) {
                    int bx = newX + i;
                    int by = newY + j;

                    // 보드 밖 체크
                    if (bx < 0 || bx >= GameState.WIDTH || by >= GameState.HEIGHT)
                        return false;
                    
                    //  by < 0 체크 제거 (스폰 시 위쪽에서 시작 가능)
                    if (by < 0)
                        continue;

                    // 고정된 블록 위면 이동 불가
                    if (board[by][bx] != null)
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

        if (curr == null)
            return false;

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
     * 현재 블록이 "어디까지 떨어질 수 있는지" 계산해서 Y좌표 반환
     * - 바닥 또는 쌓인 블록 위에서 멈춤
     * - 고스트 블록 표시용
     */
    public int getGhostY(Block block) {
        if (block == null)
            return -1;

        Color[][] board = state.getBoard();
        int bx = state.getX();
        int by = state.getY();
        int maxDrop = GameState.HEIGHT; // 매우 큰 값으로 초기화

        // 블록의 각 열(column)에 대해 독립적으로 검사
        for (int i = 0; i < block.width(); i++) {
            int drop = 0;

            // 그 열에서 가장 아래쪽 셀 찾기
            int bottomCell = -1;
            for (int j = block.height() - 1; j >= 0; j--) {
                if (block.getShape(i, j) == 1) {
                    bottomCell = j;
                    break;
                }
            }
            if (bottomCell == -1)
                continue; // 이 열엔 블록 없음

            int globalX = bx + i;
            int globalY = by + bottomCell;

            // 그 아래로 얼마나 내려갈 수 있는지 확인
            int ny = globalY + 1;
            while (ny < GameState.HEIGHT && board[ny][globalX] == null) {
                ny++;
                drop++;
            }

            // 열별로 가능한 drop 거리 중 가장 작은 값 선택
            maxDrop = Math.min(maxDrop, drop);
        }

        return by + maxDrop;
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