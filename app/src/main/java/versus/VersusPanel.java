package versus;

import component.GameConfig;
import component.PausePanel;
import component.sidebar.HUDSidebar;

import javax.swing.*;
import java.awt.*;

/**
 * VersusPanel
 * - UI 레이아웃/라벨만 관리
 * - 실제 게임/이벤트/공격 규칙은 VersusGameManager가 담당
 * - OnlineVersusPanel 스타일로 UI 통일화
 */
public class VersusPanel extends JPanel {

    private VersusGameManager manager;
    private PausePanel pausePanel;

    private final JLabel p1Queue = new JLabel("0");
    private final JLabel p2Queue = new JLabel("0");

    private final JLabel syncStatsLabel = new JLabel("");

    // 타이머 라벨 & 남은 시간
    private final JLabel timerLabel = new JLabel("02:00", SwingConstants.CENTER);
    private javax.swing.Timer timeAttackTimer;
    private int remainingSeconds = 180; // 2분 고정
    private JPanel timerPanel;

    private HUDSidebar p1Sidebar;
    private HUDSidebar p2Sidebar;

    private final GameConfig p1Config;
    private final GameConfig p2Config;
    private final Runnable backToMenu;

    // 🔹 선택된 게임 룰 (Normal / Item / Time Limit (3min) 등)
    private final String gameRule;

    public VersusPanel(GameConfig p1Config, GameConfig p2Config, String gameRule) {
        this.p1Config = p1Config;
        this.p2Config = p2Config;
        this.gameRule = (gameRule != null) ? gameRule : "Normal";

        setLayout(new BorderLayout(0, 0));
        setBackground(new Color(18, 22, 30));

        // ───── 상단 HUD ─────
        JPanel topHud = new JPanel(new GridLayout(1, 5, 15, 0));
        topHud.setBackground(new Color(18, 22, 30));
        topHud.setBorder(BorderFactory.createEmptyBorder(10, 20, 10, 20));
        topHud.setPreferredSize(new Dimension(0, 80));

        topHud.add(buildHud("P1 Incoming", p1Queue));

        timerPanel = new JPanel();
        timerPanel.setBackground(new Color(24, 28, 38));
        timerPanel.setBorder(BorderFactory.createEmptyBorder(8, 12, 8, 12));
        timerLabel.setForeground(Color.WHITE);
        timerLabel.setFont(new Font("Arial", Font.BOLD, 28));
        timerLabel.setHorizontalAlignment(SwingConstants.CENTER);
        timerPanel.add(timerLabel);
        topHud.add(timerPanel);

        JPanel syncPanel = new JPanel();
        syncPanel.setBackground(new Color(24, 28, 38));
        syncPanel.setBorder(BorderFactory.createEmptyBorder(8, 12, 8, 12));
        syncStatsLabel.setForeground(new Color(150, 200, 255));
        syncStatsLabel.setFont(new Font("Arial", Font.PLAIN, 11));
        syncStatsLabel.setHorizontalAlignment(SwingConstants.CENTER);
        syncPanel.add(syncStatsLabel);
        topHud.add(syncPanel);

        topHud.add(buildHud("P2 Incoming", p2Queue));
        add(topHud, BorderLayout.NORTH);

        this.backToMenu = () -> {
            JFrame frame = (JFrame) SwingUtilities.getWindowAncestor(this);
            if (frame != null) frame.dispose();
        };

        // ───── 가운데 영역(좌 HUD + 보드 2개 + 우 HUD) ─────
        JPanel centerContainer = new JPanel(new BorderLayout(0, 0));
        centerContainer.setBackground(new Color(18, 22, 30));

        // 사이드바
        p1Sidebar = new HUDSidebar();
        p1Sidebar.setPreferredSize(new Dimension(160, 0));
        centerContainer.add(p1Sidebar, BorderLayout.WEST);

        p2Sidebar = new HUDSidebar();
        p2Sidebar.setPreferredSize(new Dimension(160, 0));
        centerContainer.add(p2Sidebar, BorderLayout.EAST);

        // === 사이드바 생성 후 Time UI 제거 ===
        removeTimeFromSidebar(p1Sidebar);
        removeTimeFromSidebar(p2Sidebar);

        // === 게임 매니저 초기화 (보드/플레이어 생성 포함) ===
        manager = new VersusGameManager(
                p1Config,
                p2Config,
                backToMenu,
                pending -> p1Queue.setText(String.valueOf(pending)),
                pending -> p2Queue.setText(String.valueOf(pending)),
                blocks -> SwingUtilities.invokeLater(() -> {
                    if (p1Sidebar != null) {
                        p1Sidebar.setNextBlocks(blocks);
                    }
                }),
                blocks -> SwingUtilities.invokeLater(() -> {
                    if (p2Sidebar != null) {
                        p2Sidebar.setNextBlocks(blocks);
                    }
                })
        );

        // 가운데 보드 2개
        JPanel boardsContainer = new JPanel(new GridBagLayout());
        boardsContainer.setBackground(new Color(18, 22, 30));
        boardsContainer.setBorder(BorderFactory.createEmptyBorder(10, 20, 10, 20));

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.weightx = 1.0;
        gbc.weighty = 1.0;
        gbc.anchor = GridBagConstraints.CENTER;
        gbc.insets = new Insets(0, 10, 0, 10);

        JPanel boardsPanel = new JPanel(new GridLayout(1, 2, 30, 0));
        boardsPanel.setBackground(new Color(18, 22, 30));
        boardsPanel.add(manager.getP1Component());
        boardsPanel.add(manager.getP2Component());

        boardsContainer.add(boardsPanel, gbc);
        centerContainer.add(boardsContainer, BorderLayout.CENTER);

        add(centerContainer, BorderLayout.CENTER);

        // 시작 시점에 NEXT 한 번 동기화
        SwingUtilities.invokeLater(() -> {
            p1Sidebar.setNextBlocks(manager.getP1NextBlocks());
            p2Sidebar.setNextBlocks(manager.getP2NextBlocks());
        });

        // === 초기 HUD 동기화 ===
        p1Queue.setText(String.valueOf(manager.getP1Pending()));
        p2Queue.setText(String.valueOf(manager.getP2Pending()));

        // 🔹 타임어택 여부 판정
        boolean isTimeAttack =
                p1Config.mode() == GameConfig.Mode.TIME_ATTACK
             || p2Config.mode() == GameConfig.Mode.TIME_ATTACK
             || (this.gameRule != null && this.gameRule.contains("Time"));

        if (timerPanel != null) {
            timerPanel.setVisible(isTimeAttack);
        }

        if (isTimeAttack) {
            startTimeAttackTimer();
        }

        // === 사이드 HUD 주기적 갱신 ===
        javax.swing.Timer hudTimer = new javax.swing.Timer(100, e -> {
            p1Sidebar.setScore(manager.getP1Score());
            p2Sidebar.setScore(manager.getP2Score());
        });
        hudTimer.start();

        // === PausePanel / P, R 키 바인딩 ===
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
                        // 🔹 RESTART 시에도 같은 gameRule 유지
                        frame.setContentPane(new VersusPanel(p1Config, p2Config, this.gameRule));
                        frame.revalidate();
                    },
                    () -> { // EXIT
                        manager.pauseBoth();
                        stopTimeAttackTimer();
                        backToMenu.run();
                    }
            );
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

    private JPanel buildHud(String title, JLabel value) {
        JPanel p = new JPanel();
        p.setBackground(new Color(24, 28, 38));
        p.setBorder(BorderFactory.createEmptyBorder(8, 12, 8, 12));
        p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));

        JLabel t = new JLabel(title);
        t.setForeground(new Color(160, 180, 200));
        t.setFont(new Font("Arial", Font.PLAIN, 12));
        t.setAlignmentX(Component.CENTER_ALIGNMENT);

        value.setForeground(Color.WHITE);
        value.setFont(new Font("Arial", Font.BOLD, 20));
        value.setAlignmentX(Component.CENTER_ALIGNMENT);

        p.add(t);
        p.add(Box.createVerticalStrut(4));
        p.add(value);
        return p;
    }

    // ───── 타임어택 타이머 로직 ─────
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

    @Override
    public Dimension getPreferredSize() {
        // P1 보드 컴포넌트 기준으로 크기 계산 (BoardPanel)
        JComponent p1Comp = manager != null ? manager.getP1Component() : null;

        int boardWidth;
        int boardHeight;

        if (p1Comp != null) {
            Dimension bd = p1Comp.getPreferredSize();
            boardWidth = bd.width;
            boardHeight = bd.height;
        } else {
            boardWidth = 400;
            boardHeight = 720;
        }

        int totalWidth  = (boardWidth * 2) + (160 * 2) + 100;
        int totalHeight = boardHeight + 180;

        return new Dimension(totalWidth, totalHeight);
    }

    private void removeTimeFromSidebar(HUDSidebar sidebar) {
        // HUDSidebar는 BoxLayout(Y축)이므로 컴포넌트를 순서대로 찾으면 됨
        Component[] comps = sidebar.getComponents();

        for (Component c : comps) {
            if (c instanceof JLabel label) {
                String text = label.getText();
                if ("Time".equalsIgnoreCase(text)) {
                    sidebar.remove(label); // "Time" 제목 제거
                }
            }

            // 실제 timeLabel도 제거
            if (c.getName() != null && c.getName().equals("timeLabel")) {
                sidebar.remove(c);
            }
        }

        sidebar.revalidate();
        sidebar.repaint();
    }

}
