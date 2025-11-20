package component;

import javax.swing.*;
import java.awt.*;

public class GameFrame extends JFrame {

    // 단일 필드로 통합 (BoardPanel 또는 OnlineVersusPanel)
    private final JPanel activePanel;

    /**
     * @param config  게임 설정
     * @param p2pMode true면 온라인 대전 모드, false면 싱글 모드
     * @param isServer true면 서버로 실행, false면 클라이언트
     */
    public GameFrame(GameConfig config, boolean p2pMode, boolean isServer) {
        super("SeoulTech SE Tetris");
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        setLayout(new BorderLayout());

        // 모드 분기
        if (p2pMode) {
            // 🧩 온라인 대전 모드
            this.activePanel = new component.network.websocket.OnlineVersusPanel(isServer);
            setTitle("Tetris Online Battle");
            setSize(950, 750);
        } else {
            // 🎮 싱글 모드
            this.activePanel = new BoardPanel(config, this::returnToMenu);
            setSize(720, 800);
        }

        add(activePanel, BorderLayout.CENTER);

        pack();
        setLocationRelativeTo(null);
        setVisible(true);

        SwingUtilities.invokeLater(() -> {
            activePanel.revalidate();
            activePanel.setFocusable(true);
            activePanel.requestFocusInWindow();
        });
    }

    // 메뉴로 돌아가기 콜백
    private void returnToMenu() {
        dispose();
    }

    // BoardPanel 접근자 (싱글모드일 때만 유효)
    public JPanel getActivePanel() {
        return activePanel;
    }

    public void updateTitle(String state) {
        setTitle("TETRIS - " + state);
    }

    public void toggleFullScreen() {
        GraphicsDevice gd = GraphicsEnvironment.getLocalGraphicsEnvironment().getDefaultScreenDevice();
        boolean isFull = gd.getFullScreenWindow() == this;

        try {
            dispose();
            setUndecorated(!isFull);

            if (isFull) {
                gd.setFullScreenWindow(null);
            } else {
                gd.setFullScreenWindow(this);
            }

            // 포커스 복구
            SwingUtilities.invokeLater(() -> {
                setVisible(true);
                activePanel.requestFocusInWindow();
            });

        } catch (Exception e) {
            System.err.println("[ERROR] Fullscreen toggle failed: " + e.getMessage());
        }
    }
}