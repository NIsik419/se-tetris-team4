package component.sidebar;

import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GridLayout;
import java.util.List;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;

import blocks.Block;
import component.ColorBlindPalette;
import versus.GarbagePreviewPanel;

public class HUDSidebar extends JPanel {
    private final JLabel scoreLabel = value("0");
    private final JLabel levelLabel = value("1");
    private final JLabel timeTitleLabel = title("Time");
    private final JLabel timeLabel  = value("00:00");

    // Next 블록 프리뷰
    private final NextBlockPanel next1 = new NextBlockPanel(80);

    // Time 박스 전체를 담는 패널 (showTime에서 토글)
    private JPanel timePanel;

    // === UI color constants (to match BoardPanel style) ===
    private static final Color BG_SIDEBAR   = new Color(0x0F141C);
    private static final Color BG_STAT      = new Color(0x181C26);
    private static final Color TEXT_TITLE   = new Color(0xB8D6FF);
    private static final Color TEXT_STAT    = new Color(0x8892B0);
    private static final Color TEXT_VALUE   = new Color(0xEDEFF2);
    private static final Color BORDER_PANEL = new Color(0x262C3A);

    // 가비지 미니 보드 (맨 아래에 배치)
    private final GarbagePreviewPanel garbagePreview = new GarbagePreviewPanel("INCOMING");

    public HUDSidebar() {
        setBackground(BG_SIDEBAR);
        setBorder(BorderFactory.createEmptyBorder(18, 18, 18, 18));
        setPreferredSize(new Dimension(220, 720));
        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));

        // NEXT 영역 -------------------------------------------------
        add(title("Next"));
        add(Box.createVerticalStrut(4));

        JPanel nextWrap = new JPanel();
        nextWrap.setOpaque(false);
        nextWrap.setLayout(new GridLayout(1, 1, 10, 10));
        nextWrap.add(next1);
        nextWrap.setMaximumSize(new Dimension(200, 150));
        nextWrap.setPreferredSize(new Dimension(200, 150));
        nextWrap.setBorder(BorderFactory.createLineBorder(BORDER_PANEL, 1));
        add(nextWrap);

        // 스탯 박스들 -----------------------------------------------
        add(Box.createVerticalStrut(18));
        add(statBox("Score", scoreLabel));
        add(Box.createVerticalStrut(10));
        add(statBox("Level", levelLabel));
        add(Box.createVerticalStrut(10));

        // Time 박스 (기본은 숨김, showTime(true) 때 보이게)
        timePanel = statBoxCustom(timeTitleLabel, timeLabel);
        timePanel.setVisible(false);
        add(timePanel);
        add(Box.createVerticalStrut(10));

        // 나머지 공간 비우고, 맨 아래에 가비지 미니보드 배치 ----------------
        add(Box.createVerticalGlue());

        garbagePreview.setAlignmentX(Component.CENTER_ALIGNMENT);
        // 사이드바 폭 안에서 예쁘게 보이도록 최대 크기 조정
        garbagePreview.setMaximumSize(new Dimension(200, 320));
        add(Box.createVerticalStrut(8));
        add(garbagePreview);
    }

    private static JLabel title(String t) {
        JLabel l = new JLabel(t);
        l.setForeground(TEXT_TITLE);
        l.setFont(l.getFont().deriveFont(Font.BOLD, 20f));
        l.setAlignmentX(Component.CENTER_ALIGNMENT);
        l.setHorizontalAlignment(SwingConstants.CENTER);
        return l;
    }

    private static JLabel value(String t) {
        JLabel l = new JLabel(t);
        l.setForeground(TEXT_VALUE);
        l.setFont(l.getFont().deriveFont(Font.BOLD, 17f));
        l.setAlignmentX(LEFT_ALIGNMENT);
        l.setBorder(BorderFactory.createEmptyBorder(4, 0, 6, 0));
        return l;
    }

    public void setScore(int s) {
        scoreLabel.setText(String.valueOf(s));
    }

    public void setLevel(int lv) {
        levelLabel.setText(String.valueOf(lv));
    }

    public void setTime(long seconds) {
        long m = seconds / 60, s = seconds % 60;
        timeLabel.setText(String.format("%02d:%02d", m, s));
    }

    /** 기존 char[][] 기반 Next queue */
    public void setNextQueue(List<char[][]> shapes) {
        next1.setShape(shapes.size() > 0 ? shapes.get(0) : null);
    }

    /** Block 리스트를 직접 받는 메서드 */
    public void setNextBlocks(List<Block> blocks) {
        if (blocks == null || blocks.isEmpty()) {
            next1.setBlock(null);
            next1.repaint();
            return;
        }

        next1.setBlock(blocks.get(0));
        next1.repaint();
    }

    private JPanel statBox(String label, JLabel value) {
        JLabel titleLabel = new JLabel(label.toUpperCase());
        titleLabel.setForeground(TEXT_STAT);
        titleLabel.setFont(titleLabel.getFont().deriveFont(Font.BOLD, 13f));
        titleLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        titleLabel.setHorizontalAlignment(SwingConstants.CENTER);

        return statBoxCustom(titleLabel, value);
    }

    /** 공통 stat 박스 빌더 (Time 포함) */
    private JPanel statBoxCustom(JLabel title, JLabel value) {
        JPanel p = new JPanel();
        p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));
        p.setOpaque(true);
        p.setBackground(BG_STAT);
        p.setBorder(BorderFactory.createEmptyBorder(10, 20, 10, 20));

        Dimension boxSize = new Dimension(180, 70);
        p.setPreferredSize(boxSize);
        p.setMaximumSize(boxSize);
        p.setMinimumSize(boxSize);
        p.setAlignmentX(Component.CENTER_ALIGNMENT);

        title.setAlignmentX(Component.CENTER_ALIGNMENT);
        value.setAlignmentX(Component.CENTER_ALIGNMENT);
        title.setHorizontalAlignment(SwingConstants.CENTER);
        value.setHorizontalAlignment(SwingConstants.CENTER);

        p.add(title);
        p.add(Box.createVerticalStrut(4));
        p.add(value);
        return p;
    }

    /** Time 박스 보여줄지 여부 */
    public void showTime(boolean visible) {
        timeTitleLabel.setVisible(visible);
        timeLabel.setVisible(visible);
        if (timePanel != null) {
            timePanel.setVisible(visible);
        }
        revalidate();
        repaint();
    }

    public void reset() {
        setScore(0);
        setLevel(1);
        setTime(0);
        setNextQueue(java.util.Collections.emptyList());
        setGarbageLines(java.util.Collections.emptyList());
    }

    // VersusPanel / VersusGameManager 에서 호출할 가비지 프리뷰 업데이트
    public void setGarbageLines(List<boolean[]> lines) {
        garbagePreview.setGarbageLines(lines);
        repaint();
    }

    // 색맹 모드 적용 (SettingsScreen / BoardPanel에서 연결할 수 있게)
    public void setColorMode(ColorBlindPalette.Mode mode) {
        if (next1 != null) {
            next1.setColorMode(mode);
        }
        repaint();
    }
}
