package component;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GridLayout;
import java.util.List;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JLabel;
import javax.swing.JPanel;

public class HUDSidebar extends JPanel {
    private final JLabel scoreLabel = value("0");
    private final JLabel levelLabel = value("1");
    private final JLabel timeLabel  = value("00:00");

    // smaller preview boxes
    private final NextBlockPanel next1 = new NextBlockPanel(96);
    private final NextBlockPanel next2 = new NextBlockPanel(96);
    private final NextBlockPanel next3 = new NextBlockPanel(96);

    public HUDSidebar() {
        setBackground(new Color(0x0F141C));
        setBorder(BorderFactory.createEmptyBorder(18, 18, 18, 18));
        setPreferredSize(new Dimension(220, 720));
        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));

        add(title("Next"));
        add(Box.createVerticalStrut(8));

        JPanel nextWrap = new JPanel();
        nextWrap.setOpaque(false);
        nextWrap.setLayout(new GridLayout(3, 1, 10, 10));
        nextWrap.add(next1);
        nextWrap.add(next2);
        nextWrap.add(next3);
        add(nextWrap);

        add(Box.createVerticalStrut(18));
        add(title("Score"));  add(scoreLabel);  add(Box.createVerticalStrut(10));
        add(title("Level"));  add(levelLabel);  add(Box.createVerticalStrut(10));
        add(title("Time"));   add(timeLabel);   add(Box.createVerticalGlue());
    }

    private JLabel title(String t) {
        JLabel l = new JLabel(t);
        l.setForeground(new Color(0xB8D6FF));
        l.setFont(l.getFont().deriveFont(Font.BOLD, 20f));
        l.setAlignmentX(LEFT_ALIGNMENT);
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
        next2.setShape(shapes.size() > 1 ? shapes.get(1) : null);
        next3.setShape(shapes.size() > 2 ? shapes.get(2) : null);
    }

    public void reset() {
        setScore(0); setLevel(1); setTime(0);
        setNextQueue(java.util.Collections.emptyList());
    }
}
