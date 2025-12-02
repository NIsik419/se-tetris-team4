package versus;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

/**
 * GarbagePreviewPanel
 * - 넘어올 예정인 가비지 줄을 미니 보드 형태로 보여주는 패널
 * - 10열 기준, 아래에서부터 최대 MAX_ROWS 줄까지 표시
 *
 * 사용법:
 *   List<boolean[]> lines = ...; // 각 줄당 length <= 10, true = 블록, false = 빈칸
 *   previewPanel.setGarbageLines(lines);
 */
public class GarbagePreviewPanel extends JPanel {

    // 테트리스 보드 기본 10칸 가정
    private static final int COLS = 10;
    // 미니 보드에 최대 몇 줄까지 보여줄지
    private static final int MAX_ROWS = 6;

    private final JLabel titleLabel;
    private final JLabel countLabel;

    // 아래에서부터 위로 쌓이는 순서로 저장 (0번이 가장 아래 라인이라고 보기)
    private final List<boolean[]> lines = new ArrayList<>();

    public GarbagePreviewPanel(String title) {
        setOpaque(false);
        setLayout(new BorderLayout(0, 4));
        setBorder(BorderFactory.createEmptyBorder(6, 8, 6, 8));

        // 🔹 사이드바 폭에 맞추고, 높이만 좀 더 키움
        setPreferredSize(new Dimension(160, 320));   // (기존 180, 240 정도였음)

        titleLabel = new JLabel(title, SwingConstants.CENTER);
        titleLabel.setForeground(new Color(180, 195, 210));
        titleLabel.setFont(new Font("Arial", Font.PLAIN, 12));   // 살짝 키움

        countLabel = new JLabel("0", SwingConstants.CENTER);
        countLabel.setForeground(new Color(230, 240, 255));
        countLabel.setFont(new Font("Arial", Font.BOLD, 14));    // 숫자 조금 더 크게

        add(titleLabel, BorderLayout.NORTH);
        add(countLabel, BorderLayout.SOUTH);
    }

    /**
     * 넘어올 예정인 가비지 라인 목록 설정
     * @param newLines 전체 가비지 큐 (아래 라인부터 위쪽 순서라고 가정하거나,
     *                 순서는 게임 로직에서 맞춰서 넘겨주면 됨)
     */
    public synchronized void setGarbageLines(List<boolean[]> newLines) {
        lines.clear();
        if (newLines != null && !newLines.isEmpty()) {
            // 너무 많이 오면 마지막 MAX_ROWS줄만 보여주기
            int start = Math.max(0, newLines.size() - MAX_ROWS);
            for (int i = start; i < newLines.size(); i++) {
                boolean[] src = newLines.get(i);
                boolean[] row = new boolean[COLS];
                if (src != null) {
                    System.arraycopy(src, 0, row, 0, Math.min(COLS, src.length));
                }
                lines.add(row);
            }
            countLabel.setText(String.valueOf(newLines.size())); // 전체 줄 수 표시
        } else {
            countLabel.setText("0");
        }
        repaint();
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        List<boolean[]> snapshot;
        synchronized (this) {
            snapshot = new ArrayList<>(lines);
        }

        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                RenderingHints.VALUE_ANTIALIAS_ON);

        int w = getWidth();
        int h = getHeight();

        // 미니 보드 전체 영역 (타이틀/카운트 제외)
        int margin = 8;
        int boardX = margin;
        int boardY = margin + 20;         // 타이틀 밑으로 조금 띄우기
        int boardW = w - margin * 2;
        int boardH = h - boardY - 28;     // 아래 countLabel 공간 조금 더 확보

        // 셀을 정사각형에 가깝게 만들기 위해 한 변 길이를 통일
        int cellMargin = 2;
        int usableW = boardW - cellMargin * 2;
        int usableH = boardH - cellMargin * 2;

        // 🔹 높이를 늘렸으니, 같은 MAX_ROWS 기준에서 셀 크기가 자연스럽게 더 커짐
        int cellSize = Math.min(usableW / COLS, usableH / MAX_ROWS);
        int gridW = cellSize * COLS;
        int gridH = cellSize * MAX_ROWS;

        // 실제 그리드가 가운데 오도록 위치 보정
        int gridX = boardX + (boardW - gridW) / 2;
        // int gridY = boardY + (boardH - gridH) / 2;
        int gridY = boardY + cellMargin;

        // 배경 & 테두리 (그리드 전체를 감싸는 보드)
        g2.setColor(new Color(20, 24, 34));
        g2.fillRoundRect(gridX - cellMargin, gridY - cellMargin,
                gridW + cellMargin * 2, gridH + cellMargin * 2, 10, 10);
        g2.setColor(new Color(80, 90, 110));
        g2.drawRoundRect(gridX - cellMargin, gridY - cellMargin,
                gridW + cellMargin * 2, gridH + cellMargin * 2, 10, 10);

        if (!snapshot.isEmpty() && cellSize > 0) {
            int rowsToDraw = snapshot.size();       // 최대 MAX_ROWS

            // 아래 줄부터 위로 그리기
            for (int r = 0; r < rowsToDraw; r++) {
                boolean[] row = snapshot.get(rowsToDraw - 1 - r);
                int y = gridY + gridH - (r + 1) * cellSize;
                for (int c = 0; c < COLS; c++) {
                    int x = gridX + c * cellSize;

                    if (row != null && c < row.length && row[c]) {
                        // 가비지 블록 칸
                        g2.setColor(new Color(115, 180, 230));
                        g2.fillRect(x + 1, y + 1, cellSize - 2, cellSize - 2);
                    } else {
                        // 빈칸 그리드(연하게)
                        g2.setColor(new Color(40, 50, 70));
                        g2.drawRect(x + 1, y + 1, cellSize - 2, cellSize - 2);
                    }
                }
            }
        }

        g2.dispose();
    }
}
