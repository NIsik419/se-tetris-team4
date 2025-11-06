package component.score;

import javax.swing.*;

import component.GameConfig;

import java.awt.*;
import java.time.format.DateTimeFormatter;

/** 스코어보드 오버레이 UI만 담당 */
public final class ScoreboardOverlay {
    private final JComponent container; // Board의 dialogPanel을 받음
    private final ScoreBoard scoreBoard;
    private final Runnable onRetry;
    private final Runnable onHome;

    public ScoreboardOverlay(JComponent container, ScoreBoard scoreBoard,
            Runnable onRetry, Runnable onHome) {
        this.container = container;
        this.scoreBoard = scoreBoard;
        this.onRetry = onRetry;
        this.onHome = onHome;
    }

    /**
     * Displays the scoreboard overlay.
     *
     * [UI Modification Guide]
     * - Everything except the 'addActionListener' parts can be freely modified
     * (layout, colors, fonts, table/columns, components, etc.).
     * - Please keep the following callback calls unchanged for integration:
     * onRetry.run();
     * onHome.run();
     *
     * @param highlightIndex the rank index to highlight in the table.
     *                       If the value is -1, no specific row will be
     *                       highlighted.
     *                       Used to visually emphasize the player's latest score.
     */
    public void show(int highlightIndex, GameConfig.Mode mode, GameConfig.Difficulty diff) {
        String[] cols = { "순위", "이름", "점수", "기록 시간" };
        var model = new javax.swing.table.DefaultTableModel(cols, 0) {
            @Override
            public boolean isCellEditable(int r, int c) {
                return false;
            }
        };

        var F = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
        var list = scoreBoard.getEntries(mode, diff);
        for (int i = 0; i < list.size(); i++) {
            var e = list.get(i);
            model.addRow(new Object[] { i + 1, e.name(), e.score(), F.format(e.at()) });
        }

        JTable table = new JTable(model);
        table.setFillsViewportHeight(true);
        if (highlightIndex >= 0 && highlightIndex < table.getRowCount()) {
            table.setRowSelectionInterval(highlightIndex, highlightIndex);
            table.setSelectionBackground(new Color(255, 230, 180));
            table.setSelectionForeground(Color.BLACK);
        }

        JLabel title = new JLabel("스코어보드 - " + mode + " / " + diff, JLabel.CENTER);
        title.setFont(new Font("Apple SD Gothic Neo, 맑은 고딕, Dialog", Font.PLAIN, 14));
        title.setBorder(BorderFactory.createEmptyBorder(4, 4, 8, 4));

        JPanel btns = new JPanel();
        JButton retry = new JButton("다시하기");
        JButton home = new JButton("홈으로");
        btns.add(retry);
        btns.add(home);

        container.removeAll();
        container.setLayout(new BorderLayout(8, 8));
        container.add(title, BorderLayout.NORTH);
        container.add(new JScrollPane(table), BorderLayout.CENTER);
        container.add(btns, BorderLayout.SOUTH);
        container.revalidate();
        container.repaint();

        retry.addActionListener(e -> onRetry.run());
        home.addActionListener(e -> {
            System.out.println("[DEBUG] 홈 버튼 클릭됨"); // 👈 추가
            onHome.run();
        }); 
    }
}
