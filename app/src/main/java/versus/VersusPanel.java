package versus;

import component.GameConfig;
import component.PausePanel;

import javax.swing.*;
import java.awt.*;

/**
 * VersusPanel
 * - UI 레이아웃/라벨만 관리
 * - 실제 게임/이벤트/공격 규칙은 VersusGameManager가 담당
 */
public class VersusPanel extends JPanel {

    private VersusGameManager manager;
    private PausePanel pausePanel;

    private final JLabel p1Queue = new JLabel("0");
    private final JLabel p2Queue = new JLabel("0");

    public VersusPanel(boolean itemMode) {
        setLayout(new BorderLayout(12, 0));
        setBackground(new Color(18, 22, 30));

        // 상단 HUD
        JPanel top = new JPanel(new GridLayout(1, 2));
        top.setBackground(new Color(18, 22, 30));
        top.add(buildSmallHud("P1 Incoming", p1Queue));
        top.add(buildSmallHud("P2 Incoming", p2Queue));
        add(top, BorderLayout.NORTH);

        var mode = itemMode ? GameConfig.Mode.ITEM : GameConfig.Mode.CLASSIC;

        // 매니저 생성(이벤트 배선 + HUD 콜백)
        Runnable backToMenu = () -> SwingUtilities.getWindowAncestor(this).dispose();

        manager = new VersusGameManager(
                new GameConfig(mode, GameConfig.Difficulty.NORMAL, false),
                new GameConfig(mode, GameConfig.Difficulty.NORMAL, false),
                backToMenu,
                pending -> p1Queue.setText(String.valueOf(pending)), // P1 라벨 갱신
                pending -> p2Queue.setText(String.valueOf(pending))  // P2 라벨 갱신
        );

        // 보드 2개 배치
        JPanel boards = new JPanel(new GridLayout(1, 2, 12, 0));
        boards.setBackground(new Color(18, 22, 30));
        boards.add(manager.getP1Component());
        boards.add(manager.getP2Component());
        add(boards, BorderLayout.CENTER);

        // 초기 HUD 동기화 (optional)
        p1Queue.setText(String.valueOf(manager.getP1Pending()));
        p2Queue.setText(String.valueOf(manager.getP2Pending()));

        SwingUtilities.invokeLater(() -> {
            JFrame frame = (JFrame) SwingUtilities.getWindowAncestor(this);
            if (frame == null) return;

            pausePanel = new PausePanel(
                    frame,
                    () -> { // CONTINUE
                        manager.resumeBoth();
                        pausePanel.hidePanel();
                    },
                    () -> { // RESTART 
                        manager.pauseBoth();
                        frame.setContentPane(new VersusPanel(itemMode));
                        frame.revalidate();
                    },
                    () -> { // EXIT
                        manager.pauseBoth();
                        backToMenu.run();
                    }
            );
            // ESC 키로 pause 토글
            setupPauseKeyBinding();
        });
    }

    private void setupPauseKeyBinding() {
        InputMap im = getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW);
        ActionMap am = getActionMap();

        im.put(KeyStroke.getKeyStroke("P"), "togglePause");
        im.put(KeyStroke.getKeyStroke("R"), "togglePause");
        
        am.put("togglePause", new AbstractAction() {
            @Override
            public void actionPerformed(java.awt.event.ActionEvent e) {
                if (pausePanel == null) return;

                if (pausePanel.isVisible()) {
                    manager.resumeBoth();
                    pausePanel.hidePanel();
                } else {
                    manager.pauseBoth();
                    pausePanel.showPanel();
                }
            }
        });
    }

    private JPanel buildSmallHud(String title, JLabel value) {
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

    // 실행용
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            boolean itemMode = false;
            JFrame f = new JFrame(itemMode ? "Versus Test (Item)" : "Versus Test");
            f.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            f.setContentPane(new VersusPanel(itemMode));
            f.setSize(1100, 800);
            f.setLocationRelativeTo(null);
            f.setVisible(true);
        });
    }
}
