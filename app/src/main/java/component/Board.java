package component;

import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GraphicsDevice;
import java.awt.GraphicsEnvironment;
import java.awt.GridLayout;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.util.LinkedList;
import java.util.Queue;
import java.util.Random;

import javax.swing.AbstractAction;
import javax.swing.ActionMap;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.InputMap;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.KeyStroke;
import javax.swing.border.EmptyBorder;

import blocks.Block;
import blocks.IBlock;
import blocks.JBlock;
import blocks.LBlock;
import blocks.OBlock;
import blocks.SBlock;
import blocks.TBlock;
import blocks.ZBlock;

public class Board extends JFrame {

    // --- Playfield constants
    private static final int WIDTH = 10;
    private static final int HEIGHT = 20;
    private static final int CELL_SIZE = 35;
    private static final int CELL_GAP = 2;
    private static final int ARC = 8;

    // --- Board state
    private final Color[][] board = new Color[HEIGHT][WIDTH];
    private Block curr;
    private int x = 3, y = 0;

    // Next queue
    private final Queue<Block> nextBlocks = new LinkedList<>();
    private static final int NEXT_SIZE = 3;

    // Stats
    private int score = 0;
<<<<<<< HEAD
    private int level = 1;
    private int linesCleared = 0;
    private final JLabel scoreLabel = new JLabel("0");
    private final JLabel levelLabel = new JLabel("1");
    private final JLabel linesLabel = new JLabel("0");
    private final JPanel nextPanel = new JPanel();
=======
    private int clearedLines = 0;

    private BlockBag bag = new BlockBag();
>>>>>>> origin/dev

    // Game loop
    private javax.swing.Timer timer;
<<<<<<< HEAD
    private int baseSpeed = 500; // ms
=======
    private boolean isPaused = false;
    private final SpeedManager speedManager = new SpeedManager();
>>>>>>> origin/dev

    // Full screen
    private boolean isFullScreen = false;
    private Rectangle normalBounds;
    private GraphicsDevice graphicsDevice;

    // Visual effects (merged)
    private boolean flashActive = false;
    private long flashUntil = 0L;
    private boolean itemGlowActive = false;
    private long itemGlowUntil = 0L;

    // Config (mode/difficulty)
    private GameConfig config;

    // Palette
    private static final Color BG_DARK = new Color(20, 25, 35);
    private static final Color BG_PANEL = new Color(30, 35, 50);
    private static final Color BG_GAME = new Color(25, 30, 42);
    private static final Color ACCENT = new Color(100, 255, 218);
    private static final Color TEXT_PRIMARY = new Color(230, 237, 243);
    private static final Color TEXT_SECONDARY = new Color(136, 146, 176);
    private static final Color GRID_LINE = new Color(40, 45, 60);

    // --- Constructors
    public Board() {
        this(null);
    }

    public Board(GameConfig cfg) {
        this.config = cfg;

        setTitle(cfg == null ? "TETRIS"
                : "TETRIS – " + cfg.mode() + " / " + cfg.difficulty());
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        getContentPane().setBackground(BG_DARK);

<<<<<<< HEAD
        // optional: adjust start speed by difficulty
        if (cfg != null) {
            switch (cfg.difficulty()) {
                case EASY   -> baseSpeed = 600;
                case NORMAL -> baseSpeed = 500;
                case HARD   -> baseSpeed = 380;
=======
        // ===== 사이드 패널 =====
        JPanel side = new JPanel();
        side.setLayout(new BoxLayout(side, BoxLayout.Y_AXIS));
        side.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        scoreLabel = new JLabel("Score: 0");
        statusLabel = new JLabel("Ready");
        scoreLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        statusLabel.setAlignmentX(Component.LEFT_ALIGNMENT);

        side.add(scoreLabel);
        side.add(Box.createVerticalStrut(8));
        side.add(statusLabel);

        rootPanel.add(side, BorderLayout.EAST);
        setContentPane(rootPanel);

        // ===== 보드/블럭 초기화 =====
        board = new Color[HEIGHT][WIDTH];
        curr = bag.next();

        // ===== 게임 루프 타이머 =====
        timer = new javax.swing.Timer(speedManager.getDropInterval(), e -> {
            if (!isPaused) {
                moveDown();
                drawBoard();
>>>>>>> origin/dev
            }
        }

        // Graphics device for full screen
        graphicsDevice = GraphicsEnvironment.getLocalGraphicsEnvironment().getDefaultScreenDevice();

        // Init queue & first piece
        curr = getRandomBlock();
        refillNextQueue();

        // Layout root
        JPanel root = new JPanel(new BorderLayout(20, 0));
        root.setBackground(BG_DARK);
        root.setBorder(new EmptyBorder(20, 20, 20, 20));

        // Game panel container
        JPanel gameContainer = new JPanel(new BorderLayout());
        gameContainer.setBackground(BG_DARK);
        GamePanel gamePanel = new GamePanel();
        gameContainer.add(gamePanel, BorderLayout.CENTER);
        root.add(gameContainer, BorderLayout.CENTER);

        // Right panel (stats, next, controls)
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

        add(root);
        pack();
        setLocationRelativeTo(null);
        setVisible(true);

        // Inputs + timer
        setupKeys(gamePanel);

        timer = new javax.swing.Timer(baseSpeed, e -> {
            // move
            moveDown();

            // expire effects
            long now = System.currentTimeMillis();
            if (flashActive && now >= flashUntil) flashActive = false;
            if (itemGlowActive && now >= itemGlowUntil) itemGlowActive = false;

            // repaint
            gamePanel.repaint();
        });
        timer.start();
    }

    // --- UI helpers
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
            "← → Move",
            "↑ Rotate",
            "↓ Soft Drop",
            "SPACE Hard Drop",
            "F11 Full Screen",
            "ESC Exit Full Screen"
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
        InputMap im = comp.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW);
        ActionMap am = comp.getActionMap();

        im.put(KeyStroke.getKeyStroke("LEFT"), "left");
        im.put(KeyStroke.getKeyStroke("RIGHT"), "right");
        im.put(KeyStroke.getKeyStroke("DOWN"), "down");
        im.put(KeyStroke.getKeyStroke("UP"), "rotate");
        im.put(KeyStroke.getKeyStroke("SPACE"), "drop");
        im.put(KeyStroke.getKeyStroke(KeyEvent.VK_F11, 0), "fullscreen");
        im.put(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), "exitfullscreen");

        am.put("left", new AbstractAction() { public void actionPerformed(ActionEvent e) { moveLeft(); comp.repaint(); }});
        am.put("right", new AbstractAction(){ public void actionPerformed(ActionEvent e) { moveRight(); comp.repaint(); }});
        am.put("down", new AbstractAction() { public void actionPerformed(ActionEvent e) { moveDown(); comp.repaint(); }});
        am.put("rotate", new AbstractAction(){ public void actionPerformed(ActionEvent e) { rotate(); comp.repaint(); }});
        am.put("drop", new AbstractAction()   { public void actionPerformed(ActionEvent e) { hardDrop(); comp.repaint(); }});
        am.put("fullscreen", new AbstractAction(){ public void actionPerformed(ActionEvent e) { toggleFullScreen(); }});
        am.put("exitfullscreen", new AbstractAction(){ public void actionPerformed(ActionEvent e) { if (isFullScreen) toggleFullScreen(); }});
    }

    private void toggleFullScreen() {
        if (!isFullScreen) {
            normalBounds = getBounds();
            dispose();
            setUndecorated(true);
            setResizable(false);
            if (graphicsDevice.isFullScreenSupported()) graphicsDevice.setFullScreenWindow(this);
            else setExtendedState(JFrame.MAXIMIZED_BOTH);
            setVisible(true);
            isFullScreen = true;
        } else {
            if (graphicsDevice.isFullScreenSupported()) graphicsDevice.setFullScreenWindow(null);
            dispose();
            setUndecorated(false);
            setResizable(false);
            if (normalBounds != null) setBounds(normalBounds);
            setVisible(true);
            isFullScreen = false;
        }
    }

    // --- Game logic
    private Block getRandomBlock() {
        switch (new Random().nextInt(7)) {
            case 0: return new IBlock();
            case 1: return new JBlock();
            case 2: return new LBlock();
            case 3: return new ZBlock();
            case 4: return new SBlock();
            case 5: return new TBlock();
            default: return new OBlock();
        }
    }

    private void refillNextQueue() {
        while (nextBlocks.size() < NEXT_SIZE) nextBlocks.add(getRandomBlock());
        updateNextHUD();
    }

    private void spawnNext() {
        curr = nextBlocks.poll();
        x = 3; y = 0;
        refillNextQueue();

        // Item-mode visual cue (merged)
        if (config != null && config.mode() == GameConfig.Mode.ITEM) {
            triggerItemHighlight(800);
        }

        if (!canMove(curr, x, y)) {
            timer.stop();
            showGameOver();
        }
    }

    private void showGameOver() {
        JDialog dialog = new JDialog(this, "Game Over", true);
        dialog.setLayout(new BorderLayout(20, 20));
        dialog.getContentPane().setBackground(BG_DARK);

        JPanel content = new JPanel();
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
        content.setBackground(BG_DARK);
        content.setBorder(new EmptyBorder(30, 40, 30, 40));

        JLabel gameOverLabel = new JLabel("GAME OVER");
        gameOverLabel.setFont(new Font("Arial", Font.BOLD, 32));
        gameOverLabel.setForeground(ACCENT);
        gameOverLabel.setAlignmentX(Component.CENTER_ALIGNMENT);

        JLabel scoreL = new JLabel("Final Score: " + score);
        scoreL.setFont(new Font("Arial", Font.PLAIN, 18));
        scoreL.setForeground(TEXT_PRIMARY);
        scoreL.setAlignmentX(Component.CENTER_ALIGNMENT);

        content.add(gameOverLabel);
        content.add(Box.createRigidArea(new Dimension(0, 20)));
        content.add(scoreL);

        dialog.add(content, BorderLayout.CENTER);
        dialog.pack();
        dialog.setLocationRelativeTo(this);
        dialog.setVisible(true);
        dispose();
    }

    private boolean canMove(Block b, int nx, int ny) {
        for (int j = 0; j < b.height(); j++) {
            for (int i = 0; i < b.width(); i++) {
                if (b.getShape(i, j) == 1) {
                    int bx = nx + i, by = ny + j;
                    if (bx < 0 || bx >= WIDTH || by < 0 || by >= HEIGHT) return false;
                    if (board[by][bx] != null) return false;
                }
            }
        }
        return true;
    }

    private void rotate() {
        Block backup = curr.clone();
        curr.rotate();
        if (!canMove(curr, x, y)) curr = backup;
    }

    private void moveLeft()  { if (canMove(curr, x - 1, y)) x--; }
    private void moveRight() { if (canMove(curr, x + 1, y)) x++; }

    private void moveDown() {
        if (canMove(curr, x, y + 1)) {
            y++;
        } else {
            placeBlock();
            clearLines();
            spawnNext();
        }
    }

    private void hardDrop() {
        while (canMove(curr, x, y + 1)) y++;
        moveDown();
    }

    private void placeBlock() {
        for (int j = 0; j < curr.height(); j++)
            for (int i = 0; i < curr.width(); i++)
                if (curr.getShape(i, j) == 1)
                    board[y + j][x + i] = getColor(curr);
    }

    private void clearLines() {
        int cleared = 0;
        for (int r = HEIGHT - 1; r >= 0; r--) {
            boolean full = true;
            for (int c = 0; c < WIDTH; c++)
                if (board[r][c] == null) { full = false; break; }
            if (full) {
                cleared++;
                for (int k = r; k > 0; k--) board[k] = board[k - 1].clone();
                board[0] = new Color[WIDTH];
                r++;
            }
        }
        if (cleared > 0) {
            linesCleared += cleared;
            score += cleared * 100 * level;
            level = 1 + linesCleared / 10;

            scoreLabel.setText(String.valueOf(score));
            levelLabel.setText(String.valueOf(level));
            linesLabel.setText(String.valueOf(linesCleared));

            // speed up
            int newSpeed = Math.max(100, baseSpeed - (level - 1) * 50);
            timer.setDelay(newSpeed);

            // merged: trigger flash
            triggerLineClearFlash();
        }
    }

    private void updateNextHUD() {
        nextPanel.removeAll();
        nextPanel.setLayout(new GridLayout(NEXT_SIZE, 1, 0, 10));

        for (Block b : nextBlocks) {
            JPanel container = new JPanel(new BorderLayout());
            container.setBackground(BG_PANEL);
            container.setPreferredSize(new Dimension(120, 80));

            JPanel blockPanel = new JPanel() {
                @Override protected void paintComponent(Graphics g) {
                    super.paintComponent(g);
                    Graphics2D g2 = (Graphics2D) g;
                    g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

                    int blockSize = 15;
                    int offsetX = (getWidth() - b.width() * blockSize) / 2;
                    int offsetY = (getHeight() - b.height() * blockSize) / 2;

                    for (int j = 0; j < b.height(); j++) {
                        for (int i = 0; i < b.width(); i++) {
                            if (b.getShape(i, j) == 1) {
                                Color color = getColor(b);
                                g2.setColor(color);
                                g2.fillRoundRect(
                                        offsetX + i * blockSize,
                                        offsetY + j * blockSize,
                                        blockSize - 2,
                                        blockSize - 2,
                                        4, 4
                                );
                                // highlight cap
                                g2.setColor(new Color(255, 255, 255, 40));
                                g2.fillRoundRect(
                                        offsetX + i * blockSize,
                                        offsetY + j * blockSize,
                                        blockSize - 2,
                                        4,
                                        4, 4
                                );
                            }
                        }
                    }
                }
            };
            blockPanel.setBackground(BG_PANEL);
            container.add(blockPanel, BorderLayout.CENTER);
            nextPanel.add(container);
        }
        nextPanel.revalidate();
        nextPanel.repaint();
    }

    private Color getColor(Block b) {
        if (b instanceof IBlock) return new Color(0, 240, 240);
        if (b instanceof JBlock) return new Color(0, 0, 240);
        if (b instanceof LBlock) return new Color(240, 160, 0);
        if (b instanceof OBlock) return new Color(240, 240, 0);
        if (b instanceof SBlock) return new Color(0, 240, 0);
        if (b instanceof TBlock) return new Color(160, 0, 240);
        if (b instanceof ZBlock) return new Color(240, 0, 0);
        return Color.GRAY;
    }

    // --- Effects helpers (merged)
    private void triggerLineClearFlash() {
        flashActive = true;
        flashUntil = System.currentTimeMillis() + 300;
    }
    private void triggerItemHighlight(long durationMs) {
        itemGlowActive = true;
        itemGlowUntil = System.currentTimeMillis() + durationMs;
    }

    // --- Rendering panel
    private class GamePanel extends JPanel {
        GamePanel() {
            setPreferredSize(new Dimension(WIDTH * CELL_SIZE, HEIGHT * CELL_SIZE));
            setBackground(BG_GAME);
            setBorder(BorderFactory.createLineBorder(GRID_LINE, 3));
        }

        @Override protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            // grid
            g2.setColor(GRID_LINE);
            for (int r = 0; r <= HEIGHT; r++)
                g2.drawLine(0, r * CELL_SIZE, WIDTH * CELL_SIZE, r * CELL_SIZE);
            for (int c = 0; c <= WIDTH; c++)
                g2.drawLine(c * CELL_SIZE, 0, c * CELL_SIZE, HEIGHT * CELL_SIZE);

            // placed
            for (int r = 0; r < HEIGHT; r++) {
                for (int c = 0; c < WIDTH; c++) {
                    if (board[r][c] != null) {
                        drawCell(g2, c, r, board[r][c]);
                    }
                }
            }

            // ghost
            if (curr != null) {
                int ghostY = y;
                while (canMove(curr, x, ghostY + 1)) ghostY++;
                g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.2f));
                for (int j = 0; j < curr.height(); j++) {
                    for (int i = 0; i < curr.width(); i++) {
                        if (curr.getShape(i, j) == 1) {
                            drawCell(g2, x + i, ghostY + j, getColor(curr));
                        }
                    }
                }
                g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 1f));
            }

            // current
            if (curr != null) {
                for (int j = 0; j < curr.height(); j++) {
                    for (int i = 0; i < curr.width(); i++) {
                        if (curr.getShape(i, j) == 1) {
                            drawCell(g2, x + i, y + j, getColor(curr));
                        }
                    }
                }
            }

            // item highlight border
            if (itemGlowActive) {
                g2.setColor(new Color(255, 255, 255, 120));
                g2.setStroke(new BasicStroke(4f));
                g2.drawRect(1, 1, WIDTH * CELL_SIZE - 2, HEIGHT * CELL_SIZE - 2);
            }

            // line clear flash overlay
            if (flashActive) {
                g2.setColor(new Color(255, 255, 255, 140));
                g2.fillRect(0, 0, getWidth(), getHeight());
            }
<<<<<<< HEAD

            g2.dispose();
=======
            if (full) {
                for (int k = i; k > 0; k--) {
                    board[k] = board[k - 1].clone();
                }
                board[0] = new Color[WIDTH];
                score += 100;
                clearedLines++;
                if (clearedLines % 10 == 0)
                    speedManager.increaseLevel();
                    timer.setDelay(speedManager.getDropInterval());
            }
        }
    }

    private void gameOver() {
        timer.stop();
        setStatus("GAME OVER! Score: " + score);
    }

    // ===== 키 바인딩 =====
    private void setupKeyBindings() {
        InputMap im = rootPanel.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW);
        ActionMap am = rootPanel.getActionMap();
        rootPanel.setFocusTraversalKeysEnabled(false);

        im.put(KeyStroke.getKeyStroke("RIGHT"), "moveRight");
        im.put(KeyStroke.getKeyStroke("LEFT"), "moveLeft");
        im.put(KeyStroke.getKeyStroke("DOWN"), "moveDown");
        im.put(KeyStroke.getKeyStroke("UP"), "rotate");
        im.put(KeyStroke.getKeyStroke("SPACE"), "hardDrop");
        im.put(KeyStroke.getKeyStroke("P"), "pause");
        im.put(KeyStroke.getKeyStroke("ESCAPE"), "exit");

        am.put("moveRight", new AbstractAction() {
            public void actionPerformed(ActionEvent e) {
                moveRight();
                drawBoard();
            }
        });
        am.put("moveLeft", new AbstractAction() {
            public void actionPerformed(ActionEvent e) {
                moveLeft();
                drawBoard();
            }
        });
        am.put("moveDown", new AbstractAction() {
            public void actionPerformed(ActionEvent e) {
                moveDown();
                drawBoard();
            }
        });
        am.put("rotate", new AbstractAction() {
            public void actionPerformed(ActionEvent e) {
                rotateBlock();
            }
        });
        am.put("hardDrop", new AbstractAction() {
            public void actionPerformed(ActionEvent e) {
                hardDrop();
                drawBoard();
            }
        });
        am.put("pause", new AbstractAction() {
            public void actionPerformed(ActionEvent e) {
                togglePause();
            }
        });
        am.put("exit", new AbstractAction() {
            public void actionPerformed(ActionEvent e) {
                exitGame();
            }
        });
    }

    public void drawBoard() {
        StringBuilder sb = new StringBuilder();

        // ====== 보드 문자열 구성 ======
        // 윗 테두리
        for (int t = 0; t < WIDTH + 2; t++)
            sb.append(BORDER_CHAR);
        sb.append("\n");

        // 내부
        for (int i = 0; i < HEIGHT; i++) {
            sb.append(BORDER_CHAR); // 왼쪽 테두리
            for (int j = 0; j < WIDTH; j++) {
                if (board[i][j] != null || isCurrBlockAt(j, i)) {
                    sb.append("O"); // 블록
                } else {
                    sb.append(" "); // 빈칸은 그냥 공백
                }
            }
            sb.append(BORDER_CHAR).append("\n"); // 오른쪽 테두리
>>>>>>> origin/dev
        }

        private void drawCell(Graphics2D g2, int col, int row, Color color) {
            int px = col * CELL_SIZE + CELL_GAP;
            int py = row * CELL_SIZE + CELL_GAP;
            int size = CELL_SIZE - CELL_GAP * 2;

<<<<<<< HEAD
            // body
            g2.setColor(color);
            g2.fillRoundRect(px, py, size, size, ARC, ARC);
=======
        // 게임 정보
        sb.append("\nSCORE: ").append(score);
        sb.append("\nLEVEL: ").append(speedManager.getLevel());
        sb.append("\nNEXT: ").append(bag.peekNext(1).get(0).getClass().getSimpleName());
        if (isPaused)
            sb.append("\n[일시정지]");
>>>>>>> origin/dev

            // highlight
            g2.setColor(new Color(255, 255, 255, 60));
            g2.fillRoundRect(px, py, size, size / 3, ARC, ARC);

            // shade
            g2.setColor(new Color(0, 0, 0, 40));
            g2.fillRoundRect(px, py + size * 2 / 3, size, size / 3, ARC, ARC);
        }
    }
}
