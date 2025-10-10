package component;

import java.awt.*;
import java.awt.event.*;
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

    private JTextPane pane;
    private JLabel scoreLabel;
    private JLabel statusLabel;
    private JPanel rootPanel;

    // --- Game state
    private Color[][] board;
    private Block curr;
    private int x = 3, y = 0;
    private int score = 0;
    private int clearedLines = 0;
    private int speedLevel = 1;

    // 다음 블럭 큐
    private Queue<Block> nextBlocks = new LinkedList<>();
    private static final int NEXT_SIZE = 3;

    private javax.swing.Timer timer;
    private boolean isPaused = false;

    private static final int initInterval = 1000;

    public Board() {
        super("SeoulTech SE Tetris");
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE); 

        // ===== 메인 보드 패널 =====
        pane = new JTextPane();
        pane.setEditable(false);
        pane.setFocusable(false);
        pane.setBackground(Color.BLACK);
        pane.setFont(new Font("Courier New", Font.PLAIN, 18)); // 고정폭 폰트 강제
        CompoundBorder border = BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(Color.GRAY, 10),
                BorderFactory.createLineBorder(Color.DARK_GRAY, 5));
        pane.setBorder(border);

        rootPanel = new JPanel(new BorderLayout());
        rootPanel.add(pane, BorderLayout.CENTER);

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
        for (int i = 0; i < NEXT_SIZE; i++) {
            nextBlocks.add(getRandomBlock());
        }
        curr = nextBlocks.poll();

        // ===== 게임 루프 타이머 =====
        timer = new javax.swing.Timer(initInterval, e -> {
            if (!isPaused) {
                moveDown();
                drawBoard();
            }
        });

        setupKeyBindings();

        drawBoard();
        timer.start();

        setSize(500, 700);
        setVisible(true);
        rootPanel.requestFocusInWindow();
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

        // 회전 시도
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
            // 고정 (경계 체크 포함)
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

            // 새 블럭
            curr = nextBlocks.poll();
            nextBlocks.add(getRandomBlock());
            x = 3;
            y = 0;

            if (!canMove(curr, x, y)) {
                gameOver();
            }
        }
    }

    protected void moveRight() {
        if (canMove(curr, x + 1, y))
            x++;
    }

    protected void moveLeft() {
        if (canMove(curr, x - 1, y))
            x--;
    }

    protected void hardDrop() {
        while (canMove(curr, x, y + 1)) { y++; score += 2; }
        hud.setScore(score);
        moveDown();
    }

    //  줄 삭제/난이도 
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
    }

    // NEW: HUD에 3개의 Next 박스를 채워 넣는다
    private void refreshNextHUD() {
        java.util.List<char[][]> shapes = new java.util.ArrayList<>();
        int i = 0;
        for (Block b : nextBlocks) {
            if (i++ == 3) break;
            shapes.add(blockToShape(b));
        }
        hud.setNextQueue(shapes);
    }

    // 키 바인딩 설정 (보이는 컴포넌트에 연결)
    private void setupKeyBindings() {
        attachBindingsTo(rootPanel);
        attachBindingsTo(gamePanel);
    }
    private void attachBindingsTo(JComponent comp) {
        InputMap im = comp.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW);
        ActionMap am = comp.getActionMap();
        comp.setFocusTraversalKeysEnabled(false);

        im.put(KeyStroke.getKeyStroke("RIGHT"), "moveRight");
        im.put(KeyStroke.getKeyStroke("LEFT"),  "moveLeft");
        im.put(KeyStroke.getKeyStroke("DOWN"),  "moveDown");
        im.put(KeyStroke.getKeyStroke("UP"),    "rotate");
        im.put(KeyStroke.getKeyStroke("SPACE"), "hardDrop");
        im.put(KeyStroke.getKeyStroke("P"),     "pause");
        im.put(KeyStroke.getKeyStroke("ESCAPE"),"exit");

        am.put("moveRight", new AbstractAction() { public void actionPerformed(ActionEvent e) { moveRight(); drawBoard(); }});
        am.put("moveLeft",  new AbstractAction() { public void actionPerformed(ActionEvent e) { moveLeft();  drawBoard(); }});
        am.put("moveDown",  new AbstractAction() { public void actionPerformed(ActionEvent e) { moveDown(); }});
        am.put("rotate",    new AbstractAction() { public void actionPerformed(ActionEvent e) { rotateBlock(); }});
        am.put("hardDrop",  new AbstractAction() { public void actionPerformed(ActionEvent e) { hardDrop(); }});
        am.put("pause",     new AbstractAction() { public void actionPerformed(ActionEvent e) { togglePause(); }});
        am.put("exit",      new AbstractAction() { public void actionPerformed(ActionEvent e) { exitGame(); }});
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
        }

        // 아랫 테두리
        for (int t = 0; t < WIDTH + 2; t++)
            sb.append(BORDER_CHAR);

        // 게임 정보
        sb.append("\nSCORE: ").append(score);
        sb.append("\nLEVEL: ").append(speedLevel);
        sb.append("\nNEXT: ").append(nextBlocks.peek().getClass().getSimpleName());
        if (isPaused)
            sb.append("\n[일시정지]");

        // ====== 텍스트 반영 ======
        pane.setText(sb.toString());
        StyledDocument doc = pane.getStyledDocument();

        // ====== 색칠 ======
        // 전체 문자열 좌표 순회 (테두리 포함)
        for (int i = 0; i < HEIGHT; i++) {
            for (int j = 0; j < WIDTH; j++) {
                Color c = null;

                // 보드 블록
                if (board[i][j] != null) {
                    c = board[i][j];
                }
                // 현재 움직이는 블록
                if (isCurrBlockAt(j, i)) {
                    c = curr.getColor();
                }

                if (c != null) {
                    SimpleAttributeSet blockStyle = new SimpleAttributeSet();
                    StyleConstants.setForeground(blockStyle, c);
                    int pos = (i + 1) * (WIDTH + 3) + (j + 1); // 줄바꿈 포함 offset
                    doc.setCharacterAttributes(pos, 1, blockStyle, true);
                }
            }
        }

        // ====== 테두리 색칠 ======
        Color borderColor = Color.LIGHT_GRAY; // 테두리 색
        SimpleAttributeSet borderStyle = new SimpleAttributeSet();
        StyleConstants.setForeground(borderStyle, borderColor);

        String text = pane.getText();
        for (int i = 0; i < text.length(); i++) {
            char ch = text.charAt(i);
            if (ch == BORDER_CHAR) {
                doc.setCharacterAttributes(i, 1, borderStyle, true);
            }
        }

        scoreLabel.setText("Score: " + score);
    }

    private boolean isCurrBlockAt(int j, int i) {
        for (int dy = 0; dy < curr.height(); dy++) {
            for (int dx = 0; dx < curr.width(); dx++) {
                if (curr.getShape(dx, dy) == 1) {
                    if (i == y + dy && j == x + dx)
                        return true;
                }
            }
        }
        return false;
    }

    private void togglePause() {
        isPaused = !isPaused;
        setStatus(isPaused ? "일시정지" : "진행중");
    }
    private void exitGame() {
        timer.stop();
        System.exit(0);
    }

    public void setStatus(String text) {
        if (statusLabel != null)
            statusLabel.setText(text);
    }
}