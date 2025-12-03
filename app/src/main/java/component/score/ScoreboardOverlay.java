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
        // 기존 내용 싹 비우고, 컨테이너를 투명 + 가운데 정렬로 사용
        container.removeAll();
        container.setOpaque(false);
        container.setBackground(new Color(0, 0, 0, 0));
        container.setLayout(new GridBagLayout());

        // ===== 1) 데이터 준비 =====
        String[] cols = { "순위", "이름", "점수", "기록 시간" };
        var model = new javax.swing.table.DefaultTableModel(cols, 0) {
            @Override
            public boolean isCellEditable(int r, int c) {
                return false;
            }
        };

        var formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
        var list = scoreBoard.getEntries(mode, diff);
        for (int i = 0; i < list.size(); i++) {
            var e = list.get(i);
            model.addRow(new Object[]{
                    i + 1,
                    e.name(),
                    e.score(),
                    formatter.format(e.at())
            });
        }

        JTable table = new JTable(model);
        table.setFillsViewportHeight(true);
        table.setRowHeight(22);
        table.setFont(new Font("Apple SD Gothic Neo, 맑은 고딕, Dialog", Font.PLAIN, 12));
        table.getTableHeader().setFont(new Font("Apple SD Gothic Neo, 맑은 고딕, Dialog", Font.BOLD, 12));

        JScrollPane scroll = new JScrollPane(table);
            scroll.setBorder(BorderFactory.createEmptyBorder());
            scroll.setPreferredSize(new Dimension(420, 160));


        if (highlightIndex >= 0 && highlightIndex < table.getRowCount()) {
            table.setRowSelectionInterval(highlightIndex, highlightIndex);
            table.setSelectionBackground(new Color(255, 230, 180));
            table.setSelectionForeground(Color.BLACK);

            // 선택된 줄이 보이도록 스크롤 이동
            SwingUtilities.invokeLater(() -> {
                Rectangle rect = table.getCellRect(highlightIndex, 0, true);
                JViewport vp = scroll.getViewport();   // ← ScoreboardOverlay.show 안에 있는 scroll
                // 이 줄의 y 위치를 뷰의 맨 위에 오도록 스크롤 이동
                vp.setViewPosition(new Point(0, rect.y));
            });
        }

        // ===== 2) 네이비 카드 패널 (이름 입력창과 톤 통일) =====
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBackground(new Color(30, 38, 56)); // 어두운 네이비
        panel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(180, 180, 180), 2),  // 회색 테두리
                BorderFactory.createEmptyBorder(16, 20, 16, 20)                // 안쪽 여백
        ));
        panel.setPreferredSize(new Dimension(460, 260));

        JLabel title = new JLabel("스코어보드 - " + mode + " / " + diff, JLabel.CENTER);
        title.setForeground(Color.WHITE);
        title.setFont(new Font("Apple SD Gothic Neo, 맑은 고딕, Dialog", Font.BOLD, 14));
        title.setAlignmentX(Component.CENTER_ALIGNMENT);

        // 버튼 구역
        JPanel btns = new JPanel(new FlowLayout(FlowLayout.CENTER, 18, 0));
        btns.setOpaque(false);

        JButton retry = new JButton("다시하기");
        JButton home = new JButton("홈으로");

        Dimension btnSize = new Dimension(110, 32);
        retry.setPreferredSize(btnSize);
        home.setPreferredSize(btnSize);

        btns.add(retry);
        btns.add(home);

        // 카드 안에 컴포넌트 배치
        panel.add(title);
        panel.add(Box.createVerticalStrut(10));
        panel.add(scroll);
        panel.add(Box.createVerticalStrut(12));
        panel.add(btns);

        // 컨테이너 중앙에 카드 올리기
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.gridy = 0;
        container.add(panel, gbc);

        container.revalidate();
        container.repaint();

        // ===== 3) 버튼 콜백 유지 =====
        retry.addActionListener(e -> onRetry.run());
        home.addActionListener(e -> onHome.run());
    }
}
