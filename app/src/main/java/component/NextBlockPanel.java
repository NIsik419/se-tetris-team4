package component;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.GradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;

import javax.swing.BorderFactory;
import javax.swing.JPanel;

public class NextBlockPanel extends JPanel {
    private char[][] shape; // 4x4, 'O' filled

    private final int box;  // side length

    public NextBlockPanel() { this(96); }
    public NextBlockPanel(int sizePx) {
        this.box = sizePx;
        setPreferredSize(new Dimension(box, box));
        setMinimumSize(new Dimension(box, box));
        setBackground(new Color(0x191E28));
        setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(0x303540), 2),
                BorderFactory.createEmptyBorder(10,10,10,10)
        ));
    }

    public void setShape(char[][] s) { this.shape = s; repaint(); }

    @Override protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        int cell = Math.min((getWidth()-20)/4, (getHeight()-20)/4);
        int offX = (getWidth() - cell*4)/2, offY = (getHeight() - cell*4)/2;

        // inner rounded board
        g2.setColor(new Color(0x232937));
        g2.fillRoundRect(6,6,getWidth()-12,getHeight()-12,16,16);

        if (shape != null) {
            for (int r=0; r<shape.length; r++) {
                for (int c=0; c<shape[r].length; c++) {
                    if (shape[r][c] != ' ') {
                        int x = offX + c*cell + 2;
                        int y = offY + r*cell + 2;
                        int s = cell - 4;
                        Color base = new Color(0xFFD764);
                        g2.setPaint(new GradientPaint(x, y, base.brighter(), x, y+s, base.darker()));
                        g2.fillRoundRect(x, y, s, s, 10, 10);
                    }
                }
            }
        }
        g2.dispose();
    }
}
