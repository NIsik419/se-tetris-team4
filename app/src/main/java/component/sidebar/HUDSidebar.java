package component.sidebar;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GridLayout;
import java.awt.Component;
import java.util.List;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;

import blocks.Block; 

public class HUDSidebar extends JPanel {
    private final JLabel scoreLabel = value("0");
    private final JLabel levelLabel = value("1");
    private final JLabel timeTitleLabel = title("Time");
    private final JLabel timeLabel  = value("00:00");

    // smaller preview boxes
    private final NextBlockPanel next1 = new NextBlockPanel(80);
    // private final NextBlockPanel next2 = new NextBlockPanel(96);
    // private final NextBlockPanel next3 = new NextBlockPanel(96);

    public HUDSidebar() {
        setBackground(new Color(0x0F141C));
        setBorder(BorderFactory.createEmptyBorder(18, 18, 18, 18));
        setPreferredSize(new Dimension(220, 720));
        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));

        add(title("Next"));
        add(Box.createVerticalStrut(4));

        JPanel nextWrap = new JPanel();
        nextWrap.setOpaque(false);
        nextWrap.setLayout(new GridLayout(1, 1, 10, 10));
        nextWrap.add(next1);
        nextWrap.setMaximumSize(new Dimension(200, 150));
        nextWrap.setPreferredSize(new Dimension(200, 150));

        // nextWrap.add(next2);
        // nextWrap.add(next3);
        add(nextWrap);

        add(Box.createVerticalStrut(18));
        add(statBox("Score", scoreLabel));  
        add(Box.createVerticalStrut(10));
        add(statBox("Level", levelLabel)); 
        add(Box.createVerticalStrut(10));
        add(Box.createVerticalGlue());
    }

    private JLabel title(String t) {
        JLabel l = new JLabel(t);
        l.setForeground(new Color(0xB8D6FF));
        l.setFont(l.getFont().deriveFont(Font.BOLD, 20f));
        l.setAlignmentX(Component.CENTER_ALIGNMENT);
        l.setHorizontalAlignment(SwingConstants.CENTER);
        return l;
    }
    private static JLabel value(String t) {
        JLabel l = new JLabel(t);
        l.setForeground(new Color(0xEDEFF2));
        l.setFont(l.getFont().deriveFont(Font.BOLD, 17f));
        l.setAlignmentX(LEFT_ALIGNMENT);
        l.setBorder(BorderFactory.createEmptyBorder(4, 0, 6, 0));
        return l;
    }

    public void setScore(int s) { scoreLabel.setText(String.valueOf(s)); }
    public void setLevel(int lv) { levelLabel.setText(String.valueOf(lv)); }
    public void setTime(long seconds) {
        long m = seconds / 60, s = seconds % 60;
        timeLabel.setText(String.format("%02d:%02d", m, s));
    }

    /** Set up to 3 next shapes; extra slots may be null. */
    public void setNextQueue(List<char[][]> shapes) {
        next1.setShape(shapes.size() > 0 ? shapes.get(0) : null);
        // next2.setShape(shapes.size() > 1 ? shapes.get(1) : null);
        // next3.setShape(shapes.size() > 2 ? shapes.get(2) : null);
    }

    // Block 리스트를 직접 받는 메서드 추가
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
        JPanel p = new JPanel();
        p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));
        p.setOpaque(true);
        p.setBackground(new Color(0x181C26));
        p.setBorder(BorderFactory.createEmptyBorder(10, 20, 10, 20));

        // width + height 고정
        Dimension boxSize = new Dimension(180, 70);
        p.setPreferredSize(boxSize);
        p.setMaximumSize(boxSize);
        p.setMinimumSize(boxSize);

        // BoxLayout(Y_AXIS)에서 width를 강제로 줄이려면 반드시 필요
        p.setAlignmentX(Component.CENTER_ALIGNMENT);

        JLabel title = new JLabel(label.toUpperCase());
        title.setForeground(new Color(0x8892B0));
        title.setFont(title.getFont().deriveFont(Font.BOLD, 13f));
        title.setAlignmentX(Component.CENTER_ALIGNMENT);
        value.setAlignmentX(Component.CENTER_ALIGNMENT);
        title.setHorizontalAlignment(SwingConstants.CENTER);
        value.setHorizontalAlignment(SwingConstants.CENTER);

        p.add(title);
        p.add(Box.createVerticalStrut(4));
        p.add(value);
        return p;
    }

    public void showTime(boolean visible) {
        timeTitleLabel.setVisible(visible);
        timeLabel.setVisible(visible);
    }

    public void reset() {
        setScore(0); setLevel(1); setTime(0);
        setNextQueue(java.util.Collections.emptyList());
    }
}