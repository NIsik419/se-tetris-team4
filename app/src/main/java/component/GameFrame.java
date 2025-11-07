package component;

import javax.swing.*;
import java.awt.*;

public class GameFrame extends JFrame {

    // 필드로 올리기
    private final BoardPanel boardPanel;

    public GameFrame(GameConfig config) {
        super("SeoulTech SE Tetris");
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        setLayout(new BorderLayout());

        // 필드 초기화
        this.boardPanel = new BoardPanel(config, this::returnToMenu);
        add(boardPanel, BorderLayout.CENTER);

        pack();
        setSize(720, 800);
        setLocationRelativeTo(null);
        setVisible(true);
        SwingUtilities.invokeLater(boardPanel::revalidate);
        boardPanel.setFocusable(true);
        boardPanel.requestFocusInWindow();
    }

    // 메뉴로 돌아가기 콜백
    private void returnToMenu() {
        dispose();
        SwingUtilities.invokeLater(() -> new launcher.GameLauncher());
    }

    // 외부에서 BoardPanel 접근할 수 있도록 getter
    public BoardPanel getBoardPanel() {
        return boardPanel;
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
                if (boardPanel != null) {
                    boardPanel.requestFocusInWindow();
                }
            });

        } catch (Exception e) {
            System.err.println("[ERROR] Fullscreen toggle failed: " + e.getMessage());
        }
    }

}
