package versus;

import component.BoardPanel;
import component.GameConfig;
import component.ai.AIPlayer;
import logic.BoardLogic;

import java.util.function.IntConsumer;
import java.awt.*;
import javax.swing.*;

/**
 * VersusGameManager
 * - 두 Player를 생성/보유
 * - P1↔P2 이벤트(라인 클리어 → 마스크 전송) 배선
 * - HUD(대기열 라벨) 갱신 콜백 실행
 * - 규칙: 2줄 이상일 때만 전송, 최대 10줄 큐는 Player가 보장
 * - AI 모드 추가
 */
public class VersusGameManager {

    private final Player p1;
    private final Player p2;

    // AI 관련
    private AIPlayer aiPlayer;
    private Timer aiTimer;
    private boolean isAIMode;

    // HUD 업데이트 콜백 (각각 "상대에게서 들어올 예정" 줄 수 표시)
    private final IntConsumer onP1PendingChanged; // P1 라벨 값 갱신
    private final IntConsumer onP2PendingChanged; // P2 라벨 값 갱신

    private final Runnable backToMenu;
    private boolean finished = false;

    public VersusGameManager(
            // 게임 설정(필요 시 난이도/모드 주입)
            GameConfig p1Config,
            GameConfig p2Config,
            // 메뉴로 돌아가기 콜백 (두 보드 공용)
            Runnable backToMenu,
            // HUD 갱신 콜백
            IntConsumer onP1PendingChanged,
            IntConsumer onP2PendingChanged) {
        this.onP1PendingChanged = onP1PendingChanged;
        this.onP2PendingChanged = onP2PendingChanged;
        this.backToMenu = backToMenu;

        // P2가 AI인지 체크
        this.isAIMode = p2Config.mode() == GameConfig.Mode.AI;

        // Player 생성
        p1 = new Player(Player.Id.P1, p1Config, new Player.Events(), backToMenu, false);
        p2 = new Player(Player.Id.P2, p2Config, new Player.Events(), backToMenu, isAIMode);

        // ─── 이벤트 배선 (마스크 기반 공격만 사용) ───
        p1.events.onLinesClearedWithMasks = masks -> {
            if (masks == null || masks.length < 2) return; // 규칙: 2줄 이상만
            p2.enqueueGarbageMasks(masks);
            safeHudUpdateP2();
            System.out.printf("[P1->P2] send masks %d%n", masks.length);
        };

        p2.events.onLinesClearedWithMasks = masks -> {
            if (masks == null || masks.length < 2) return;
            p1.enqueueGarbageMasks(masks);
            safeHudUpdateP1();
            System.out.printf("[P2->P1] send masks %d%n", masks.length);
        };

        // 숫자 기반 onLineCleared는 사용하지 않음(중복 공격 방지)
        p1.events.onLineCleared = null;
        p2.events.onLineCleared = null;

        p1.events.onGameOver = score -> onPlayerOver(Player.Id.P1);
        p2.events.onGameOver = score -> onPlayerOver(Player.Id.P2);

        // AI 초기화
        if (isAIMode) {
            initializeAI(p2Config);
        }

        // 초기 HUD 갱신
        safeHudUpdateP1();
        safeHudUpdateP2();
    }

    /**
     * AI 초기화 및 타이머 시작
     */
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
        if (finished) return;
        finished = true;

        Player.Id winner = (loser == Player.Id.P1) ? Player.Id.P2 : Player.Id.P1;

        // 양쪽 루프 정지
        p1.stop();
        p2.stop();
        
        // AI 타이머 정지
        if (aiTimer != null && aiTimer.isRunning()) {
            aiTimer.stop();
        }

        // 승자 메시지
        Component any = p1.getComponent();
        JFrame frame = (JFrame) SwingUtilities.getWindowAncestor(any);
        String msg = (winner == Player.Id.P1) ? "P1 WINS!" : "P2 WINS!";
        
        if (isAIMode) {
            msg = (winner == Player.Id.P1) ? "YOU WIN!" : "AI WINS!";
        }
        
        JOptionPane.showMessageDialog(frame, msg, "Game Over", JOptionPane.INFORMATION_MESSAGE);

        // 창 닫고 메뉴 복귀
        if (frame != null) frame.dispose();
        // if (backToMenu != null) backToMenu.run();
    }

    /**
     * TIME ATTACK 모드 종료 (점수 비교)
     */
    public void finishByTimeAttack() {
        if (finished) return;

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
        p1.stop();
        p2.stop();
        
        if (aiTimer != null && aiTimer.isRunning()) {
            aiTimer.stop();
        }

        Component any = p1.getComponent();
        JFrame frame = (JFrame) SwingUtilities.getWindowAncestor(any);

        JOptionPane.showMessageDialog(
                frame,
                "Time's up!\nDRAW",
                "무승부입니다",
                JOptionPane.INFORMATION_MESSAGE);

        if (frame != null) frame.dispose();
        // if (backToMenu != null) backToMenu.run();

        finished = true;
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
    
    public boolean isAIMode() {
        return isAIMode;
    }
}