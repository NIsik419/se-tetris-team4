package logic;

import java.awt.Color;
import java.awt.Point;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Deque;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import java.util.Set;
import java.util.function.Consumer;

import javax.swing.SwingUtilities;
import javax.swing.Timer;

import blocks.Block;
import component.BlockBag;
import component.BoardView;
import component.GameConfig;
import component.GameConfig.Difficulty;
import component.SpeedManager;
import component.items.ItemBlock;
import component.GameSettings;

public class BoardLogic {
    private final SoundManager sound = SoundManager.getInstance();
    private BoardView boardView;
    private static final int GRAVITY_ANIMATION_DELAY = 40;
    public static final int WIDTH = GameState.WIDTH;
    public static final int HEIGHT = GameState.HEIGHT;
    private static final int MAX_GARBAGE = 10;
    private static final int ITEM_LINES_INTERVAL = 10; // 아이템 등장 주기 (누적 라인)
    private static final int LINES_PER_LEVEL = 10;
    private int deletedLinesTotal = 0;
    private final boolean[] isGarbageRow = new boolean[HEIGHT];
    private int garbageCount = 0;

    private Runnable pauseCallback;
    private Runnable resumeCallback;
    private Runnable onGameOverCallback;
    private Runnable onGarbageApplied;

    public void setBoardView(BoardView view) {
        this.boardView = view;
    }

    public void setOnGarbageApplied(Runnable callback) {
        this.onGarbageApplied = callback;
    }

    public void setLoopControl(Runnable pause, Runnable resume) {
        this.pauseCallback = pause;
        this.resumeCallback = resume;
    }

    private boolean animatedGravityEnabled = true;

    public void setAnimatedGravityEnabled(boolean enabled) {
        this.animatedGravityEnabled = enabled;
    }

    public boolean isAnimatedGravityEnabled() {
        return animatedGravityEnabled;
    }

    private int comboCount = 0;
    private long lastClearTime = 0;
    private int shakeOffset = 0;
    private boolean lastClearWasTetris = false;

    private Color[][] opponentBoard = new Color[HEIGHT][WIDTH];

    private java.util.function.IntConsumer onLineCleared;
    private Runnable beforeSpawnHook;
    private java.util.function.Consumer<int[]> onLinesClearedWithMasks;
    private java.util.function.IntConsumer onIncomingChanged;
    // 가비지 미리보기용 콜백 (각 줄을 boolean[WIDTH] 로 표현)
    private java.util.function.Consumer<List<boolean[]>> onGarbagePreviewChanged;

    private final boolean[][] recentPlaced = new boolean[HEIGHT][WIDTH];

    private final Queue<Integer> incomingGarbageQueue = new LinkedList<>();

    public int getShakeOffset() {
        return shakeOffset;
    }

    public void setShakeOffset(int offset) {
        this.shakeOffset = offset;
    }

    private final GameState state = new GameState();
    private final BlockBag bag;
    private final Difficulty difficulty;

    private final SpeedManager speedManager;
    private final MovementService move = new MovementService(state);
    private final ClearService clear = new ClearService(state);
    private final BuffManager buff = new BuffManager();
    private final AnimationManager animMgr = new AnimationManager();
    private ItemManager item;

    private final Consumer<Integer> onGameOver;
    private Consumer<List<boolean[]>> onIncomingLinesChanged;
    private Runnable onFrameUpdate;

    private boolean gameOver = false;
    private int score = 0;
    private int clearedLines = 0;
    private boolean nextIsItem = false;
    private boolean itemMode = false;
    private int currentCellSize = 25; // 기본값

    private static final Color GARBAGE_COLOR = new Color(80, 80, 80);

    private final LinkedList<Block> previewQueue = new LinkedList<>();
    private Consumer<List<Block>> onNextQueueUpdate;

    public void setOnIncomingLinesChanged(Consumer<List<boolean[]>> callback) {
        this.onIncomingLinesChanged = callback;
    }

    // GameSettings에서 난이도를 읽어오는 기본 생성자
    public BoardLogic(Consumer<Integer> onGameOver) {
        this(onGameOver, GameSettings.getDifficulty());
    }

    public BoardLogic(Consumer<Integer> onGameOver, GameConfig.Difficulty diff) {
        System.out.println("✅[BoardLogic] difficulty = " + diff);
        this.onGameOver = onGameOver;
        this.difficulty = diff;

        this.bag = new BlockBag(diff);
        this.item = new ItemManager(bag);

        this.speedManager = new SpeedManager(diff);
        clear.setAnimationManager(animMgr);

        refillPreview();
        state.setCurr(previewQueue.removeFirst());
        fireNextQueueChanged();
    }

    public void setOnLinesClearedWithMasks(java.util.function.Consumer<int[]> cb) {
        this.onLinesClearedWithMasks = cb;
    }

    public void setOnIncomingChanged(java.util.function.IntConsumer cb) {
        this.onIncomingChanged = cb;
    }

    // ★ VersusGameManager → VersusPanel → GarbagePreviewPanel 로 보내줄 콜백
    public void setOnGarbagePreviewChanged(java.util.function.Consumer<List<boolean[]>> cb) {
        this.onGarbagePreviewChanged = cb;
    }

    // ★ 현재 incomingGarbageQueue 를 미리보기용 boolean[] 리스트로 변환
    private List<boolean[]> buildGarbagePreviewFromQueue() {
        List<boolean[]> preview = new ArrayList<>();
        for (int mask : incomingGarbageQueue) {
            boolean[] row = new boolean[WIDTH];
            for (int x = 0; x < WIDTH; x++) {
                row[x] = ((mask >> x) & 1) != 0; // 비트 1이면 블록, 0이면 구멍
            }
            preview.add(row);
        }
        return preview;
    }

    // ★ 큐가 바뀔 때마다 한 번씩 호출해 주면 됨
    private void fireGarbagePreviewChanged() {
        System.out.println("[DEBUG] fireGarbagePreviewChanged called, queue size: " + incomingGarbageQueue.size());
        if (onGarbagePreviewChanged != null) {
            List<boolean[]> preview = buildGarbagePreviewFromQueue();
            System.out.println("[DEBUG] Preview list size: " + preview.size());
            onGarbagePreviewChanged.accept(preview);
        } else {
            System.out.println("[DEBUG] onGarbagePreviewChanged is NULL!");
        }
    }

    private void refillPreview() {
        while (previewQueue.size() < 4) {
            previewQueue.add(bag.next());
        }
    }

    public void setItemMode(boolean enabled) {
        this.itemMode = enabled;
    }

    public void setCellSize(int cellSize) {
        this.currentCellSize = cellSize;
    }

    public int getCellSize() {
        return currentCellSize;
    }

    public void addScore(int delta) {
        if (buff.isDoubleScoreActive())
            score += delta * 2;
        else
            score += delta;
    }

    public void moveDown() {
        if (state.getCurr() == null) {
            return;
        }

        if (move.canMove(state.getCurr(), state.getX(), state.getY() + 1)) {
            move.moveDown();
            score++;
        } else {
            fixBlock();
            if (gameOver) {
                return;
            }
        }
    }

    // ============================================
    // fixBlock - 블록 고정 및 라인 클리어 시작
    // ============================================
    private void fixBlock() {
        var b = state.getCurr();
        var board = state.getBoard();
        int id = state.allocatePieceId();
        int[][] pid = state.getPieceId();

        boolean blockOutOfBounds = false;

        // recentPlaced 초기화
        for (int yy = 0; yy < HEIGHT; yy++)
            Arrays.fill(recentPlaced[yy], false);

        for (int j = 0; j < b.height(); j++) {
            for (int i = 0; i < b.width(); i++) {
                if (b.getShape(i, j) == 1) {
                    int bx = state.getX() + i;
                    int by = state.getY() + j;

                    if (by < 0) {
                        blockOutOfBounds = true;
                        continue;
                    }

                    if (bx >= 0 && bx < WIDTH && by >= 0 && by < HEIGHT) {
                        board[by][bx] = b.getColor();
                        recentPlaced[by][bx] = true;
                        pid[by][bx] = id;
                    }
                }
            }
        }

        state.setCurr(null);

        if (blockOutOfBounds) {
            gameOver();
            return;
        }

        if (itemMode && b instanceof ItemBlock ib) {
            ib.activate(this, () -> {
                clearLinesAfterItem(this::spawnNext);
            });
        } else {
            clearLinesAndThen(this::spawnNext);
        }
    }

    // ============================================
    // clearLinesAfterItem - 아이템 전용 (타이머 제거)
    // ============================================
    private void clearLinesAfterItem(Runnable afterClear) {
        var board = state.getBoard();
        var pid = state.getPieceId();

        java.util.List<Integer> clearedRows = new java.util.ArrayList<>();
        for (int y = 0; y < HEIGHT; y++) {
            boolean full = true;
            for (int x = 0; x < WIDTH; x++) {
                if (board[y][x] == null) {
                    full = false;
                    break;
                }
            }
            if (full)
                clearedRows.add(y);
        }

        int lines = clearedRows.size();
        if (lines == 0) {
            comboCount = 0;
            if (afterClear != null)
                afterClear.run();
            return;
        }

        if (pauseCallback != null) {
            pauseCallback.run();
        }

        // ★★★ 공격 계산을 가비지 플래그 업데이트보다 먼저 ★★★
        java.util.List<Integer> nonGarbageRows = new java.util.ArrayList<>();
        for (int y : clearedRows) {
            if (!isGarbageRow[y]) {
                nonGarbageRows.add(y);
            } else {
                System.out.println("[DEBUG] Row " + y + " is garbage, excluding from attack");
            }
        }

        System.out.println("[ITEM] Total cleared: " + clearedRows.size() +
                ", Non-garbage: " + nonGarbageRows.size());

        if (nonGarbageRows.size() >= 2 && onLinesClearedWithMasks != null) {
            int[] masks = buildAttackMasks(nonGarbageRows);
            onLinesClearedWithMasks.accept(masks);
            System.out.println("[ITEM] " + nonGarbageRows.size() + "줄 클리어 (가비지 제외) → 공격 전송");
        } else {
            System.out.println("[ITEM] No attack sent: " + nonGarbageRows.size() + " non-garbage lines");
        }

        // 이제 가비지 플래그 업데이트
        updateGarbageFlagsOnClear(clearedRows);

        final int CELL_SIZE = 25;
        for (int row : clearedRows) {
            clear.getParticleSystem().createLineParticles(row, board, CELL_SIZE, WIDTH);
        }

        for (int row : clearedRows) {
            for (int x = 0; x < WIDTH; x++) {
                board[row][x] = null;
                pid[row][x] = 0;
            }
        }

        recentPlacedInitialize();
        processScoreAndCombo(lines);

        if (onFrameUpdate != null)
            javax.swing.SwingUtilities.invokeLater(onFrameUpdate);

        clear.animateParticlesAsync(
                () -> {
                    if (onFrameUpdate != null)
                        javax.swing.SwingUtilities.invokeLater(onFrameUpdate);
                });

        applySimpleCellGravityAnimated(() -> {
            if (onFrameUpdate != null)
                javax.swing.SwingUtilities.invokeLater(onFrameUpdate);
        }, () -> {
            if (resumeCallback != null) {
                resumeCallback.run();
            }
            checkChainClearImmediate(afterClear);
        });
    }

    // ★ updateGarbageFlagsOnClear() - 로그만 개선
    private void updateGarbageFlagsOnClear(List<Integer> clearedRows) {
        int clearedGarbageCount = 0;

        for (int y : clearedRows) {
            if (y < 0 || y >= HEIGHT)
                continue;

            if (isGarbageRow[y]) {
                garbageCount--;
                clearedGarbageCount++;
                System.out.println("[DEBUG] Garbage row cleared at y=" + y + ", remaining: " + garbageCount);
            }
            isGarbageRow[y] = false;
        }

        System.out.println("[DEBUG] Cleared " + clearedGarbageCount + " garbage rows, " +
                (clearedRows.size() - clearedGarbageCount) + " normal rows");

        if (onIncomingChanged != null) {
            onIncomingChanged.accept(incomingGarbageQueue.size());
        }
        fireGarbagePreviewChanged();
    }

    // 12. 아이템용 라인 체크 & 클리어 메서드
    public void checkAndClearLinesAfterItem(Runnable onComplete) {
        var board = state.getBoard();
        java.util.List<Integer> newFullRows = new java.util.ArrayList<>();

        for (int y = 0; y < HEIGHT; y++) {
            boolean full = true;
            for (int x = 0; x < WIDTH; x++) {
                if (board[y][x] == null) {
                    full = false;
                    break;
                }
            }
            if (full)
                newFullRows.add(y);
        }

        if (!newFullRows.isEmpty()) {
            System.out.println("[Item] Found " + newFullRows.size() + " lines after gravity");
            clearLinesAfterItem(onComplete);
        } else {
            if (onComplete != null) {
                onComplete.run();
            }
        }
    }

    public void applySimpleCellGravity() {
        Color[][] board = state.getBoard();
        int[][] pid = state.getPieceId();

        boolean moved = true;
        int iterations = 0;
        final int MAX_ITERATIONS = 100;

        while (moved && iterations < MAX_ITERATIONS) {
            moved = false;
            iterations++;

            // 아래에서 위로 스캔
            for (int y = HEIGHT - 2; y >= 0; y--) {
                for (int x = 0; x < WIDTH; x++) {
                    if (board[y][x] != null && board[y + 1][x] == null) {
                        // 한 칸 아래로 이동
                        board[y + 1][x] = board[y][x];
                        pid[y + 1][x] = pid[y][x];

                        if (isGarbageRow[y]) {
                            isGarbageRow[y + 1] = true;
                        }

                        board[y][x] = null;
                        pid[y][x] = 0;
                        moved = true;
                    }
                }
            }
        }

        // 가비지 플래그 정리
        for (int y = 0; y < HEIGHT; y++) {
            if (isRowEmpty(board[y])) {
                isGarbageRow[y] = false;
            }
        }

        System.out.println("[DEBUG] Simple cell gravity (item) applied after " + iterations + " iterations");
    }

    private void applySimpleCellGravityAnimated(Runnable onFrameUpdate, Runnable onComplete) {
        System.out.println("[DEBUG] Starting animated simple gravity with effects");

        Timer gravityTimer = new Timer(80, null);

        gravityTimer.addActionListener(e -> {
            Color[][] board = state.getBoard();
            int[][] pid = state.getPieceId();

            boolean moved = false;
            List<Point> landedBlocks = new ArrayList<>();

            for (int y = HEIGHT - 2; y >= 0; y--) {
                for (int x = 0; x < WIDTH; x++) {
                    if (board[y][x] != null && board[y + 1][x] == null) {
                        // 궤적 생성
                        clear.getParticleSystem().createGravityTrailParticle(
                                x, y, board[y][x], currentCellSize);

                        // 먼지 파티클
                        clear.getParticleSystem().createGravityDustParticle(
                                x, y, board[y][x], currentCellSize);

                        board[y + 1][x] = board[y][x];
                        pid[y + 1][x] = pid[y][x];

                        if (isGarbageRow[y]) {
                            isGarbageRow[y + 1] = true;
                        }

                        board[y][x] = null;
                        pid[y][x] = 0;
                        moved = true;

                        // 착지 감지
                        if (y + 2 >= HEIGHT || board[y + 2][x] != null) {
                            landedBlocks.add(new Point(x, y + 1));
                        }
                    }
                }
            }

            // 착지 충격파
            if (!landedBlocks.isEmpty()) {
                clear.getParticleSystem().createClusterLandingImpact(
                        landedBlocks, board, currentCellSize);
            }

            for (int y = 0; y < HEIGHT; y++) {
                if (isRowEmpty(board[y])) {
                    isGarbageRow[y] = false;
                }
            }

            if (onFrameUpdate != null) {
                onFrameUpdate.run();
            }

            if (!moved) {
                ((Timer) e.getSource()).stop();
                System.out.println("[DEBUG] Animated simple gravity complete");

                if (onComplete != null) {
                    onComplete.run();
                }
            }
        });

        gravityTimer.start();
    }

    // ============================================
    // clearLinesAndThen - 일반 라인 클리어 (타이머 제거)
    // ============================================
    private void clearLinesAndThen(Runnable afterClear) {
        var board = state.getBoard();
        var pid = state.getPieceId();

        java.util.List<Integer> clearedRows = new java.util.ArrayList<>();
        for (int y = 0; y < HEIGHT; y++) {
            boolean full = true;
            for (int x = 0; x < WIDTH; x++) {
                if (board[y][x] == null) {
                    full = false;
                    break;
                }
            }
            if (full)
                clearedRows.add(y);
        }

        int lines = clearedRows.size();
        if (lines == 0) {
            comboCount = 0;
            afterClear.run();
            return;
        }

        // 중력 애니메이션 동안 입력 차단
        if (animatedGravityEnabled && pauseCallback != null) {
            pauseCallback.run();
        }

        // ★★★ 중요: 가비지 플래그 업데이트보다 먼저 공격 계산 ★★★
        // 1. 먼저 가비지가 아닌 라인만 필터링 (isGarbageRow 상태 사용)
        java.util.List<Integer> nonGarbageRows = new java.util.ArrayList<>();
        for (int y : clearedRows) {
            if (!isGarbageRow[y]) { // 가비지가 아닌 라인만
                nonGarbageRows.add(y);
            } else {
                System.out.println("[DEBUG] Row " + y + " is garbage, excluding from attack");
            }
        }

        System.out.println("[DEBUG] Total cleared: " + clearedRows.size() +
                ", Non-garbage: " + nonGarbageRows.size());

        // 2. 가비지가 아닌 라인이 2줄 이상일 때만 공격 전송
        if (nonGarbageRows.size() >= 2 && onLinesClearedWithMasks != null) {
            int[] masks = buildAttackMasks(nonGarbageRows);
            onLinesClearedWithMasks.accept(masks);
            System.out.println("[ATTACK] " + nonGarbageRows.size() + "줄 클리어 (가비지 제외) → 공격 전송");
        } else {
            System.out.println("[ATTACK] No attack sent: " + nonGarbageRows.size() + " non-garbage lines (need 2+)");
        }

        // 3. 이제 가비지 플래그 업데이트 (공격 계산 후)
        updateGarbageFlagsOnClear(clearedRows);

        final int CELL_SIZE = 25;
        for (int row : clearedRows) {
            clear.getParticleSystem().createLineParticles(row, board, CELL_SIZE, WIDTH);
        }

        // 다음 턴을 위해 recentPlaced 초기화
        for (int yy = 0; yy < HEIGHT; yy++)
            java.util.Arrays.fill(recentPlaced[yy], false);

        for (int row : clearedRows) {
            for (int x = 0; x < WIDTH; x++) {
                board[row][x] = null;
                pid[row][x] = 0;
            }
        }

        recentPlacedInitialize();
        processScoreAndCombo(lines);

        if (onFrameUpdate != null)
            javax.swing.SwingUtilities.invokeLater(onFrameUpdate);

        clear.animateParticlesAsync(
                () -> {
                    if (onFrameUpdate != null)
                        javax.swing.SwingUtilities.invokeLater(onFrameUpdate);
                });

        // 애니메이션 중력만 실행
        if (animatedGravityEnabled) {
            System.out.println("[DEBUG] Starting ANIMATED gravity (no instant gravity)");
            applyClusterGravityAnimated(() -> {
                if (onFrameUpdate != null)
                    javax.swing.SwingUtilities.invokeLater(onFrameUpdate);
            }, () -> {
                if (resumeCallback != null) {
                    resumeCallback.run();
                }
                checkChainClearImmediate(afterClear);
            });
        } else {
            applyClusterGravityInstant();
            if (onFrameUpdate != null)
                javax.swing.SwingUtilities.invokeLater(onFrameUpdate);
            checkChainClearImmediate(afterClear);
        }
    }

    // ============================================
    // 즉시 연쇄 체크 (타이머 없음)
    // ============================================
    private void checkChainClearImmediate(Runnable afterClear) {
        var board = state.getBoard();
        java.util.List<Integer> newFullRows = new java.util.ArrayList<>();

        for (int y = 0; y < HEIGHT; y++) {
            boolean full = true;
            for (int x = 0; x < WIDTH; x++) {
                if (board[y][x] == null) {
                    full = false;
                    break;
                }
            }
            if (full)
                newFullRows.add(y);
        }

        if (!newFullRows.isEmpty()) {
            System.out.println("[CHAIN] " + newFullRows.size() + " more lines found! (instant)");
            clearLinesAndThen(afterClear);
        } else {
            if (afterClear != null)
                afterClear.run();
        }
    }

    // ============================================
    // 공격 마스크 생성 (recentPlaced와 가비지 제외)
    // ============================================
    private int[] buildAttackMasks(List<Integer> clearedRows) {
        var board = state.getBoard();
        int[] masks = new int[clearedRows.size()];

        for (int i = 0; i < clearedRows.size(); i++) {
            int y = clearedRows.get(i);
            int mask = 0;

            // 이 메서드는 가비지가 아닌 라인만 받아야 함
            if (isGarbageRow[y]) {
                System.err.println("[ERROR] Garbage row " + y + " passed to buildAttackMasks! This should not happen!");
                masks[i] = 0;
                continue;
            }

            for (int x = 0; x < WIDTH; x++) {
                // 방금 고정된 블록은 제외
                if (board[y][x] != null && !recentPlaced[y][x]) {
                    mask |= (1 << x);
                }
            }

            System.out.println("[ATTACK] Row " + y + " mask: " + Integer.toBinaryString(mask) +
                    " (bits set: " + Integer.bitCount(mask) + ")");

            masks[i] = mask;
        }
        return masks;
    }

    // ============================================
    // 점수/콤보 처리 공통 로직
    // ============================================
    private void processScoreAndCombo(int lines) {
        clearedLines += lines;
        // 이전 누적 줄 수 기억
        int prevDeleted = deletedLinesTotal;
        deletedLinesTotal += lines; // 이번에 지운 줄 수 더함

        if (onLineCleared != null)
            onLineCleared.accept(lines);
        addScore(lines * 100);

        if (boardView != null && lines >= 2) {
            boardView.showLineClear(lines);
        }

        playLineClearSound(lines);

        long now = System.currentTimeMillis();
        comboCount = (now - lastClearTime < 3000) ? (comboCount + 1) : 1;
        lastClearTime = now;

        if (onVisualEffect != null) {
            onVisualEffect.accept("lineClear", lines);
        }

        if (comboCount > 1) {
            int comboBonus = comboCount * 50;
            addScore(comboBonus);
            System.out.println("Combo! x" + comboCount + " (+" + comboBonus + ")");

            if (boardView != null) {
                boardView.showCombo(comboBonus);
            }

            if (onVisualEffect != null) {
                onVisualEffect.accept("combo", comboCount);
            }

            playComboSound(comboBonus);
        }

        // Back-to-Back TETRIS 보너스 (4줄 + 바로 또 4줄)
        boolean isTetris = (lines == 4);
        if (isTetris && lastClearWasTetris) {
            int b2bBonus = 200; // 보너스 점수, 필요하면 조정 가능
            addScore(b2bBonus);
        }
        lastClearWasTetris = isTetris;

        int prevStepForSpeed = prevDeleted / LINES_PER_LEVEL;
        int currStepForSpeed = deletedLinesTotal / LINES_PER_LEVEL;

        if (currStepForSpeed > prevStepForSpeed) {
            int levelUps = currStepForSpeed - prevStepForSpeed;
            for (int i = 0; i < levelUps; i++) {
                speedManager.increaseLevel();
                int bonus = speedManager.getLevel() * 100;
                addScore(bonus);
            }

            if (boardView != null) {
                int currentLevel = speedManager.getLevel();
                SwingUtilities.invokeLater(() -> boardView.showSpeedUp(currentLevel));
            }
        }

        if (itemMode) {
            int prevStep = prevDeleted / ITEM_LINES_INTERVAL;
            int currStep = deletedLinesTotal / ITEM_LINES_INTERVAL;

            if (currStep > prevStep) {
                // 1) 프리뷰 큐 길이 확보
                refillPreview();

                // 2) 아이템 블록 생성
                Block itemBlock = item.generateItemBlock();

                // 3) 프리뷰 큐의 "두 번째" 위치에 넣기 (index 1)
                if (previewQueue.size() >= 2) {
                    // 기존 두 번째 블록은 버리고 아이템으로 교체
                    previewQueue.remove(1);
                    previewQueue.add(1, itemBlock);
                } else if (previewQueue.size() == 1) {
                    // 하나밖에 없으면 뒤에 붙이기
                    previewQueue.add(itemBlock);
                } else {
                    // 이론상 잘 안 오겠지만, 큐가 비어 있으면 그냥 추가
                    previewQueue.add(itemBlock);
                }

                // 4) HUD에 프리뷰 변경 알림
                fireNextQueueChanged();
            }
        }
    }

    private void applyClusterGravityInstant() {
        Color[][] board = state.getBoard();
        int[][] pid = state.getPieceId();

        boolean moved = true;
        int iterations = 0;
        final int MAX_ITERATIONS = 100;

        while (moved && iterations < MAX_ITERATIONS) {
            moved = false;
            iterations++;

            // 현재 보드의 모든 클러스터 찾기
            List<List<Point>> clusters = findConnectedClusters(board, pid);

            // 아래쪽 클러스터부터 처리 (맨 아래 y 좌표 기준 정렬)
            clusters.sort((a, b) -> Integer.compare(maxY(b), maxY(a)));

            // 각 클러스터를 한 칸씩 떨어뜨리기
            for (List<Point> cluster : clusters) {
                if (canClusterFallOneStep(cluster, board, pid)) {
                    moveClusterDownOneStep(cluster, board, pid);
                    moved = true;
                }
            }
        }

        System.out.println("[DEBUG] Cluster gravity applied after " + iterations + " iterations");
    }

    // 2. 클러스터 찾기 (같은 pieceId끼리 연결된 블록들)
    private List<List<Point>> findConnectedClusters(Color[][] board, int[][] pid) {
        int h = HEIGHT;
        int w = WIDTH;

        boolean[][] visited = new boolean[h][w];
        List<List<Point>> clusters = new ArrayList<>();

        int[] dx = { 1, -1, 0, 0 };
        int[] dy = { 0, 0, 1, -1 };

        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                if (board[y][x] == null || visited[y][x])
                    continue;

                int id = pid[y][x];
                if (id == 0) // pieceId가 0이면 개별 블록 (가비지 등)
                    continue;

                List<Point> cluster = new ArrayList<>();
                Deque<Point> stack = new ArrayDeque<>();
                stack.push(new Point(x, y));
                visited[y][x] = true;

                // DFS로 같은 pieceId 찾기
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
                        if (pid[ny][nx] != id) // 같은 pieceId만
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

    // 3. 클러스터가 한 칸 떨어질 수 있는지 확인
    private boolean canClusterFallOneStep(List<Point> cluster, Color[][] board, int[][] pid) {
        Set<Point> clusterSet = new HashSet<>(cluster);

        for (Point p : cluster) {
            int x = p.x;
            int y = p.y;

            // 바닥에 닿았으면 못 떨어짐
            if (y == HEIGHT - 1) {
                return false;
            }

            Color below = board[y + 1][x];

            // 아래가 비어있으면 OK
            if (below == null) {
                continue;
            }

            // 아래에 블록이 있는데 같은 클러스터면 OK (자기 자신의 일부)
            Point belowPoint = new Point(x, y + 1);
            if (clusterSet.contains(belowPoint)) {
                continue;
            }

            // 아래에 다른 블록이 있으면 못 떨어짐
            return false;
        }

        return true;
    }

    // 4. 클러스터를 한 칸 아래로 이동
    private void moveClusterDownOneStep(List<Point> cluster, Color[][] board, int[][] pid) {
        // 아래쪽 블록부터 이동 (덮어쓰기 방지)
        cluster.sort((a, b) -> Integer.compare(b.y, a.y));

        for (Point p : cluster) {
            int x = p.x;
            int y = p.y;

            Color c = board[y][x];
            int id = pid[y][x];

            if (c == null)
                continue;

            // 한 칸 아래로 이동
            board[y][x] = null;
            pid[y][x] = 0;

            board[y + 1][x] = c;
            pid[y + 1][x] = id;

            // 가비지 플래그도 이동
            if (isGarbageRow[y]) {
                isGarbageRow[y + 1] = true;
            }
        }
    }

    // 5. 클러스터의 최하단 y 좌표 찾기
    private int maxY(List<Point> cluster) {
        int max = -1;
        for (Point p : cluster) {
            if (p.y > max)
                max = p.y;
        }
        return max;
    }

    // ============================================
    // 연쇄 클리어 체크
    // ============================================
    private void checkChainClear(Runnable afterClear) {
        var board = state.getBoard();
        java.util.List<Integer> newFullRows = new java.util.ArrayList<>();

        for (int y = 0; y < HEIGHT; y++) {
            boolean full = true;
            for (int x = 0; x < WIDTH; x++) {
                if (board[y][x] == null) {
                    full = false;
                    break;
                }
            }
            if (full)
                newFullRows.add(y);
        }

        if (!newFullRows.isEmpty()) {
            System.out.println("[CHAIN] " + newFullRows.size() + " more lines found!");
            clearLinesAndThen(afterClear);
        } else {
            if (afterClear != null)
                afterClear.run();
        }
    }

    private void recentPlacedInitialize() {
        for (int y = 0; y < HEIGHT; y++) {
            Arrays.fill(recentPlaced[y], false);
        }
    }

    private void applyClusterGravityAnimated(Runnable onFrameUpdate, Runnable onComplete) {
        System.out.println("[DEBUG] Starting animated cluster gravity with trail effects");

        Timer gravityTimer = new Timer(80, null);

        gravityTimer.addActionListener(e -> {
            Color[][] board = state.getBoard();
            int[][] pid = state.getPieceId();

            // 현재 떨어질 수 있는 클러스터 찾기
            List<List<Point>> clusters = findConnectedClusters(board, pid);
            clusters.sort((a, b) -> Integer.compare(maxY(b), maxY(a)));

            List<List<Point>> fallingClusters = new ArrayList<>();
            for (List<Point> cluster : clusters) {
                if (canClusterFallOneStep(cluster, board, pid)) {
                    fallingClusters.add(cluster);
                }
            }

            boolean movedAny = false;
            List<Point> allLandedBlocks = new ArrayList<>();

            for (List<Point> cluster : fallingClusters) {
                // 1. 이동 전 궤적 생성
                clear.getParticleSystem().createClusterTrail(cluster, board, currentCellSize);

                // 2. 먼지 파티클
                for (Point p : cluster) {
                    if (board[p.y][p.x] != null) {
                        clear.getParticleSystem().createGravityDustParticle(
                                p.x, p.y, board[p.y][p.x], currentCellSize);
                    }
                }

                // 3. 착지 감지 (다음 이동이 불가능하면 착지)
                moveClusterDownOneStep(cluster, board, pid);

                // 이동 후 위치 확인
                List<Point> movedCluster = new ArrayList<>();
                for (Point p : cluster) {
                    movedCluster.add(new Point(p.x, p.y + 1));
                }

                // 더 이상 떨어질 수 없으면 착지
                if (!canClusterFallOneStep(movedCluster, board, pid)) {
                    allLandedBlocks.addAll(movedCluster);
                }

                movedAny = true;
            }

            // 4. 착지 충격파 효과
            if (!allLandedBlocks.isEmpty()) {
                clear.getParticleSystem().createClusterLandingImpact(
                        allLandedBlocks, board, currentCellSize);
            }

            if (onFrameUpdate != null) {
                onFrameUpdate.run();
            }

            if (!movedAny) {
                ((Timer) e.getSource()).stop();

                Color[][] fade = state.getFadeLayer();
                for (int y = 0; y < HEIGHT; y++) {
                    Arrays.fill(fade[y], null);
                }

                System.out.println("[DEBUG] Animated cluster gravity complete");

                if (onComplete != null) {
                    onComplete.run();
                }
            }
        });

        gravityTimer.start();
    }

    // ============================================
    // 다음 블록 스폰 (가비지 먼저 추가)
    // ============================================
    private void spawnNext() {
        if (beforeSpawnHook != null)
            beforeSpawnHook.run();

        applyIncomingGarbage();

        if (onGarbageApplied != null) {
            onGarbageApplied.run();
        }

        refillPreview();

        // 이제는 무조건 프리뷰 큐의 맨 앞 블록을 사용
        Block next = previewQueue.removeFirst();

        state.setCurr(next);
        state.setPosition(3, 0);

        refillPreview();
        fireNextQueueChanged();

        if (!move.canMove(next, state.getX(), state.getY())) {
            gameOver();
        }
    }

    // ============================================
    // 가비지 라인 추가 (보드를 위로 밀고 맨 아래에 추가)
    // ============================================
    private void applyIncomingGarbage() {
        if (incomingGarbageQueue.isEmpty())
            return;

        var board = state.getBoard();
        int[][] pid = state.getPieceId();

        int available = MAX_GARBAGE - garbageCount; // 가비지 10줄까지
        if (available <= 0) {
            System.out.println("[WARN] Max garbage limit reached, clearing queue");
            incomingGarbageQueue.clear();

            // 큐가 비었으니까 HUD/미리보기도 0으로
            if (onIncomingChanged != null) {
                onIncomingChanged.accept(0);
            }
            fireGarbagePreviewChanged();
            return;
        }

        int addedLines = 0;

        while (!incomingGarbageQueue.isEmpty() && addedLines < available) {
            int mask = incomingGarbageQueue.poll();

            System.out.println("[DEBUG] Adding garbage line with mask: " + Integer.toBinaryString(mask));

            // 보드 전체를 한 칸 위로 밀기
            for (int y = 0; y < HEIGHT - 1; y++) {
                for (int x = 0; x < WIDTH; x++) {
                    board[y][x] = board[y + 1][x];
                    pid[y][x] = pid[y + 1][x];
                }
                isGarbageRow[y] = isGarbageRow[y + 1];
            }

            // 맨 아래 줄에 가비지 추가
            int garbagePid = state.allocatePieceId();
            for (int x = 0; x < WIDTH; x++) {
                boolean filled = ((mask >> x) & 1) != 0;
                if (filled) {
                    board[HEIGHT - 1][x] = GARBAGE_COLOR;
                    pid[HEIGHT - 1][x] = garbagePid;
                } else {
                    board[HEIGHT - 1][x] = null;
                    pid[HEIGHT - 1][x] = 0;
                }
            }
            isGarbageRow[HEIGHT - 1] = true;

            addedLines++;
            garbageCount++;
        }
        System.out.println(
                "[DEBUG] Garbage applied: " + addedLines + " lines, remaining queue: " + incomingGarbageQueue.size());

        // ★ while 끝난 뒤, 남은 큐 길이 + 미리보기 한 번에 갱신
        if (onIncomingChanged != null) {
            onIncomingChanged.accept(incomingGarbageQueue.size());
        }
        fireGarbagePreviewChanged();

        System.out.println("[DEBUG] Garbage applied: " + addedLines + " lines, total garbage: " + garbageCount);
    }

    // ============================================
    // addGarbageMasks 수정 (진동 효과 추가)
    // ============================================
    public void addGarbageMasks(int[] masks) {
        if (masks == null || masks.length == 0)
            return;

        for (int mask : masks) {
            incomingGarbageQueue.offer(mask);
        }

        System.out.println(
                "[DEBUG] Enqueued " + masks.length + " garbage masks, total pending: " + incomingGarbageQueue.size());

        if (onIncomingChanged != null) {
            onIncomingChanged.accept(incomingGarbageQueue.size());
        }
        fireGarbagePreviewChanged();

        // 진동 효과 트리거 (라인 수에 비례)
        triggerShakeEffect(masks.length);

        // if (onIncomingLinesChanged != null) {
        // onIncomingLinesChanged.accept(convertMasksToPreview(incomingGarbageQueue));
        // }

        // 사운드 추가
        if (masks.length >= 3) {
            sound.play(SoundManager.Sound.GAME_OVER, 0.2f); // 큰 공격용 임시 사운드
        } else {
            sound.play(SoundManager.Sound.ROTATE, 0.3f); // 작은 공격용 임시 사운드
        }

        System.out.println(
                "[DEBUG] Enqueued " + masks.length + " garbage masks, total pending: " + incomingGarbageQueue.size());
    }

    
    private boolean isRowEmpty(Color[] row) {
        for (Color c : row)
            if (c != null)
                return false;
        return true;
    }

    // === 이동 입력 ===
    public void moveLeft() {
        if (state.getCurr() == null)
            return;
        if (move.canMove(state.getCurr(), state.getX() - 1, state.getY()))
            move.moveLeft();
        sound.play(SoundManager.Sound.MOVE, 0.2f);
    }

    public void moveRight() {
        if (state.getCurr() == null)
            return;
        if (move.canMove(state.getCurr(), state.getX() + 1, state.getY()))
            move.moveRight();
        sound.play(SoundManager.Sound.MOVE, 0.2f);
    }

    public void rotateBlock() {
        if (state.getCurr() == null)
            return;
        Block backup = state.getCurr().clone();
        state.getCurr().rotate();
        if (!move.canMove(state.getCurr(), state.getX(), state.getY()))
            state.setCurr(backup);
        else
            sound.play(SoundManager.Sound.ROTATE, 0.3f);
    }

    public void hardDrop() {
        if (state.getCurr() == null)
            return;

        Block curr = state.getCurr();
        int startY = state.getY();

        // 하드드롭으로 이동
        while (move.canMove(curr, state.getX(), state.getY() + 1)) {
            move.moveDown();
            score += 2;
        }

        int endY = state.getY();

        // 동적 셀 크기 사용
        int cellSize = currentCellSize;

        // 블록의 최소/최대 X 좌표 찾기 (전체 너비 계산)
        int minX = curr.width();
        int maxX = -1;

        for (int j = 0; j < curr.height(); j++) {
            for (int i = 0; i < curr.width(); i++) {
                if (curr.getShape(i, j) == 1) {
                    minX = Math.min(minX, i);
                    maxX = Math.max(maxX, i);
                }
            }
        }

        // 블록 전체를 하나의 광선으로 생성
        if (minX <= maxX) {
            int beamStartX = state.getX() + minX;
            int beamWidth = (maxX - minX + 1);

            clear.getParticleSystem().createHardDropBeamWide(
                    beamStartX,
                    beamWidth,
                    startY,
                    endY,
                    curr.getColor(),
                    cellSize);
        }

        sound.play(SoundManager.Sound.HARD_DROP, 0.2f);
        moveDown();
    }

    // === Getter ===
    public Color[][] getBoard() {
        return state.getBoard();
    }

    public Block getCurr() {
        return state.getCurr();
    }

    public int getX() {
        return state.getX();
    }

    public int getY() {
        return state.getY();
    }

    public int getScore() {
        return score;
    }

    public int getLevel() {
        return speedManager.getLevel();
    }

    public int getLinesCleared() {
        return clearedLines;
    }

    public boolean isGameOver() {
        return gameOver;
    }

    public void setOnFrameUpdate(Runnable r) {
        this.onFrameUpdate = r;
    }

    public Runnable getOnFrameUpdate() {
        return onFrameUpdate;
    }

    public int getDropInterval() {
        return buff.isSlowed()
                ? (int) (speedManager.getDropInterval() * 1.5)
                : speedManager.getDropInterval();
    }

    public BuffManager getBuffManager() {
        return buff;
    }

    public ClearService getClearService() {
        return clear;
    }

    public boolean isItemMode() {
        return itemMode;
    }

    public GameState getState() {
        return state;
    }

    public Color[][] getFadeLayer() {
        return state.getFadeLayer();
    }

    public AnimationManager getAnimationManager() {
        return animMgr;
    }

    public List<Block> getNextBlocks() {
        return previewQueue.size() > 1
                ? new ArrayList<>(previewQueue.subList(0, Math.min(3, previewQueue.size())))
                : List.of();
    }

    public void setOnNextQueueUpdate(Consumer<List<Block>> cb) {
        this.onNextQueueUpdate = cb;
    }

    private void fireNextQueueChanged() {
        if (onNextQueueUpdate != null) {
            onNextQueueUpdate.accept(List.copyOf(previewQueue));
        }
    }

    public void setOnLineCleared(java.util.function.IntConsumer c) {
        this.onLineCleared = c;
    }

    public void setBeforeSpawnHook(Runnable r) {
        this.beforeSpawnHook = r;
    }

    public void updateOpponentBoard(Color[][] newBoard) {
        this.opponentBoard = newBoard;
    }

    public void setBoard(Color[][] newBoard) {
        Color[][] board = state.getBoard();
        for (int y = 0; y < HEIGHT && y < newBoard.length; y++) {
            for (int x = 0; x < WIDTH && x < newBoard[y].length; x++) {
                board[y][x] = newBoard[y][x];
            }
        }
    }

    public void onOpponentGameOver() {
        System.out.println("[INFO] Opponent Game Over - YOU WIN!");
        sound.play(SoundManager.Sound.VICTORY);
        if (pauseCallback != null)
            pauseCallback.run();
    }

    public void setOnGameOverCallback(Runnable r) {
        this.onGameOverCallback = r;
    }

    private void gameOver() {
        if (this.gameOver) {
            return;
        }
        this.gameOver = true;
        sound.play(SoundManager.Sound.GAME_OVER, 0.4f); // 추가
        System.out.println("[GAME OVER] Your Score: " + score);

        state.setCurr(null);
        if (onGameOverCallback != null)
            onGameOverCallback.run();
        if (onGameOver != null)
            onGameOver.accept(score);
    }

    public void debugSetNextItem(Block itemBlock) {
        try {
            var field = bag.getClass().getDeclaredField("nextBlocks");
            field.setAccessible(true);
            @SuppressWarnings("unchecked")
            Queue<Block> queue = (Queue<Block>) field.get(bag);

            if (!queue.isEmpty())
                queue.poll();
            queue.add(itemBlock);

            nextIsItem = false;
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    public void reset() {
        Color[][] board = state.getBoard();
        for (int y = 0; y < HEIGHT; y++) {
            Arrays.fill(board[y], null);
        }

        Color[][] fade = state.getFadeLayer();
        if (fade != null) {
            for (int y = 0; y < HEIGHT; y++) {
                Arrays.fill(fade[y], null);
            }
        }

        state.reset();
        state.setCurr(null);
        state.setPosition(3, 0);
        score = 0;
        clearedLines = 0;
        deletedLinesTotal = 0;
        comboCount = 0;
        gameOver = false;
        nextIsItem = false;

        incomingGarbageQueue.clear();
        recentPlacedInitialize();
        Arrays.fill(isGarbageRow, false);
        garbageCount = 0;

        if (onIncomingChanged != null) {
            onIncomingChanged.accept(0);
        }
        fireGarbagePreviewChanged();

        previewQueue.clear();
        bag.reset();
        refillPreview();
        state.setCurr(previewQueue.removeFirst());
        fireNextQueueChanged();

        speedManager.resetLevel();
        lastClearWasTetris = false;

        SwingUtilities.invokeLater(() -> {
            if (onFrameUpdate != null)
                onFrameUpdate.run();
        });

        if (state.getCurr() == null && !previewQueue.isEmpty()) {
            Block next = previewQueue.removeFirst();
            state.setCurr(next);
            state.setPosition(3, 0);
        }

        System.out.println("[RESET] BoardLogic reset complete.");
    }

    public boolean isLineClearing() {
        return clear != null && clear.isClearing();
    }

    // ============================================
    // 라인 클리어 사운드
    // ============================================
    private void playLineClearSound(int lines) {
        switch (lines) {
            case 1:
                sound.play(SoundManager.Sound.LINE_CLEAR_1, 0.3f);
                break;
            case 2:
                sound.play(SoundManager.Sound.LINE_CLEAR_2, 0.35f);
                break;
            case 3:
                sound.play(SoundManager.Sound.LINE_CLEAR_3, 0.4f);
                break;
            case 4:
            default:
                sound.play(SoundManager.Sound.LINE_CLEAR_4, 0.4f);
                break;
        }
    }

    // ============================================
    // 콤보 사운드
    // ============================================
    private void playComboSound(int combo) {
        if (combo >= 5) {
            sound.play(SoundManager.Sound.COMBO_5, 0.4f);
        } else if (combo >= 3) {
            sound.play(SoundManager.Sound.COMBO_3, 0.4f);
        } else if (combo >= 2) {
            sound.play(SoundManager.Sound.COMBO_2, 0.4f);
        }
    }

    // ============================================
    // 가비지 수신 시 화면 진동 트리거
    // ============================================
    private void triggerShakeEffect(int intensity) {
        if (onFrameUpdate != null) {
            // 진동 강도에 따라 흔들림 설정
            int shakeIntensity = Math.min(intensity * 2, 10); // 최대 10픽셀

            new Thread(() -> {
                try {
                    for (int i = 0; i < 8; i++) { // 8프레임 동안 진동
                        int offset = (i % 2 == 0) ? shakeIntensity : -shakeIntensity;
                        shakeOffset = offset * (8 - i) / 8; // 점점 약해짐

                        SwingUtilities.invokeLater(onFrameUpdate);
                        Thread.sleep(30);
                    }
                    shakeOffset = 0;
                    SwingUtilities.invokeLater(onFrameUpdate);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }).start();
        }
    }

    public void setScore(int score) {
        this.score = score;
    }

    private java.util.function.BiConsumer<String, Integer> onVisualEffect;

    public void setOnVisualEffect(java.util.function.BiConsumer<String, Integer> callback) {
        this.onVisualEffect = callback;
    }

    // 변환 헬퍼 메서드
    private List<boolean[]> convertMasksToPreview(List<Integer> masks) {
        List<boolean[]> preview = new ArrayList<>();
        for (int mask : masks) {
            boolean[] line = new boolean[WIDTH];
            for (int x = 0; x < WIDTH; x++) {
                line[x] = (mask & (1 << x)) != 0;
            }
            preview.add(line);
        }
        return preview;
    }

    // =====================================
    // ======= TESTING HELPERS =============
    // =====================================

    /** JUnit에서 incomingGarbageQueue 크기를 검사하기 위한 getter */
    public int getIncomingQueueSize() {
        return incomingGarbageQueue.size();
    }

    /** private applyIncomingGarbage() 테스트용 wrapper */
    public void testApplyIncomingGarbage() {
        applyIncomingGarbage();
    }

    /** recentPlaced 배열 접근용 (공격 마스크 테스트에 필요) */
    public boolean[][] getRecentPlacedForTest() {
        return recentPlaced;
    }

}