package versus;

import component.GameConfig;
import component.PausePanel;
import component.sidebar.HUDSidebar;
import logic.SoundManager;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;

/**
 * VersusPanel
 * - UI 레이아웃/라벨만 관리
 * - 실제 게임/이벤트/공격 규칙은 VersusGameManager가 담당
 * - OnlineVersusPanel 스타일로 UI 통일화
 */
public class VersusPanel extends JPanel {

    private VersusGameManager manager;
    private PausePanel pausePanel;
    private SoundManager soundManager;
    private final JLabel p1Queue = new JLabel("0");
    private final JLabel p2Queue = new JLabel("0");

    private final JLabel syncStatsLabel = new JLabel("");

    // 타이머 라벨 & 남은 시간
    private final JLabel timerLabel = new JLabel("02:00", SwingConstants.CENTER);
    private javax.swing.Timer timeAttackTimer;
    private int remainingSeconds = 120; // 2분 고정
    private JPanel timerPanel;

    private HUDSidebar p1Sidebar;
    private HUDSidebar p2Sidebar;

    private final GameConfig p1Config;
    private final GameConfig p2Config;
    private final Runnable backToMenu;

    // 🔹 선택된 게임 룰 (Normal / Item / Time Limit (3min) 등)
    private final String gameRule;

    // private Image bgImage;

    // ─── 게임 오버 오버레이 관련 ───
    private JComponent p1BoardRef;
    private JComponent p2BoardRef;
    private JPanel gameOverOverlay; // 보드만 어둡게 + WIN/LOSE 텍스트
    private JPanel resultDialogPanel; // 중앙 작은 결과 카드
    private VersusGameManager.GameResult lastResult;

    public VersusPanel(GameConfig p1Config, GameConfig p2Config, String gameRule) {
        this.p1Config = p1Config;
        this.p2Config = p2Config;
        this.gameRule = (gameRule != null) ? gameRule : "Normal";
        this.soundManager = SoundManager.getInstance();

        // // 배경 이미지 로드
        // try {
        // // 예시: src/main/resources/images/versus_bg.jpg
        // java.net.URL url = getClass().getResource("/images/versusBG.jpeg");
        // if (url != null) {
        // bgImage = new ImageIcon(url).getImage();
        // } else {
        // System.err.println("[VersusPanel] 배경 이미지 리소스를 찾을 수 없습니다:
        // /images/versus_bg.jpg");
        // }
        // } catch (Exception ex) {
        // ex.printStackTrace();
        // bgImage = null; // 이미지 로드 실패해도 게임은 돌아가도록
        // }

        setLayout(new BorderLayout(0, 0));
        setBackground(new Color(18, 22, 30));

        soundManager.stopBGM();
        soundManager.playBGM(SoundManager.BGM.VERSUS);

        // ───── 상단 타이머 / 여백 패널 ─────
        timerPanel = new JPanel(new BorderLayout());
        timerPanel.setOpaque(false);
        timerPanel.setPreferredSize(new Dimension(0, 80)); // 예전 topHud 높이랑 동일

        timerLabel.setForeground(Color.WHITE);
        timerLabel.setFont(new Font("Arial", Font.BOLD, 26));
        timerLabel.setHorizontalAlignment(SwingConstants.CENTER);

        timerPanel.add(timerLabel, BorderLayout.CENTER);
        add(timerPanel, BorderLayout.NORTH);

        this.backToMenu = () -> {
            System.out.println("[VersusPanel] backToMenu called");

            // 게임 정리
            if (manager != null) {
                manager.cleanup();
            }
            stopTimeAttackTimer();
            soundManager.stopBGM();

            // VersusFrame 찾아서 제대로 종료
            JFrame frame = (JFrame) SwingUtilities.getWindowAncestor(this);
            if (frame instanceof VersusFrame) {
                VersusFrame vf = (VersusFrame) frame;
                // 사용자가 나가기를 선택한 것으로 플래그 설정
                vf.closeAfterGameOver();
            } else if (frame != null) {
                frame.dispose();
            }
        };

        // ───── 가운데 영역(좌 HUD + 보드 2개 + 우 HUD) ─────
        JPanel centerContainer = new JPanel(new BorderLayout(0, 0));
        // centerContainer.setBackground(new Color(18, 22, 30));
        centerContainer.setOpaque(false);

        // 사이드바
        p1Sidebar = new HUDSidebar();
        p1Sidebar.setPreferredSize(new Dimension(160, 0));
        p1Sidebar.setOpaque(false);
        centerContainer.add(p1Sidebar, BorderLayout.WEST);

        p2Sidebar = new HUDSidebar();
        p2Sidebar.setPreferredSize(new Dimension(160, 0));
        p2Sidebar.setOpaque(false);
        centerContainer.add(p2Sidebar, BorderLayout.EAST);

        p1Sidebar.showTime(false);
        p2Sidebar.showTime(false);

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
                }),
                this::handleGameFinished // ★ 게임 종료 콜백
        );

        // 가운데 보드 2개
        JPanel boardsContainer = new JPanel(new GridBagLayout());
        // boardsContainer.setBackground(new Color(18, 22, 30));
        // boardsContainer.setBorder(BorderFactory.createEmptyBorder(10, 20, 10, 20));
        boardsContainer.setOpaque(false);

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.weightx = 1.0;
        gbc.weighty = 1.0;
        gbc.anchor = GridBagConstraints.CENTER;
        gbc.insets = new Insets(0, 10, 0, 10);

        JPanel boardsPanel = new JPanel(new GridLayout(1, 2, 40, 0));
        // boardsPanel.setBackground(new Color(18, 22, 30));
        boardsPanel.setOpaque(false);
        JComponent p1Board = manager.getP1Component();
        JComponent p2Board = manager.getP2Component();

        // 보드 레퍼런스 저장 (오버레이에서 사용)
        this.p1BoardRef = p1Board;
        this.p2BoardRef = p2Board;

        boardsPanel.add(createLabeledBoard("P1", p1Board));
        boardsPanel.add(createLabeledBoard("P2", p2Board));

        boardsContainer.add(boardsPanel, gbc);
        centerContainer.add(boardsContainer, BorderLayout.CENTER);

        add(centerContainer, BorderLayout.CENTER);

        // 시작 시점에 NEXT 한 번 동기화
        SwingUtilities.invokeLater(() -> {
            p1Sidebar.setNextBlocks(manager.getP1NextBlocks());
            p2Sidebar.setNextBlocks(manager.getP2NextBlocks());
        });

        // === 초기 HUD 동기화 ===

        // 🔹 타임어택 여부 판정
        boolean isTimeAttack = p1Config.mode() == GameConfig.Mode.TIME_ATTACK
                || p2Config.mode() == GameConfig.Mode.TIME_ATTACK
                || (this.gameRule != null && this.gameRule.contains("Time"));

        if (timerPanel != null) {
            timerPanel.setVisible(true);
            timerLabel.setVisible(isTimeAttack);
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
            if (frame == null)
                return;

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

                        VersusPanel newPanel = new VersusPanel(p1Config, p2Config, this.gameRule);
                        frame.setContentPane(newPanel);
                        frame.pack();
                        frame.setLocationRelativeTo(null);

                        // 🔹 새 패널에도 오버레이 부착
                        newPanel.attachOverlayToFrame(frame);
                    },
                    () -> { // EXIT 
                        manager.pauseBoth();
                        stopTimeAttackTimer();

                        // VersusFrame을 제대로 종료
                        if (frame instanceof VersusFrame) {
                            VersusFrame vf = (VersusFrame) frame;

                            // cleanup 먼저
                            if (manager != null) {
                                manager.cleanup();
                            }
                            soundManager.stopBGM();

                            vf.closeAfterGameOver(); // 메뉴로 복귀 처리
                        } else {
                            backToMenu.run();
                        }
                    });
            setupPauseKeyBinding();
        });

        // ★ 게임 오버 오버레이 초기화 (레이어드팬에 추가)
        initGameOverOverlay();
    }

    // ─────────────────────────────────────────────────────────────
    // 게임 오버 시 연출: 보드만 어둡게 + WIN/LOSE 텍스트 + 결과 카드
    // ─────────────────────────────────────────────────────────────
    private void initGameOverOverlay() {
        gameOverOverlay = new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                if (lastResult == null)
                    return;

                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                        RenderingHints.VALUE_ANTIALIAS_ON);

                drawBoardOverlay(g2, p1BoardRef, getResultLabelForPlayer(Player.Id.P1));
                drawBoardOverlay(g2, p2BoardRef, getResultLabelForPlayer(Player.Id.P2));

                g2.dispose();
            }
        };
        gameOverOverlay.setOpaque(false);
        gameOverOverlay.setVisible(false);
    }

    private String getResultLabelForPlayer(Player.Id id) {
        if (lastResult == null)
            return "";
        if (lastResult.winner == null) {
            return "DRAW";
        }
        if (lastResult.winner == id)
            return "WIN!";
        if (lastResult.loser == id)
            return "LOSE!";
        return "";
    }

    private void drawBoardOverlay(Graphics2D g2, JComponent board, String text) {
        if (board == null)
            return;

        Rectangle r = SwingUtilities.convertRectangle(
                board.getParent(),
                board.getBounds(),
                gameOverOverlay);

        // 어두운 반투명 사각형 (보드 영역만)
        g2.setColor(new Color(0, 0, 0, 170));
        g2.fillRect(r.x, r.y, r.width, r.height);

        // WIN / LOSE / DRAW 텍스트
        if (text != null && !text.isEmpty()) {
            g2.setFont(new Font("Arial", Font.BOLD, 32));
            g2.setColor(new Color(255, 255, 255, 230));
            FontMetrics fm = g2.getFontMetrics();
            int tx = r.x + (r.width - fm.stringWidth(text)) / 2;
            int ty = r.y + (r.height + fm.getAscent()) / 2;
            g2.drawString(text, tx, ty);
        }
    }

    /** VersusGameManager 에서 게임 종료 시 호출되는 콜백 */
    private void handleGameFinished(VersusGameManager.GameResult result) {
        this.lastResult = result;

        // 타이머/사운드 정지
        stopTimeAttackTimer();
        soundManager.stopBGM();

        if (gameOverOverlay != null) {
            gameOverOverlay.setVisible(true);
            gameOverOverlay.repaint();
        }

        // 1.5초 후 결과 카드 표시
        javax.swing.Timer t = new javax.swing.Timer(1500, e -> {
            ((javax.swing.Timer) e.getSource()).stop();
            showResultDialog();
        });
        t.setRepeats(false);
        t.start();
    }

    /** 중앙 작은 결과 카드 (점수 + 다시하기 / 홈으로 버튼) */
    private void showResultDialog() {
        if (gameOverOverlay == null || lastResult == null)
            return;

        if (resultDialogPanel != null) {
            gameOverOverlay.remove(resultDialogPanel);
        }

        resultDialogPanel = new JPanel();
        resultDialogPanel.setLayout(new BoxLayout(resultDialogPanel, BoxLayout.Y_AXIS));
        resultDialogPanel.setBackground(new Color(30, 38, 56));
        // 회색 테두리 + 안쪽 여백
        Color borderGray = new Color(150, 160, 175); // 원하는 톤으로 조절
        resultDialogPanel.setBorder(
                BorderFactory.createCompoundBorder(
                        BorderFactory.createLineBorder(borderGray, 1, true), // 바깥 회색 선
                        new EmptyBorder(16, 24, 16, 24) // 안쪽 여백
                ));

        JLabel title = new JLabel("RESULT", SwingConstants.CENTER);
        title.setAlignmentX(Component.CENTER_ALIGNMENT);
        title.setForeground(Color.WHITE);
        title.setFont(new Font("Arial", Font.BOLD, 18));

        String subtitleText;
        if (lastResult.winner == null) {
            subtitleText = "DRAW";
        } else if (lastResult.winner == Player.Id.P1) {
            subtitleText = "P1 WINS!";
        } else {
            subtitleText = manager.isAIMode() ? "AI WINS!" : "P2 WINS!";
        }

        JLabel subtitle = new JLabel(subtitleText, SwingConstants.CENTER);
        subtitle.setAlignmentX(Component.CENTER_ALIGNMENT);
        subtitle.setForeground(new Color(200, 220, 240));
        subtitle.setFont(new Font("Arial", Font.PLAIN, 14));

        JLabel scoreLabel = new JLabel(
                "P1: " + lastResult.p1Score + "    |    P2: " + lastResult.p2Score,
                SwingConstants.CENTER);
        scoreLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        scoreLabel.setForeground(Color.WHITE);
        scoreLabel.setFont(new Font("Arial", Font.PLAIN, 13));

        JPanel btnPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 16, 0));
        btnPanel.setOpaque(false);

        JButton retry = new JButton("다시하기");
        JButton home = new JButton("홈으로");

        Dimension btnSize = new Dimension(100, 30);
        retry.setPreferredSize(btnSize);
        home.setPreferredSize(btnSize);

        btnPanel.add(retry);
        btnPanel.add(home);

        resultDialogPanel.add(title);
        resultDialogPanel.add(Box.createVerticalStrut(6));
        resultDialogPanel.add(subtitle);
        resultDialogPanel.add(Box.createVerticalStrut(8));
        resultDialogPanel.add(scoreLabel);
        resultDialogPanel.add(Box.createVerticalStrut(12));
        resultDialogPanel.add(btnPanel);

        gameOverOverlay.setLayout(null);
        Dimension pref = resultDialogPanel.getPreferredSize();
        int x = (gameOverOverlay.getWidth() - pref.width) / 2;
        int y = (gameOverOverlay.getHeight() - pref.height) / 2;
        resultDialogPanel.setBounds(x, y, pref.width, pref.height);

        gameOverOverlay.add(resultDialogPanel);
        gameOverOverlay.revalidate();
        gameOverOverlay.repaint();

        // 버튼 콜백
        retry.addActionListener(e -> {
            // 1) 이 패널 쪽 상태 정리
            lastResult = null;

            if (resultDialogPanel != null && gameOverOverlay != null) {
                gameOverOverlay.remove(resultDialogPanel);
                resultDialogPanel = null;
            }

            if (gameOverOverlay != null) {
                gameOverOverlay.setVisible(false);
            }

            // 2) 프레임에서 기존 오버레이 제거 + 새 VersusPanel로 완전 교체
            JFrame frame = (JFrame) SwingUtilities.getWindowAncestor(VersusPanel.this);
            if (frame != null) {
                JLayeredPane lp = frame.getLayeredPane();
                if (gameOverOverlay != null) {
                    lp.remove(gameOverOverlay);
                }
                lp.revalidate();
                lp.repaint();

                // 새 게임 패널 생성
                VersusPanel newPanel = new VersusPanel(p1Config, p2Config, this.gameRule);
                frame.setContentPane(newPanel);

                // 레이아웃 다시 계산 + 위치 보정
                frame.pack();
                frame.setLocationRelativeTo(null);

                // 🔹 새 게임의 오버레이를 레이어드팬에 다시 붙이기
                newPanel.attachOverlayToFrame(frame);
            }
        });

        home.addActionListener(e -> {
            JFrame frame = (JFrame) SwingUtilities.getWindowAncestor(VersusPanel.this);
            if (frame != null) {
                JLayeredPane lp = frame.getLayeredPane();
                lp.remove(gameOverOverlay);
                lp.revalidate();
                lp.repaint();
            }
            backToMenu.run();
        });
    }

    // ─────────────────────────────────────────────────────────────
    // 나머지 기존 로직 (타임어택 / Pause 등)
    // ─────────────────────────────────────────────────────────────

    private void setupPauseKeyBinding() {
        InputMap im = getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW);
        ActionMap am = getActionMap();

        im.put(KeyStroke.getKeyStroke("P"), "togglePause");
        im.put(KeyStroke.getKeyStroke("R"), "togglePause");

        am.put("togglePause", new AbstractAction() {
            @Override
            public void actionPerformed(java.awt.event.ActionEvent e) {
                if (pausePanel == null)
                    return;

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

        int totalWidth = (boardWidth * 2) + (160 * 2) + 100;
        int totalHeight = boardHeight + 180;

        return new Dimension(totalWidth, totalHeight);
    }

    private JPanel createLabeledBoard(String title, JComponent board) {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setOpaque(false);

        JLabel label = new JLabel(title, SwingConstants.CENTER);
        label.setForeground(new Color(210, 220, 240));
        label.setFont(new Font("Arial", Font.BOLD, 20));
        label.setBorder(BorderFactory.createEmptyBorder(0, 0, 6, 0)); // 보드와 살짝 간격

        panel.add(label, BorderLayout.NORTH);
        panel.add(board, BorderLayout.CENTER);

        return panel;
    }

    void attachOverlayToFrame(JFrame frame) {
        if (frame == null || gameOverOverlay == null)
            return;

        JLayeredPane lp = frame.getLayeredPane();

        // 이미 붙어 있는지 한 번 체크 (중복 add 방지)
        boolean alreadyAdded = false;
        for (Component c : lp.getComponentsInLayer(JLayeredPane.POPUP_LAYER)) {
            if (c == gameOverOverlay) {
                alreadyAdded = true;
                break;
            }
        }
        if (!alreadyAdded) {
            lp.add(gameOverOverlay, JLayeredPane.POPUP_LAYER);
        }

        gameOverOverlay.setBounds(0, 0, lp.getWidth(), lp.getHeight());

        lp.addComponentListener(new ComponentAdapter() {
            @Override
            public void componentResized(ComponentEvent e) {
                gameOverOverlay.setBounds(0, 0, lp.getWidth(), lp.getHeight());
            }
        });
    }

    // @Override
    // protected void paintComponent(Graphics g) {
    // super.paintComponent(g);

    // if (bgImage == null) return;

    // Graphics2D g2 = (Graphics2D) g.create();

    // int pw = getWidth();
    // int ph = getHeight();
    // int iw = bgImage.getWidth(null);
    // int ih = bgImage.getHeight(null);

    // double panelRatio = (double) pw / ph;
    // double imageRatio = (double) iw / ih;

    // int drawW, drawH;

    // // 패널이 더 넓으면 → 높이에 맞춰서 스케일 후 좌우 크롭
    // if (panelRatio > imageRatio) {
    // drawH = ph;
    // drawW = (int) (ih * panelRatio);
    // } else { // 패널이 더 세로로 길면 → 넓이에 맞춰서 스케일 후 상하 크롭
    // drawW = pw;
    // drawH = (int) (pw / imageRatio);
    // }

    // int x = (pw - drawW) / 2;
    // int y = (ph - drawH) / 2;

    // g2.drawImage(bgImage, x, y, drawW, drawH, this);
    // g2.dispose();
    // }
    public void stopGame() {
        System.out.println("[VersusPanel] Stopping game...");

        if (manager != null) {
            manager.pauseBoth();
        }

        stopTimeAttackTimer();
        soundManager.stopBGM();

        System.out.println("[VersusPanel] Game stopped");
    }

    public void cleanup() {
        System.out.println("[VersusPanel] Starting cleanup...");

        // 타이머 정리
        stopTimeAttackTimer();

        // 매니저 정리
        if (manager != null) {
            manager.cleanup();
        }

        // BGM 정지
        if (soundManager != null) {
            soundManager.stopBGM();
        }

        System.out.println("[VersusPanel] Cleanup completed");
    }

}
