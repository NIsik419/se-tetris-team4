package logic;

import java.awt.Color;
import java.util.*;
import java.util.function.Consumer;

import blocks.Block;
import component.*;
import component.GameConfig.Difficulty;
import component.items.ItemBlock;

/**
 * BoardLogic
 * ------------
 * - 게임 전체 로직 관리
 * - 블럭 생성: RWS 기반 BlockBag 사용
 * - 속도 관리: SpeedManager로 위임
 */
public class BoardLogic {
    public static final int WIDTH = GameState.WIDTH;
    public static final int HEIGHT = GameState.HEIGHT;

    private int comboCount = 0; // 연속 클리어 카운트
    private long lastClearTime = 0; // 마지막 클리어 시간 (콤보 유지 확인용)
    private int shakeOffset = 0;

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

    private ItemManager item; // ItemManager는 bag 초기화 이후 생성

    private final Consumer<Integer> onGameOver;
    private Runnable onFrameUpdate;

    private boolean gameOver = false;
    private int score = 0;
    private int clearedLines = 0;
    private int deletedLinesTotal = 0;
    private boolean nextIsItem = false;
    private boolean itemMode = false;

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

        // RWS 기반 BlockBag 난이도 적용
        this.bag = new BlockBag(diff);
        this.item = new ItemManager(bag);

        // SpeedManager 난이도 적용
        speedManager.setDifficulty(diff);

        // 초기 블럭 준비
        refillPreview();
        state.setCurr(previewQueue.removeFirst());
        fireNextQueueChanged();
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
        }
    }

    /** 블럭 고정 및 다음 블럭 생성 */
    private void fixBlock() {
        var b = state.getCurr();
        var board = state.getBoard();

        for (int j = 0; j < b.height(); j++) {
            for (int i = 0; i < b.width(); i++) {
                if (b.getShape(i, j) == 1) {
                    int bx = state.getX() + i;
                    int by = state.getY() + j;
                    if (bx >= 0 && bx < WIDTH && by >= 0 && by < HEIGHT)
                        board[by][bx] = b.getColor();
                }
            }
        }

        // 아이템 블럭 처리
        if (itemMode && b instanceof ItemBlock ib) {
            ib.activate(this, this::spawnNext);
        } else {
            clearLines();
            spawnNext();
        }
    }

    /** 라인 클리어 처리 */
    private void clearLines() {
        int lines = clear.clearLines(onFrameUpdate, null);
        clearedLines += lines;
        deletedLinesTotal += lines;

        if (lines > 0) {
            addScore(lines * 100); // 기본 점수

            // 🔹 콤보 판정: 최근 3초 내에 또 클리어했다면 콤보 유지
            long now = System.currentTimeMillis();
            if (now - lastClearTime < 3000)
                comboCount++;
            else
                comboCount = 1;

            lastClearTime = now;

            // 🔹 콤보 보너스 점수 (2콤보부터 가산)
            if (comboCount > 1) {
                int comboBonus = comboCount * 50;
                addScore(comboBonus);
                System.out.println("Combo! x" + comboCount + " (+" + comboBonus + ")");
            }

            // 속도 상승
            if (clearedLines % 10 == 0)
                speedManager.increaseLevel();

            // 아이템 등장 주기
            if (itemMode && deletedLinesTotal > 0 && deletedLinesTotal % 2 == 0)
                nextIsItem = true;
        } else {
            // 🔹 라인 미클리어 시 콤보 리셋
            comboCount = 0;
        }
    }

    /** 다음 블럭 스폰 */
    private void spawnNext() {
        // 현재 큐에 블럭이 부족하면 채워 넣기
    refillPreview();

    // 다음 블럭을 꺼냄
    Block next;
    if (itemMode && nextIsItem) {
        next = item.generateItemBlock();
        nextIsItem = false;
    } else {
        next = previewQueue.removeFirst(); // bag.next() 대신 previewQueue에서 꺼내기
    }

    // 현재 블럭으로 설정
    state.setCurr(next);
    state.setPosition(3, 0);

    // 다시 부족하면 채워 넣기
    refillPreview();

    // NEXT 큐 변경 알림 (여기서 3개 고정)
    fireNextQueueChanged();

    // 게임 오버 체크
    if (!move.canMove(next, 3, 0)) {
        gameOver = true;
        onGameOver.accept(score);
    }
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

    /** HUD용 NEXT 블록 미리보기 */
    public List<Block> getNextBlocks() {
        return previewQueue.size() > 1
                ? new ArrayList<>(previewQueue.subList(0, Math.min(3, previewQueue.size())))
                : List.of();
    }

    public void setOnNextQueueUpdate(Consumer<List<Block>> cb) {
        this.onNextQueueUpdate = cb;
    }

    // nextQueue가 바뀔 때마다 호출하는 유틸
    private void fireNextQueueChanged() {
        if (onNextQueueUpdate != null) {
            onNextQueueUpdate.accept(List.copyOf(previewQueue)); // nextQueue는 내부 큐
        }
    }
}
