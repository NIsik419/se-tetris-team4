package logic;

import java.awt.Color;
import javax.swing.Timer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class ClearService {
    private final GameState state;
    private boolean skipDuringItem = false;
    private boolean clearing = false;
    private boolean animating = false;

    private static final Color FLASH_WHITE = new Color(255, 255, 255, 250);
    private List<Integer> lastClearedRows = new ArrayList<>();

    public ClearService(GameState state) {
        this.state = state;
    }

    /** 메인 라인 클리어 로직 */
    public int clearLines(Runnable onFrameUpdate, Runnable onComplete) {
        if (clearing) {
            System.out.println("[WARN] clearLines() called while already clearing");
            return 0;
        }
        clearing = true;

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

        lastClearedRows = new ArrayList<>(fullRows);

        if (fullRows.isEmpty()) {
            clearing = false;
            if (onComplete != null)
                onComplete.run();
            return 0;
        }

        int linesCleared = fullRows.size();
        System.out.println("[DEBUG] Clearing " + linesCleared + " lines: " + fullRows);

        // 빠른 애니메이션 실행
        animateFastClear(fullRows, onFrameUpdate, () -> {
            // 실제 삭제
            for (int row : fullRows) {
                Arrays.fill(board[row], null);
            }
            
            // 중력 적용
            applyGravityInstantly();
            
            // 화면 갱신
            if (onFrameUpdate != null)
                onFrameUpdate.run();

            clearing = false;
            System.out.println("[DEBUG] Clear animation completed");

            // 완료 콜백
            if (onComplete != null)
                onComplete.run();
        });

        return linesCleared;
    }

    /** 빠른 클리어 애니메이션 (200ms 이내) */
    private void animateFastClear(List<Integer> rows, Runnable onFrameUpdate, Runnable onComplete) {
        if (animating) {
            System.out.println("[WARN] Animation already running");
            return;
        }
        animating = true;

        var fade = state.getFadeLayer();
        System.out.println("[DEBUG] Animation started on EDT: " + javax.swing.SwingUtilities.isEventDispatchThread());

        // 1단계: 화이트 플래시 (즉시)
        for (int row : rows) {
            for (int x = 0; x < GameState.WIDTH; x++) {
                fade[row][x] = FLASH_WHITE;
            }
        }
        
        // 디버그: fadeLayer 설정 확인
        System.out.println("[DEBUG] fadeLayer[" + rows.get(0) + "][0] = " + fade[rows.get(0)][0]);
        
        if (onFrameUpdate != null) {
            onFrameUpdate.run();
            System.out.println("[DEBUG] Frame update called (flash)");
        }
        
       
        Timer fadeTimer = new Timer(10, null);
        final int[] frame = { 0 };
        final int TOTAL_FRAMES = 1;
        
        fadeTimer.addActionListener(e -> {
            frame[0]++;
            
            // 알파값 점진적 감소
            int alpha = 250 - (frame[0] * 250 / TOTAL_FRAMES);
            alpha = Math.max(0, alpha);
            
            for (int row : rows) {
                for (int x = 0; x < GameState.WIDTH; x++) {
                    if (alpha > 0) {
                        fade[row][x] = new Color(255, 255, 255, alpha);
                    } else {
                        fade[row][x] = null;
                    }
                }
            }
            
            if (onFrameUpdate != null) {
                onFrameUpdate.run();
            }
            
            // 애니메이션 완료
            if (frame[0] >= TOTAL_FRAMES) {
                ((Timer) e.getSource()).stop();
                
                // fadeLayer 완전 클리어
                for (int row : rows) {
                    Arrays.fill(fade[row], null);
                }
                
                animating = false;
                System.out.println("[DEBUG] Fade animation completed");
                
                if (onComplete != null) {
                    onComplete.run();
                }
            }
        });
        
        fadeTimer.setRepeats(true);
        fadeTimer.start();
    }


    /** 단일 줄 클리어 (아이템용) */
    public void animateSingleLineClear(int targetY, Runnable onFrameUpdate, Runnable onComplete) {
        List<Integer> singleRow = List.of(targetY);
        animateFastClear(singleRow, onFrameUpdate, onComplete);
    }

    /** 즉시 중력 적용 */
    public void applyGravityInstantly() {
        if (skipDuringItem)
            return;

        Color[][] board = state.getBoard();

        // 아래부터 위로 스캔하며 빈 줄 채우기
        for (int y = GameState.HEIGHT - 1; y > 0; y--) {
            if (isRowEmpty(board[y])) {
                // 위에서 비어있지 않은 줄 찾기
                int above = y - 1;
                while (above >= 0 && isRowEmpty(board[above])) {
                    above--;
                }

                if (above >= 0) {
                    // 한 줄씩 내리기
                    for (int x = 0; x < GameState.WIDTH; x++) {
                        board[y][x] = board[above][x];
                        board[above][x] = null;
                    }
                    y++; // 같은 y를 다시 체크
                }
            }
        }
    }

    /** 라인별 중력 */
    public void applyLineGravity() {
        if (skipDuringItem)
            return;
        applyGravityInstantly();
    }

    /** 단계별 중력 애니메이션 */
    public void applyGravityStepwise(Runnable onFrameUpdate, Runnable onComplete) {
        new Thread(() -> {
            try {
                Color[][] board = state.getBoard();
                Color[][] fade = state.getFadeLayer();
                boolean moved = true;

                while (moved) {
                    moved = false;

                    for (int y = 0; y < GameState.HEIGHT; y++)
                        Arrays.fill(fade[y], null);

                    for (int y = GameState.HEIGHT - 2; y >= 0; y--) {
                        for (int x = 0; x < GameState.WIDTH; x++) {
                            if (board[y][x] != null && board[y + 1][x] == null) {
                                fade[y + 1][x] = new Color(
                                    board[y][x].getRed(),
                                    board[y][x].getGreen(),
                                    board[y][x].getBlue(), 150
                                );

                                board[y + 1][x] = board[y][x];
                                board[y][x] = null;
                                moved = true;
                            }
                        }
                    }

                    if (onFrameUpdate != null)
                        onFrameUpdate.run();

                    Thread.sleep(40);
                }

                for (int y = 0; y < GameState.HEIGHT; y++)
                    Arrays.fill(fade[y], null);

                if (onFrameUpdate != null)
                    onFrameUpdate.run();

                if (onComplete != null)
                    onComplete.run();

            } catch (InterruptedException ignored) {
            }
        }).start();
    }

    /** 꽉 찬 줄 찾기 */
    public List<Integer> findFullRows() {
        var board = state.getBoard();
        List<Integer> fullRows = new ArrayList<>();

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
        return fullRows;
    }

    /** 꽉 찬 줄 개수 세기 */
    public int countFullLines() {
        return findFullRows().size();
    }

    /** 한 줄이 비어있는지 검사 */
    private boolean isRowEmpty(Color[] row) {
        for (Color c : row)
            if (c != null)
                return false;
        return true;
    }

    /** 폭발 효과 (ColorBomb 등) */
    public void playExplosionEffect(List<Integer> rows, Runnable onFrameUpdate, Runnable onComplete) {
        animateFastClear(rows, onFrameUpdate, onComplete);
    }

    // === Getters & Setters ===
    
    public void setSkipDuringItem(boolean skip) {
        this.skipDuringItem = skip;
    }

    public boolean isSkipDuringItem() {
        return skipDuringItem;
    }

    public List<Integer> getLastClearedRows() {
        return lastClearedRows;
    }

    public boolean isClearing() {
        return clearing;
    }

    @Deprecated
    public void applyGravityFromRow(int deletedRow) {
        applyLineGravity();
    }
}