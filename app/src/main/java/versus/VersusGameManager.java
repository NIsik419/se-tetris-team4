package versus;

import component.BoardPanel;
import component.GameConfig;

import java.util.function.IntConsumer;
import java.awt.*; 
import javax.swing.*;

/**
 * VersusGameManager
 * - 두 Player를 생성/보유
 * - P1↔P2 이벤트(라인 클리어 → 마스크 전송) 배선
 * - HUD(대기열 라벨) 갱신 콜백 실행
 * - 규칙: 2줄 이상일 때만 전송, 최대 10줄 큐는 Player가 보장
 */
public class VersusGameManager {

    private final Player p1;
    private final Player p2;

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
            IntConsumer onP2PendingChanged
    ) {
        this.onP1PendingChanged = onP1PendingChanged;
        this.onP2PendingChanged = onP2PendingChanged;
        this.backToMenu = backToMenu;   

        // Player 생성
        p1 = new Player(Player.Id.P1, p1Config, new Player.Events(), backToMenu);
        p2 = new Player(Player.Id.P2, p2Config, new Player.Events(), backToMenu);

        // ─── 이벤트 배선 (마스크 기반 공격만 사용) ───
        p1.events.onLinesClearedWithMasks = masks -> {
            if (masks == null || masks.length < 2) return; // 규칙: 2줄 이상만
            p2.enqueueGarbageMasks(masks);
            // P2 입장에서 들어올 예정 줄 수 갱신
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

        // 초기 HUD 갱신
        safeHudUpdateP1();
        safeHudUpdateP2();
    }

    // 승패 처리
    private void onPlayerOver(Player.Id loser) {
        if (finished) return;
        finished = true;

        Player.Id winner = (loser == Player.Id.P1) ? Player.Id.P2 : Player.Id.P1;

        // 양쪽 루프 정지
        p1.stop();
        p2.stop();

        // 승자 메시지
        Component any = p1.getComponent(); // 프레임 찾기용
        JFrame frame = (JFrame) SwingUtilities.getWindowAncestor(any);
        String msg = (winner == Player.Id.P1) ? "P1 WINS!" : "P2 WINS!";
        JOptionPane.showMessageDialog(frame, msg, "Game Over", JOptionPane.INFORMATION_MESSAGE);

        // 창 닫고 메뉴 복귀
        if (frame != null) frame.dispose();
        if (backToMenu != null) backToMenu.run();
    }

    public void finishByTimeAttack() {
        if (finished) return;

        int p1Score = p1.getScore();
        int p2Score = p2.getScore();

        if (p1Score > p2Score) {
            onPlayerOver(Player.Id.P2);  // P1 승리
            return;
        }
        if (p2Score > p1Score) {
            onPlayerOver(Player.Id.P1);  // P2 승리
            return;
        }

        // === 무승부 (DRAW) ===
        // 팝업 없이 종료 + 메뉴 복귀만 수행
        p1.stop();
        p2.stop();


        Component any = p1.getComponent();
        JFrame frame = (JFrame) SwingUtilities.getWindowAncestor(any);

        JOptionPane.showMessageDialog(
                frame,
                "Time's up!\nDRAW",
                "무승부입니다",
                JOptionPane.INFORMATION_MESSAGE
        );

        if (frame != null) frame.dispose();
        if (backToMenu != null) backToMenu.run();

        finished = true;
    }


    private void safeHudUpdateP1() {
        if (onP1PendingChanged != null) onP1PendingChanged.accept(getP1Pending());
    }

    private void safeHudUpdateP2() {
        if (onP2PendingChanged != null) onP2PendingChanged.accept(getP2Pending());
    }

    // ─── 외부 제공 API ───
    public JComponent getP1Component() { return p1.getComponent(); }
    public JComponent getP2Component() { return p2.getComponent(); }

    public int getP1Pending() { return p1.getPendingGarbage(); } // HUD 초기 렌더/디버그용
    public int getP2Pending() { return p2.getPendingGarbage(); }

    public Player getP1() { return p1; }
    public Player getP2() { return p2; }

    public void pauseBoth() {
        p1.stop();
        p2.stop();
    }

    public void resumeBoth() {
        p1.start();
        p2.start();
    }

    public int getP1Score() {
        return p1.getScore();
    }

    public int getP2Score() {
        return p2.getScore();
    }
}
