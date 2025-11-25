package logic;

import java.awt.Color;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import java.util.function.Consumer;

import javax.swing.SwingUtilities;

import blocks.Block;
import component.BlockBag;
import component.GameConfig;
import component.GameConfig.Difficulty;
import component.SpeedManager;
import component.items.ItemBlock;

public class BoardLogic {
    public static final int WIDTH = GameState.WIDTH;
    public static final int HEIGHT = GameState.HEIGHT;
    private static final int MAX_GARBAGE = 10;
    private final boolean[] isGarbageRow = new boolean[HEIGHT];
    private int garbageCount = 0;

    private Runnable pauseCallback;
    private Runnable resumeCallback;
    private Runnable onGameOverCallback;
    private Runnable onGarbageApplied;

    public void setOnGarbageApplied(Runnable callback) {
        this.onGarbageApplied = callback;
    }

    public void setLoopControl(Runnable pause, Runnable resume) {
        this.pauseCallback = pause;
        this.resumeCallback = resume;
    }

    private int comboCount = 0;
    private long lastClearTime = 0;
    private int shakeOffset = 0;

    private Color[][] opponentBoard = new Color[HEIGHT][WIDTH];

    private java.util.function.IntConsumer onLineCleared;
    private Runnable beforeSpawnHook;
    private java.util.function.Consumer<int[]> onLinesClearedWithMasks;
    private java.util.function.IntConsumer onIncomingChanged;

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

    private final SpeedManager speedManager = new SpeedManager();
    private final MovementService move = new MovementService(state);
    private final ClearService clear = new ClearService(state);
    private final BuffManager buff = new BuffManager();
    private final AnimationManager animMgr = new AnimationManager();
    private ItemManager item;

    private final Consumer<Integer> onGameOver;
    private Runnable onFrameUpdate;

    private boolean gameOver = false;
    private int score = 0;
    private int clearedLines = 0;
    private int deletedLinesTotal = 0;
    private boolean nextIsItem = false;
    private boolean itemMode = false;

    private static final Color GARBAGE_COLOR = new Color(80, 80, 80);

    private final LinkedList<Block> previewQueue = new LinkedList<>();
    private Consumer<List<Block>> onNextQueueUpdate;

    public BoardLogic(Consumer<Integer> onGameOver) {
        this(onGameOver, GameConfig.Difficulty.NORMAL);
    }

    public BoardLogic(Consumer<Integer> onGameOver, GameConfig.Difficulty diff) {
        this.onGameOver = onGameOver;
        this.difficulty = diff;

        this.bag = new BlockBag(diff);
        this.item = new ItemManager(bag);

        speedManager.setDifficulty(diff);
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

    private void refillPreview() {
        while (previewQueue.size() < 4) {
            previewQueue.add(bag.next());
        }
    }

    public void setItemMode(boolean enabled) {
        this.itemMode = enabled;
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
    // clearLinesAfterItem - 아이템 전용 (중력 없이 줄만 체크)
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

        System.out.println("[DEBUG clearLinesAfterItem] Clearing " + lines + " lines");

        updateGarbageFlagsOnClear(clearedRows);

        // 2줄 이상만 공격 전송
        if (lines >= 2 && onLinesClearedWithMasks != null) {
            int[] masks = buildAttackMasks(clearedRows);
            onLinesClearedWithMasks.accept(masks);
            System.out.println("[ATTACK] " + lines + "줄 클리어 → " + lines + "줄 공격 전송");
        }

        // 파티클 생성
        final int CELL_SIZE = 25;
        for (int row : clearedRows) {
            clear.getParticleSystem().createLineParticles(row, board, CELL_SIZE, WIDTH);
        }

        // 블록 삭제
        for (int row : clearedRows) {
            for (int x = 0; x < WIDTH; x++) {
                board[row][x] = null;
                pid[row][x] = 0;
            }
        }

        recentPlacedInitialize();

        // 점수/콤보 처리
        processScoreAndCombo(lines);

        // ✅ 줄 중력 적용 (줄 단위로만 내림)
        applyLineGravityOnly();

        if (onFrameUpdate != null)
            javax.swing.SwingUtilities.invokeLater(onFrameUpdate);

        clear.animateParticlesOnly(
                () -> {
                    if (onFrameUpdate != null)
                        javax.swing.SwingUtilities.invokeLater(onFrameUpdate);
                },
                null);

        checkChainClear(afterClear);
    }

    // ============================================
    // clearLinesAndThen - 일반 라인 클리어 (줄 중력)
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

        System.out.println("[DEBUG clearLinesAndThen] Full rows: " + clearedRows);

        int lines = clearedRows.size();
        if (lines == 0) {
            comboCount = 0;
            afterClear.run();
            return;
        }

        updateGarbageFlagsOnClear(clearedRows);

        // 2줄 이상만 공격 전송
        if (lines >= 2 && onLinesClearedWithMasks != null) {
            int[] masks = buildAttackMasks(clearedRows);
            onLinesClearedWithMasks.accept(masks);
            System.out.println("[ATTACK] " + lines + "줄 클리어 → " + lines + "줄 공격 전송");
        }

        // 파티클 생성
        final int CELL_SIZE = 25;
        for (int row : clearedRows) {
            clear.getParticleSystem().createLineParticles(row, board, CELL_SIZE, WIDTH);
        }

        // 블록 삭제
        for (int row : clearedRows) {
            for (int x = 0; x < WIDTH; x++) {
                board[row][x] = null;
                pid[row][x] = 0;
            }
        }

        recentPlacedInitialize();

        // 점수/콤보 처리
        processScoreAndCombo(lines);

        // ✅ 줄 중력 적용 (줄 단위로만 내림)
        applyLineGravityOnly();

        if (onFrameUpdate != null)
            javax.swing.SwingUtilities.invokeLater(onFrameUpdate);

        clear.animateParticlesOnly(
                () -> {
                    if (onFrameUpdate != null)
                        javax.swing.SwingUtilities.invokeLater(onFrameUpdate);
                },
                null);

        // ✅ 약간의 지연 후 연쇄 체크
        javax.swing.Timer delayTimer = new javax.swing.Timer(100, e -> {
            ((javax.swing.Timer) e.getSource()).stop();
            checkChainClear(afterClear);
        });
        delayTimer.setRepeats(false);
        delayTimer.start();
    }

    // ============================================
    // 공격 마스크 생성 (recentPlaced 제외)
    // ============================================
    private int[] buildAttackMasks(List<Integer> clearedRows) {
        var board = state.getBoard();
        int[] masks = new int[clearedRows.size()];
        
        for (int i = 0; i < clearedRows.size(); i++) {
            int y = clearedRows.get(i);
            int mask = 0;
            for (int x = 0; x < WIDTH; x++) {
                // ✅ 방금 고정된 블록은 제외
                if (board[y][x] != null && !recentPlaced[y][x]) {
                    mask |= (1 << x);
                }
            }
            masks[i] = mask;
        }
        return masks;
    }

    // ============================================
    // 점수/콤보 처리 공통 로직
    // ============================================
    private void processScoreAndCombo(int lines) {
        clearedLines += lines;
        deletedLinesTotal += lines;

        if (onLineCleared != null)
            onLineCleared.accept(lines);
        addScore(lines * 100);

        long now = System.currentTimeMillis();
        comboCount = (now - lastClearTime < 3000) ? (comboCount + 1) : 1;
        lastClearTime = now;

        if (comboCount > 1) {
            int comboBonus = comboCount * 50;
            addScore(comboBonus);
            System.out.println("Combo! x" + comboCount + " (+" + comboBonus + ")");
        }

        if (clearedLines % 10 == 0) {
            speedManager.increaseLevel();
        }
        if (itemMode && deletedLinesTotal > 0 && deletedLinesTotal % 2 == 0) {
            nextIsItem = true;
        }
    }

    // ============================================
    // 줄 단위 중력 (빈 줄을 제거하고 위 줄들을 한 번에 내림)
    // ============================================
    private void applyLineGravityOnly() {
        Color[][] board = state.getBoard();
        int[][] pid = state.getPieceId();

        // 임시 배열 생성
        Color[][] tempBoard = new Color[HEIGHT][WIDTH];
        int[][] tempPid = new int[HEIGHT][WIDTH];
        boolean[] tempGarbage = new boolean[HEIGHT];

        int writeRow = HEIGHT - 1;

        // 아래에서 위로 스캔하면서 비어있지 않은 줄만 복사
        for (int readRow = HEIGHT - 1; readRow >= 0; readRow--) {
            if (!isRowEmpty(board[readRow])) {
                for (int x = 0; x < WIDTH; x++) {
                    tempBoard[writeRow][x] = board[readRow][x];
                    tempPid[writeRow][x] = pid[readRow][x];
                }
                tempGarbage[writeRow] = isGarbageRow[readRow];
                writeRow--;
            }
        }

        // 위쪽 빈 줄 초기화
        for (int y = writeRow; y >= 0; y--) {
            for (int x = 0; x < WIDTH; x++) {
                tempBoard[y][x] = null;
                tempPid[y][x] = 0;
            }
            tempGarbage[y] = false;
        }

        // 원본에 복사
        for (int y = 0; y < HEIGHT; y++) {
            board[y] = tempBoard[y];
            pid[y] = tempPid[y];
            isGarbageRow[y] = tempGarbage[y];
        }

        System.out.println("[DEBUG] Line gravity applied (row-based compression)");
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

        Block next;
        if (itemMode && nextIsItem) {
            next = item.generateItemBlock();
            nextIsItem = false;
        } else {
            next = previewQueue.removeFirst();
        }

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

        int available = MAX_GARBAGE - garbageCount;
        if (available <= 0) {
            System.out.println("[WARN] Max garbage limit reached, clearing queue");
            incomingGarbageQueue.clear();
            return;
        }

        int addedLines = 0;

        while (!incomingGarbageQueue.isEmpty() && addedLines < available) {
            int mask = incomingGarbageQueue.poll();

            System.out.println("[DEBUG] Adding garbage line with mask: " + Integer.toBinaryString(mask));

            // ✅ 보드 전체를 한 칸 위로 밀기
            for (int y = 0; y < HEIGHT - 1; y++) {
                for (int x = 0; x < WIDTH; x++) {
                    board[y][x] = board[y + 1][x];
                    pid[y][x] = pid[y + 1][x];
                }
                isGarbageRow[y] = isGarbageRow[y + 1];
            }

            // ✅ 맨 아래 줄에 가비지 추가
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

            if (onIncomingChanged != null) {
                onIncomingChanged.accept(incomingGarbageQueue.size());
            }
        }

        System.out.println("[DEBUG] Garbage applied: " + addedLines + " lines, total garbage: " + garbageCount);
    }

    public void addGarbageMasks(int[] masks) {
        if (masks == null || masks.length == 0)
            return;

        for (int mask : masks) {
            incomingGarbageQueue.offer(mask);
        }

        if (onIncomingChanged != null) {
            onIncomingChanged.accept(incomingGarbageQueue.size());
        }

        System.out.println("[DEBUG] Enqueued " + masks.length + " garbage masks, total pending: " + incomingGarbageQueue.size());
    }

    private void updateGarbageFlagsOnClear(List<Integer> clearedRows) {
        for (int y : clearedRows) {
            if (y < 0 || y >= HEIGHT)
                continue;

            if (isGarbageRow[y]) {
                garbageCount--;
                System.out.println("[DEBUG] Garbage row cleared at y=" + y + ", remaining: " + garbageCount);
            }
        }
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
    }

    public void moveRight() {
        if (state.getCurr() == null)
            return;
        if (move.canMove(state.getCurr(), state.getX() + 1, state.getY()))
            move.moveRight();
    }

    public void rotateBlock() {
        if (state.getCurr() == null)
            return;
        Block backup = state.getCurr().clone();
        state.getCurr().rotate();
        if (!move.canMove(state.getCurr(), state.getX(), state.getY()))
            state.setCurr(backup);
    }

    public void hardDrop() {
        if (state.getCurr() == null)
            return;
        while (move.canMove(state.getCurr(), state.getX(), state.getY() + 1)) {
            move.moveDown();
            score += 2;
        }
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
        if (pauseCallback != null)
            pauseCallback.run();
    }

    public void setOnGameOverCallback(Runnable r) {
        this.onGameOverCallback = r;
    }

    private void gameOver() {
        this.gameOver = true;
        System.out.println("[GAME OVER] Your Score: " + score);
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

        previewQueue.clear();
        bag.reset();
        refillPreview();
        state.setCurr(previewQueue.removeFirst());
        fireNextQueueChanged();

        speedManager.resetLevel();

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
}