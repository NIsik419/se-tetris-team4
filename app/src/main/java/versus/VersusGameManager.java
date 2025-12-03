package versus;

import component.BoardPanel;
import component.GameConfig;
import component.ai.AIPlayer;
import logic.BoardLogic;

import java.util.function.Consumer;
import java.util.function.IntConsumer;
import java.util.List;
import java.util.ArrayList;
import java.util.Collections;

import javax.swing.JComponent;
import javax.swing.SwingUtilities;
import javax.swing.Timer;

import blocks.Block;

/**
 * VersusGameManager
 * - 두 Player를 생성/보유
 * - P1↔P2 이벤트(라인 클리어 → 마스크 전송) 배선
 * - HUD(대기열 라벨) 갱신 콜백 실행
 * - 규칙: 2줄 이상일 때만 전송, 최대 10줄 큐는 Player가 보장
 * - AI 모드 추가
 */
public class VersusGameManager {

    // 보드 가로 칸 수 (비트마스크 → boolean[] 변환용)
    private static final int BOARD_COLS = 10;

    public static class GameResult {
        public final Player.Id winner;   // 무승부면 null
        public final Player.Id loser;    // 무승부면 null
        public final int p1Score;
        public final int p2Score;

        public GameResult(Player.Id winner, Player.Id loser, int p1Score, int p2Score) {
            this.winner = winner;
            this.loser = loser;
            this.p1Score = p1Score;
            this.p2Score = p2Score;
        }
    }

    private final Player p1;
    private final Player p2;

    // AI 관련
    private AIPlayer aiPlayer;
    private Timer aiTimer;
    private final boolean isAIMode;

    // HUD 업데이트 콜백 (각각 "상대에게서 들어올 예정" 줄 수 표시)
    private final IntConsumer onP1PendingChanged;
    private final IntConsumer onP2PendingChanged;

    private final Consumer<List<Block>> onP1Next;
    private final Consumer<List<Block>> onP2Next;

    // 🔹 가비지 프리뷰(미니보드) 업데이트 콜백
    private final Consumer<List<boolean[]>> onP1GarbagePreview;
    private final Consumer<List<boolean[]>> onP2GarbagePreview;

    private final Runnable backToMenu;
    private final Consumer<GameResult> onGameFinished;

    private boolean finished = false;

    // 🔹 “아직 상대 보드에 적용되지 않은” 가비지 미리보기 버퍼
    private final List<boolean[]> p1GarbagePreviewBuffer = new ArrayList<>();
    private final List<boolean[]> p2GarbagePreviewBuffer = new ArrayList<>();

    public VersusGameManager(
            GameConfig p1Config,
            GameConfig p2Config,
            Runnable backToMenu,
            IntConsumer onP1PendingChanged,
            IntConsumer onP2PendingChanged,
            Consumer<List<Block>> onP1Next,
            Consumer<List<Block>> onP2Next,
            Consumer<GameResult> onGameFinished,
            Consumer<List<boolean[]>> onP1GarbagePreview,
            Consumer<List<boolean[]>> onP2GarbagePreview) {

        this.onP1PendingChanged = onP1PendingChanged;
        this.onP2PendingChanged = onP2PendingChanged;
        this.onP1Next = onP1Next;
        this.onP2Next = onP2Next;
        this.backToMenu = backToMenu;
        this.onGameFinished = onGameFinished;

        this.onP1GarbagePreview = onP1GarbagePreview;
        this.onP2GarbagePreview = onP2GarbagePreview;

        // P2가 AI인지 체크
        this.isAIMode = p2Config.mode() == GameConfig.Mode.AI;

        // Player 생성
        p1 = new Player(Player.Id.P1, p1Config, new Player.Events(), backToMenu, false);
        p2 = new Player(Player.Id.P2, p2Config, new Player.Events(), backToMenu, isAIMode);

        // ─── 이벤트 배선 (마스크 기반 공격) ───
        p1.events.onLinesClearedWithMasks = masks -> {
            if (masks == null || masks.length < 2) return; // 규칙: 2줄 이상만

            // 상대에게 가비지 마스크 큐 전송
            p2.enqueueGarbageMasks(masks);
            safeHudUpdateP2();

            // ▶ P2 입장 미리보기 버퍼에 이번 공격을 추가
            p2GarbagePreviewBuffer.addAll(toPreviewList(masks));
            notifyP2GarbagePreview(new ArrayList<>(p2GarbagePreviewBuffer));

            System.out.printf("[P1->P2] send masks %d%n", masks.length);
        };

        p2.events.onLinesClearedWithMasks = masks -> {
            if (masks == null || masks.length < 2) return;

            p1.enqueueGarbageMasks(masks);
            safeHudUpdateP1();

            // P1 입장 미리보기 버퍼에 이번 공격을 추가
            p1GarbagePreviewBuffer.addAll(toPreviewList(masks));
            notifyP1GarbagePreview(new ArrayList<>(p1GarbagePreviewBuffer));

            System.out.printf("[P2->P1] send masks %d%n", masks.length);
        };

        // 숫자 기반 onLineCleared는 사용하지 않음(중복 공격 방지)
        p1.events.onLineCleared = null;
        p2.events.onLineCleared = null;

        // 게임 오버 콜백
        p1.events.onGameOver = score -> onPlayerOver(Player.Id.P1);
        p2.events.onGameOver = score -> onPlayerOver(Player.Id.P2);

        // === 각 보드의 BoardLogic과 HUD/미리보기 콜백 연결 ===
        BoardPanel p1Panel = (BoardPanel) p1.getComponent();
        BoardLogic p1Logic = p1Panel.getLogic();
        p1Logic.setOnIncomingChanged(count -> {
            if (onP1PendingChanged != null) {
                onP1PendingChanged.accept(count);
            }
        });
        // ▶ P1 보드에 가비지가 “실제로 적용된 뒤”에는 미리보기 리셋
        p1Logic.setOnGarbageApplied(() -> {
            p1GarbagePreviewBuffer.clear();
            notifyP1GarbagePreview(Collections.emptyList());
        });

        BoardPanel p2Panel = (BoardPanel) p2.getComponent();
        BoardLogic p2Logic = p2Panel.getLogic();
        p2Logic.setOnIncomingChanged(count -> {
            if (onP2PendingChanged != null) {
                onP2PendingChanged.accept(count);
            }
        });
        // ▶ P2 보드에 가비지가 “실제로 적용된 뒤”에는 미리보기 리셋
        p2Logic.setOnGarbageApplied(() -> {
            p2GarbagePreviewBuffer.clear();
            notifyP2GarbagePreview(Collections.emptyList());
        });

        // AI 초기화
        if (isAIMode) {
            initializeAI(p2Config);
        }

        if (onP1Next != null) {
            p1.events.onNext = blocks -> onP1Next.accept(blocks);
        }
        if (onP2Next != null) {
            p2.events.onNext = blocks -> onP2Next.accept(blocks);
        }

        // 초기 HUD 갱신
        safeHudUpdateP1();
        safeHudUpdateP2();
        notifyP1GarbagePreview(Collections.emptyList());
        notifyP2GarbagePreview(Collections.emptyList());
    }

    /**
     * int 비트마스크 배열을 미니보드용 List<boolean[]> 로 변환
     * - 각 int 하나가 한 줄
     * - 하위 10비트(0~9)를 보드 가로 10칸으로 사용 (1=블록, 0=빈칸)
     */
    private static List<boolean[]> toPreviewList(int[] masks) {
        if (masks == null || masks.length == 0) {
            return Collections.emptyList();
        }
        List<boolean[]> list = new ArrayList<>(masks.length);
        for (int m : masks) {
            boolean[] row = new boolean[BOARD_COLS];
            for (int c = 0; c < BOARD_COLS; c++) {
                row[c] = ((m >> c) & 1) != 0; // 비트가 1이면 블록 있음
            }
            list.add(row);
        }
        return list;
    }

    // ─── 가비지 프리뷰 콜백 래퍼 ───
    private void notifyP1GarbagePreview(List<boolean[]> lines) {
        if (onP1GarbagePreview != null) {
            onP1GarbagePreview.accept(lines);
        }
    }

    private void notifyP2GarbagePreview(List<boolean[]> lines) {
        if (onP2GarbagePreview != null) {
            onP2GarbagePreview.accept(lines);
        }
    }

    // ================== 이하 기존 코드 그대로 ==================

    private void initializeAI(GameConfig p2Config) {
        BoardPanel p2Panel = (BoardPanel) p2.getComponent();
        BoardLogic p2Logic = p2Panel.getLogic();

        aiPlayer = new AIPlayer(p2Logic);

        // 난이도 설정
        String difficulty = switch (p2Config.difficulty()) {
            case AI_EASY -> "easy";
            case AI_HARD -> "hard";
            case AI_NORMAL -> "normal";
            default -> "normal";
        };
        aiPlayer.setDifficulty(difficulty);

        System.out.println("[AI] Initialized with difficulty: " + difficulty);

        // AI 행동 타이머 (100ms마다 체크)
        aiTimer = new Timer(100, e -> executeAIAction());
        aiTimer.start();
    }

    /**
     * AI 행동 실행
     */
    private void executeAIAction() {
        if (aiPlayer == null || p2.isGameOver()) {
            return;
        }

        String action = aiPlayer.getNextAction();
        if (action == null) {
            return;
        }

        BoardPanel p2Panel = (BoardPanel) p2.getComponent();
        BoardLogic p2Logic = p2Panel.getLogic();

        SwingUtilities.invokeLater(() -> {
            switch (action) {
                case "LEFT" -> p2Logic.moveLeft();
                case "RIGHT" -> p2Logic.moveRight();
                case "ROTATE" -> p2Logic.rotateBlock();
                case "DROP" -> p2Logic.hardDrop();
                case "DOWN" -> p2Logic.moveDown();
            }
        });
    }

    /**
     * 승패 처리
     */
    private void onPlayerOver(Player.Id loser) {
        if (finished)
            return;
        finished = true;

        Player.Id winner = (loser == Player.Id.P1) ? Player.Id.P2 : Player.Id.P1;

        // 양쪽 루프 정지
        p1.stop();
        p2.stop();

        // AI 타이머 정지
        if (aiTimer != null && aiTimer.isRunning()) {
            aiTimer.stop();
        }

        // VersusPanel 에 결과 전달
        if (onGameFinished != null) {
            GameResult result = new GameResult(
                    winner,
                    loser,
                    p1.getScore(),
                    p2.getScore());
            SwingUtilities.invokeLater(() -> onGameFinished.accept(result));
        }
    }

    /**
     * TIME ATTACK 모드 종료 (점수 비교)
     */
    public void finishByTimeAttack() {
        if (finished)
            return;

        int p1Score = p1.getScore();
        int p2Score = p2.getScore();

        if (p1Score > p2Score) {
            onPlayerOver(Player.Id.P2); // P1 승리
            return;
        }
        if (p2Score > p1Score) {
            onPlayerOver(Player.Id.P1); // P2 승리
            return;
        }

        // === 무승부 (DRAW) ===
        finished = true;
        p1.stop();
        p2.stop();

        if (aiTimer != null && aiTimer.isRunning()) {
            aiTimer.stop();
        }

        if (onGameFinished != null) {
            GameResult result = new GameResult(
                    null,   // winner
                    null,   // loser
                    p1.getScore(),
                    p2.getScore());
            SwingUtilities.invokeLater(() -> onGameFinished.accept(result));
        }
    }

    private void safeHudUpdateP1() {
        if (onP1PendingChanged != null) {
            onP1PendingChanged.accept(getP1Pending());
        }
    }

    private void safeHudUpdateP2() {
        if (onP2PendingChanged != null) {
            onP2PendingChanged.accept(getP2Pending());
        }
    }

    public void cleanup() {
        System.out.println("[VersusGameManager] Cleaning up...");

        // AI 타이머 정리
        if (aiTimer != null) {
            aiTimer.stop();
            aiTimer = null;
        }

        // AI 플레이어 정리
        if (aiPlayer != null) {
            aiPlayer = null;
        }

        p1.cleanup();
        p2.cleanup();

        System.out.println("[VersusGameManager] Cleanup completed");
    }

    // ─── 외부 제공 API ───

    public JComponent getP1Component() {
        return p1.getComponent();
    }

    public JComponent getP2Component() {
        return p2.getComponent();
    }

    public int getP1Pending() {
        return p1.getPendingGarbage();
    }

    public int getP2Pending() {
        return p2.getPendingGarbage();
    }

    public Player getP1() {
        return p1;
    }

    public Player getP2() {
        return p2;
    }

    /**
     * 일시정지 (ESC 키)
     */
    public void pauseBoth() {
        p1.stop();
        p2.stop();

        if (isAIMode && aiTimer != null && aiTimer.isRunning()) {
            aiTimer.stop();
        }
    }

    /**
     * 재개 (Continue)
     */
    public void resumeBoth() {
        p1.start();
        p2.start();

        if (isAIMode && aiTimer != null && !aiTimer.isRunning()) {
            aiTimer.start();
        }
    }

    public int getP1Score() {
        return p1.getScore();
    }

    public int getP2Score() {
        return p2.getScore();
    }

    public List<Block> getP1NextBlocks() {
        return p1.getNextBlocks();
    }

    public List<Block> getP2NextBlocks() {
        return p2.getNextBlocks();
    }

    public boolean isAIMode() {
        return isAIMode;
    }
}