package component;

import logic.BoardLogic;
import logic.GameState;
import blocks.Block;
import component.items.*;
import component.config.Settings;
import component.config.Settings.Action;

import static component.config.Settings.Action;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import logic.MovementService;
import component.score.*;
import launcher.GameLauncher;

public class Board extends JFrame {

    public boolean isRestarting = false;
    private PausePanel pausePanel;
    private static final long serialVersionUID = 1L;
    private final ScoreBoard scoreBoard = ScoreBoard.createDefault();
    private JPanel overlay;
    private JPanel dialogPanel;
    private NameInputOverlay nameInputOverlay;
    private ScoreboardOverlay scoreboardOverlay; 
    private final GameConfig config;
    private java.util.function.Function<Color, Color> colorMapper = c -> c;

    // === 시각용 상수 ===
    private static final int CELL_SIZE = 35;
    private static final int CELL_GAP = 2;
    private static final int ARC = 8;

    private static final Color BG_DARK = new Color(20, 25, 35);
    private static final Color BG_PANEL = new Color(30, 35, 50);
    private static final Color BG_GAME = new Color(25, 30, 42);
    private static final Color ACCENT = new Color(100, 255, 218);
    private static final Color TEXT_PRIMARY = new Color(230, 237, 243);
    private static final Color TEXT_SECONDARY = new Color(136, 146, 176);
    private static final Color GRID_LINE = new Color(40, 45, 60);

    // === 로직 참조 ===
    private final BoardLogic logic;

    // === HUD ===
    private final JLabel scoreLabel = new JLabel("0");
    private final JLabel levelLabel = new JLabel("1");
    private final JLabel linesLabel = new JLabel("0");
    private final JPanel nextPanel = new JPanel();

    private final MovementService move;
    private boolean isFullScreen = false;
    private Rectangle normalBounds;
    private GraphicsDevice graphicsDevice;
    private ColorBlindPalette.Mode colorMode = ColorBlindPalette.Mode.NORMAL;

    private final GamePanel gamePanel;
    private final javax.swing.Timer timer;

    private Settings settings;
    private final java.util.Map<Action, Integer> boundKeys = new java.util.EnumMap<>(Action.class);

    private final HUDSidebar sidebar = new HUDSidebar();

    private static final String ACT_LEFT = "left";
    private static final String ACT_RIGHT = "right";
    private static final String ACT_DOWN = "down";
    private static final String ACT_ROTATE = "rotate";
    private static final String ACT_DROP = "drop";

    public Board(GameConfig config) {
        super("SeoulTech SE Tetris");
        this.config = config;

        // === 로직 초기화 ===
        logic = new BoardLogic(score -> showGameOver(score));
        logic.setOnFrameUpdate(this::drawBoard);

        move = new MovementService(logic.getState());

        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setBackground(BG_DARK);

        graphicsDevice = GraphicsEnvironment.getLocalGraphicsEnvironment()
                .getDefaultScreenDevice();

        JPanel root = new JPanel(new BorderLayout(20, 0));
        root.setBackground(BG_DARK);
        root.setBorder(new EmptyBorder(20, 20, 20, 20));

        // 중앙 게임 화면
        gamePanel = new GamePanel();
        gamePanel.setMaximumSize(gamePanel.getPreferredSize());
        gamePanel.setMinimumSize(gamePanel.getPreferredSize());
        root.add(gamePanel, BorderLayout.CENTER);

        // 오른쪽 HUD
        JPanel rightPanel = new JPanel();
        rightPanel.setLayout(new BoxLayout(rightPanel, BoxLayout.Y_AXIS));
        rightPanel.setBackground(BG_DARK);
        rightPanel.setBorder(new EmptyBorder(0, 10, 0, 0));

        JLabel titleLabel = new JLabel("TETRIS");
        titleLabel.setFont(new Font("Arial", Font.BOLD, 32));
        titleLabel.setForeground(ACCENT);
        titleLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        rightPanel.add(titleLabel);
        rightPanel.add(Box.createRigidArea(new Dimension(0, 30)));

        rightPanel.add(createStatPanel("SCORE", scoreLabel));
        rightPanel.add(Box.createRigidArea(new Dimension(0, 15)));
        rightPanel.add(createStatPanel("LEVEL", levelLabel));
        rightPanel.add(Box.createRigidArea(new Dimension(0, 15)));
        rightPanel.add(createStatPanel("LINES", linesLabel));
        rightPanel.add(Box.createRigidArea(new Dimension(0, 30)));

        JLabel nextLabel = new JLabel("NEXT");
        nextLabel.setFont(new Font("Arial", Font.BOLD, 18));
        nextLabel.setForeground(TEXT_PRIMARY);
        nextLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        rightPanel.add(nextLabel);
        rightPanel.add(Box.createRigidArea(new Dimension(0, 10)));

        nextPanel.setBackground(BG_DARK);
        nextPanel.setAlignmentX(Component.CENTER_ALIGNMENT);
        rightPanel.add(nextPanel);

        rightPanel.add(Box.createVerticalGlue());
        rightPanel.add(createControlsPanel());
        root.add(rightPanel, BorderLayout.EAST);

        // logic.setOnNextQueueUpdate(nextBlocks ->
        //         SwingUtilities.invokeLater(() -> updateNextHUD(nextBlocks)));

        SwingUtilities.invokeLater(() -> updateNextHUD(logic.getNextBlocks()));
        updateNextHUD(logic.getNextBlocks()); 

        add(root);
        setupKeys(gamePanel);

        pack();
        setLocationRelativeTo(null);
        setVisible(true);

        // === 오버레이 생성 & 부착 ===
        initOverlay();

        // 프레임/레이아웃 계산이 끝난 뒤 크기 맞추기
        addComponentListener(new java.awt.event.ComponentAdapter() {
            @Override
            public void componentResized(java.awt.event.ComponentEvent e) {
                if (overlay != null)
                    overlay.setBounds(0, 0, getWidth(), getHeight());
            }

            @Override
            public void componentShown(java.awt.event.ComponentEvent e) {
                if (overlay != null)
                    overlay.setBounds(0, 0, getWidth(), getHeight());
            }
        });

        pack();
        setLocationRelativeTo(null);
        setVisible(true);

        // === 드롭 타이머 ===
        timer = new javax.swing.Timer(logic.getDropInterval(), e -> {
            if (!logic.isGameOver()) {
                logic.moveDown();
                drawBoard();
            }
        });
        timer.start();
        SwingUtilities.invokeLater(() -> {
            setupKeys(gamePanel);
            requestGameFocus();
        });

        // === 일시정지 패널 ===
        pausePanel = new PausePanel(
                this,
                () -> { // Resume
                    timer.start();
                    pausePanel.hidePanel();
                    setTitle("TETRIS");
                },
                () -> { // Restart
                    isRestarting = true;
                    timer.stop();
                    dispose(); // 현재 창 닫기
                    new Board(config); // 새 게임 시작
                },

                () -> { // Exit to Menu
                    timer.stop();
                    dispose();
                    new GameLauncher();
                });
    }

    private JPanel createStatPanel(String label, JLabel valueLabel) {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBackground(BG_PANEL);
        panel.setBorder(new EmptyBorder(15, 20, 15, 20));
        panel.setMaximumSize(new Dimension(200, 80));

        JLabel labelComp = new JLabel(label);
        labelComp.setFont(new Font("Arial", Font.BOLD, 12));
        labelComp.setForeground(TEXT_SECONDARY);
        labelComp.setAlignmentX(Component.CENTER_ALIGNMENT);

        valueLabel.setFont(new Font("Arial", Font.BOLD, 28));
        valueLabel.setForeground(TEXT_PRIMARY);
        valueLabel.setAlignmentX(Component.CENTER_ALIGNMENT);

        panel.add(labelComp);
        panel.add(Box.createRigidArea(new Dimension(0, 5)));
        panel.add(valueLabel);
        return panel;
    }

    private JPanel createControlsPanel() {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBackground(BG_PANEL);
        panel.setBorder(new EmptyBorder(15, 15, 15, 15));
        panel.setMaximumSize(new Dimension(200, 230));

        JLabel titleLabel = new JLabel("CONTROLS");
        titleLabel.setFont(new Font("Arial", Font.BOLD, 12));
        titleLabel.setForeground(TEXT_SECONDARY);
        titleLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        panel.add(titleLabel);
        panel.add(Box.createRigidArea(new Dimension(0, 10)));

        String[] controls = {
                "← → Move", "↑ Rotate", "↓ Soft Drop",
                "SPACE Hard Drop", "P Pause",
                "F11 Full Screen", "ESC Exit"
        };
        for (String control : controls) {
            JLabel label = new JLabel(control);
            label.setFont(new Font("Arial", Font.PLAIN, 11));
            label.setForeground(TEXT_SECONDARY);
            label.setAlignmentX(Component.CENTER_ALIGNMENT);
            panel.add(label);
            panel.add(Box.createRigidArea(new Dimension(0, 5)));
        }
        return panel;
    }

    private void setupKeys(JComponent comp) {
        // 플레이(이동)용 키맵: 포커스 조상 기준
        InputMap imPlay = comp.getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT);
        // 전역 키맵: 윈도우 전체 기준
        InputMap imGlobal = comp.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW);
        ActionMap am = comp.getActionMap();

        imPlay.put(KeyStroke.getKeyStroke("LEFT"), ACT_LEFT);
        imPlay.put(KeyStroke.getKeyStroke("RIGHT"), ACT_RIGHT);
        imPlay.put(KeyStroke.getKeyStroke("DOWN"), ACT_DOWN);
        imPlay.put(KeyStroke.getKeyStroke("UP"), ACT_ROTATE);
        imPlay.put(KeyStroke.getKeyStroke("SPACE"), ACT_DROP);

        am.put(ACT_LEFT, new AbstractAction() {
            public void actionPerformed(ActionEvent e) {
                logic.moveLeft();
                drawBoard();
            }
        });
        am.put(ACT_RIGHT, new AbstractAction() {
            public void actionPerformed(ActionEvent e) {
                logic.moveRight();
                drawBoard();
            }
        });
        am.put(ACT_DOWN, new AbstractAction() {
            public void actionPerformed(ActionEvent e) {
                logic.moveDown();
                drawBoard();
            }
        });
        am.put(ACT_ROTATE, new AbstractAction() {
            public void actionPerformed(ActionEvent e) {
                logic.rotateBlock();
                drawBoard();
            }
        });
        am.put(ACT_DROP, new AbstractAction() {
            public void actionPerformed(ActionEvent e) {
                logic.hardDrop();
                drawBoard();
            }
        });

        imGlobal.put(KeyStroke.getKeyStroke("P"), "pause");
        imGlobal.put(KeyStroke.getKeyStroke(KeyEvent.VK_F11, 0), "fullscreen");
        imGlobal.put(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), "exit");
        imGlobal.put(KeyStroke.getKeyStroke("C"), "toggleColorBlind");
        imGlobal.put(KeyStroke.getKeyStroke("1"), "debugLineClear");
        imGlobal.put(KeyStroke.getKeyStroke("2"), "debugWeight");
        imGlobal.put(KeyStroke.getKeyStroke("3"), "debugSpinLock");
        imGlobal.put(KeyStroke.getKeyStroke("4"), "debugColorBomb");
        imGlobal.put(KeyStroke.getKeyStroke("5"), "debugLightning");

        am.put("pause", new AbstractAction() {
            public void actionPerformed(ActionEvent e) {
                if (pausePanel.isVisible()) {
                    pausePanel.hidePanel();
                    timer.start();
                    setTitle("TETRIS");
                } else {
                    timer.stop();
                    setTitle("TETRIS (PAUSED)");
                    pausePanel.showPanel();
                }
            }
        });

        am.put("toggleColorBlind", new AbstractAction() {
            public void actionPerformed(ActionEvent e) {
                switch (colorMode) {
                    case NORMAL -> colorMode = ColorBlindPalette.Mode.PROTAN;
                    case PROTAN -> colorMode = ColorBlindPalette.Mode.DEUTER;
                    case DEUTER -> colorMode = ColorBlindPalette.Mode.TRITAN;
                    case TRITAN -> colorMode = ColorBlindPalette.Mode.NORMAL;
                }
                setTitle("TETRIS - " + colorMode.name() + " mode");
                drawBoard();
            }
        });

        // 디버그 키 동작 ===
        am.put("debugLineClear", new AbstractAction() {
            public void actionPerformed(ActionEvent e) {
                if (!logic.isItemMode())
                    return; // 일반모드에서는 무시
                logic.debugSetNextItem(new LineClearItem(logic.getCurr()));
                System.out.println("🧪 Debug: 다음 블록 = LineClearItem");
                drawBoard();
            }
        });

        am.put("debugWeight", new AbstractAction() {
            public void actionPerformed(ActionEvent e) {
                if (!logic.isItemMode())
                    return;
                logic.debugSetNextItem(new WeightItem());
                System.out.println("🧪 Debug: 다음 블록 = WeightItem");
                drawBoard();
            }
        });

        am.put("debugSpinLock", new AbstractAction() {
            public void actionPerformed(ActionEvent e) {
                if (!logic.isItemMode())
                    return;
                logic.debugSetNextItem(new SpinLockItem(logic.getCurr()));
                System.out.println("🧪 Debug: 다음 블록 = SpinLockItem (회전금지)");
                drawBoard();
            }
        });
        am.put("debugColorBomb", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                if (!logic.isItemMode())
                    return;
                logic.debugSetNextItem(new ColorBombItem(logic.getCurr()));

                System.out.println("🧪 Debug: 다음 블록 = ColorBombItem (색상 폭탄)");
                drawBoard();
            }
        });

        am.put("debugLightning", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                if (!logic.isItemMode())
                    return;
                logic.debugSetNextItem(new LightningItem());
                System.out.println("🧪 Debug: 다음 블록 = LightningItem (번개)");
                drawBoard();
            }
        });

        am.put("fullscreen", new AbstractAction() {
            public void actionPerformed(ActionEvent e) {
                toggleFullScreen();
            }
        });
        am.put("exit", new AbstractAction() {
            public void actionPerformed(ActionEvent e) {
                dispose();
            }
        });
    }

    // === 화면 갱신 ===
    public void drawBoard() {
        scoreLabel.setText(String.valueOf(logic.getScore()));
        levelLabel.setText(String.valueOf(logic.getLevel()));
        linesLabel.setText(String.valueOf(logic.getLinesCleared()));

        timer.setDelay(logic.getDropInterval());

        // === 디버깅: 다음 블록 확인 ===
        List<Block> nextBlocks = logic.getNextBlocks();

        updateNextHUD(nextBlocks);
        gamePanel.repaint();
    }

    private void updateNextHUD(List<Block> nextBlocks) {

        nextBlocks = nextBlocks.subList(0, Math.min(3, nextBlocks.size()));

        nextPanel.removeAll();
        nextPanel.setLayout(new GridLayout(nextBlocks.size(), 1, 0, 10));

        for (int idx = 0; idx < nextBlocks.size(); idx++) {
            Block b = nextBlocks.get(idx);

            JPanel container = new JPanel(new BorderLayout());
            container.setBackground(BG_PANEL);
            container.setPreferredSize(new Dimension(120, 80));

            JPanel blockPanel = new JPanel() {
                @Override
                protected void paintComponent(Graphics g) {
                    super.paintComponent(g);

                    Graphics2D g2 = (Graphics2D) g;

                    g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                            RenderingHints.VALUE_ANTIALIAS_ON);

                    int blockSize = 18;
                    int totalWidth = b.width() * blockSize;
                    int totalHeight = b.height() * blockSize;
                    int offsetX = (getWidth() - totalWidth) / 2;
                    int offsetY = (getHeight() - totalHeight) / 2;

                    // 배경 (디버깅용으로 더 진하게)
                    g2.setColor(new Color(60, 65, 80));
                    g2.fillRoundRect(offsetX - 5, offsetY - 5,
                            totalWidth + 10, totalHeight + 10, 8, 8);

                    // 블록 그리기
                    int cellsDrawn = 0;
                    for (int j = 0; j < b.height(); j++) {
                        for (int i = 0; i < b.width(); i++) {
                            if (b.getShape(i, j) == 1) {
                                cellsDrawn++;
                                int x = offsetX + i * blockSize;
                                int y = offsetY + j * blockSize;

                                g2.setColor(b.getColor());
                                g2.fillRoundRect(x, y, blockSize - 2, blockSize - 2, 4, 4);

                                g2.setColor(new Color(255, 255, 255, 60));
                                g2.fillRoundRect(x, y, blockSize - 2, (blockSize - 2) / 3, 4, 4);

                                g2.setColor(new Color(0, 0, 0, 40));
                                g2.fillRoundRect(x, y + (blockSize - 2) * 2 / 3,
                                        blockSize - 2, (blockSize - 2) / 3, 4, 4);
                            }
                        }
                    }

                }
            };
            blockPanel.setBackground(BG_PANEL);
            blockPanel.setPreferredSize(new Dimension(120, 80));

            container.add(blockPanel, BorderLayout.CENTER);
            nextPanel.add(container);
        }

        nextPanel.revalidate();
        nextPanel.repaint();
    }

    private void showGameOver(int score) {
        timer.stop();
        setStatus("GAME OVER! Score: " + score);
        showNameInputOverlay(score);

    }

    private void toggleFullScreen() {
        if (!isFullScreen) {
            normalBounds = getBounds();
            dispose();
            setUndecorated(true);
            if (graphicsDevice.isFullScreenSupported())
                graphicsDevice.setFullScreenWindow(this);
            else
                setExtendedState(JFrame.MAXIMIZED_BOTH);
            setVisible(true);
            isFullScreen = true;
        } else {
            if (graphicsDevice.isFullScreenSupported())
                graphicsDevice.setFullScreenWindow(null);
            dispose();
            setUndecorated(false);
            if (normalBounds != null)
                setBounds(normalBounds);
            setVisible(true);
            isFullScreen = false;
        }
    }

    /** === 내부 패널: 게임판 렌더링 === */
    private class GamePanel extends JPanel {
        GamePanel() {
            setPreferredSize(new Dimension(BoardLogic.WIDTH * CELL_SIZE, BoardLogic.HEIGHT * CELL_SIZE));
            setBackground(BG_GAME);
            setBorder(BorderFactory.createLineBorder(GRID_LINE, 3));
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            Color[][] grid = logic.getBoard();
            Color[][] fade = logic.getFadeLayer();

            // === 배경 격자 ===
            g2.setColor(GRID_LINE);
            for (int r = 0; r <= BoardLogic.HEIGHT; r++)
                g2.drawLine(0, r * CELL_SIZE, BoardLogic.WIDTH * CELL_SIZE, r * CELL_SIZE);
            for (int c = 0; c <= BoardLogic.WIDTH; c++)
                g2.drawLine(c * CELL_SIZE, 0, c * CELL_SIZE, BoardLogic.HEIGHT * CELL_SIZE);

            // === 고정 블록 ===
            for (int r = 0; r < BoardLogic.HEIGHT; r++)
                for (int c = 0; c < BoardLogic.WIDTH; c++)
                    if (grid[r][c] != null)
                        drawCell(g2, c, r, grid[r][c], null);

            // === 잔상 (fade layer) ===
            for (int r = 0; r < BoardLogic.HEIGHT; r++) {
                for (int c = 0; c < BoardLogic.WIDTH; c++) {
                    if (fade[r][c] != null) {
                        int px = c * CELL_SIZE + CELL_GAP;
                        int py = r * CELL_SIZE + CELL_GAP;
                        int size = CELL_SIZE - CELL_GAP * 2;

                        // fade 흔들림 효과 (파편 느낌)
                        int shake = (int) (Math.random() * 4 - 2); // -2~+2 px
                        px += shake;
                        py += shake;

                        g2.setColor(new Color(255, 255, 255, 180)); // 흰색 반투명 테두리
                        g2.setStroke(new BasicStroke(3));
                        g2.drawRoundRect(px, py, size, size, ARC, ARC);
                    }
                }
            }

            // === 현재 블록 ===
            Block curr = logic.getCurr();
            if (curr != null) {
                int bx = logic.getX();
                int by = logic.getY();
                int ghostY = move.getGhostY(curr);

                // === 고스트 블록 (테두리만)
                g2.setColor(new Color(200, 200, 200));
                Stroke oldStroke = g2.getStroke();
                g2.setStroke(new BasicStroke(2));

                for (int j = 0; j < curr.height(); j++) {
                    for (int i = 0; i < curr.width(); i++) {
                        if (curr.getShape(i, j) == 1) {
                            int x = (bx + i) * CELL_SIZE + CELL_GAP;
                            int y = (ghostY + j) * CELL_SIZE + CELL_GAP;
                            int size = CELL_SIZE - CELL_GAP * 2;
                            g2.drawRect(x, y, size, size);
                        }
                    }
                }

                g2.setStroke(oldStroke);

                // 실제 블록 그리기
                for (int j = 0; j < curr.height(); j++) {
                    for (int i = 0; i < curr.width(); i++) {
                        if (curr.getShape(i, j) == 1) {
                            drawCell(g2, bx + i, by + j, curr.getColor(), curr);
                        }
                    }
                }
            }

            g2.dispose();
        }

        /** 셀 하나를 그리는 함수 */
        private void drawCell(Graphics2D g2, int col, int row, Color color, Block block) {
            int px = col * CELL_SIZE + CELL_GAP;
            int py = row * CELL_SIZE + CELL_GAP;
            int size = CELL_SIZE - CELL_GAP * 2;

            // 색맹모드용 대비 강화
            color = ColorBlindPalette.convert(color, colorMode);

            // === 고정 블록 및 현재 블록은 흔들림 없음 ===
            // (fade 흔들림은 paintComponent에서만 처리)

            g2.setColor(color);
            g2.fillRoundRect(px, py, size, size, ARC, ARC);
            g2.setColor(new Color(255, 255, 255, 60));
            g2.fillRoundRect(px, py, size, size / 3, ARC, ARC);
            g2.setColor(new Color(0, 0, 0, 40));
            g2.fillRoundRect(px, py + size * 2 / 3, size, size / 3, ARC, ARC);

            // === 아이템 블록 문자 오버레이 ===
            if (block instanceof ItemBlock item) {
                if (item instanceof LineClearItem lineItem) {
                    int localX = col - logic.getX();
                    int localY = row - logic.getY();
                    if (localX == lineItem.getLX() && localY == lineItem.getLY()) {
                        drawSymbol(g2, "L", px, py, size);
                    }
                } else if (item instanceof WeightItem) {
                    drawSymbol(g2, "W", px, py, size);
                } else if (item instanceof SpinLockItem) {
                    drawSymbol(g2, SpinLockItem.getSymbol(), px, py, size);
                } else if (item instanceof ColorBombItem) {
                    Stroke oldStroke = g2.getStroke();
                    g2.setColor(new Color(255, 255, 255, 150));
                    g2.setStroke(new BasicStroke(3f));
                    g2.drawOval(px + 3, py + 3, size - 6, size - 6);

                    g2.setColor(new Color(255, 220, 100, 120));
                    g2.drawOval(px + 6, py + 6, size - 12, size - 12);

                    drawSymbol(g2, "💥", px, py, size);
                    g2.setStroke(oldStroke);
                } else if (item instanceof LightningItem) {
                    Stroke oldStroke = g2.getStroke();
                    g2.setColor(new Color(255, 255, 120, 160));
                    g2.setStroke(new BasicStroke(3f));
                    g2.drawOval(px + 4, py + 4, size - 8, size - 8);

                    g2.setColor(new Color(100, 180, 255, 140));
                    g2.drawOval(px + 7, py + 7, size - 14, size - 14);

                    drawSymbol(g2, "⚡", px, py, size);
                    g2.setStroke(oldStroke);
                }
            }
        }

        /** 아이템 문자 그리기 공통 함수 */
        private void drawSymbol(Graphics2D g2, String symbol, int px, int py, int size) {
            g2.setColor(Color.BLACK);
            // g2.setFont(new Font("Arial", Font.BOLD, 18));
            g2.setFont(new Font("Segoe UI Emoji", Font.BOLD, 18)); // 윈도우 10 이상에서 이모지 지원 폰트-> spinlock위해
            FontMetrics fm = g2.getFontMetrics();
            int tx = px + (size - fm.stringWidth(symbol)) / 2;
            int ty = py + (size + fm.getAscent() - fm.getDescent()) / 2;
            g2.drawString(symbol, tx, ty);

        }

    }

    // === 오버레이(모달창) 초기화 ===
    private void initOverlay() {
        overlay = new JPanel(null) { // null layout: 가운데 카드 위치 직접 지정
            @Override
            public boolean isOpaque() {
                return true;
            }
        };
        overlay.setBackground(new Color(0, 0, 0, 150)); // 반투명 검정
        overlay.setBounds(0, 0, getWidth(), getHeight());
        overlay.setVisible(false);

        // 마우스/키 입력 차단(아무것도 하지 않게 소비)
        overlay.addMouseListener(new java.awt.event.MouseAdapter() {
        });
        overlay.addKeyListener(new java.awt.event.KeyAdapter() {
        });

        // 가운데 카드
        dialogPanel = new JPanel(new BorderLayout(8, 8));
        dialogPanel.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));
        dialogPanel.setBackground(new Color(245, 246, 250));
        dialogPanel.setOpaque(true);

        // 초기 크기 & 위치(가운데)
        int w = 420, h = 360;
        dialogPanel.setBounds((getWidth() - w) / 2, (getHeight() - h) / 2, w, h);
        overlay.add(dialogPanel);

        // 창 크기 바뀌면 가운데 유지
        overlay.addComponentListener(new java.awt.event.ComponentAdapter() {
            @Override
            public void componentResized(java.awt.event.ComponentEvent e) {
                int W = overlay.getWidth(), H = overlay.getHeight();
                int ww = dialogPanel.getWidth(), hh = dialogPanel.getHeight();
                dialogPanel.setLocation((W - ww) / 2, (H - hh) / 2);
            }
        });

        // 프레임의 레이어드페인에 올림(같은 창 내부에서 모달처럼 보임)
        getLayeredPane().add(overlay, javax.swing.JLayeredPane.POPUP_LAYER);

        // === 오버레이 관련 객체 초기화 ===
        nameInputOverlay = new NameInputOverlay(
                dialogPanel,
                scoreBoard,
                (rankIndex) -> showScoreboardOverlay(rankIndex), // OK 클릭 완료 시
                () -> {
                    hideOverlay();
                    setStatus("GAME OVER");
                }
        );

        scoreboardOverlay = new ScoreboardOverlay(
                dialogPanel,
                scoreBoard,
                () -> {
                    hideOverlay();
                    isRestarting = true;
                    timer.stop();
                    dispose();
                    Board newBoard = new Board(config);
                    newBoard.requestGameFocus();
                },

                () -> {
                    hideOverlay();
                    timer.stop();
                    dispose();
                });
    }

    /** 이름 입력 오버레이 표시 (게임 종료 후 점수 등록용) */
    private void showNameInputOverlay(int score) {
        overlay.setVisible(true);
        nameInputOverlay.show(score, config.mode(), config.difficulty());
    }

    /** 스코어보드 오버레이 표시 */
    private void showScoreboardOverlay(int highlightIndex) {
        if (dialogPanel.getParent() != overlay) {
            overlay.add(dialogPanel);
        }
        overlay.setVisible(true);
        scoreboardOverlay.show(highlightIndex, config.mode(), config.difficulty());
        overlay.requestFocusInWindow();
    }

    /** 오버레이 닫기 */
    private void hideOverlay() {
        overlay.setVisible(false);
    }

    /** 상태창 텍스트 변경 (타이틀바 업데이트용) */
    public void setStatus(String text) {
        setTitle("TETRIS - " + text);
    }

    public BoardLogic getLogic() {
        return logic;
    }

    public boolean isRestarting() {
        return isRestarting;
    }

    public void setSettings(Settings s) {
        this.settings = s;

        // 바인딩/팔레트 등 즉시 반영하고 싶으면 여기서 한 번 적용
        applySettingsFromConfig();
        // 설정 변경을 실시간 반영하고 싶으면 리스너 연결도 가능
        s.onChange(changed -> applySettingsFromConfig());
    }

    private void applySettingsFromConfig() {
        if (settings == null)
            return;

        colorMode = settings.colorBlindMode;
        System.out.println("[Settings] blindMode=" + colorMode);

        // 키맵 재바인딩
        rebindKeymap();

        revalidate();
        drawBoard();
    }

    private void rebindKeymap() {
        if (settings == null)
            return;

        JComponent comp = gamePanel; // setupKeys를 gamePanel에 했으므로 동일 대상
        InputMap im = comp.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW);

        // 이전 바인딩 제거
        for (var e : boundKeys.entrySet()) {
            Integer code = e.getValue();
            if (code != null) {
                im.remove(KeyStroke.getKeyStroke(code, 0));
            }
        }
        boundKeys.clear();

        // 새 바인딩 등록
        bind(im, Action.Left, settings.keymap.get(Action.Left), ACT_LEFT);
        bind(im, Action.Right, settings.keymap.get(Action.Right), ACT_RIGHT);
        bind(im, Action.SoftDrop, settings.keymap.get(Action.SoftDrop), ACT_DOWN);
        bind(im, Action.HardDrop, settings.keymap.get(Action.HardDrop), ACT_DROP);
        bind(im, Action.Rotate, settings.keymap.get(Action.Rotate), ACT_ROTATE);
    }

    private void bind(InputMap im, Action action, Integer keyCode, String actionName) {
        if (keyCode == null)
            return;
        KeyStroke ks = KeyStroke.getKeyStroke(keyCode, 0);
        im.put(ks, actionName);
        boundKeys.put(action, keyCode);
    }

    /** 새 게임창 또는 Overlay 복귀 시 게임 패널에 포커스 부여 */
    public void requestGameFocus() {
        SwingUtilities.invokeLater(() -> {
            if (gamePanel != null) {
                gamePanel.setFocusable(true);
                boolean ok = gamePanel.requestFocusInWindow();
                // 포커스 회복 시점에 InputMap도 재적용
                setupKeys(gamePanel);
            }
        });
    }

    // public void setColorMapper(java.util.function.Function<Color, Color> mapper) {
    //     this.colorMapper = (mapper != null) ? mapper : (c -> c);
    // }

    // private Color mapDisplayColor(Color base) {
    //     try { return colorMapper.apply(base); }
    //     catch (Exception ignore) { return base; }
    // }

}
