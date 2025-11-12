package component.network.websocket;

import component.GameConfig;
import component.GameLoop;
import component.board.KeyBindingInstaller;
import component.BoardView;
import component.ColorBlindPalette;
import logic.BoardLogic;

import javax.swing.*;
import java.awt.*;

/**
 * ✅ OnlineVersusPanel (연결 대기 추가)
 * --------------------------------------
 * - 양쪽 플레이어 연결 확인 후 게임 시작
 * - 대기 중에는 GameLoop 멈춤
 * - GAME_START 메시지 수신 시 게임 시작
 */
public class OnlineVersusPanel extends JPanel {

    private final JLabel myIncoming = new JLabel("0");
    private final JLabel oppIncoming = new JLabel("0");
    private final JLabel statusLabel = new JLabel("연결 대기 중...");

    private final BoardLogic myLogic;
    private final BoardLogic oppLogic;
    private final BoardView myView;
    private final BoardView oppView;

    private final GameClient client;
    private BoardSyncAdapter adapter;
    private final GameLoop loop;
    private final Timer syncTimer;

    private boolean isReady = false;      // 내가 준비됨
    private boolean oppReady = false;     // 상대 준비됨
    private boolean gameStarted = false;  // 게임 시작됨

    public OnlineVersusPanel(boolean isServer) {
        setLayout(new BorderLayout(12, 0));
        setBackground(new Color(18, 22, 30));

        /* 🎛 상단 HUD */
        JPanel top = new JPanel(new GridLayout(1, 3));
        top.setBackground(new Color(18, 22, 30));
        top.add(buildHud("My Incoming", myIncoming));
        
        // 중앙 상태 표시
        JPanel statusPanel = new JPanel();
        statusPanel.setBackground(new Color(24, 28, 38));
        statusPanel.setBorder(BorderFactory.createEmptyBorder(8, 12, 8, 12));
        statusLabel.setForeground(new Color(255, 200, 100));
        statusLabel.setFont(statusLabel.getFont().deriveFont(Font.BOLD, 16f));
        statusPanel.add(statusLabel);
        top.add(statusPanel);
        
        top.add(buildHud("Opponent Incoming", oppIncoming));
        add(top, BorderLayout.NORTH);

        /* 🎮 로직 구성 */
        myLogic = new BoardLogic(score -> adapter.sendGameOver());
        oppLogic = new BoardLogic(score -> {}); // 상대는 단순 표시용
        
        // ✅ oppLogic은 보드만 표시하므로 현재 블록 제거
        oppLogic.getState().setCurr(null);
        
        // ✅ Incoming 카운트 업데이트 연결
        myLogic.setOnIncomingChanged(count -> 
            SwingUtilities.invokeLater(() -> myIncoming.setText(String.valueOf(count)))
        );

        myView = new BoardView(myLogic);
        oppView = new BoardView(oppLogic);

        JPanel boards = new JPanel(new GridLayout(1, 2, 12, 0));
        boards.setBackground(new Color(18, 22, 30));
        boards.add(myView);
        boards.add(oppView);
        add(boards, BorderLayout.CENTER);

        /* 🌐 네트워크 초기화 */
        client = new GameClient(this::onNetworkMessage);
        adapter = new BoardSyncAdapter(myLogic, oppLogic, client);

        // ✅ 연결 전에 콜백 먼저 설정!
        client.setOnConnected(() -> {
            System.out.println("[DEBUG] onConnected 콜백 실행!");
            statusLabel.setText("연결됨! 상대 대기 중...");
            isReady = true;
            System.out.println("[SEND] PLAYER_READY 전송");
            client.send(new Message(MessageType.PLAYER_READY, "ready"));
            checkGameStart();
        });

        try {
            if (isServer) {
                statusLabel.setText("서버 시작 중...");
                GameServer.startServer(8081);
                Thread.sleep(1000);  // 서버 완전히 시작될 때까지
                client.connect("ws://localhost:8081/game");
                statusLabel.setText("클라이언트 연결 대기 중...");
            } else {
                String ip = JOptionPane.showInputDialog("서버 IP 입력:", "localhost");
                if (ip == null || ip.trim().isEmpty()) {
                    ip = "localhost";
                }
                statusLabel.setText("서버 연결 중...");
                client.connect("ws://" + ip + ":8081/game");
            }
        } catch (Exception e) {
            JOptionPane.showMessageDialog(this, "연결 실패: " + e.getMessage());
            throw new RuntimeException(e);
        }

        /* ⏳ 자동 하강 루프 (초기에는 멈춤) */
        loop = new GameLoop(myLogic, myView::repaint);
        myLogic.setLoopControl(loop::pause, loop::resume);
        // ❌ loop.startLoop();  // 아직 시작 안 함!

        /* 🎹 키 입력 바인딩 */
        KeyBindingInstaller.Deps deps = new KeyBindingInstaller.Deps(
                myLogic,
                myView::repaint,
                () -> {}, // 풀스크린 미사용
                () -> {}, // 종료 없음
                () -> false, // 일시정지 상태 없음
                () -> {}, () -> {}, // show/hide pause
                loop::startLoop, // 재시작
                loop::stopLoop,  // 중단
                t -> {},         // 제목 변경 미사용
                () -> ColorBlindPalette.Mode.NORMAL,
                m -> {},
                m -> {}
        );
        new KeyBindingInstaller().install(myView, deps, KeyBindingInstaller.KeySet.ARROWS, false);

        myView.setFocusable(true);
        SwingUtilities.invokeLater(myView::requestFocusInWindow);

        /* 🔁 주기적 보드 동기화 (게임 시작 후에만) */
        syncTimer = new Timer(200, e -> {
            if (gameStarted) {
                adapter.sendBoardState();
            }
        });
        syncTimer.start();
    }

    /** HUD 박스 빌더 */
    private JPanel buildHud(String title, JLabel label) {
        JPanel p = new JPanel();
        p.setBackground(new Color(24, 28, 38));
        p.setBorder(BorderFactory.createEmptyBorder(8, 12, 8, 12));
        p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));

        JLabel t = new JLabel(title);
        t.setForeground(new Color(160, 180, 200));
        t.setAlignmentX(Component.CENTER_ALIGNMENT);

        label.setForeground(Color.WHITE);
        label.setFont(label.getFont().deriveFont(20f));
        label.setAlignmentX(Component.CENTER_ALIGNMENT);

        p.add(t);
        p.add(Box.createVerticalStrut(4));
        p.add(label);
        return p;
    }

    /** 수신 메시지 처리 */
    private void onNetworkMessage(Message msg) {
        System.out.println("[RECV] 메시지 타입: " + msg.type);
        
        switch (msg.type) {
            case PLAYER_READY -> {
                System.out.println("[RECV] 상대 READY");
                oppReady = true;
                statusLabel.setText("상대 준비됨! 게임 시작 대기...");
                checkGameStart();
            }
            case GAME_START -> {
                System.out.println("[RECV] GAME_START");
                startGame();
            }
            default -> {
                adapter.handleIncoming(msg);
                SwingUtilities.invokeLater(oppView::repaint);
            }
        }
    }

    /** 양쪽 준비 확인 후 게임 시작 신호 전송 */
    private void checkGameStart() {
        if (isReady && oppReady && !gameStarted) {
            System.out.println("[INFO] 양쪽 준비 완료! 게임 시작");
            client.send(new Message(MessageType.GAME_START, "start"));
            startGame();
        }
    }

    /** 실제 게임 시작 */
    private void startGame() {
        if (gameStarted) return;
        gameStarted = true;
        
        SwingUtilities.invokeLater(() -> {
            statusLabel.setText("🎮 게임 진행 중");
            statusLabel.setForeground(new Color(100, 255, 100));
            loop.startLoop();
            System.out.println("[GAME] Loop Started!");
        });
    }

    /** 단독 실행 테스트용 main */
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            JFrame f = new JFrame("Online Versus Test");
            f.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            boolean isServer = JOptionPane.showConfirmDialog(f, "서버로 시작할까요?", "P2P 설정",
                    JOptionPane.YES_NO_OPTION) == JOptionPane.YES_OPTION;
            f.setContentPane(new OnlineVersusPanel(isServer));
            f.setSize(1100, 800);
            f.setLocationRelativeTo(null);
            f.setVisible(true);
        });
    }
}