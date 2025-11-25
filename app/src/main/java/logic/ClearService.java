package logic;

import java.awt.Color;
import java.awt.Point;
import javax.swing.Timer;
import logic.GameState;

import java.util.ArrayDeque;
import logic.ParticleSystem.Particle;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

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

    public void setAnimationManager(AnimationManager mgr) {
        this.animMgr = mgr;
    }

    public ParticleSystem getParticleSystem() {
        return particleSystem;
    }

    // animateWithParticles도 수정 (플래그 제거)
    public void animateWithParticles(List<Integer> rows, Runnable onFrameUpdate, Runnable onComplete) {
        var board = state.getBoard();
        var pid = state.getPieceId();

        final int CELL_SIZE = 25;
        for (int row : rows) {
            particleSystem.createLineParticles(row, board, CELL_SIZE, GameState.WIDTH);
        }

        for (int row : rows) {
            Arrays.fill(board[row], null);
            Arrays.fill(pid[row], 0);
        }

        if (onFrameUpdate != null)
            onFrameUpdate.run();

        Timer particleTimer = new Timer(16, null);
        final int[] frame = { 0 };
        final int MAX_FRAMES = 12;

        particleTimer.addActionListener(e -> {
            frame[0]++;
            particleSystem.update();

            if (onFrameUpdate != null)
                onFrameUpdate.run();

            if (frame[0] >= MAX_FRAMES || particleSystem.getParticles().isEmpty()) {
                ((Timer) e.getSource()).stop();
                particleSystem.clear();
                if (onComplete != null)
                    onComplete.run();
            }
        });
        particleTimer.start();
    }

    public void setClearing(boolean clearing) {
        this.clearing = clearing;
        System.out.println("[DEBUG] ClearService.clearing = " + clearing);
    }

    // ============================================
    // 논블로킹 파티클 애니메이션 (새로 추가)
    // ============================================
    public void animateParticlesAsync(Runnable onFrameUpdate) {
        if (particleSystem.getParticles().isEmpty()) {
            System.out.println("[ClearService] No particles to animate");
            return;
        }

        System.out.println("[ClearService] Starting async particle animation");

        Timer particleTimer = new Timer(16, null);
        final int[] frame = { 0 };
        final int MAX_FRAMES = 12;

        particleTimer.addActionListener(e -> {
            frame[0]++;
            particleSystem.update();

            if (onFrameUpdate != null)
                onFrameUpdate.run();

            if (frame[0] >= MAX_FRAMES || particleSystem.getParticles().isEmpty()) {
                ((Timer) e.getSource()).stop();
                particleSystem.clear();
                System.out.println("[ClearService] Async particle animation complete");
            }
        });

        particleTimer.start();
    }

    // ============================================
    // 기존 animateParticlesOnly - animating 플래그 제거
    // ============================================
    public void animateParticlesOnly(Runnable onFrameUpdate, Runnable onComplete) {
        // 파티클이 없으면 바로 완료
        if (particleSystem.getParticles().isEmpty()) {
            if (onComplete != null)
                onComplete.run();
            return;
        }

        Timer particleTimer = new Timer(16, null);
        final int[] frame = { 0 };
        final int MAX_FRAMES = 12;

        particleTimer.addActionListener(e -> {
            frame[0]++;
            particleSystem.update();

            if (onFrameUpdate != null)
                onFrameUpdate.run();

            if (frame[0] >= MAX_FRAMES || particleSystem.getParticles().isEmpty()) {
                ((Timer) e.getSource()).stop();
                particleSystem.clear();

                if (onComplete != null) {
                    onComplete.run();
                }
            }
        });
        particleTimer.start();
    }

    private void animateFastClear(List<Integer> rows, Runnable onFrameUpdate, Runnable onComplete) {
        if (animating)
            return;
        animating = true;

        var board = state.getBoard();
        var fade = state.getFadeLayer();
        var pid = state.getPieceId();

        applyOutlineEffect(rows);

        if (onFrameUpdate != null)
            onFrameUpdate.run();

        Timer fadeTimer = new Timer(10, null);
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
                for (int row : rows) {
                    Arrays.fill(board[row], null);
                    Arrays.fill(pid[row], 0);
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

    private void applyOutlineEffect(List<Integer> rows) {
        Color[][] board = state.getBoard();
        Color[][] fade = state.getFadeLayer();

        for (int row : rows) {
            for (int x = 0; x < GameState.WIDTH; x++) {
                Color base = board[row][x];
                if (base != null) {
                    boolean isEdge = (x == 0 || board[row][x - 1] == null) ||
                            (x == GameState.WIDTH - 1 || board[row][x + 1] == null);

                    int r = base.getRed();
                    int g = base.getGreen();
                    int b = base.getBlue();

                    if (isEdge) {
                        fade[row][x] = new Color(r, g, b, 180);
                    } else {
                        fade[row][x] = new Color(r, g, b, 60);
                    }
                }
            }
        }
    }

    public void animateSingleLineClear(int targetY, Runnable onFrameUpdate, Runnable onComplete) {
        List<Integer> singleRow = List.of(targetY);
        animateFastClear(singleRow, onFrameUpdate, onComplete);
    }

    // ============================================
    // 줄 단위 압축 - 수정 버전 (pieceId 제대로 처리)
    // ============================================
    private void compressBoardByRows() {
        Color[][] board = state.getBoard();
        int[][] pid = state.getPieceId();

        // ✅ 임시 배열 사용 (참조 복사 문제 방지)
        Color[][] tempBoard = new Color[GameState.HEIGHT][GameState.WIDTH];
        int[][] tempPid = new int[GameState.HEIGHT][GameState.WIDTH];

        int writeRow = GameState.HEIGHT - 1;

        // 아래에서 위로 스캔하면서 비어있지 않은 줄만 복사
        for (int readRow = GameState.HEIGHT - 1; readRow >= 0; readRow--) {
            if (!isRowEmpty(board[readRow])) {
                for (int x = 0; x < GameState.WIDTH; x++) {
                    tempBoard[writeRow][x] = board[readRow][x];
                    tempPid[writeRow][x] = pid[readRow][x];
                }
                writeRow--;
            }
        }

        // 위쪽 빈 줄 초기화
        for (int y = writeRow; y >= 0; y--) {
            for (int x = 0; x < GameState.WIDTH; x++) {
                tempBoard[y][x] = null;
                tempPid[y][x] = 0;
            }
        }

        // ✅ 원본에 다시 복사
        for (int y = 0; y < GameState.HEIGHT; y++) {
            board[y] = tempBoard[y];
            pid[y] = tempPid[y];
        }
    }

    // ============================================
    // 즉시 중력 - 로직 개선
    // ============================================
    public void applyGravityInstantly() {
        System.out.println("[DEBUG] applyGravityInstantly() called");

        Color[][] board = state.getBoard();
        int[][] pid = state.getPieceId();

        boolean moved = true;
        int iterations = 0;
        final int MAX_ITERATIONS = 100;

        while (moved && iterations < MAX_ITERATIONS) {
            moved = false;
            iterations++;

            List<List<Point>> clusters = findConnectedClusters(board);
            clusters.sort((a, b) -> Integer.compare(maxY(b), maxY(a)));

            for (List<Point> cluster : clusters) {
                if (canClusterFallOneStep(cluster, board)) {
                    moveClusterDownOneStep(cluster, board);
                    moved = true;
                }
            }
        }

        System.out.println("[DEBUG] Gravity completed after " + iterations + " iterations");
    }

    public void applyLineGravity() {
        applyGravityInstantly();
    }

    // ============================================
    // 클러스터 중력 애니메이션
    // ============================================
    public void applyClusterGravityAnimated(Runnable onFrameUpdate, Runnable onComplete) {
        Color[][] fade = state.getFadeLayer();
        for (int y = 0; y < GameState.HEIGHT; y++)
            Arrays.fill(fade[y], null);
        if (onFrameUpdate != null)
            onFrameUpdate.run();

        Timer timer = new Timer(50, null);
        timer.addActionListener(e -> {
            Color[][] board = state.getBoard();

            List<List<Point>> clusters = findConnectedClusters(board);
            clusters.sort((a, b) -> Integer.compare(maxY(b), maxY(a)));

            boolean movedAny = false;
            for (List<Point> cluster : clusters) {
                if (canClusterFallOneStep(cluster, board)) {
                    moveClusterDownOneStep(cluster, board);
                    movedAny = true;
                }
            }

            if (onFrameUpdate != null)
                onFrameUpdate.run();

            if (!movedAny) {
                timer.stop();
                if (onComplete != null)
                    onComplete.run();
            }
        });

        timer.start();
    }

    private List<List<Point>> findConnectedClusters(Color[][] board) {
        int h = GameState.HEIGHT;
        int w = GameState.WIDTH;

        boolean[][] visited = new boolean[h][w];
        List<List<Point>> clusters = new ArrayList<>();

        int[] dx = { 1, -1, 0, 0 };
        int[] dy = { 0, 0, 1, -1 };

        int[][] pid = state.getPieceId();

        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                if (board[y][x] == null || visited[y][x])
                    continue;

                int id = pid[y][x];
                if (id == 0)
                    continue;

                List<Point> cluster = new ArrayList<>();
                Deque<Point> stack = new ArrayDeque<>();
                stack.push(new Point(x, y));
                visited[y][x] = true;

                while (!stack.isEmpty()) {
                    Point p = stack.pop();
                    cluster.add(p);

                    for (int dir = 0; dir < 4; dir++) {
                        int nx = p.x + dx[dir];
                        int ny = p.y + dy[dir];
                        if (nx < 0 || nx >= w || ny < 0 || ny >= h)
                            continue;
                        if (visited[ny][nx])
                            continue;
                        if (board[ny][nx] == null)
                            continue;
                        if (pid[ny][nx] != id)
                            continue;

                        visited[ny][nx] = true;
                        stack.push(new Point(nx, ny));
                    }
                }

                clusters.add(cluster);
            }
        }

        return clusters;
    }

    private boolean canClusterFallOneStep(List<Point> cluster, Color[][] board) {
        int h = GameState.HEIGHT;
        Set<Point> set = new HashSet<>(cluster);

        for (Point p : cluster) {
            int x = p.x;
            int y = p.y;

            if (y == h - 1) {
                return false;
            }

            Color below = board[y + 1][x];

            if (below == null) {
                continue;
            }

            Point belowPoint = new Point(x, y + 1);
            if (!set.contains(belowPoint)) {
                return false;
            }
        }

        return true;
    }

    private void moveClusterDownOneStep(List<Point> cluster, Color[][] board) {
        cluster.sort((a, b) -> Integer.compare(b.y, a.y));

        int[][] pid = state.getPieceId();

        for (Point p : cluster) {
            int x = p.x;
            int y = p.y;

            Color c = board[y][x];
            if (c == null)
                continue;

            int id = pid[y][x];

            board[y][x] = null;
            pid[y][x] = 0;

            board[y + 1][x] = c;
            pid[y + 1][x] = id;
        }
    }

    private int maxY(List<Point> cluster) {
        int max = -1;
        for (Point p : cluster) {
            if (p.y > max)
                max = p.y;
        }
        return max;
    }

    private void compressBoardByRowsAnimated(Runnable onFrameUpdate, Runnable onComplete) {
        final int TICK_MS = 100;

        Timer timer = new Timer(TICK_MS, null);
        timer.addActionListener(e -> {
            Color[][] board = state.getBoard();
            int[][] pid = state.getPieceId();
            boolean moved = false;

            for (int y = GameState.HEIGHT - 1; y > 0; y--) {
                if (isRowEmpty(board[y]) && !isRowEmpty(board[y - 1])) {
                    for (int x = 0; x < GameState.WIDTH; x++) {
                        board[y][x] = board[y - 1][x];
                        pid[y][x] = pid[y - 1][x];
                        board[y - 1][x] = null;
                        pid[y - 1][x] = 0;
                    }
                    moved = true;
                }
            }

            if (onFrameUpdate != null)
                onFrameUpdate.run();

            if (!moved) {
                timer.stop();
                if (onComplete != null)
                    onComplete.run();
            }
        });

        timer.start();
    }

    // ============================================
    // 유틸리티
    // ============================================
    public List<Integer> findFullRows() {
        Color[][] board = state.getBoard();
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

    public int countFullLines() {
        return findFullRows().size();
    }

    private boolean isRowEmpty(Color[] row) {
        for (Color c : row)
            if (c != null)
                return false;
        return true;
    }

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
        applyGravityInstantly();
    }

    @Deprecated
    public void applyGravityStepwise(Runnable onFrameUpdate, Runnable onComplete) {
        System.out.println("[WARN] applyGravityStepwise() is deprecated! Use applyClusterGravityAnimated().");
        applyClusterGravityAnimated(onFrameUpdate, onComplete);
    }

    public int clearLines(Runnable onFrameUpdate, Runnable onComplete) {
        if (clearing) {
            return 0;
        }

        clearing = true;

        if (animMgr != null) {
            animMgr.tryStart(AnimationManager.AnimationType.LINE_CLEAR);
        }

        int[] totalCleared = { 0 };

        clearLinesStep(onFrameUpdate, () -> {
            clearing = false;

            if (animMgr != null) {
                animMgr.finish(AnimationManager.AnimationType.LINE_CLEAR);
            }

            if (onComplete != null) {
                onComplete.run();
            }
        }, totalCleared);

        return totalCleared[0];
    }

    private void clearLinesStep(Runnable onFrameUpdate, Runnable finalComplete, int[] totalCleared) {
        Color[][] board = state.getBoard();
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

        lastClearedRows = new ArrayList<>(fullRows);

        if (fullRows.isEmpty()) {
            finalComplete.run();
            return;
        }

        int linesCleared = fullRows.size();
        System.out.println("[DEBUG] Clearing " + linesCleared + " lines: " + fullRows);

        animateWithParticles(fullRows, onFrameUpdate, () -> {
            applyGravityInstantly();

            if (onFrameUpdate != null)
                onFrameUpdate.run();

            clearing = false;

            if (animMgr != null) {
                animMgr.finish(AnimationManager.AnimationType.LINE_CLEAR);
            }

            System.out.println("[DEBUG] Clear + Gravity completed with particles");

            if (finalComplete != null)
                finalComplete.run();
        });
    }
}