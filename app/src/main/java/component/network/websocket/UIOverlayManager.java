package component.network.websocket;

import javax.swing.*;
import java.awt.*;
import java.net.InetAddress;

/**
 * UIOverlayManager - 오버레이 UI 전담 클래스
 */
public class UIOverlayManager {

    private final JPanel parentPanel;
    private final boolean isServer;
    private String selectedMode;

    private JPanel overlayPanel;
    private JLabel statusLabel;
    private JLabel ipLabel;
    private JComboBox<String> modeSelector;
    private JButton startButton;

    private final Runnable onStartGame;
    private final java.util.function.Consumer<String> onModeChanged;
    private final Runnable onRestart;
    private final Runnable onCleanup;
    private Runnable onExecuteRestart;

    private JPanel gameOverPanel;

    public UIOverlayManager(JPanel parentPanel,
            boolean isServer,
            String initialMode,
            Runnable onStartGame,
            java.util.function.Consumer<String> onModeChanged,
            Runnable onRestart,
            Runnable onCleanup) {
        this.parentPanel = parentPanel;
        this.isServer = isServer;
        this.selectedMode = initialMode;
        this.onStartGame = onStartGame;
        this.onModeChanged = onModeChanged;
        this.onRestart = onRestart;
        this.onCleanup = onCleanup;
    }

    public void setOnExecuteRestart(Runnable onExecuteRestart) {
        this.onExecuteRestart = onExecuteRestart;
    }

    public void updateRestartStatus(String msg) {
        if (gameOverPanel != null && gameOverPanel.getComponentCount() > 0) {
            Component c = gameOverPanel.getComponent(0);
            if (c instanceof JLabel label) {
                label.setText(msg);
                label.setForeground(new Color(100, 200, 255));
                gameOverPanel.repaint();
            }
        }
    }

    public void triggerRestart() {
        // 오버레이 숨기기
        JRootPane root = SwingUtilities.getRootPane(parentPanel);
        if (root != null) {
            root.getGlassPane().setVisible(false);
        }

        // 실제 재시작 실행
        if (onExecuteRestart != null) {
            onExecuteRestart.run();
        }
    }

    // UIOverlayManager.triggerGameStart()
    public void triggerGameStart() {
        System.out.println("[UI] triggerGameStart called");
        hideOverlay();
        if (onStartGame != null) {
            System.out.println("[UI] Calling onStartGame");
            onStartGame.run();
        }
    }

    public String getSelectedMode() {
        return selectedMode;
    }

    public void createOverlay() {
        overlayPanel = new JPanel();
        overlayPanel.setLayout(new BoxLayout(overlayPanel, BoxLayout.Y_AXIS));
        overlayPanel.setBackground(new Color(30, 35, 45, 240));
        overlayPanel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(100, 150, 200), 2),
                BorderFactory.createEmptyBorder(30, 40, 30, 40)));

        statusLabel = new JLabel("Connecting...");
        statusLabel.setForeground(new Color(255, 200, 100));
        statusLabel.setFont(new Font("Arial", Font.BOLD, 16));
        statusLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        overlayPanel.add(statusLabel);
        overlayPanel.add(Box.createVerticalStrut(15));

        if (isServer) {
            createServerUI();
        } else {
            overlayPanel.add(Box.createVerticalStrut(40));
        }

        startButton = new JButton("Start Game");
        startButton.setEnabled(false);
        startButton.setFont(new Font("Arial", Font.BOLD, 16));
        startButton.setPreferredSize(new Dimension(180, 45));
        startButton.setAlignmentX(Component.CENTER_ALIGNMENT);
        startButton.setBackground(new Color(70, 70, 70));
        startButton.setForeground(Color.WHITE);
        startButton.setFocusPainted(false);
        startButton.addActionListener(e -> {
            // 서버가 GAME_START 메시지 전송
            if (isServer) {
                // NetworkManager를 통해 전송해야 함
            }
            onStartGame.run();
        });
        overlayPanel.add(startButton);

        SwingUtilities.invokeLater(() -> {
            JRootPane root = SwingUtilities.getRootPane(parentPanel);
            if (root == null)
                return;

            JPanel glass = new JPanel(null);
            glass.setOpaque(false);
            glass.add(overlayPanel);

            root.setGlassPane(glass);
            glass.setVisible(true);
            glass.setBounds(0, 0, root.getWidth(), root.getHeight());

            centerOverlay();
        });
    }

    private void createServerUI() {
        ipLabel = new JLabel("IP: " + getLocalIP() + ":8081");
        ipLabel.setForeground(new Color(100, 200, 255));
        ipLabel.setFont(new Font("Monospaced", Font.BOLD, 14));
        ipLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        overlayPanel.add(ipLabel);
        overlayPanel.add(Box.createVerticalStrut(20));

        JLabel modeLabel = new JLabel("Game Mode:");
        modeLabel.setForeground(Color.WHITE);
        modeLabel.setFont(new Font("Arial", Font.BOLD, 13));
        modeLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        overlayPanel.add(modeLabel);
        overlayPanel.add(Box.createVerticalStrut(8));

        modeSelector = new JComboBox<>(new String[] {
                "Normal",
                "Item",
                "Time Limit (3min)"
        });
        modeSelector.setSelectedItem(selectedMode);
        modeSelector.setLightWeightPopupEnabled(false);
        modeSelector.setMaximumSize(new Dimension(220, 40));
        modeSelector.setAlignmentX(Component.CENTER_ALIGNMENT);
        modeSelector.setBackground(new Color(70, 80, 95));
        modeSelector.setForeground(Color.WHITE);
        modeSelector.setFont(new Font("Arial", Font.BOLD, 15));
        modeSelector.setOpaque(true);
        modeSelector.setFocusable(false);
        modeSelector.setBorder(BorderFactory.createLineBorder(new Color(120, 180, 255), 3));

        modeSelector.setRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> list, Object value,
                    int index, boolean isSelected, boolean cellHasFocus) {
                JLabel label = (JLabel) super.getListCellRendererComponent(
                        list, value, index, isSelected, cellHasFocus);

                label.setFont(new Font("Arial", Font.BOLD, 15));
                label.setOpaque(true);

                if (isSelected) {
                    label.setBackground(new Color(80, 140, 220));
                    label.setForeground(Color.WHITE);
                } else if (index == -1) {
                    label.setBackground(new Color(70, 80, 95));
                    label.setForeground(Color.WHITE);
                } else {
                    label.setBackground(Color.WHITE);
                    label.setForeground(Color.BLACK);
                }

                label.setBorder(BorderFactory.createCompoundBorder(
                        BorderFactory.createMatteBorder(0, 0, 1, 0, new Color(200, 200, 200)),
                        BorderFactory.createEmptyBorder(8, 12, 8, 12)));

                return label;
            }
        });

        modeSelector.addActionListener(e -> {
            selectedMode = (String) modeSelector.getSelectedItem();
            onModeChanged.accept(selectedMode);
        });

        overlayPanel.add(modeSelector);
        overlayPanel.add(Box.createVerticalStrut(20));
    }

    private void centerOverlay() {
        JRootPane root = SwingUtilities.getRootPane(parentPanel);
        if (overlayPanel != null && root != null) {
            JPanel glass = (JPanel) root.getGlassPane();
            Dimension size = glass.getSize();

            int width = 400;
            int height = isServer ? 320 : 220;

            overlayPanel.setBounds(
                    (size.width - width) / 2,
                    (size.height - height) / 2,
                    width, height);
        }
    }

    public void updateStatus(String message) {
        SwingUtilities.invokeLater(() -> statusLabel.setText(message));
    }

    public void enableStartButton() {
        SwingUtilities.invokeLater(() -> {
            updateStatus("Ready! Press Start");
            startButton.setEnabled(true);
            startButton.setBackground(new Color(50, 180, 80));
        });
    }

    public void hideOverlay() {
        JRootPane rootPane = SwingUtilities.getRootPane(parentPanel);
        if (rootPane != null) {
            rootPane.getGlassPane().setVisible(false);
        }
    }

    private String getLocalIP() {
        try {
            return InetAddress.getLocalHost().getHostAddress();
        } catch (Exception e) {
            return "localhost";
        }
    }

    public void showGameOverOverlay(boolean iLost, int myScore, int oppScore,
            int myTotalLines, long gameStartTime) {
        System.out.println("[OVERLAY] showGameOverOverlay called: iLost=" + iLost);

        JRootPane root = SwingUtilities.getRootPane(parentPanel);
        if (root == null) {
            System.err.println("[OVERLAY] ERROR: root is null!");
            return;
        }

        long gameDuration = System.currentTimeMillis() - gameStartTime;
        int minutes = (int) (gameDuration / 60000);
        int seconds = (int) ((gameDuration % 60000) / 1000);

        JPanel glass = (JPanel) root.getGlassPane();
        System.out.println("[OVERLAY] Glass pane: " + glass);

        glass.removeAll();
        glass.setLayout(null);
        glass.setVisible(true); 
        System.out.println("[OVERLAY] Glass pane visible: " + glass.isVisible());

        gameOverPanel = createGameOverPanel(iLost, myScore, oppScore,
                myTotalLines, minutes, seconds);

        int w = 450, h = 420;
        gameOverPanel.setBounds(
                (parentPanel.getWidth() - w) / 2,
                (parentPanel.getHeight() - h) / 2,
                w, h);

        glass.add(gameOverPanel);
        System.out.println("[OVERLAY] Panel added at bounds: " + gameOverPanel.getBounds());

        glass.repaint();
        glass.revalidate();

        System.out.println("[OVERLAY] Glass pane component count: " + glass.getComponentCount());
    }

    public void showTimeLimitGameOverOverlay(boolean iWon, int myScore, int oppScore,
            int myTotalLines, long timeLimitSeconds) {
        JRootPane root = SwingUtilities.getRootPane(parentPanel);
        if (root == null)
            return;

        int minutes = (int) (timeLimitSeconds / 60);
        int seconds = (int) (timeLimitSeconds % 60);

        JPanel glass = (JPanel) root.getGlassPane();
        glass.removeAll();
        glass.setLayout(null);
        glass.setVisible(true);

        gameOverPanel = new JPanel();
        gameOverPanel.setLayout(new BoxLayout(gameOverPanel, BoxLayout.Y_AXIS));
        gameOverPanel.setBackground(new Color(20, 20, 25, 240));
        gameOverPanel.setBorder(BorderFactory.createEmptyBorder(40, 50, 40, 50));

        JLabel title = new JLabel(iWon ? "TIME'S UP - YOU WIN!" : "TIME'S UP - YOU LOSE");
        title.setFont(new Font("Arial", Font.BOLD, 28));
        title.setForeground(iWon ? new Color(80, 255, 80) : new Color(255, 80, 80));
        title.setAlignmentX(Component.CENTER_ALIGNMENT);
        gameOverPanel.add(title);
        gameOverPanel.add(Box.createVerticalStrut(30));

        JPanel statsPanel = createStatsPanel(myScore, oppScore, myTotalLines, minutes, seconds);
        statsPanel.setAlignmentX(Component.CENTER_ALIGNMENT);
        gameOverPanel.add(statsPanel);
        gameOverPanel.add(Box.createVerticalStrut(30));

        gameOverPanel.add(createButtonPanel());

        int w = 450, h = 420;
        gameOverPanel.setBounds(
                (parentPanel.getWidth() - w) / 2,
                (parentPanel.getHeight() - h) / 2,
                w, h);

        glass.add(gameOverPanel);
        glass.repaint();
        glass.revalidate();
    }

    private JPanel createGameOverPanel(boolean iLost, int myScore, int oppScore,
            int myTotalLines, int minutes, int seconds) {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBackground(new Color(20, 20, 25, 230));
        panel.setBorder(BorderFactory.createEmptyBorder(40, 40, 40, 40));

        JLabel title = new JLabel(iLost ? "YOU LOSE" : "YOU WIN");
        title.setFont(new Font("Arial", Font.BOLD, 32));
        title.setForeground(iLost ? new Color(255, 80, 80) : new Color(80, 255, 80));
        title.setAlignmentX(Component.CENTER_ALIGNMENT);
        panel.add(title);
        panel.add(Box.createVerticalStrut(30));

        JPanel statsPanel = createStatsPanel(myScore, oppScore, myTotalLines, minutes, seconds);
        statsPanel.setAlignmentX(Component.CENTER_ALIGNMENT);
        panel.add(statsPanel);
        panel.add(Box.createVerticalStrut(30));

        panel.add(createButtonPanel());

        return panel;
    }

    private JPanel createStatsPanel(int myScore, int oppScore, int myLines,
            int minutes, int seconds) {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setOpaque(false);

        addStatRow(panel, "Your Score", String.valueOf(myScore), new Color(100, 200, 255));
        addStatRow(panel, "Opponent Score", String.valueOf(oppScore), new Color(255, 150, 100));
        panel.add(Box.createVerticalStrut(10));
        addStatRow(panel, "Lines Cleared", String.valueOf(myLines), Color.WHITE);

        String timeStr = String.format("%d:%02d", minutes, seconds);
        addStatRow(panel, "Time Played", timeStr, Color.WHITE);

        return panel;
    }

    private void addStatRow(JPanel panel, String label, String value, Color valueColor) {
        JPanel row = new JPanel(new FlowLayout(FlowLayout.CENTER, 10, 5));
        row.setOpaque(false);

        JLabel labelText = new JLabel(label + ":");
        labelText.setFont(new Font("Arial", Font.PLAIN, 16));
        labelText.setForeground(new Color(180, 180, 180));
        row.add(labelText);

        JLabel valueText = new JLabel(value);
        valueText.setFont(new Font("Arial", Font.BOLD, 20));
        valueText.setForeground(valueColor);
        row.add(valueText);

        panel.add(row);
    }

    private JPanel createButtonPanel() {
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 15, 0));
        buttonPanel.setOpaque(false);

        JButton restartBtn = new JButton("Restart");
        restartBtn.setFont(new Font("Arial", Font.BOLD, 16));
        restartBtn.setPreferredSize(new Dimension(120, 45));
        restartBtn.setBackground(new Color(70, 150, 70));
        restartBtn.setForeground(Color.WHITE);
        restartBtn.setFocusPainted(false);
        restartBtn.addActionListener(e -> {
            onRestart.run();
            updateGameOverStatus("Waiting for opponent...");
        });
        buttonPanel.add(restartBtn);

        JButton exitBtn = new JButton("Exit");
        exitBtn.setFont(new Font("Arial", Font.BOLD, 16));
        exitBtn.setPreferredSize(new Dimension(120, 45));
        exitBtn.setBackground(new Color(150, 70, 70));
        exitBtn.setForeground(Color.WHITE);
        exitBtn.setFocusPainted(false);
        exitBtn.addActionListener(e -> {
            onCleanup.run();
            Window w = SwingUtilities.getWindowAncestor(parentPanel);
            if (w != null)
                w.dispose();
        });
        buttonPanel.add(exitBtn);

        return buttonPanel;
    }

    private void updateGameOverStatus(String msg) {
        if (gameOverPanel != null && gameOverPanel.getComponentCount() > 0) {
            Component c = gameOverPanel.getComponent(0);
            if (c instanceof JLabel label) {
                label.setText(msg);
                gameOverPanel.repaint();
            }
        }
    }
}