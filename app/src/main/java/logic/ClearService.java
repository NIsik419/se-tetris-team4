package logic;

import java.awt.Color;
import javax.swing.Timer;

import logic.ParticleSystem.Particle;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class ClearService {
    private final GameState state;
    private boolean skipDuringItem = false;
    private boolean clearing = false;
    private boolean animating = false;

    private AnimationManager animMgr;

    private ParticleSystem particleSystem = new ParticleSystem();

    private static final Color FLASH_WHITE = new Color(255, 255, 255, 250);
    private List<Integer> lastClearedRows = new ArrayList<>();

    public ClearService(GameState state) {
        this.state = state;
    }

    /** AnimationManager 설정 */
    public void setAnimationManager(AnimationManager mgr) {
        this.animMgr = mgr;
    }

    public ParticleSystem getParticleSystem() {
        return particleSystem;
    }

    /** 메인 라인 클리어 로직 */
    public int clearLines(Runnable onFrameUpdate, Runnable onComplete) {
        if (clearing) {
            System.out.println("[WARN] clearLines() called while already clearing");
            return 0;
        }

        // 애니메이션 매니저 등록만 (대기 없음)
        if (animMgr != null) {
            animMgr.tryStart(AnimationManager.AnimationType.LINE_CLEAR);
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

        // 빠른 애니메이션 실행 (내부에서 즉시 삭제)
        animateWithParticles(fullRows, onFrameUpdate, () -> {
            applyGravityInstantly();

            if (onFrameUpdate != null)
                onFrameUpdate.run();

            clearing = false;

            if (animMgr != null) {
                animMgr.finish(AnimationManager.AnimationType.LINE_CLEAR);
            }

            System.out.println("[DEBUG] Clear + Gravity completed with particles");

            if (onComplete != null)
                onComplete.run();
        });

        return linesCleared;
    }

    /** 파티클 효과 애니메이션 */
    private void animateWithParticles(List<Integer> rows, Runnable onFrameUpdate, Runnable onComplete) {
        if (animating)
            return;
        animating = true;

        var board = state.getBoard();

        // 1. 파티클 생성 (각 블록마다)
        final int CELL_SIZE = 25; // BoardView의 CELL_SIZE와 동일하게 설정
        for (int row : rows) {
            particleSystem.createLineParticles(row, board, CELL_SIZE, GameState.WIDTH);
        }

        // 2. 블록 즉시 삭제 (잔상 없음)
        for (int row : rows) {
            Arrays.fill(board[row], null);
        }

        animating=false;
        if (onFrameUpdate != null)
            onFrameUpdate.run();

        // 3. 파티클 애니메이션 
        Timer particleTimer = new Timer(8, null);
        final int[] frame = { 0 };
        final int MAX_FRAMES = 5;

        particleTimer.addActionListener(e -> {
            frame[0]++;

            // 파티클 업데이트
            particleSystem.update();

            if (onFrameUpdate != null)
                onFrameUpdate.run();

            // 30프레임 또는 파티클이 모두 사라지면 종료
            if (frame[0] >= MAX_FRAMES || particleSystem.getParticles().isEmpty()) {
                ((Timer) e.getSource()).stop();
                particleSystem.clear();
                animating = false;
                if (onComplete != null)
                    onComplete.run();
            }
        });
        particleTimer.start();
    }


    /** 초고속 클리어 애니메이션 */
    private void animateFastClear(List<Integer> rows, Runnable onFrameUpdate, Runnable onComplete) {
        if (animating)
            return;
        animating = true;

        var board = state.getBoard();
        var fade = state.getFadeLayer();

        // 0단계: 잔상 생성
        applyOutlineEffect(rows);

        if (onFrameUpdate != null)
            onFrameUpdate.run();

        // 즉시 삭제 대신 잠깐 색 유지 -> 페이드 아웃 중에 null 처리
        Timer fadeTimer = new Timer(10, null); // 조금 느리게 (25ms)
        final int[] frame = { 0 };
        final int TOTAL_FRAMES = 4;

        fadeTimer.addActionListener(e -> {
            frame[0]++;
            int alphaFade = 180 - (frame[0] * 180 / TOTAL_FRAMES);
            alphaFade = Math.max(0, alphaFade);

            for (int row : rows) {
                for (int x = 0; x < GameState.WIDTH; x++) {
                    if (fade[row][x] != null) {
                        Color base = fade[row][x];
                        fade[row][x] = new Color(base.getRed(), base.getGreen(), base.getBlue(), alphaFade);
                    }

                }
            }

            if (onFrameUpdate != null)
                onFrameUpdate.run();

            if (frame[0] == TOTAL_FRAMES / 2) {
                // 중간쯤에서 실제 블록 제거
                for (int row : rows) {
                    Arrays.fill(board[row], null);
                }
            }

            if (frame[0] >= TOTAL_FRAMES) {
                ((Timer) e.getSource()).stop();
                for (int row : rows)
                    Arrays.fill(fade[row], null);

                animating = false;
                if (onComplete != null)
                    onComplete.run();
            }
        });
        fadeTimer.start();
    }

    // 잔상효과 이펙트
    private void applyOutlineEffect(List<Integer> rows) {
        Color[][] board = state.getBoard();
        Color[][] fade = state.getFadeLayer();

        for (int row : rows) {
            for (int x = 0; x < GameState.WIDTH; x++) {
                Color base = board[row][x];
                if (base != null) {
                    // 블록 주변이 비었으면 테두리로 간주
                    boolean isEdge = (x == 0 || board[row][x - 1] == null) ||
                            (x == GameState.WIDTH - 1 || board[row][x + 1] == null);

                    // 원래 블록 색상 기반으로 잔상 생성 (화이트 대신)
                    int r = base.getRed();
                    int g = base.getGreen();
                    int b = base.getBlue();

                    if (isEdge) {
                        fade[row][x] = new Color(r, g, b, 180); // 테두리: 좀 더 강하게 남김
                    } else {
                        fade[row][x] = new Color(r, g, b, 60); // 내부: 약한 잔상
                    }
                }
            }
        }
    }

    /** 단일 줄 클리어 (아이템용) */
    public void animateSingleLineClear(int targetY, Runnable onFrameUpdate, Runnable onComplete) {
        List<Integer> singleRow = List.of(targetY);
        animateFastClear(singleRow, onFrameUpdate, onComplete);
    }

    /** 즉시 중력 적용 */
    public void applyGravityInstantly() {
        System.out.println("[DEBUG] applyGravityInstantly() called");
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

    /** 단계별 중력 애니메이션 (더 이상 사용 안 함 - 레거시) */
    @Deprecated
    public void applyGravityStepwise(Runnable onFrameUpdate, Runnable onComplete) {
        System.out.println("[WARN] applyGravityStepwise() is deprecated! Use instant gravity.");
        // 즉시 중력으로 대체
        applyGravityInstantly();
        if (onFrameUpdate != null)
            onFrameUpdate.run();
        if (onComplete != null)
            onComplete.run();
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