package versus;

import javax.swing.*;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import component.GameConfig;

public class VersusFrame extends JFrame {
    private VersusPanel versusPanel;
    private boolean returningToMenu = false;

    public VersusFrame(GameConfig p1Config, GameConfig p2Config, String gameRule) {
        super(makeTitle(p1Config)); 

        setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);

        versusPanel = new VersusPanel(p1Config, p2Config, gameRule);
        setContentPane(versusPanel);

        // ⭐ X 버튼 처리 추가
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                handleWindowClose();
            }

            @Override
            public void windowClosed(WindowEvent e) {
                System.out.println("[VERSUS] Window closed");

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
    }

    public VersusFrame(GameConfig p1Config, GameConfig p2Config) {
        this(p1Config, p2Config, "Normal");   
    }

    //  X 버튼 클릭 시 처리 (대전 모드는 즉시 나가기)
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
            dispose();
            
            // 메뉴로 복귀
            SwingUtilities.invokeLater(() -> {
                // MainMenu 표시 로직
                // 예: new MainMenu().setVisible(true);
            });
        }
        // choice == 1 또는 -1 (취소) → 아무것도 안 함 (대전 계속)
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