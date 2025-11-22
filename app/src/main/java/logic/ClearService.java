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
            return 0;
        }

        clearing = true;

        // 애니메이션 매니저: 맨 처음 한 번만 start
        if (animMgr != null) {
            animMgr.tryStart(AnimationManager.AnimationType.LINE_CLEAR);
        }

        int[] totalCleared = { 0 }; // 연쇄로 몇 줄 지웠는지 합계

        clearLinesStep(onFrameUpdate, () -> {
            // 여기까지 왔으면 더 이상 지울 줄 없음
            clearing = false;

            if (animMgr != null) {
                animMgr.finish(AnimationManager.AnimationType.LINE_CLEAR);
            }

            if (onComplete != null) {
                onComplete.run();
            }
        }, totalCleared);

        // 반환값은 이번 연쇄에서 지운 줄 총 개수
        return totalCleared[0];
    }

    /** 연쇄 클리어 한 스텝 (재귀적으로 자기 자신을 다시 부름) */
    private void clearLinesStep(Runnable onFrameUpdate,
            Runnable finalComplete,
            int[] totalCleared) {

        Color[][] board = state.getBoard();
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
            // 더 이상 지울 줄 없으면 연쇄 종료
            finalComplete.run();
            return;
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

            if (finalComplete != null)
                finalComplete.run();
        });
    }

    /** 파티클 효과 애니메이션 */
    public void animateWithParticles(List<Integer> rows, Runnable onFrameUpdate, Runnable onComplete) {
        if (animating)
            return;
        animating = true;

        var board = state.getBoard();
        var pid = state.getPieceId();

        // 1. 파티클 생성 (각 블록마다)
        final int CELL_SIZE = 25;
        for (int row : rows) {
            particleSystem.createLineParticles(row, board, CELL_SIZE, GameState.WIDTH);
        }

        // 2. 블록 즉시 삭제 (잔상 없음)
        for (int row : rows) {
            Arrays.fill(board[row], null);
            Arrays.fill(pid[row], 0); // ← pieceId도 함께 초기화
        }

        // 3. 즉시 화면 갱신 (블록이 사라진 상태를 바로 보여줌)
        if (onFrameUpdate != null)
            onFrameUpdate.run();

        // 4. 파티클 애니메이션 (더 빠르게)
        Timer particleTimer = new Timer(16, null); // ← 12ms → 16ms (60fps)
        final int[] frame = { 0 };
        final int MAX_FRAMES = 12; // ← 20 → 12 (약 200ms)

        particleTimer.addActionListener(e -> {
            frame[0]++;

            // 파티클 업데이트
            particleSystem.update();

            if (onFrameUpdate != null)
                onFrameUpdate.run();

            // 12프레임 또는 파티클이 모두 사라지면 종료
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

    // clearing 상태를 외부에서 제어할 수 있도록 setter 추가
    public void setClearing(boolean clearing) {
        this.clearing = clearing;
        System.out.println("[DEBUG] ClearService.clearing = " + clearing);
    }

    /** 파티클 애니메이션만 실행 (블록은 이미 삭제된 상태) */
    public void animateParticlesOnly(Runnable onFrameUpdate, Runnable onComplete) {
        if (animating) {
            System.out.println("[WARN] animateParticlesOnly() called while already animating");
            return;
        }
        animating = true;

        // 파티클 애니메이션
        Timer particleTimer = new Timer(16, null);
        final int[] frame = { 0 };
        final int MAX_FRAMES = 12;

        particleTimer.addActionListener(e -> {
            frame[0]++;

            // 파티클 업데이트
            particleSystem.update();

            if (onFrameUpdate != null)
                onFrameUpdate.run();

            // 12프레임 또는 파티클이 모두 사라지면 종료
            if (frame[0] >= MAX_FRAMES || particleSystem.getParticles().isEmpty()) {
                ((Timer) e.getSource()).stop();
                particleSystem.clear();
                animating = false;

                // 완료 콜백 실행
                if (onComplete != null) {
                    onComplete.run();
                }
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
        var pid = state.getPieceId();

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

    /** 라인 클리어 시 잔상/테두리 효과 */
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

    /** 단일 줄 클리어 (아이템용) */
    public void animateSingleLineClear(int targetY, Runnable onFrameUpdate, Runnable onComplete) {
        List<Integer> singleRow = List.of(targetY);
        animateFastClear(singleRow, onFrameUpdate, onComplete);
    }

    // =========================
    // 줄 단위 보드 압축 (윗줄이 한 번에 내려오는 효과)
    // =========================
    private void compressBoardByRows() {
        Color[][] board = state.getBoard();
        int[][] pid = state.getPieceId();

        int write = GameState.HEIGHT - 1; // 아래에서부터 쌓을 위치

        // 아래에서 위로 올라가며, 비어있지 않은 줄만 아래로 복사
        for (int read = GameState.HEIGHT - 1; read >= 0; read--) {
            if (!isRowEmpty(board[read])) {
                if (write != read) {
                    for (int x = 0; x < GameState.WIDTH; x++) {
                        board[write][x] = board[read][x];
                        pid[write][x] = pid[read][x];
                        board[read][x] = null;
                        pid[read][x] = 0;
                    }
                }
                write--;
            }
        }

        // 남은 위쪽 줄은 모두 비우기
        for (int y = write; y >= 0; y--) {
            for (int x = 0; x < GameState.WIDTH; x++) {
                board[y][x] = null;
                pid[y][x] = 0;
            }
        }
    }

    // ========================= 
    // "즉시" 중력 (줄 압축 + 칸 단위 중력)  -> 클러스터 잘 안되어서 임시변경
    // =========================
    public void applyGravityInstantly() {
        System.out.println("[DEBUG] applyGravityInstantly() called");

        Color[][] board = state.getBoard();
        int[][] pid = state.getPieceId();

        // 1) 줄 단위 압축: 빈 줄 제거
        compressBoardByRows();

        // 2) 칸 단위 중력: 각 블록을 아래로 떨어뜨림
        boolean moved = true;
        int iterations = 0;
        final int MAX_ITERATIONS = 100; // 무한루프 방지

        while (moved && iterations < MAX_ITERATIONS) {
            moved = false;
            iterations++;

            // 아래에서 위로 스캔하면서 각 칸을 아래로 이동
            for (int y = GameState.HEIGHT - 2; y >= 0; y--) {
                for (int x = 0; x < GameState.WIDTH; x++) {
                    // 현재 칸에 블록이 있고, 바로 아래가 비어있으면
                    if (board[y][x] != null && board[y + 1][x] == null) {
                        // 한 칸 아래로 이동
                        board[y + 1][x] = board[y][x];
                        pid[y + 1][x] = pid[y][x];
                        board[y][x] = null;
                        pid[y][x] = 0;
                        moved = true;
                    }
                }
            }
        }

        System.out.println("[DEBUG] Gravity completed after " + iterations + " iterations");
    }

    // // =========================
    // // "즉시" 중력 (줄 압축 + 클러스터 중력)
    // // =========================
    // public void applyGravityInstantly() {
    //     System.out.println("[DEBUG] applyGravityInstantly() called");
    //     // if (skipDuringItem)
    //     // return;

    //     Color[][] board = state.getBoard();

    //     // 1) 줄 단위 압축: 위 줄이 모두 한 번에 아래로 내려옴
    //     compressBoardByRows();

    //     // 2) 압축 이후 떠 있는 블럭들에 대해, 클러스터 단위 중력을 즉시 반복
    //     while (true) {
    //         List<List<Point>> clusters = findConnectedClusters(board);
    //         clusters.sort((a, b) -> Integer.compare(maxY(b), maxY(a)));

    //         boolean movedAny = false;
    //         for (List<Point> cluster : clusters) {
    //             if (canClusterFallOneStep(cluster, board)) {
    //                 moveClusterDownOneStep(cluster, board);
    //                 movedAny = true;
    //             }
    //         }

    //         if (!movedAny)
    //             break;
    //     }
    // }

    /** 줄 단위 중력 (예전 API 호환용 이름) */
    public void applyLineGravity() {
        applyGravityInstantly();
    }

    // =========================
    // 클러스터 중력 애니메이션 (느리게 떨어지는 연출)
    // =========================
    public void applyClusterGravityAnimated(Runnable onFrameUpdate, Runnable onComplete) {

        // 중력 시작 전에 fadeLayer 완전히 초기화 (잔상 방지)
        Color[][] fade = state.getFadeLayer();
        for (int y = 0; y < GameState.HEIGHT; y++)
            Arrays.fill(fade[y], null);
        if (onFrameUpdate != null)
            onFrameUpdate.run();

        Timer timer = new Timer(50, null); // 느린/무거운 느낌
        timer.addActionListener(e -> {
            Color[][] board = state.getBoard();

            // 매 tick마다 클러스터 다시 계산
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

    // =========================
    // "색 상관 없이" 연결된 클러스터 찾기
    // =========================
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
                            continue; // 색은 상관X, 빈칸만 아니면 같은 덩어리
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

    /** 이 클러스터가 "한 칸 아래"로 전체 이동할 수 있는지 검사 */
    private boolean canClusterFallOneStep(List<Point> cluster, Color[][] board) {
        int h = GameState.HEIGHT;
        Set<Point> set = new HashSet<>(cluster);

        for (Point p : cluster) {
            int x = p.x;
            int y = p.y;

            if (y == h - 1) {
                return false; // 바닥
            }

            Color below = board[y + 1][x];

            if (below == null) {
                continue; // 빈 칸이면 OK
            }

            // 아래 칸이 같은 클러스터에 속하면 OK
            Point belowPoint = new Point(x, y + 1);
            if (!set.contains(belowPoint)) {
                return false; // 다른 블럭에 걸림 → 전체 클러스터 이동 불가
            }
        }

        return true;
    }

    /** 클러스터 전체를 "한 칸 아래"로 이동 (아래쪽부터 처리) */
    private void moveClusterDownOneStep(List<Point> cluster, Color[][] board) {
        // y가 큰(아래쪽) 것부터 처리해야 덮어쓰기 안 꼬임
        cluster.sort((a, b) -> Integer.compare(b.y, a.y));

        int[][] pid = state.getPieceId();

        for (Point p : cluster) {
            int x = p.x;
            int y = p.y;

            Color c = board[y][x];
            if (c == null)
                continue; // 혹시 이미 비어 있으면 스킵

            int id = pid[y][x];

            board[y][x] = null;
            pid[y][x] = 0;

            board[y + 1][x] = c;
            pid[y + 1][x] = id;
        }
    }

    /** 해당 클러스터에서 가장 아래(y 최대값) */
    private int maxY(List<Point> cluster) {
        int max = -1;
        for (Point p : cluster) {
            if (p.y > max)
                max = p.y;
        }
        return max;
    }

    /**
     * 줄 삭제 후, 윗줄들이 "한 칸씩" 내려오는 애니메이션.
     * - 실제 로직: 맨 아래에서 위로 스캔하면서
     * "비어 있는 줄 아래에, 바로 위 줄이 비어있지 않으면" 한 칸 내려보냄.
     * - 이 한 칸 내리는 동작을 Timer로 여러 번 반복해서
     * 전체가 스르르 내려오는 느낌을 만든다.
     */
    private void compressBoardByRowsAnimated(Runnable onFrameUpdate,
            Runnable onComplete) {
        final int TICK_MS = 100; // 줄 내려오는 속도(작을수록 빠름)

        Timer timer = new Timer(TICK_MS, null);
        timer.addActionListener(e -> {
            Color[][] board = state.getBoard();
            int[][] pid = state.getPieceId();
            boolean moved = false;

            // 아래에서 위로 스캔하면서 "비어있는 줄 <- 바로 위 줄" 복사
            for (int y = GameState.HEIGHT - 1; y > 0; y--) {
                if (isRowEmpty(board[y]) && !isRowEmpty(board[y - 1])) {
                    // row (y-1)를 row y로 한 칸 내리고, 위는 비움
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

            // 더 내려올 줄이 없으면 애니메이션 종료
            if (!moved) {
                timer.stop();
                if (onComplete != null)
                    onComplete.run();
            }
        });

        timer.start();
    }

    // =========================
    // 유틸 / 기타 API
    // =========================

    /** 꽉 찬 줄 찾기 */
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
        applyGravityInstantly();
    }

    /** 레거시 단계별 중력 → 클러스터 애니메이션으로 우회 */
    @Deprecated
    public void applyGravityStepwise(Runnable onFrameUpdate, Runnable onComplete) {
        System.out.println("[WARN] applyGravityStepwise() is deprecated! Use applyClusterGravityAnimated().");
        applyClusterGravityAnimated(onFrameUpdate, onComplete);
    }
}