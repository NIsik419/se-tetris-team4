package logic;

import java.awt.Color;
import javax.swing.Timer;
import java.util.ArrayList;
import java.util.List;

public class ClearService {
    private final GameState state;
    private boolean skipDuringItem = false;
    
    // 삭제 표시 색상 (하얀색 잔상)
    private static final Color DELETE_MARKER = new Color(255, 255, 255, 220);

    public ClearService(GameState state) {
        this.state = state;
    }

    /** 메인 라인 클리어 로직 - 동시 처리 방식 */
    public int clearLines(Runnable onFrameUpdate, Runnable onComplete) {
        var board = state.getBoard();
        List<Integer> fullRows = new ArrayList<>();

        // 꽉 찬 줄 찾기
        for (int y = 0; y < GameState.HEIGHT; y++) {
            boolean full = true;
            for (int x = 0; x < GameState.WIDTH; x++) {
                if (board[y][x] == null) {
                    full = false;
                    break;
                }
            }
            if (full)
                fullRows.add(y);
        }

        if (fullRows.isEmpty()) {
            if (onComplete != null)
                onComplete.run();
            return 0;
        }

        int linesCleared = fullRows.size();

        // 잠깐 표시하고 삭제
        showDeleteMarkAndClear(fullRows, onFrameUpdate, () -> {
            applyGravityInstantly();
            if (onFrameUpdate != null)
                onFrameUpdate.run();
            if (onComplete != null)
                onComplete.run();
        });

        return linesCleared;
    }

    /** 삭제될 줄을 잠깐 하얗게 표시하고 삭제 */
    private void showDeleteMarkAndClear(List<Integer> rows, Runnable onFrameUpdate, Runnable onComplete) {
        var board = state.getBoard();
        var fade = state.getFadeLayer();
        
        // 1단계: 하얀색으로 표시
        for (int row : rows) {
            for (int x = 0; x < GameState.WIDTH; x++) {
                fade[row][x] = DELETE_MARKER;
            }
        }
        
        if (onFrameUpdate != null)
            onFrameUpdate.run();
        
        // 2단계: 200ms 후 삭제 + 중력
        Timer deleteTimer = new Timer(200, e -> {
            ((Timer) e.getSource()).stop();
            
            // fadeLayer 제거 + 실제 블록 삭제
            for (int row : rows) {
                for (int x = 0; x < GameState.WIDTH; x++) {
                    fade[row][x] = null;
                    board[row][x] = null;
                }
            }
            
            if (onFrameUpdate != null)
                onFrameUpdate.run();
            
            // 약간의 딜레이 후 완료
            Timer completeTimer = new Timer(50, ev -> {
                ((Timer) ev.getSource()).stop();
                if (onComplete != null)
                    onComplete.run();
            });
            completeTimer.setRepeats(false);
            completeTimer.start();
        });
        deleteTimer.setRepeats(false);
        deleteTimer.start();
    }

    /** 
     * 단일 줄 표시하고 삭제 (아이템용)
     */
    public void animateSingleLineClear(int targetY, Runnable onFrameUpdate, Runnable onComplete) {
        var board = state.getBoard();
        var fade = state.getFadeLayer();
        
        // 1단계: 하얀색으로 표시
        for (int x = 0; x < GameState.WIDTH; x++) {
            fade[targetY][x] = DELETE_MARKER;
        }
        
        if (onFrameUpdate != null)
            onFrameUpdate.run();
        
        // 2단계: 200ms 후 삭제
        Timer deleteTimer = new Timer(200, e -> {
            ((Timer) e.getSource()).stop();
            
            // fadeLayer 제거 + 실제 블록 삭제
            for (int x = 0; x < GameState.WIDTH; x++) {
                fade[targetY][x] = null;
                board[targetY][x] = null;
            }
            
            if (onFrameUpdate != null)
                onFrameUpdate.run();
            
            Timer completeTimer = new Timer(50, ev -> {
                ((Timer) ev.getSource()).stop();
                if (onComplete != null)
                    onComplete.run();
            });
            completeTimer.setRepeats(false);
            completeTimer.start();
        });
        deleteTimer.setRepeats(false);
        deleteTimer.start();
    }

    /** 중력 적용 */
    public void applyGravityInstantly() {
        if (skipDuringItem)
            return;
        Color[][] board = state.getBoard();
        boolean moved;

        do {
            moved = false;
            for (int y = GameState.HEIGHT - 2; y >= 0; y--) {
                for (int x = 0; x < GameState.WIDTH; x++) {
                    if (board[y][x] != null && board[y + 1][x] == null) {
                        board[y + 1][x] = board[y][x];
                        board[y][x] = null;
                        moved = true;
                    }
                }
            }
        } while (moved);
    }

    /** 
     * 특정 줄이 삭제된 후, 그 위의 블록들만 한 칸씩 내리는 중력
     * - deletedRow: 이미 삭제된 줄의 인덱스
     */
    public void applyGravityFromRow(int deletedRow) {
        if (skipDuringItem)
            return;
            
        Color[][] board = state.getBoard();

        // 삭제된 줄 위쪽의 모든 블록을 한 칸씩 아래로
        for (int y = deletedRow - 1; y >= 0; y--) {
            for (int x = 0; x < GameState.WIDTH; x++) {
                if (board[y][x] != null) {
                    // 아래로 이동 가능한 만큼 이동
                    int targetY = y;
                    while (targetY + 1 < GameState.HEIGHT && board[targetY + 1][x] == null) {
                        targetY++;
                    }
                    
                    if (targetY != y) {
                        board[targetY][x] = board[y][x];
                        board[y][x] = null;
                    }
                }
            }
        }
    }

    public void setSkipDuringItem(boolean skip) {
        this.skipDuringItem = skip;
    }

    public boolean isSkipDuringItem() {
        return skipDuringItem;
    }
}