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

/**
 * BoardLogic (대전 모드 완성)
 * ------------
 * - 2줄 이상 클리어 시 공격
 * - 다음 블록 생성 전 가비지 라인 추가
 */
public class BoardLogic {
    public static final int WIDTH = GameState.WIDTH;
    public static final int HEIGHT = GameState.HEIGHT;
    private static final int MAX_GARBAGE = 10; // 회색 줄 max
    private final boolean[] isGarbageRow = new boolean[HEIGHT];
    private int garbageCount = 0;

    private Runnable pauseCallback;
    private Runnable resumeCallback;
    private Runnable onGameOverCallback;

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
    private java.util.function.IntConsumer onIncomingChanged; // ✅ Incoming 변경 알림

    // 방금 고정된 블록 칸 표시 (마스크 전송 시 제외 용도)
    private final boolean[][] recentPlaced = new boolean[HEIGHT][WIDTH];

    // 대기 중인 가비지 라인 큐 (각 원소는 int 마스크)
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

     public enum Context { SINGLE, VERSUS_NORMAL, VERSUS_ITEM, VERSUS_TIME }
    private Context context = Context.SINGLE;
    private int playerId = 1;

    public void configureFor(Context ctx, int playerId) {
        this.context = ctx;
        this.playerId = playerId;
    }

    public interface AttackListener {
        void onAttack(int fromPlayerId, int linesToSend, int[] masks);
    }
    private AttackListener attackListener;
    public void setAttackListener(AttackListener l) { this.attackListener = l; }

    private final java.util.concurrent.ConcurrentLinkedQueue<Integer> incomingLines =
            new java.util.concurrent.ConcurrentLinkedQueue<>();
    private final java.util.concurrent.ConcurrentLinkedQueue<int[]> incomingMasks =
            new java.util.concurrent.ConcurrentLinkedQueue<>();

    public void queueGarbage(int lines, boolean excludeLastPart) {
        if (lines > 0) incomingLines.add(lines);
    }

    public void queueGarbageMasks(int[] masks) {
        if (masks != null && masks.length > 0) incomingMasks.add(masks);
    }

    private void addGarbageLines(int lines) {
        if (lines <= 0) return;

        java.util.Random rand = new java.util.Random();

        for (int i = 0; i < lines; i++) {
            // pick a random hole (empty cell) in this row
            int holeX = rand.nextInt(WIDTH);

            int mask = 0;
            for (int x = 0; x < WIDTH; x++) {
                if (x == holeX) continue;   // hole → 0
                mask |= (1 << x);           // filled → 1
            }

            // enqueue this garbage row; it’ll be applied in applyIncomingGarbage()
            incomingGarbageQueue.offer(mask);
        }
    }

    public int getPendingGarbageCount() {
        int c = incomingLines.stream().mapToInt(Integer::intValue).sum();
        for (int[] m : incomingMasks) c += m.length;
        return c;
    }

    private void applyPendingGarbageIfSafe() {
        if (isPieceActive()) return;
        Integer n;
        while ((n = incomingLines.poll()) != null) addGarbageLines(n);
        int[] masks;
        while ((masks = incomingMasks.poll()) != null) addGarbageMasks(masks);
    }

    private boolean isPieceActive() {
        return state.getCurr() != null;
    }

    /** 기본 생성자 (NORMAL) */
    public BoardLogic(Consumer<Integer> onGameOver) {
        this(onGameOver, GameConfig.Difficulty.NORMAL);
    }

    /** 난이도 지정 생성자 */
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

    /** 큐가 부족하면 블럭 채워넣기 */
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
        // 현재 블록이 없으면 아무것도 하지 않음
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

    /** 블럭 고정 및 다음 블럭 생성 */
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

                    // 상단 넘으면 즉시 game over
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

        // 블록 고정 직후 즉시 현재 블록 제거
        state.setCurr(null);

        if (blockOutOfBounds) {
            gameOver();
            return;
        }

        if (itemMode && b instanceof ItemBlock ib) {
            // 아이템 activate 후 clearLinesAndThen + spawnNext 실행
            ib.activate(this, () -> {
                clearLinesAndThen(this::spawnNext);
            });
        } else {
            clearLinesAndThen(this::spawnNext);
        }
    }

    /** 라인 클리어 처리 - 중력 즉시 적용 버전 */
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

<<<<<<< HEAD
        updateGarbageFlagsOnClear(clearedRows);

        // 2줄 이상 클리어 시에만 공격
        if (lines >= 2 && onLinesClearedWithMasks != null) {
            int[] masks = new int[lines];
            for (int i = 0; i < lines; i++) {
                int y = clearedRows.get(i);
                int mask = 0;
                for (int x = 0; x < WIDTH; x++) {
                    if (board[y][x] != null && !recentPlaced[y][x]) {
                        mask |= (1 << x);
                    }
=======
        // 2) 대전용: 최근 고정 블록(recentPlaced)을 제외한 마스크를 만들어 전송
       
        int[] masks = new int[lines];
        for (int i = 0; i < lines; i++) {
            int y = clearedRows.get(i);
            int mask = 0;
            for (int x = 0; x < WIDTH; x++) {
                if (board[y][x] != null && !recentPlaced[y][x]) {
                    mask |= (1 << x);
>>>>>>> 3732601 (Restore my today's work from commit 87c9521)
                }
            }
            masks[i] = mask;
        }
<<<<<<< HEAD

        //  1. 파티클 생성 (삭제 전 색상 저장)
        final int CELL_SIZE = 25;
        for (int row : clearedRows) {
            clear.getParticleSystem().createLineParticles(row, board, CELL_SIZE, WIDTH);
        }
=======
        if (onLinesClearedWithMasks != null)
        onLinesClearedWithMasks.accept(masks);
        if (context != Context.SINGLE && attackListener != null && lines >= 2) {
            int send = switch (lines) {
                case 2 -> 1;
                case 3 -> 2;
                case 4 -> 4;
                default -> 0;
            };
            if (send > 0) attackListener.onAttack(playerId, send, masks);
        }
        
        // 다음 턴을 위해 recentPlaced 초기화
        for (int yy = 0; yy < HEIGHT; yy++) java.util.Arrays.fill(recentPlaced[yy], false);
>>>>>>> 3732601 (Restore my today's work from commit 87c9521)

        //  2. 블록 즉시 삭제
        for (int row : clearedRows) {
            java.util.Arrays.fill(board[row], null);
            java.util.Arrays.fill(pid[row], 0);
        }

        recentPlacedInitialize();

        // 3. 점수/콤보 처리 (즉시)
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

        // 4. 중력 즉시 적용 (파티클 기다리지 않음!)
        clear.applyGravityInstantly();

        //  5. 화면 갱신
        if (onFrameUpdate != null)
            javax.swing.SwingUtilities.invokeLater(onFrameUpdate);

        //  6. 파티클은 백그라운드에서 재생
        clear.animateParticlesOnly(
                () -> {
                    if (onFrameUpdate != null)
                        javax.swing.SwingUtilities.invokeLater(onFrameUpdate);
                },
                null);

        // 7. 연쇄 클리어 체크 (약간의 지연 후)
        SwingUtilities.invokeLater(() -> checkChainClear(afterClear));
    }

    /** 연쇄 클리어 체크 */
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

    /** recentPlaced 배열 초기화 */
    private void recentPlacedInitialize() {
        for (int y = 0; y < HEIGHT; y++) {
            Arrays.fill(recentPlaced[y], false);
        }
    }

    /** 다음 블럭 스폰 (가비지 라인 먼저 추가) */
    private void spawnNext() {

        if (beforeSpawnHook != null)
            beforeSpawnHook.run();

        applyIncomingGarbage();

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
        applyPendingGarbageIfSafe();

        if (!move.canMove(next, state.getX(), state.getY())) {
            gameOver();
        }
    }

    /** 대기 중인 가비지 라인을 보드 하단에 추가 */
    private void applyIncomingGarbage() {
        if (incomingGarbageQueue.isEmpty())
            return;

        var board = state.getBoard();
        int[][] pid = state.getPieceId();

        // 필드에 남은 "가비지 슬롯" 계산
        int available = MAX_GARBAGE - garbageCount;
        if (available <= 0) {
            incomingGarbageQueue.clear();
            return;
        }

        int addedLines = 0;

        // available 만큼만 실제로 필드에 깔기
        while (!incomingGarbageQueue.isEmpty() && addedLines < available) {
            int mask = incomingGarbageQueue.poll();

            // 한 줄 위로 밀면서 isGarbageRow도 같이 밀기
            for (int y = 0; y < HEIGHT - 1; y++) {
                board[y] = java.util.Arrays.copyOf(board[y + 1], WIDTH);
                pid[y] = Arrays.copyOf(pid[y + 1], WIDTH);
                isGarbageRow[y] = isGarbageRow[y + 1];
            }

            // 맨 아래에 가비지 라인 추가
            Color[] last = new Color[WIDTH];
            int[] lastPid = new int[WIDTH];

            for (int x = 0; x < WIDTH; x++) {
                boolean filled = ((mask >> x) & 1) != 0;
                last[x] = filled ? GARBAGE_COLOR : null;
                lastPid[x] = 0;
            }
            board[HEIGHT - 1] = last;
            isGarbageRow[HEIGHT - 1] = true;

            addedLines++;
            garbageCount++; // 필드에 깔린 회색줄 개수 증가
        }
    }

    /** 상대에게서 가비지 라인 수신 (큐에 추가) */
    public void addGarbageMasks(int[] masks) {
        if (masks == null || masks.length == 0)
            return;

        for (int mask : masks) {
            incomingGarbageQueue.offer(mask);
        }
    }

    private void updateGarbageFlagsOnClear(List<Integer> clearedRows) {

        for (int idx = 0; idx < clearedRows.size(); idx++) {
            int y = clearedRows.get(idx) - idx; // 이미 이전 삭제로 한 칸씩 내려온 만큼 보정

            if (y < 0 || y >= HEIGHT)
                continue;

            // 이 줄이 가비지 줄이었다면 카운트 감소
            if (isGarbageRow[y]) {
                garbageCount--;
            }

            // y줄 위에 있는 줄들을 한 칸씩 아래로
            for (int row = y; row > 0; row--) {
                isGarbageRow[row] = isGarbageRow[row - 1];
            }
            isGarbageRow[0] = false; // 맨 위는 빈 줄
        }
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

    /** 대전모드 전용: 상대 보드 전체를 교체 */
    public void setBoard(Color[][] newBoard) {
        Color[][] board = state.getBoard();
        for (int y = 0; y < HEIGHT && y < newBoard.length; y++) {
            for (int x = 0; x < WIDTH && x < newBoard[y].length; x++) {
                board[y][x] = newBoard[y][x];
            }
        }
    }

    /** 상대가 게임오버 되었을 때 호출 */
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

    /**
     * 게임 상태 초기화 (대전 재시작용)
     * -----------------------------------------
     * - 보드, 점수, 라인 카운트, 블록 큐, 속도 모두 초기화
     */
    public void reset() {
        // 보드 전체 비우기
        Color[][] board = state.getBoard();
        for (int y = 0; y < HEIGHT; y++) {
            Arrays.fill(board[y], null);
        }

        // Fade 레이어 초기화
        Color[][] fade = state.getFadeLayer();
        if (fade != null) {
            for (int y = 0; y < HEIGHT; y++) {
                Arrays.fill(fade[y], null);
            }
        }

        // 상태 및 변수 초기화
        state.reset(); // GameState 내부 블록, 위치 초기화
        state.setCurr(null); // 현재 블록 제거
        state.setPosition(3, 0);
        score = 0;
        clearedLines = 0;
        deletedLinesTotal = 0;
        comboCount = 0;
        gameOver = false;
        nextIsItem = false;

        // 공격/가비지 큐 초기화
        incomingGarbageQueue.clear();
        recentPlacedInitialize();

        // 블록 백 및 프리뷰 재설정
        previewQueue.clear();
        bag.reset(); // BlockBag 내부 nextBlocks 초기화 (직접 구현 필요)
        refillPreview();
        state.setCurr(previewQueue.removeFirst());
        fireNextQueueChanged();

        // 속도 리셋
        speedManager.resetLevel();

        // HUD/UI 초기화
        SwingUtilities.invokeLater(() -> {
            if (onFrameUpdate != null)
                onFrameUpdate.run(); // 즉시 화면 갱신 요청
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