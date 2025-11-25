package component;

import javax.swing.*;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.Set;

public class GameFrame extends JFrame {
    private boolean returningToMenu = false;
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
            // 게임 룰 전달
            this.activePanel = new component.network.websocket.OnlineVersusPanel(isServer, gameRule);
            setTitle("Tetris Online Battle - " + gameRule);
            setSize(950, 750);
        } else {
            this.activePanel = new BoardPanel(config, this::returnToMenu);
            setSize(720, 800);
        }

        add(activePanel, BorderLayout.CENTER);

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("\n========== SHUTDOWN HOOK ==========");
            System.out.println("Active threads:");

            Thread.getAllStackTraces().forEach((thread, stackTrace) -> {  // ⭐ stack → stackTrace
                if (thread.isAlive() && !thread.isDaemon()) {
                    System.out.println("\n⚠️  " + thread.getName() + 
                                    " (state: " + thread.getState() + ")");
                    
                    // 스택 트레이스 출력 (어디서 실행 중인지 확인)
                    System.out.println("    Stack trace:");
                    for (int i = 0; i < Math.min(stackTrace.length, 10); i++) {  // ⭐ 수정
                        System.out.println("      " + stackTrace[i]);
                    }
                }
            });

            System.out.println("===================================\n");
        }));

        // 윈도우 이벤트 리스너 추가
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                // 창 닫힐 때 리소스 정리
                System.out.println("\n========== WINDOW CLOSING ==========");

                logActiveThreads();
    
                pauseGame();
                if (activePanel instanceof BoardPanel) {
                    ((BoardPanel) activePanel).stopGame();
                }
                // else if (activePanel instanceof
                // component.network.websocket.OnlineVersusPanel) {
                // // OnlineVersusPanel도 stopGame() 구현 필요
                // component.network.websocket.OnlineVersusPanel onlinePanel =
                // (component.network.websocket.OnlineVersusPanel) activePanel;
                // onlinePanel.stopGame();

                // 프레임 완전히 제거
                System.out.println("\n[After cleanup]");
                logActiveThreads();
    
                dispose();
                System.out.println("[WINDOW] Closed");
            }
             @Override
            public void windowClosed(WindowEvent e) {
                System.out.println("[WINDOW] Closed");

                // 메뉴로 돌아가는 경우는 종료하지 않음
                if (returningToMenu) {
                    System.out.println("[INFO] Returning to menu, not exiting...");
                    return;
                }
                
                //  모든 윈도우가 닫혔는지 확인
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
                    
                    //  AWT 이벤트 디스패치 스레드 종료 대기
                    SwingUtilities.invokeLater(() -> {
                        try {
                            Thread.sleep(200); // 정리 대기
                        } catch (InterruptedException ex) {
                            ex.printStackTrace();
                        }
                        System.exit(0); // 강제 종료
                    });
                }
            }
            

            // 헬퍼 메서드
            private void logActiveTimers() {
                System.out.println("Active threads:");
                Set<Thread> threadSet = Thread.getAllStackTraces().keySet();
                
                for (Thread t : threadSet) {
                    // Swing Timer 관련 스레드 찾기
                    if (t.getName().contains("AWT") || 
                        t.getName().contains("Timer") || 
                        t.getName().contains("Swing")) {
                        System.out.println("  ⚠️  " + t.getName() + 
                                        " [" + t.getState() + "]");
                    }
                }
            }

            @Override
            public void windowIconified(WindowEvent e) {
                // 창 최소화 시 일시정지
                pauseGame();
            }

            @Override
            public void windowDeiconified(WindowEvent e) {
                // 창 복원 시 재개 (선택사항)
                // resumeGame(); // 자동 재개를 원하지 않으면 주석 처리
            }

            @Override
            public void windowLostFocus(WindowEvent e) {
                // 포커스 잃을 때 일시정지 (선택사항)
                pauseGame();
            }

            @Override
            public void windowGainedFocus(WindowEvent e) {
                // 포커스 얻을 때 재개 (선택사항)
                // resumeGame(); // 자동 재개를 원하지 않으면 주석 처리
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

    //  활성 스레드 로깅
    private void logActiveThreads() {
        System.out.println("Active threads:");
        Set<Thread> threadSet = Thread.getAllStackTraces().keySet();
        
        for (Thread t : threadSet) {
            if (t.getName().contains("AWT") || 
                t.getName().contains("Timer") || 
                t.getName().contains("Swing")) {
                System.out.println("  ??  " + t.getName() + 
                                 " [" + t.getState() + "]");
            }
        }
    }

    // 게임 일시정지 메서드
    private void pauseGame() {
        if (activePanel instanceof BoardPanel) {
            BoardPanel boardPanel = (BoardPanel) activePanel;
            boardPanel.pauseGame();
        }
        // } else if (activePanel instanceof
        // component.network.websocket.OnlineVersusPanel) {
        // component.network.websocket.OnlineVersusPanel onlinePanel =
        // (component.network.websocket.OnlineVersusPanel) activePanel;
        // onlinePanel.pauseGame();
        // }
    }

    // 메뉴로 돌아가기 콜백
    private void returnToMenu() {
        returningToMenu = true;
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
}