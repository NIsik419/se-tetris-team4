package component;

import javax.swing.*;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.Set;

public class GameFrame extends JFrame {
    private boolean returningToMenu = false;
    private final JPanel activePanel;
    private boolean restartRequested = false;
    private boolean showingDialog = false;
    private boolean userRequestedClose = false; // ⭐ 사용자가 명시적으로 닫기를 요청했는지 추적

    public GameFrame(GameConfig config, boolean p2pMode, boolean isServer, String gameRule) {
        super("SeoulTech SE Tetris");

        setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
        setLayout(new BorderLayout());

        // GameFrame.java 생성자 부분
        if (p2pMode) {
            this.activePanel = new component.network.websocket.OnlineVersusPanel(isServer, gameRule);
            setTitle("Tetris Online Battle - " + gameRule);
            setSize(950, 750);
        } else {
            //  onExitToMenu 호출 추적
            this.activePanel = new BoardPanel(config, () -> {
                System.out.println("\n========== onExitToMenu CALLED ==========");
                Thread.dumpStack(); // 스택 트레이스 출력
                System.out.println("==========================================\n");
                returnToMenu();
            });
            setSize(720, 800);
        }

        add(activePanel, BorderLayout.CENTER);

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("\n========== SHUTDOWN HOOK ==========");
            System.out.println("Active threads:");

            Thread.getAllStackTraces().forEach((thread, stackTrace) -> {
                if (thread.isAlive() && !thread.isDaemon()) {
                    System.out.println("\n⚠️  " + thread.getName() +
                            " (state: " + thread.getState() + ")");

                    System.out.println("    Stack trace:");
                    for (int i = 0; i < Math.min(stackTrace.length, 10); i++) {
                        System.out.println("      " + stackTrace[i]);
                    }
                }
            });

            System.out.println("===================================\n");
        }));

        addWindowListener(new WindowAdapter() {
            // GameFrame.java의 windowClosing 수정
            @Override
            public void windowClosing(WindowEvent e) {
                System.out.println("\n========== WINDOW CLOSING ==========");
                System.out.println("[DEBUG] isVisible: " + isVisible());
                System.out.println("[DEBUG] isDisplayable: " + isDisplayable());

                showingDialog = true;

                // ⭐ PausePanel이 열려있으면 닫기
                if (activePanel instanceof BoardPanel) {
                    BoardPanel bp = (BoardPanel) activePanel;
                    bp.hidePausePanel(); // 이 메서드를 BoardPanel에 추가해야 함
                }

                pauseGame();

                // 키 입력 비활성화
                activePanel.setEnabled(false);

                int choice = JOptionPane.showOptionDialog(
                        GameFrame.this,
                        "게임을 종료하시겠습니까?",
                        "종료 확인",
                        JOptionPane.YES_NO_CANCEL_OPTION,
                        JOptionPane.QUESTION_MESSAGE,
                        null,
                        new Object[] { "메인으로", "게임 종료", "취소" },
                        "취소");

                showingDialog = false;

                // 키 입력 재활성화
                activePanel.setEnabled(true);

                if (choice == 0) {
                    // 메인으로
                    System.out.println("[USER] Selected: Return to menu");
                    if (activePanel instanceof BoardPanel) {
                        ((BoardPanel) activePanel).stopGame();
                        ((BoardPanel) activePanel).cleanup();
                    }

                    logActiveThreads();
                    returningToMenu = true;
                    userRequestedClose = true;
                    dispose();

                } else if (choice == 1) {
                    // 게임 종료
                    System.out.println("[USER] Selected: Exit game");
                    if (activePanel instanceof BoardPanel) {
                        ((BoardPanel) activePanel).stopGame();
                        ((BoardPanel) activePanel).cleanup();
                    }

                    logActiveThreads();
                    userRequestedClose = true;
                    dispose();
                    System.exit(0);

                } else {
                    // 취소 → 게임 재개
                    System.out.println("[USER] Selected: Cancel (resume game)");
                    System.out.println("[DEBUG] Before resume - isVisible: " + isVisible());

                    if (activePanel instanceof BoardPanel) {
                        ((BoardPanel) activePanel).resumeGame();
                    }

                    // 포커스 복원
                    SwingUtilities.invokeLater(() -> {
                        activePanel.requestFocusInWindow();
                        if (activePanel instanceof BoardPanel) {
                            BoardPanel bp = (BoardPanel) activePanel;
                            // boardView에 포커스 주기
                            Component boardView = bp.getComponent(0); // centerBoard
                            if (boardView instanceof JPanel) {
                                JPanel wrapper = (JPanel) boardView;
                                if (wrapper.getComponentCount() > 0) {
                                    wrapper.getComponent(0).requestFocusInWindow();
                                }
                            }
                        }
                    });

                    System.out.println("[DEBUG] After resume - isVisible: " + isVisible());
                    if (!isVisible()) {
                        System.err.println("[ERROR] Window became invisible!");
                        setVisible(true);
                    }
                }
            }

            @Override
            public void windowClosed(WindowEvent e) {
                System.out.println("[WINDOW] Closed");
                System.out.println("[DEBUG] userRequestedClose: " + userRequestedClose);
                System.out.println("[DEBUG] returningToMenu: " + returningToMenu);
                System.out.println("[DEBUG] restartRequested: " + restartRequested);

                // ⭐ 사용자가 명시적으로 닫기를 요청하지 않았다면 무시
                if (!userRequestedClose) {
                    System.out.println("[WARNING] windowClosed called without user request - ignoring");
                    return;
                }

                if (restartRequested) {
                    System.out.println("[INFO] Restart requested, skip global exit");
                    return;
                }

                if (returningToMenu) {
                    System.out.println("[INFO] Returning to menu, not exiting...");
                    return;
                }

                Window[] windows = Window.getWindows();
                boolean allClosed = true;

                for (Window w : windows) {
                    if (w.isVisible()) {
                        allClosed = false;
                        break;
                    }
                }

                if (allClosed) {
                    System.out.println("[EXIT] All windows closed, terminating AWT...");
                    SwingUtilities.invokeLater(() -> {
                        try {
                            Thread.sleep(200);
                        } catch (InterruptedException ex) {
                            ex.printStackTrace();
                        }
                        System.exit(0);
                    });
                }
            }

            @Override
            public void windowIconified(WindowEvent e) {
                if (!showingDialog) {
                    pauseGame();
                }
            }

            @Override
            public void windowDeiconified(WindowEvent e) {
                // resumeGame();
            }

            @Override
            public void windowLostFocus(WindowEvent e) {
                if (!showingDialog) {
                    System.out.println("[DEBUG] Window lost focus (not dialog)");
                    // pauseGame();
                }
            }

            @Override
            public void windowGainedFocus(WindowEvent e) {
                System.out.println("[DEBUG] Window gained focus");
                // resumeGame();
            }
        });

        pack();
        setLocationRelativeTo(null);
        setVisible(true);

        SwingUtilities.invokeLater(() -> {
            activePanel.revalidate();
            activePanel.setFocusable(true);
            activePanel.requestFocusInWindow();
        });
    }

    private void logActiveThreads() {
        System.out.println("Active threads:");
        Set<Thread> threadSet = Thread.getAllStackTraces().keySet();

        for (Thread t : threadSet) {
            if (t.getName().contains("AWT") ||
                    t.getName().contains("Timer") ||
                    t.getName().contains("Swing")) {
                System.out.println("  ⚠️  " + t.getName() +
                        " [" + t.getState() + "]");
            }
        }
    }

    private void pauseGame() {
        System.out.println("[DEBUG] pauseGame() called");
        if (activePanel instanceof BoardPanel) {
            BoardPanel boardPanel = (BoardPanel) activePanel;
            boardPanel.pauseGame();
        }
    }

    // GameFrame.java의 returnToMenu() 메서드 수정
    private void returnToMenu() {
        System.out.println("\n========== RETURN TO MENU CALLED ==========");
        System.out.println("Stack trace:");
        for (StackTraceElement element : Thread.currentThread().getStackTrace()) {
            System.out.println("  " + element);
        }
        System.out.println("==========================================\n");

        returningToMenu = true;
        userRequestedClose = true;
        pauseGame();
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

    public void markRestartRequested() {
        this.restartRequested = true;
    }

    public boolean isRestartRequested() {
        return restartRequested;
    }
}