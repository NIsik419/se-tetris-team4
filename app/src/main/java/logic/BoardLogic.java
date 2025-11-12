package logic;

import java.awt.Color;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import java.util.function.Consumer;

import blocks.Block;
import component.BlockBag;
import component.GameConfig;
import component.GameConfig.Difficulty;
import component.SpeedManager;
import component.items.ItemBlock;

/**
 * BoardLogic (AnimationManager 통합)
 * ------------
 * - 게임 전체 로직 관리
 * - 블럭 생성: RWS 기반 BlockBag 사용
 * - 속도 관리: SpeedManager로 위임
 * - 애니메이션 충돌 방지: AnimationManager 중앙 관리
 */
public class BoardLogic {
    public static final int WIDTH = GameState.WIDTH;
    public static final int HEIGHT = GameState.HEIGHT;
    private Runnable pauseCallback;
    private Runnable resumeCallback;

    public void setLoopControl(Runnable pause, Runnable resume) {
        this.pauseCallback = pause;
        this.resumeCallback = resume;
    }

    private int comboCount = 0;
    private long lastClearTime = 0;
    private int shakeOffset = 0;

    private java.util.function.IntConsumer onLineCleared;
    private Runnable beforeSpawnHook;
    private java.util.function.Consumer<int[]> onLinesClearedWithMasks; // NEW: 마스크 이벤트

    // 방금 고정된 블록 칸 표시 (마스크 전송 시 제외 용도)
    private final boolean[][] recentPlaced = new boolean[HEIGHT][WIDTH];

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

        // RWS 기반 BlockBag 난이도 적용
        this.bag = new BlockBag(diff);
        this.item = new ItemManager(bag);

        // SpeedManager 난이도 적용
        speedManager.setDifficulty(diff);

        // ClearService에 AnimationManager 주입
        clear.setAnimationManager(animMgr);

        // 초기 블럭 준비
        refillPreview();
        state.setCurr(previewQueue.removeFirst());
        fireNextQueueChanged();
    }

    public void setOnLinesClearedWithMasks(java.util.function.Consumer<int[]> cb) {
        this.onLinesClearedWithMasks = cb;
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

    // === 점수 관리 ===
    public void addScore(int delta) {
        if (buff.isDoubleScoreActive())
            score += delta * 2;
        else
            score += delta;
    }

    // === 이동 / 중력 ===
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
        for (int yy = 0; yy < HEIGHT; yy++) Arrays.fill(recentPlaced[yy], false);

        for (int j = 0; j < b.height(); j++) {
            for (int i = 0; i < b.width(); i++) {
                if (b.getShape(i, j) == 1) {
                    int bx = state.getX() + i;
                    int by = state.getY() + j;
                    if (bx >= 0 && bx < WIDTH && by >= 0 && by < HEIGHT) {
                        board[by][bx] = b.getColor();
                        recentPlaced[by][bx] = true; // 방금 고정된 칸으로 표시
                    }
                }
            }
        }

        // 아이템 블럭 처리
        if (itemMode && b instanceof ItemBlock ib) {
            ib.activate(this, this::spawnNext);
        } else {
            clearLines(); // 여기서 recentPlaced를 고려해 마스크 이벤트 발행
            spawnNext();
        }
    }

    /** 라인 클리어 처리 (애니메이션 pause/resume + 마스크 이벤트 발행) */
    private void clearLines() {
        var board = state.getBoard();

        // 1) 이번 프레임에 지워질 줄 찾기
        java.util.List<Integer> clearedRows = new java.util.ArrayList<>();
        for (int y = 0; y < HEIGHT; y++) {
            boolean full = true;
            for (int x = 0; x < WIDTH; x++) {
                if (board[y][x] == null) { full = false; break; }
            }
            if (full) clearedRows.add(y);
        }

        int lines = clearedRows.size();
        if (lines == 0) {
            // 라인 없으면 콤보만 리셋하고 종료
            comboCount = 0;
            return;
        }

        // 2) 대전용: 최근 고정 블록(recentPlaced)을 제외한 마스크를 만들어 전송
       
        int[] masks = new int[lines];
        for (int i = 0; i < lines; i++) {
            int y = clearedRows.get(i);
            int mask = 0;
            for (int x = 0; x < WIDTH; x++) {
                if (board[y][x] != null && !recentPlaced[y][x]) {
                    mask |= (1 << x);
                }
            }
            masks[i] = mask;
        }
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

        // 3) 애니메이션 동안 게임 일시정지
        if (pauseCallback != null) pauseCallback.run();

        // 4) 실제 클리어 + 중력 적용 애니메이션 실행
        clear.clearLines(
            () -> { if (onFrameUpdate != null) javax.swing.SwingUtilities.invokeLater(onFrameUpdate); },
            () -> { if (resumeCallback != null) javax.swing.SwingUtilities.invokeLater(resumeCallback); }
            );

        // 5) 통계/점수/콤보/레벨/아이템 처리
        clearedLines      += lines;
        deletedLinesTotal += lines;

        if (onLineCleared != null) onLineCleared.accept(lines);
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

    /** 다음 블럭 스폰 */
    private void spawnNext() {
        if (beforeSpawnHook != null) beforeSpawnHook.run();
        System.out.println("[SPAWN] beforeSpawnHook run");
        // 현재 큐에 블럭이 부족하면 채워 넣기
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
            gameOver = true;
            System.out.println("[DEBUG] Game Over!");
            onGameOver.accept(score);
        }
    }

    /** 대전용: 아래에서부터 n줄 가비지 추가 (랜덤 구멍 1개) */
    public void addGarbageLines(int n) {
        if (n <= 0) return;
        var board = state.getBoard();
        java.util.Random r = new java.util.Random();

        for (int k = 0; k < n; k++) {
            // 한 줄 위로 밀기
            for (int y = 0; y < HEIGHT - 1; y++) {
                board[y] = java.util.Arrays.copyOf(board[y + 1], WIDTH);
            }
            // 맨 아래 가비지 줄 생성(구멍 1개)
            int hole = r.nextInt(WIDTH);
            Color[] last = new Color[WIDTH];
            for (int x = 0; x < WIDTH; x++)
                last[x] = (x == hole) ? null : GARBAGE_COLOR;
            board[HEIGHT - 1] = last;
        }

        System.out.println("[GARBAGE] inject = " + n);
    }

    /** 대전용: 마스크 배열을 그대로 '바닥부터' 삽입 (각 원소는 가로 WIDTH비트) */
    public void addGarbageMasks(int[] masks) {
        if (masks == null || masks.length == 0) return;
        var board = state.getBoard();

        for (int k = 0; k < masks.length; k++) {
            // 한 줄 위로 밀기
            for (int y = 0; y < HEIGHT - 1; y++) {
                board[y] = java.util.Arrays.copyOf(board[y + 1], WIDTH);
            }
            // 맨 아래에 mask대로 채움 (1=가비지, 0=빈칸)
            int mask = masks[k];
            Color[] last = new Color[WIDTH];
            for (int x = 0; x < WIDTH; x++) {
                boolean filled = ((mask >> x) & 1) != 0;
                last[x] = filled ? GARBAGE_COLOR : null;
            }
            board[HEIGHT - 1] = last;
        }

        System.out.println("[GARBAGE] inject masks = " + masks.length);
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

    // === 디버그용: 다음 블럭 강제 설정 ===
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

    /** AnimationManager Getter */
    public AnimationManager getAnimationManager() {
        return animMgr;
    }

    /** HUD용 NEXT 블록 미리보기 */
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

    public void setOnLineCleared(java.util.function.IntConsumer c) { this.onLineCleared = c; }
    public void setBeforeSpawnHook(Runnable r) { this.beforeSpawnHook = r; }
}
