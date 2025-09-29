package component;

import java.awt.*;
import java.awt.event.*;
import java.util.*;
import javax.swing.*;
import javax.swing.border.CompoundBorder;
import javax.swing.text.*;

import blocks.*;

public class Board extends JFrame {

    private static final long serialVersionUID = 1L;

    public static final int HEIGHT = 20;
    public static final int WIDTH = 10;
    public static final char BORDER_CHAR = 'X';

    private JTextPane pane;
    private Color[][] board;
    private javax.swing.Timer timer;

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
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

        // 보드 출력 패널
        pane = new JTextPane();
        pane.setEditable(false);
        pane.setBackground(Color.BLACK);
        CompoundBorder border = BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(Color.GRAY, 10),
                BorderFactory.createLineBorder(Color.DARK_GRAY, 5));
        pane.setBorder(border);
        this.getContentPane().add(pane, BorderLayout.CENTER);

        // 게임 루프 타이머
        timer = new javax.swing.Timer(initInterval, e -> {
            if (!isPaused) moveDown();
        });

        // 보드 초기화
        board = new Color[HEIGHT][WIDTH];

        // 큐 초기화
        for (int i = 0; i < NEXT_SIZE; i++) {
            nextBlocks.add(getRandomBlock());
        }
        curr = nextBlocks.poll();

        // 키 바인딩 등록
        setupKeyBindings();

        drawBoard();
        timer.start();

        // 창 크기 & 표시
        setSize(300, 600);
        setVisible(true);
        pane.requestFocusInWindow();
    }

    private Block getRandomBlock() {
        Random rnd = new Random(System.currentTimeMillis());
        int block = rnd.nextInt(7);
        switch (block) {
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
                    int bx = newX + i;
                    int by = newY + j;
                    if (bx < 0 || bx >= WIDTH || by < 0 || by >= HEIGHT) return false;
                    if (board[by][bx] != null) return false;
                }
            }
        }
        return true;
    }

    protected void moveDown() {
        if (canMove(curr, x, y + 1)) {
            y++;
            score++;
        } else {
            // 고정
            for (int j = 0; j < curr.height(); j++) {
                for (int i = 0; i < curr.width(); i++) {
                    if (curr.getShape(i, j) == 1) {
                        board[y + j][x + i] = curr.getColor();
                    }
                }
            }
            clearLines();

            // 다음 블럭 큐에서 가져오기
            curr = nextBlocks.poll();
            nextBlocks.add(getRandomBlock());
            x = 3; y = 0;

            if (!canMove(curr, x, y)) {
                gameOver();
            }
        }
        drawBoard();
    }

    private void clearLines() {
        for (int i = 0; i < HEIGHT; i++) {
            boolean full = true;
            for (int j = 0; j < WIDTH; j++) {
                if (board[i][j] == null) {
                    full = false;
                    break;
                }
            }
            if (full) {
                for (int k = i; k > 0; k--) {
                    board[k] = board[k - 1].clone();
                }
                board[0] = new Color[WIDTH];
                score += 100;
                clearedLines++;

                // 난이도 상승 체크
                if (clearedLines % 10 == 0) {
                    increaseSpeed();
                }
            }
        }
    }

    private void increaseSpeed() {
        int newDelay = Math.max(200, timer.getDelay() - 100);
        timer.setDelay(newDelay);
        speedLevel++;
        System.out.println("난이도 상승! 레벨: " + speedLevel + ", 딜레이: " + newDelay + "ms");
    }

    private void gameOver() {
        timer.stop();
        System.out.println("GAME OVER! Score: " + score);
    }

    protected void moveRight() {
        if (canMove(curr, x + 1, y)) x++;
        drawBoard();
    }

    protected void moveLeft() {
        if (canMove(curr, x - 1, y)) x--;
        drawBoard();
    }

    protected void hardDrop() {
        while (canMove(curr, x, y + 1)) {
            y++;
            score += 2;
        }
        moveDown();
    }

    // 일시정지 토글
    private void togglePause() {
        isPaused = !isPaused;
        System.out.println(isPaused ? "게임 일시정지" : "게임 재개");
    }

    // 게임 종료 처리
    private void exitGame() {
        timer.stop();
        System.out.println("게임 종료. 최종 점수: " + score);
        System.exit(0);
    }

    // 키 바인딩 설정 (즉시 반응 & 반복 입력 지원)
    private void setupKeyBindings() {
        InputMap im = pane.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW);
        ActionMap am = pane.getActionMap();

        im.put(KeyStroke.getKeyStroke("RIGHT"), "moveRight");
        im.put(KeyStroke.getKeyStroke("LEFT"), "moveLeft");
        im.put(KeyStroke.getKeyStroke("DOWN"), "moveDown");
        im.put(KeyStroke.getKeyStroke("UP"), "rotate");
        im.put(KeyStroke.getKeyStroke("SPACE"), "hardDrop");
        im.put(KeyStroke.getKeyStroke("P"), "pause");
        im.put(KeyStroke.getKeyStroke("ESCAPE"), "exit");

        am.put("moveRight", new AbstractAction() {
            public void actionPerformed(ActionEvent e) { moveRight(); drawBoard(); }
        });
        am.put("moveLeft", new AbstractAction() {
            public void actionPerformed(ActionEvent e) { moveLeft(); drawBoard(); }
        });
        am.put("moveDown", new AbstractAction() {
            public void actionPerformed(ActionEvent e) { moveDown(); drawBoard(); }
        });
        am.put("rotate", new AbstractAction() {
            public void actionPerformed(ActionEvent e) { curr.rotate(); drawBoard(); }
        });
        am.put("hardDrop", new AbstractAction() {
            public void actionPerformed(ActionEvent e) { hardDrop(); drawBoard(); }
        });
        am.put("pause", new AbstractAction() {
            public void actionPerformed(ActionEvent e) { togglePause(); }
        });
        am.put("exit", new AbstractAction() {
            public void actionPerformed(ActionEvent e) { exitGame(); }
        });
    }

    public void drawBoard() {
        StringBuilder sb = new StringBuilder();

        // 상단 테두리
        for (int t = 0; t < WIDTH + 2; t++) sb.append(BORDER_CHAR);
        sb.append("\n");

        for (int i = 0; i < HEIGHT; i++) {
            sb.append(BORDER_CHAR);
            for (int j = 0; j < WIDTH; j++) {
                if (board[i][j] != null || isCurrBlockAt(j, i)) {
                    sb.append("O");
                } else {
                    sb.append(" ");
                }
            }
            sb.append(BORDER_CHAR);
            sb.append("\n");
        }

        for (int t = 0; t < WIDTH + 2; t++) sb.append(BORDER_CHAR);
        sb.append("\nSCORE: ").append(score);
        sb.append("\nLEVEL: ").append(speedLevel);
        sb.append("\nNEXT: ").append(nextBlocks.peek().getClass().getSimpleName());
        sb.append("\n").append(isPaused ? "[일시정지]" : "");

        pane.setText(sb.toString());
        StyledDocument doc = pane.getStyledDocument();

        // 기본 폰트 스타일
        SimpleAttributeSet baseStyle = new SimpleAttributeSet();
        StyleConstants.setFontFamily(baseStyle, "Courier New");
        StyleConstants.setFontSize(baseStyle, 18);
        StyleConstants.setForeground(baseStyle, Color.WHITE);
        doc.setParagraphAttributes(0, doc.getLength(), baseStyle, false);

        // 블럭 색칠
        for (int i = 0; i < HEIGHT; i++) {
            for (int j = 0; j < WIDTH; j++) {
                Color c = board[i][j];
                if (isCurrBlockAt(j, i)) c = curr.getColor();
                if (c != null) {
                    SimpleAttributeSet blockStyle = new SimpleAttributeSet();
                    StyleConstants.setFontFamily(blockStyle, "Courier New");
                    StyleConstants.setFontSize(blockStyle, 18);
                    StyleConstants.setForeground(blockStyle, c);
                    int pos = (i + 1) * (WIDTH + 3) + (j + 1);
                    doc.setCharacterAttributes(pos, 1, blockStyle, true);
                }
            }
        }
    }

    private boolean isCurrBlockAt(int j, int i) {
        for (int dy = 0; dy < curr.height(); dy++) {
            for (int dx = 0; dx < curr.width(); dx++) {
                if (curr.getShape(dx, dy) == 1) {
                    if (i == y + dy && j == x + dx) {
                        return true;
                    }
                }
            }
        }
        return false;
    }
}
