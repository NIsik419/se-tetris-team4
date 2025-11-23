package component;

import javax.swing.*;
import java.awt.*;

public class GameFrame extends JFrame {

    private final JPanel activePanel;

    /**
     * @param config   게임 설정
     * @param p2pMode  true면 온라인 대전 모드
     * @param isServer true면 서버
     * @param gameRule P2P 게임 룰 ("Normal", "Item", "Time Limit (3min)")
     */
    public GameFrame(GameConfig config, boolean p2pMode, boolean isServer, String gameRule) {
        super("SeoulTech SE Tetris");
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        setLayout(new BorderLayout());

        if (p2pMode) {
            //  게임 룰 전달
            this.activePanel = new component.network.websocket.OnlineVersusPanel(isServer, gameRule);
            setTitle("Tetris Online Battle - " + gameRule);
            setSize(950, 750);
        } else {
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

            SwingUtilities.invokeLater(() -> {
                setVisible(true);
                activePanel.requestFocusInWindow();
            });

        } catch (Exception e) {
            System.err.println("[ERROR] Fullscreen toggle failed: " + e.getMessage());
        }
    }
}