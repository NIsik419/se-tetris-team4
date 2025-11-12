package component.network.websocket;

import component.BoardView;
import component.GameConfig;
import component.network.websocket.*;
import logic.BoardLogic;

import javax.swing.*;
import java.awt.*;

/**
 * VersusFrame (P2P 대전 모드)
 * ----------------------------
 * - WebSocket 기반 온라인 대전 프레임
 * - 좌측: 내 보드 / 우측: 상대 보드
 * - HUD 표시 + 연결 자동
 */
public class P2PFrame extends JFrame {

    private final BoardLogic myLogic;
    private final BoardLogic oppLogic;
    private final BoardView myView;
    private final BoardView oppView;
    private BoardSyncAdapter adapter;
    private final GameClient client;
    private final Timer syncTimer;

    private final JLabel myIncoming = new JLabel("0");
    private final JLabel oppIncoming = new JLabel("0");

    public P2PFrame(boolean isServer) {
        super("Tetris P2P Battle");

        // 1️⃣ 기본 창 설정
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        setLayout(new BorderLayout(12, 0));
        setBackground(new Color(18, 22, 30));

        // 2️⃣ 상단 HUD
        JPanel top = new JPanel(new GridLayout(1, 2));
        top.setBackground(new Color(18, 22, 30));
        top.add(buildHud("My Incoming", myIncoming));
        top.add(buildHud("Opponent Incoming", oppIncoming));
        add(top, BorderLayout.NORTH);

        // 3️⃣ 게임 로직 초기화
        myLogic = new BoardLogic(score -> adapter.sendGameOver());
        oppLogic = new BoardLogic(score -> {}); // 상대 보드는 표시용만 사용

        // 4️⃣ WebSocket 클라이언트 및 어댑터 초기화
        client = new GameClient(this::onNetworkMessage);
        adapter = new BoardSyncAdapter(myLogic, oppLogic,client);

        // 5️⃣ 서버 / 클라 연결 처리
        try {
            if (isServer) {
                GameServer.startServer(8081);
                Thread.sleep(500);
                client.connect("ws://localhost:8081/game");
            } else {
                String ip = JOptionPane.showInputDialog("서버 IP 입력:", "localhost");
                client.connect("ws://" + ip + ":8081/game");
            }
        } catch (Exception e) {
            JOptionPane.showMessageDialog(this, "연결 실패: " + e.getMessage());
            throw new RuntimeException(e);
        }

        // 6️⃣ 보드 뷰 생성 및 배치
        myView = new BoardView(myLogic);
        oppView = new BoardView(oppLogic);
        JPanel boards = new JPanel(new GridLayout(1, 2, 12, 0));
        boards.setBackground(new Color(18, 22, 30));
        boards.add(myView);
        boards.add(oppView);
        add(boards, BorderLayout.CENTER);

        // 7️⃣ 보드 상태 주기적 송신 (0.3초마다)
        syncTimer = new Timer(300, e -> adapter.sendBoardState());
        syncTimer.start();

        // 8️⃣ 기본 창 세팅
        setSize(1100, 820);
        setLocationRelativeTo(null);
        setVisible(true);
    }

    /** HUD 구성 */
    private JPanel buildHud(String title, JLabel value) {
        JPanel p = new JPanel();
        p.setBackground(new Color(24, 28, 38));
        p.setBorder(BorderFactory.createEmptyBorder(8, 12, 8, 12));
        p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));

        JLabel t = new JLabel(title);
        t.setForeground(new Color(160, 180, 200));
        t.setAlignmentX(Component.CENTER_ALIGNMENT);

        value.setForeground(Color.WHITE);
        value.setFont(value.getFont().deriveFont(20f));
        value.setAlignmentX(Component.CENTER_ALIGNMENT);

        p.add(t);
        p.add(Box.createVerticalStrut(4));
        p.add(value);
        return p;
    }

    /** 네트워크 메시지 수신 처리 */
    private void onNetworkMessage(Message msg) {
        adapter.handleIncoming(msg);
        oppView.repaint();
    }

    /** 실행용 테스트 */
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            boolean isServer = JOptionPane.showConfirmDialog(null, "서버로 시작할까요?", "P2P 설정",
                    JOptionPane.YES_NO_OPTION) == JOptionPane.YES_OPTION;
            new P2PFrame(isServer);
        });
    }
}
