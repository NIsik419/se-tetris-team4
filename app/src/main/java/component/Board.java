package component;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.GradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.ActionEvent;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.Queue;
import java.util.Random;

import javax.swing.AbstractAction;
import javax.swing.ActionMap;
import javax.swing.BorderFactory;
import javax.swing.InputMap;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JTextPane;
import javax.swing.KeyStroke;
import javax.swing.border.CompoundBorder;

import blocks.Block;
import blocks.IBlock;
import blocks.JBlock;
import blocks.LBlock;
import blocks.OBlock;
import blocks.SBlock;
import blocks.TBlock;
import blocks.ZBlock;

public class Board extends JFrame {

    private static final long serialVersionUID = 1L;

    public static final int HEIGHT = 20;
    public static final int WIDTH  = 10;
    public static final char BORDER_CHAR = 'X';

    // --- UI containers
    private final JPanel rootPanel = new JPanel(new BorderLayout());
    private HUDSidebar hud = new HUDSidebar(); 
    private JTextPane pane; // 유지: 기존 코드 호환용(미사용)

    // 실제 보드를 그리는 패널
    private final GamePanel gamePanel = new GamePanel();

    // --- Game state
    private Color[][] board;
    private javax.swing.Timer timer; 
    private javax.swing.Timer clockTimer;  
    private long elapsedSeconds = 0;

    private Block curr;
    private int x = 3, y = 0;
    private int score = 0;

    // 다음 블럭 큐
    private Queue<Block> nextBlocks = new LinkedList<>();
    private static final int NEXT_SIZE = 3;

    // 난이도 관련
    private int clearedLines = 0;
    private int speedLevel = 1;

    // 일시정지 상태
    private boolean isPaused = false;

    private static final int initInterval = 1000;

    public Board() {
        super("SeoulTech SE Tetris");
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE); 

        pane = new JTextPane(); // 유지

        // 보드 외곽/배경
        gamePanel.setBackground(new Color(0x080B11));
        CompoundBorder borderStyled = BorderFactory.createCompoundBorder(
            BorderFactory.createMatteBorder(8,8,8,8, new Color(0x2B2F3A)),
            BorderFactory.createMatteBorder(6,6,6,6, new Color(0x141824))
        );
        gamePanel.setBorder(borderStyled);
        gamePanel.setPreferredSize(new Dimension(340, 680));

        rootPanel.setBackground(new Color(0x0B0F18));
        rootPanel.add(gamePanel, BorderLayout.CENTER);
        rootPanel.add(hud,  BorderLayout.EAST);
        setContentPane(rootPanel);

        // game tick
        timer = new javax.swing.Timer(initInterval, e -> {
            if (!isPaused) {
                moveDown();
                gamePanel.repaint();
            }
        });

        // clock
        clockTimer = new javax.swing.Timer(1000, e -> {
            if (!isPaused) {
                elapsedSeconds++;
                hud.setTime(elapsedSeconds);
            }
        });

        // HUD init
        hud.reset();
        hud.setScore(score);
        hud.setLevel(speedLevel);

        // board init
        board = new Color[HEIGHT][WIDTH];

        // queue + first spawn
        refillNextQueueIfNeeded();
        spawnNextPiece();

        // keys
        setupKeyBindings();

        drawBoard();
        setSize(560, 720);
        setLocationRelativeTo(null);
        setVisible(true);
        gamePanel.requestFocusInWindow();

        timer.start();
        clockTimer.start();
    }

    private Block getRandomBlock() {
        Random rnd = new Random(System.currentTimeMillis());
        switch (rnd.nextInt(7)) {
            case 0: return new IBlock();
            case 1: return new JBlock();
            case 2: return new LBlock();
            case 3: return new ZBlock();
            case 4: return new SBlock();
            case 5: return new TBlock();
            case 6: return new OBlock();
        }
        return new LBlock();
    }

    // --- next queue & spawn
    private void refillNextQueueIfNeeded() {
        while (nextBlocks.size() < NEXT_SIZE) nextBlocks.add(getRandomBlock());
        refreshNextHUD();
    }
    private void spawnNextPiece() {
        if (nextBlocks.isEmpty()) refillNextQueueIfNeeded();
        curr = nextBlocks.poll();
        refillNextQueueIfNeeded();
        x = 3; y = 0;
        if (!canMove(curr, x, y)) gameOver();
        gamePanel.repaint();
    }

    private boolean canMove(Block block, int newX, int newY) {
        for (int j = 0; j < block.height(); j++) {
            for (int i = 0; i < block.width(); i++) {
                if (block.getShape(i, j) == 1) {
                    int bx = newX + i, by = newY + j;
                    if (bx < 0 || bx >= WIDTH || by < 0 || by >= HEIGHT) return false;
                    if (board[by][bx] != null) return false;
                }
            }
        }
        return true;
    }

    protected void rotateBlock() {
        Block backup = curr.clone();
        int oldX = x, oldY = y;

        curr.rotate();

        if (!canMove(curr, x, y)) {
            if (canMove(curr, x - 1, y)) x -= 1;
            else if (canMove(curr, x + 1, y)) x += 1;
            else { curr = backup; x = oldX; y = oldY; }
        }
        drawBoard();
    }

    // ===== 이동/낙하 =====
    protected void moveDown() {
        if (canMove(curr, x, y + 1)) {
            y++;
            score++;
            hud.setScore(score);
        } else {
            // 고정
            for (int j = 0; j < curr.height(); j++) {
                for (int i = 0; i < curr.width(); i++) {
                    if (curr.getShape(i, j) == 1) {
                        int bx = x + i, by = y + j;
                        if (bx >= 0 && bx < WIDTH && by >= 0 && by < HEIGHT) {
                            board[by][bx] = colorFor(curr);
                        }
                    }
                }
            }
            clearLines();
            spawnNextPiece();
            if (!canMove(curr, x, y)) { gameOver(); return; }
        }
        drawBoard();
    }

    protected void moveRight() { if (canMove(curr, x + 1, y)) x++; }
    protected void moveLeft()  { if (canMove(curr, x - 1, y)) x--;  }

    protected void hardDrop() {
        while (canMove(curr, x, y + 1)) { y++; score += 2; }
        hud.setScore(score);
        moveDown();
    }

    // ===== 줄 삭제/난이도 =====
    private void clearLines() {
        for (int r = 0; r < HEIGHT; r++) {
            boolean full = true;
            for (int c = 0; c < WIDTH; c++) {
                if (board[r][c] == null) { full = false; break; }
            }
            if (full) {
                for (int k = r; k > 0; k--) board[k] = board[k - 1].clone();
                board[0] = new Color[WIDTH];
                score += 100; hud.setScore(score);
                clearedLines++;
                if (clearedLines % 10 == 0) increaseSpeed();
            }
        }
    }

    private void increaseSpeed() {
        int newDelay = Math.max(200, timer.getDelay() - 100);
        timer.setDelay(newDelay);
        speedLevel++;
        hud.setLevel(speedLevel);
        setStatus("Level Up! " + speedLevel);
    }

    private void gameOver() {
        timer.stop();
        if (clockTimer != null) clockTimer.stop();
        setStatus("GAME OVER! Score: " + score);
        JOptionPane.showMessageDialog(this, "Game Over!\nScore: " + score,
                "Game Over", JOptionPane.INFORMATION_MESSAGE);
        dispose();
    }

    // HUD next 3
    private void refreshNextHUD() {
        java.util.List<char[][]> shapes = new java.util.ArrayList<>();
        int i = 0;
        for (Block b : nextBlocks) {
            if (i++ == 3) break;
            shapes.add(blockToShape(b));
        }
        hud.setNextQueue(shapes);
    }

    // ===== 키 바인딩 =====
    private void setupKeyBindings() {
        attachBindingsTo(rootPanel);
        attachBindingsTo(gamePanel);
    }
    private void attachBindingsTo(JComponent comp) {
        InputMap im = comp.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW);
        ActionMap am = comp.getActionMap();
        comp.setFocusTraversalKeysEnabled(false);

        im.put(KeyStroke.getKeyStroke("RIGHT"), "moveRight");
        im.put(KeyStroke.getKeyStroke("LEFT"), "moveLeft");
        im.put(KeyStroke.getKeyStroke("DOWN"), "moveDown");
        im.put(KeyStroke.getKeyStroke("UP"), "rotate");
        im.put(KeyStroke.getKeyStroke("SPACE"), "hardDrop");
        im.put(KeyStroke.getKeyStroke("P"), "pause");
        im.put(KeyStroke.getKeyStroke("ESCAPE"), "exit");

        am.put("moveRight", new AbstractAction() { public void actionPerformed(ActionEvent e) { moveRight(); drawBoard(); }});
        am.put("moveLeft",  new AbstractAction() { public void actionPerformed(ActionEvent e) { moveLeft();  drawBoard(); }});
        am.put("moveDown",  new AbstractAction() { public void actionPerformed(ActionEvent e) { moveDown(); }});
        am.put("rotate",    new AbstractAction() { public void actionPerformed(ActionEvent e) { rotateBlock(); }});
        am.put("hardDrop",  new AbstractAction() { public void actionPerformed(ActionEvent e) { hardDrop(); }});
        am.put("pause",     new AbstractAction() { public void actionPerformed(ActionEvent e) { togglePause(); }});
        am.put("exit",      new AbstractAction() { public void actionPerformed(ActionEvent e) { exitGame(); }});
    }

    public void drawBoard() { gamePanel.repaint(); }

    private boolean isCurrBlockAt(int j, int i) {
        for (int dy = 0; dy < curr.height(); dy++)
            for (int dx = 0; dx < curr.width(); dx++)
                if (curr.getShape(dx, dy) == 1 && i == y + dy && j == x + dx) return true;
        return false;
    }

    private void togglePause() {
        isPaused = !isPaused;
        setStatus(isPaused ? "일시정지" : "진행중");
    }
    private void exitGame() {
        if (clockTimer != null) clockTimer.stop();
        if (timer != null) timer.stop();
        dispose();
    }
    private void setStatus(String s) { setTitle("TETRIS — " + s); }

    private char[][] blockToShape(Block b) {
        int h = b.height(), w = b.width();
        int size = 4;
        char[][] arr = new char[size][size];
        for (int r = 0; r < size; r++) Arrays.fill(arr[r], ' ');
        int offX = (size - w) / 2, offY = (size - h) / 2;
        for (int j = 0; j < h; j++)
            for (int i = 0; i < w; i++)
                if (b.getShape(i, j) == 1)
                    arr[offY + j][offX + i] = 'O';
        return arr;
    }

    // block palette (bright, classic)
    private Color colorFor(Block b) {
        if (b instanceof IBlock) return new Color(0x00FFFF);
        if (b instanceof JBlock) return new Color(0x3B82F6);
        if (b instanceof LBlock) return new Color(0xF59E0B);
        if (b instanceof OBlock) return new Color(0xFFD400);
        if (b instanceof SBlock) return new Color(0x10B981);
        if (b instanceof TBlock) return new Color(0xA855F7);
        if (b instanceof ZBlock) return new Color(0xEF4444);
        return new Color(0xCCCCCC);
    }

    // 실제 그리기
    private class GamePanel extends JPanel {
        private static final int CELL_GAP = 2;
        private static final int ARC = 10;

        @Override protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            int w = getWidth(), h = getHeight();

            int pad = 10;
            int gridW = w - pad * 2;
            int gridH = h - pad * 2;

            int cell = Math.min(gridW / WIDTH, gridH / HEIGHT);
            if (cell <= 0) { g2.dispose(); return; }
            int startX = (w - cell * WIDTH) / 2;
            int startY = (h - cell * HEIGHT) / 2;

            // outer frame
            g2.setColor(new Color(0x0F141C));
            g2.fillRoundRect(startX - 8, startY - 8, cell * WIDTH + 16, cell * HEIGHT + 16, 16, 16);

            // field background
            g2.setColor(new Color(0x1F2531));
            g2.fillRoundRect(startX, startY, cell * WIDTH, cell * HEIGHT, 12, 12);

            // fixed blocks
            for (int r = 0; r < HEIGHT; r++) {
                for (int c = 0; c < WIDTH; c++) {
                    Color tile = board[r][c];
                    if (tile != null) {
                        int x0 = startX + c * cell + CELL_GAP;
                        int y0 = startY + r * cell + CELL_GAP;
                        int s = cell - CELL_GAP * 2;
                        g2.setPaint(new GradientPaint(x0, y0, tile.brighter(), x0, y0 + s, tile.darker()));
                        g2.fillRoundRect(x0, y0, s, s, ARC, ARC);
                    }
                }
            }

            // current falling block
            if (curr != null) {
                Color col = colorFor(curr);
                for (int dy = 0; dy < curr.height(); dy++) {
                    for (int dx = 0; dx < curr.width(); dx++) {
                        if (curr.getShape(dx, dy) == 1) {
                            int cx = x + dx, cy = y + dy;
                            if (cx >= 0 && cx < WIDTH && cy >= 0 && cy < HEIGHT) {
                                int x0 = startX + cx * cell + CELL_GAP;
                                int y0 = startY + cy * cell + CELL_GAP;
                                int s = cell - CELL_GAP * 2;
                                g2.setPaint(new GradientPaint(x0, y0, col.brighter(), x0, y0 + s, col.darker()));
                                g2.fillRoundRect(x0, y0, s, s, ARC, ARC);
                            }
                        }
                    }
                }
            }

            g2.dispose();
        }
    }
}
