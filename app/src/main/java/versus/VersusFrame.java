package versus;

import javax.swing.*;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import component.GameConfig;

// VersusFrame.java 수정
public class VersusFrame extends JFrame {
    private VersusPanel versusPanel;
    private boolean returningToMenu = false;
    private boolean userRequestedClose = false; 

    public VersusFrame(GameConfig p1Config, GameConfig p2Config, String gameRule) {
        super(makeTitle(p1Config)); 

        setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE); 

        versusPanel = new VersusPanel(p1Config, p2Config, gameRule);
        setContentPane(versusPanel);

        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                handleWindowClose();
            }

            @Override
            public void windowClosed(WindowEvent e) {
                System.out.println("[VERSUS] Window closed");
                System.out.println("[DEBUG] userRequestedClose: " + userRequestedClose);

                //  사용자가 명시적으로 닫기를 요청하지 않았다면 무시
                if (!userRequestedClose) {
                    System.out.println("[WARNING] windowClosed called without user request - ignoring");
                    return;
                }

                // 메뉴로 돌아가는 경우는 종료하지 않음
                if (returningToMenu) {
                    System.out.println("[INFO] Returning to menu, not exiting...");
                    return;
                }

                // 모든 윈도우가 닫혔는지 확인
                Window[] windows = Window.getWindows();
                boolean allClosed = true;

                for (Window w : windows) {
                    if (w.isVisible()) {
                        allClosed = false;
                        break;
                    }
                }

                if (allClosed) {
                    System.out.println("[EXIT] All windows closed, terminating...");
                    System.exit(0);
                }
            }
        });

        pack();           
        setLocationRelativeTo(null);
        setVisible(true);

        panel.attachOverlayToFrame(this);
    }

    private void handleWindowClose() {
        int choice = JOptionPane.showOptionDialog(
                this,
                "대전을 종료하시겠습니까?\n(나가면 패배 처리됩니다)",
                "대전 종료",
                JOptionPane.YES_NO_OPTION,
                JOptionPane.WARNING_MESSAGE,
                null,
                new Object[] { "나가기", "취소" },
                "취소");

        if (choice == 0) {
            // 나가기 (패배 처리)
            System.out.println("[VERSUS] Player quit - marking as defeat");
            
            if (versusPanel != null) {
                versusPanel.stopGame();
                versusPanel.cleanup();
            }
            
            returningToMenu = true;
            userRequestedClose = true; //  추가
            dispose();
            
            // 메뉴로 복귀
            SwingUtilities.invokeLater(() -> {
                // MainMenu 표시 로직
            });
        }
        // choice == 1 또는 -1 (취소) → 아무것도 안 함 (대전 계속)
    }

    // 게임 오버 시 호출할 메서드 추가
    public void closeAfterGameOver() {
        returningToMenu = true;
        userRequestedClose = true;
        dispose();
    }

    private static String makeTitle(GameConfig config) {
        return switch (config.mode()) {
            case TIME_ATTACK -> "TETRIS - Time Attack Battle";
            case ITEM        -> "TETRIS - Item Battle";
            case AI          -> "TETRIS - AI Battle";
            default          -> "TETRIS - Versus";
        };
    }
}