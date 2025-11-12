package component.network.websocket;

import javax.swing.*;
import java.awt.*;

/**
 * ✅ P2PFrame (OnlineVersusPanel 래퍼)
 * -----------------------------------
 * - WebSocket 기반 온라인 대전 프레임
 * - 내부에 OnlineVersusPanel을 포함하고, 그 패널이 모든 로직 처리
 * - 서버/클라이언트 여부만 main()에서 선택
 */
public class P2PFrame extends JFrame {

    /**
     * P2PFrame 생성자
     * @param isServer true면 서버로 실행, false면 클라이언트로 실행
     */
    public P2PFrame(boolean isServer) {
        super("Tetris Online Battle");

        // 기본 프레임 설정
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        setLayout(new BorderLayout());
        getContentPane().setBackground(new Color(18, 22, 30));
        // OnlineVersusPanel 부착 (실제 게임 로직 담당)
        OnlineVersusPanel versusPanel = new OnlineVersusPanel(isServer);
        add(versusPanel, BorderLayout.CENTER);

        // 크기 및 배치 설정
        setSize(950, 750);
        setLocationRelativeTo(null);
        setVisible(true);
    }

    /**
     * 🧪 실행용 main (테스트용)
     * -------------------------------
     * 실행 시 서버/클라이언트 여부를 묻는 창이 뜸.
     * 서버 선택 시 로컬에서 서버 모드로 실행,
     * 클라이언트 선택 시 서버 IP 입력 후 접속.
     */
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            // 서버 / 클라이언트 선택창
            boolean isServer = JOptionPane.showConfirmDialog(
                    null,
                    "서버로 시작할까요?",
                    "P2P 설정",
                    JOptionPane.YES_NO_OPTION
            ) == JOptionPane.YES_OPTION;

            // 🧩 P2PFrame 실행
            new P2PFrame(isServer);
        });
    }
}
