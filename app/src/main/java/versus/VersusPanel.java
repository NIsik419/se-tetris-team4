package versus;

import component.GameConfig;
import component.GameFrame;
import component.PausePanel;

import javax.swing.*;

import java.awt.*;

import component.MenuPanel;

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

    // 타이머 라벨 & 남은 시간
    private final JLabel timerLabel = new JLabel("02:00", SwingConstants.CENTER);
    private javax.swing.Timer timeAttackTimer;
    private int remainingSeconds = 120; // 2분 고정

    private final GameConfig p1Config;   
    private final GameConfig p2Config;  
    private final Runnable backToMenu;

    public VersusPanel(GameConfig p1Config, GameConfig p2Config) {
        this.p1Config = p1Config;
        this.p2Config = p2Config;

        setLayout(new BorderLayout(12, 0));
        setBackground(new Color(18, 22, 30));

        // 상단 HUD
        JPanel top = new JPanel(new BorderLayout());
        top.setBackground(new Color(18, 22, 30));

        top.add(buildSmallHud("P1 Incoming", p1Queue), BorderLayout.WEST);
        top.add(buildSmallHud("P2 Incoming", p2Queue), BorderLayout.EAST);

        // TIME 모드일 때만 중앙에 타이머 추가
        boolean isTimeAttack =
                p1Config.mode() == GameConfig.Mode.TIME_ATTACK
            || p2Config.mode() == GameConfig.Mode.TIME_ATTACK;

        if (isTimeAttack) {
            timerLabel.setForeground(Color.WHITE);
            timerLabel.setFont(timerLabel.getFont().deriveFont(20f));
            top.add(buildSmallHud("TIME", timerLabel), BorderLayout.CENTER);
        }

        add(top, BorderLayout.NORTH);

        // 필드에 저장
        this.backToMenu = () -> {
            JFrame frame = (JFrame) SwingUtilities.getWindowAncestor(this);
            if (frame != null) frame.dispose();
        };

        manager = new VersusGameManager(
                p1Config,
                p2Config,
                backToMenu,
                pending -> p1Queue.setText(String.valueOf(pending)),
                pending -> p2Queue.setText(String.valueOf(pending))
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

        if (isTimeAttack) {
            startTimeAttackTimer();
        }

        SwingUtilities.invokeLater(() -> {
            JFrame frame = (JFrame) SwingUtilities.getWindowAncestor(this);
            if (frame == null) return;

            pausePanel = new PausePanel(
                    frame,
                    () -> { // CONTINUE
                        manager.resumeBoth();
                        resumeTimeAttackTimer(); 
                        pausePanel.hidePanel();
                    },
                    () -> { // RESTART 
                        manager.pauseBoth();
                        stopTimeAttackTimer();
                        frame.setContentPane(new VersusPanel(p1Config, p2Config)); 
                        frame.revalidate();
                    },
                    () -> { // EXIT
                        manager.pauseBoth();
                        stopTimeAttackTimer();    
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
                    resumeTimeAttackTimer(); 
                    pausePanel.hidePanel();
                } else {
                    manager.pauseBoth();
                    pauseTimeAttackTimer(); 
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

    private void startTimeAttackTimer() {
        updateTimerLabel();

        timeAttackTimer = new javax.swing.Timer(1000, e -> {
            if (remainingSeconds > 0) {
                remainingSeconds--;
                updateTimerLabel();
            } else {
                ((javax.swing.Timer) e.getSource()).stop();
                onTimeUp(); 
            }
        });
        timeAttackTimer.start();
    }

    private void updateTimerLabel() {
        int m = remainingSeconds / 60;
        int s = remainingSeconds % 60;
        timerLabel.setText(String.format("%02d:%02d", m, s));
    }

    private void stopTimeAttackTimer() {
        if (timeAttackTimer != null) {
            timeAttackTimer.stop();
            timeAttackTimer = null;
        }
    }

    private void onTimeUp() {
        stopTimeAttackTimer();
        manager.pauseBoth();
        manager.finishByTimeAttack(); 
    }

    private void pauseTimeAttackTimer() {
        if (timeAttackTimer != null && timeAttackTimer.isRunning()) {
            timeAttackTimer.stop();
        }
    }

    private void resumeTimeAttackTimer() {
        if (timeAttackTimer != null && !timeAttackTimer.isRunning()) {
            timeAttackTimer.start();
        }
    }
}
