package component.score;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.FlowLayout;
import java.awt.Font;
import java.time.format.DateTimeFormatter;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.KeyStroke;
import javax.swing.SwingConstants;
import javax.swing.border.EmptyBorder;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.JTableHeader;

import component.GameConfig;

public class ScoreboardPanel extends JPanel {

    private final ScoreBoard scoreBoard;
    private final Runnable onBack; // 메뉴로 돌아가기 콜백

    private final JComboBox<GameConfig.Mode> cbMode =
            new JComboBox<>(GameConfig.Mode.values());
    private final JComboBox<GameConfig.Difficulty> cbDiff =
            new JComboBox<>(GameConfig.Difficulty.values());

    private final DefaultTableModel model;
    private final JTable table;

    private static final DateTimeFormatter F =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    // [UI] 공통 색상/스타일 상수 (필요하면 다른 컴포넌트에서도 재사용 가능)
    private static final Color BG_DARK = new Color(18, 18, 28);
    private static final Color BG_PANEL = new Color(30, 30, 46);
    private static final Color ACCENT = new Color(99, 179, 237);
    private static final Color ACCENT_SOFT = new Color(55, 65, 81);
    private static final Color TEXT_PRIMARY = new Color(236, 239, 244);
    private static final Color TEXT_SECONDARY = new Color(156, 163, 175);


    public ScoreboardPanel(ScoreBoard scoreBoard, Runnable onBack) {
        this.scoreBoard = scoreBoard;
        this.onBack = onBack;

        setLayout(new BorderLayout(12, 12));
        setBorder(new EmptyBorder(16, 16, 16, 16));
        setBackground(BG_DARK); // Main background theme

        //  Make a top panel that stacks header + filter vertically
        JPanel northPanel = new JPanel();
        northPanel.setLayout(new BoxLayout(northPanel, BoxLayout.Y_AXIS));
        northPanel.setOpaque(false);


        // 헤더(타이틀 + 뒤로가기)
        JPanel header = new JPanel(new BorderLayout());
        header.setOpaque(false);

        JLabel title = new JLabel("Scoreboard");
        title.setFont(title.getFont().deriveFont(Font.BOLD, 24f));
         title.setForeground(TEXT_PRIMARY); // title text color updated
        header.add(title, BorderLayout.WEST);

        JButton btnBack = new JButton("← Back");
        styleSecondaryButton(btnBack);
        btnBack.addActionListener(e -> {
            if (onBack != null) onBack.run();
        });
        header.add(btnBack, BorderLayout.EAST);
        

        // 상단 필터 바
        JPanel top = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        top.setOpaque(false); // Make top panel transparent
        top.setBorder(new EmptyBorder(10, 0, 8, 0)); // spacing

        JLabel lblMode = new JLabel("Mode:");
        lblMode.setForeground(TEXT_SECONDARY);
        top.add(lblMode);

        styleComboBox(cbMode); 
        top.add(cbMode);

        top.add(Box.createHorizontalStrut(10));

        JLabel lblDiff = new JLabel("Difficulty:");
        lblDiff.setForeground(TEXT_SECONDARY); // NEW
        top.add(lblDiff);

        styleComboBox(cbDiff); // NEW
        top.add(cbDiff);

        top.add(Box.createHorizontalStrut(12));

        JButton btnRefresh = new JButton("Refresh");
        btnRefresh.setToolTipText("파일 재로딩 후 새로 고침");
        stylePrimaryButton(btnRefresh);
        btnRefresh.addActionListener(e -> {
            scoreBoard.load(); // 파일에서 다시 로드
            reloadTable();
        });
        top.add(btnRefresh);

        // (선택) 리셋 버튼들
        JButton btnResetBucket = new JButton("Reset This Bucket");
        styleSecondaryButton(btnResetBucket); 
        btnResetBucket.addActionListener(e -> {
            var m = (GameConfig.Mode) cbMode.getSelectedItem();
            var d = (GameConfig.Difficulty) cbDiff.getSelectedItem();
            scoreBoard.resetBucket(m, d);
            reloadTable();
        });
        top.add(btnResetBucket);

        JButton btnResetAll = new JButton("Reset All");
        styleSecondaryButton(btnResetAll);
        btnResetAll.addActionListener(e -> {
            int ans = JOptionPane.showConfirmDialog(this,
                    "모든 모드/난이도 스코어를 초기화할까요?",
                    "Reset All", JOptionPane.YES_NO_OPTION);
            if (ans == JOptionPane.YES_OPTION) {
                scoreBoard.resetAll();
                reloadTable();
            }
        });
        top.add(btnResetAll);

        // Header + Filter combined into one top panel
        northPanel.add(header);
        northPanel.add(Box.createVerticalStrut(8)); // NEW spacing
        northPanel.add(top);

        add(header, BorderLayout.NORTH);   // title + back button at the top
        add(top, BorderLayout.SOUTH);      // filter bar moved to bottom again

        // 테이블
        String[] cols = {"순위", "이름", "점수", "기록 시각", "모드", "난이도"};
        model = new DefaultTableModel(cols, 0) {
            @Override public boolean isCellEditable(int r, int c) { return false; }
        };
        table = new JTable(model);

        // NEW: Table dark theme styling
        table.setBackground(BG_PANEL);
        table.setForeground(TEXT_PRIMARY);
        table.setGridColor(new Color(55, 65, 81)); // NEW
        table.setSelectionBackground(ACCENT); // NEW highlight color
        table.setSelectionForeground(Color.BLACK);
        table.setRowHeight(24);
        table.setFillsViewportHeight(true);
        table.setShowVerticalLines(false); // NEW

        // NEW: Table header styling
        JTableHeader tableHeader = table.getTableHeader();
        tableHeader.setBackground(ACCENT_SOFT);
        tableHeader.setForeground(TEXT_PRIMARY);
        tableHeader.setFont(tableHeader.getFont().deriveFont(Font.BOLD, 13f));
        tableHeader.setReorderingAllowed(false);

        // NEW: Center alignment for specific columns
        DefaultTableCellRenderer centerRenderer = new DefaultTableCellRenderer();
        centerRenderer.setHorizontalAlignment(SwingConstants.CENTER);
        table.getColumnModel().getColumn(0).setCellRenderer(centerRenderer);
        table.getColumnModel().getColumn(2).setCellRenderer(centerRenderer);
        table.getColumnModel().getColumn(4).setCellRenderer(centerRenderer);
        table.getColumnModel().getColumn(5).setCellRenderer(centerRenderer);

        // NEW: Added striped row effect for better readability
        DefaultTableCellRenderer striped = new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(
                    JTable table, Object value, boolean isSelected,
                    boolean hasFocus, int row, int column) {

                Component c = super.getTableCellRendererComponent(
                        table, value, isSelected, hasFocus, row, column);

                if (!isSelected) {
                    c.setBackground(row % 2 == 0
                            ? new Color(39, 39, 57)
                            : BG_PANEL);
                    c.setForeground(TEXT_PRIMARY);
                }
                return c;
            }
        };

        

        for (int i = 0; i < table.getColumnCount(); i++) {
            table.getColumnModel().getColumn(i).setCellRenderer(striped);
        }

        JScrollPane scroll = new JScrollPane(table);
        scroll.getViewport().setBackground(BG_PANEL); // NEW
        scroll.setBorder(BorderFactory.createEmptyBorder());
        add(scroll, BorderLayout.CENTER);

        // EDIT: hooks → instant update when mode/difficulty changes
        cbMode.addActionListener(e -> reloadTable());
        cbDiff.addActionListener(e -> reloadTable());

        // EDIT: default selection
        cbMode.setSelectedItem(GameConfig.Mode.CLASSIC);
        cbDiff.setSelectedItem(GameConfig.Difficulty.EASY);

        reloadTable();

        // EDIT: ESC = back
        registerKeyboardAction(e -> { if (onBack != null) onBack.run(); },
                KeyStroke.getKeyStroke("ESCAPE"),
                JComponent.WHEN_IN_FOCUSED_WINDOW);

        addComponentListener(new java.awt.event.ComponentAdapter() {
            @Override public void componentShown(java.awt.event.ComponentEvent e) {
                scoreBoard.load();
                reloadTable();
            }
        });
    }

    public void setInitialSelection(GameConfig.Mode mode, GameConfig.Difficulty diff) {
        cbMode.setSelectedItem(mode);
        cbDiff.setSelectedItem(diff);
        reloadTable();
    }

    private void reloadTable() {
        model.setRowCount(0);
        var mode = (GameConfig.Mode) cbMode.getSelectedItem();
        var diff = (GameConfig.Difficulty) cbDiff.getSelectedItem();
        var list = scoreBoard.getEntries(mode, diff);

        for (int i = 0; i < list.size(); i++) {
            var e = list.get(i);
            model.addRow(new Object[]{
                    i + 1,
                    e.name(),
                    e.score(),
                    e.at().format(F),
                    e.mode(),
                    e.difficulty()
            });
        }
    }

    // ===== UI Helper Methods =====

    // NEW: Styling for main/highlighted buttons
    private void stylePrimaryButton(JButton btn) {
        btn.setFocusPainted(false);
        btn.setBackground(ACCENT);
        btn.setForeground(Color.BLACK);
        btn.setBorder(BorderFactory.createEmptyBorder(6, 12, 6, 12));
        btn.setFont(btn.getFont().deriveFont(Font.BOLD, 12f));
        btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
    }

    // Styling for secondary/normal buttons
    private void styleSecondaryButton(JButton btn) {
        btn.setFocusPainted(false);
        btn.setBackground(ACCENT_SOFT);
        btn.setForeground(TEXT_PRIMARY);
        btn.setBorder(BorderFactory.createEmptyBorder(6, 12, 6, 12));
        btn.setFont(btn.getFont().deriveFont(Font.PLAIN, 12f));
        btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
    }

    // Styling for mode/difficulty dropdowns
    private void styleComboBox(JComboBox<?> comboBox) {
    comboBox.setBackground(Color.WHITE);   // NEW: white background
    comboBox.setForeground(Color.BLACK);   // NEW: black text
    comboBox.setBorder(BorderFactory.createEmptyBorder(2, 6, 2, 6));
    comboBox.setFocusable(false);

    // Ensures dropdown menu text is also black
    ((JLabel) comboBox.getRenderer()).setForeground(Color.BLACK);
    }

}