package logic;

import java.awt.Color;
import java.util.*;

import javax.swing.SwingUtilities;
import javax.swing.Timer;
import java.util.function.Consumer;

import blocks.Block;
import component.*;
import component.GameConfig.Difficulty;
import component.items.ItemBlock;

import component.network.websocket.*;

/**
 * BoardLogic (대전 모드 완성)
 * ------------
 * - 2줄 이상 클리어 시 공격
 * - 최근 놓은 블록 제외한 마스크 전송
 * - Incoming 큐 시스템 (최대 10줄)
 * - 다음 블록 생성 전 가비지 라인 추가
 */
public class BoardLogic {
    public static final int WIDTH = GameState.WIDTH;
    public static final int HEIGHT = GameState.HEIGHT;
    private static final int MAX_INCOMING = 10;  // 최대 대기 줄 수
    
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
    private java.util.function.IntConsumer onIncomingChanged;  // ✅ Incoming 변경 알림

    // 방금 고정된 블록 칸 표시 (마스크 전송 시 제외 용도)
    private final boolean[][] recentPlaced = new boolean[HEIGHT][WIDTH];
    
    // ✅ 대기 중인 가비지 라인 큐 (각 원소는 int 마스크)
    private final Queue<Integer> incomingGarbageQueue = new LinkedList<>();
    private int incomingCount = 0;

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

        // recentPlaced 초기화
        for (int yy = 0; yy < HEIGHT; yy++)
            Arrays.fill(recentPlaced[yy], false);

        for (int j = 0; j < b.height(); j++) {
            for (int i = 0; i < b.width(); i++) {
                if (b.getShape(i, j) == 1) {
                    int bx = state.getX() + i;
                    int by = state.getY() + j;
                    if (bx >= 0 && bx < WIDTH && by >= 0 && by < HEIGHT) {
                        board[by][bx] = b.getColor();
                        recentPlaced[by][bx] = true;
                    }
                }
            }
        }

        if (itemMode && b instanceof ItemBlock ib) {
            ib.activate(this, this::spawnNext);
        } else {
            clearLines();
            spawnNext();
        }
    }

    /** 라인 클리어 처리 */
    private void clearLines() {
        var board = state.getBoard();

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
            return;
        }

        // ✅ 2줄 이상 클리어 시에만 공격
        if (lines >= 2 && onLinesClearedWithMasks != null) {
            int[] masks = new int[lines];
            for (int i = 0; i < lines; i++) {
                int y = clearedRows.get(i);
                int mask = 0;
                for (int x = 0; x < WIDTH; x++) {
                    // 최근 놓은 블록 제외하고 마스크 생성
                    if (board[y][x] != null && !recentPlaced[y][x]) {
                        mask |= (1 << x);
                    }
                }
                masks[i] = mask;
            }
            onLinesClearedWithMasks.accept(masks);
            System.out.println("[ATTACK] " + lines + "줄 클리어 → " + lines + "줄 공격 전송");
        }

        // recentPlaced 초기화
        for (int yy = 0; yy < HEIGHT; yy++)
            java.util.Arrays.fill(recentPlaced[yy], false);

        if (pauseCallback != null)
            pauseCallback.run();

        clear.clearLines(
                () -> {
                    if (onFrameUpdate != null)
                        javax.swing.SwingUtilities.invokeLater(onFrameUpdate);
                },
                () -> {
                    if (resumeCallback != null)
                        javax.swing.SwingUtilities.invokeLater(resumeCallback);
                });

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

    /** 다음 블럭 스폰 (가비지 라인 먼저 추가) */
    private void spawnNext() {
        // ✅ 대기 중인 가비지 라인 추가
        applyIncomingGarbage();
        
        if (beforeSpawnHook != null)
            beforeSpawnHook.run();

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

    /** ✅ 대기 중인 가비지 라인을 보드 하단에 추가 */
    private void applyIncomingGarbage() {
        if (incomingGarbageQueue.isEmpty()) return;
        
        var board = state.getBoard();
        int addedLines = 0;
        
        while (!incomingGarbageQueue.isEmpty() && addedLines < incomingCount) {
            int mask = incomingGarbageQueue.poll();  // ✅ int로 수정
            
            // 한 줄 위로 밀기
            for (int y = 0; y < HEIGHT - 1; y++) {
                board[y] = java.util.Arrays.copyOf(board[y + 1], WIDTH);
            }
            
            // 맨 아래에 가비지 라인 추가
            Color[] last = new Color[WIDTH];
            for (int x = 0; x < WIDTH; x++) {
                boolean filled = ((mask >> x) & 1) != 0;
                last[x] = filled ? GARBAGE_COLOR : null;
            }
            board[HEIGHT - 1] = last;
            addedLines++;
        }
        
        incomingCount = 0;
        fireIncomingChanged();
        System.out.println("[GARBAGE] " + addedLines + "줄 추가됨");
    }

    /** ✅ 상대에게서 가비지 라인 수신 (큐에 추가) */
    public void addGarbageMasks(int[] masks) {
        if (masks == null || masks.length == 0) return;
        
        // 최대 10줄까지만 저장
        int toAdd = Math.min(masks.length, MAX_INCOMING - incomingCount);
        
        if (toAdd < masks.length) {
            System.out.println("[GARBAGE] 큐 초과! " + masks.length + "줄 중 " + toAdd + "줄만 추가");
        }
        
        for (int i = 0; i < toAdd; i++) {
            incomingGarbageQueue.offer(masks[i]);  // ✅ int 하나씩 추가
            incomingCount++;
        }
        
        fireIncomingChanged();
        System.out.println("[GARBAGE] 대기열에 " + toAdd + "줄 추가 (총 " + incomingCount + "줄)");
    }

    /** ✅ Incoming 변경 알림 */
    private void fireIncomingChanged() {
        if (onIncomingChanged != null) {
            onIncomingChanged.accept(incomingCount);
        }
    }

    /** ✅ Incoming 카운트 조회 */
    public int getIncomingCount() {
        return incomingCount;
    }

    // === 이동 입력 ===
    public void moveLeft() {
        if (move.canMove(state.getCurr(), state.getX() - 1, state.getY()))
            move.moveLeft();
    }

    public void moveRight() {
        if (move.canMove(state.getCurr(), state.getX() + 1, state.getY()))
            move.moveRight();
    }

    public void rotateBlock() {
        Block backup = state.getCurr().clone();
        state.getCurr().rotate();
        if (!move.canMove(state.getCurr(), state.getX(), state.getY()))
            state.setCurr(backup);
    }

    public void hardDrop() {
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
}