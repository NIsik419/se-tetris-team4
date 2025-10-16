package component;

import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.GradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.Insets;
import java.awt.Paint;
import java.awt.RadialGradientPaint;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.event.ActionEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.Point2D;
import java.awt.geom.RoundRectangle2D;
import java.util.Random;
import java.util.function.Consumer;

import javax.swing.AbstractAction;
import javax.swing.ActionMap;
import javax.swing.BorderFactory;
import javax.swing.ButtonGroup;
import javax.swing.InputMap;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.KeyStroke;
import javax.swing.SwingConstants;
import javax.swing.Timer;

public class MenuPanel extends JPanel {

    public enum MenuItem { SETTINGS, SCOREBOARD, EXIT }

    private final Consumer<GameConfig> onStart;
    private final Consumer<MenuItem> onSelect;

    // tiny particle field for gentle motion
    private static final int STARS = 80;
    private final float[] sx = new float[STARS], sy = new float[STARS], sv = new float[STARS], ss = new float[STARS];
    private final Timer anim;

    public MenuPanel(Consumer<GameConfig> onStart, Consumer<MenuItem> onSelect) {
        this.onStart = onStart;
        this.onSelect = onSelect;

        setOpaque(true);
        setBackground(new Color(0x0A0F18));
        setLayout(new GridBagLayout());

        seedStars();
        anim = new Timer(33, e -> { stepStars(); repaint(); });
        anim.start();

        // keys
        InputMap im = getInputMap(WHEN_IN_FOCUSED_WINDOW);
        ActionMap am = getActionMap();
        im.put(KeyStroke.getKeyStroke("ESCAPE"), "exit");
        im.put(KeyStroke.getKeyStroke('S'), "score");
        im.put(KeyStroke.getKeyStroke('T'), "settings");
        am.put("exit", new AbstractAction() { @Override public void actionPerformed(ActionEvent e){ onSelect.accept(MenuItem.EXIT); }});
        am.put("score", new AbstractAction() { @Override public void actionPerformed(ActionEvent e){ onSelect.accept(MenuItem.SCOREBOARD); }});
        am.put("settings", new AbstractAction() { @Override public void actionPerformed(ActionEvent e){ onSelect.accept(MenuItem.SETTINGS); }});

        GridBagConstraints gb = new GridBagConstraints();
        gb.gridx = 0; gb.gridy = 0; gb.insets = new Insets(10, 16, 18, 16); gb.gridwidth = 2;

        // Title
        JLabel title = new JLabel("TETRIS", SwingConstants.CENTER) {
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                String txt = getText();
                Font f = getFont().deriveFont(Font.BOLD, 52f);
                g2.setFont(f);
                FontMetrics fm = g2.getFontMetrics();
                int x = (getWidth() - fm.stringWidth(txt)) / 2;
                int y = (getHeight() + fm.getAscent() - fm.getDescent()) / 2;

                // bevel stack
                g2.setColor(new Color(0, 0, 0, 120)); g2.drawString(txt, x+2, y+3);
                g2.setColor(new Color(30, 35, 45, 110)); g2.drawString(txt, x+1, y+2);
                g2.setColor(new Color(255,255,255,200)); g2.drawString(txt, x, y);
                g2.setColor(new Color(220,230,250,120)); g2.drawString(txt, x, y-1);

                g2.dispose();
            }
        };
        title.setPreferredSize(new Dimension(720, 100));
        add(title, gb);

        // Cards row
        gb.gridy++;
        JPanel row = new JPanel(new GridLayout(1,2,26,0)) {{ setOpaque(false); }};
        row.add(makeModeCard("Traditional Tetris", "Normal Game Start", GameConfig.Mode.NORMAL,
                new Color(0x2B3F8C), new Color(0x1B2A55)));
        row.add(makeModeCard("Power-ups & Items", "Start With Items", GameConfig.Mode.ITEM,
                new Color(0x485822), new Color(0x2E3A17)));
        add(row, gb);

        // Bottom actions
        gb.gridy++;
        JPanel bottom = new JPanel(new FlowLayout(FlowLayout.CENTER, 24, 0)) {{ setOpaque(false); }};
        bottom.add(linkBtn("Settings (T)", () -> onSelect.accept(MenuItem.SETTINGS)));
        bottom.add(linkBtn("Scoreboard (S)", () -> onSelect.accept(MenuItem.SCOREBOARD)));
        bottom.add(linkBtn("Exit (Esc)", () -> onSelect.accept(MenuItem.EXIT)));
        add(bottom, gb);
    }

    // ===== Background paint (gradients + vignette + twinkles) =====
    @Override protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        int w = getWidth(), h = getHeight();

        // deep diagonal gradient
        GradientPaint sky = new GradientPaint(0, 0, new Color(12,17,26), w, h, new Color(18, 28, 44));
        g2.setPaint(sky); g2.fillRect(0, 0, w, h);

        // center glow
        Point2D center = new Point2D.Float(w * 0.50f, h * 0.42f);
        float radius = Math.max(w, h) * 0.6f;
        float[] dist = {0f, 1f};
        Color[] cols = { new Color(255,255,255,50), new Color(255,255,255,0) };
        RadialGradientPaint glow = new RadialGradientPaint(center, radius, dist, cols);
        g2.setPaint(glow); g2.fillRect(0,0,w,h);

        // dotted grid (very soft)
        g2.setColor(new Color(255,255,255,22));
        int gap = 22;
        int yStart = (int)(h*0.55);
        for (int y=yStart; y<h-40; y+=gap)
            for (int x=40; x<w-40; x+=gap)
                g2.fill(new RoundRectangle2D.Float(x, y, 2, 2, 2, 2));

        // vignette
        Paint vignette = new RadialGradientPaint(new Point2D.Float(w/2f, h/2f),
                Math.max(w,h), new float[]{0.75f, 1f},
                new Color[]{new Color(0,0,0,0), new Color(0,0,0,140)});
        g2.setPaint(vignette); g2.fillRect(0,0,w,h);

        // twinkle particles
        g2.setComposite(AlphaComposite.SrcOver);
        for (int i=0;i<STARS;i++) {
            int a = (int)(120 * ss[i]);
            g2.setColor(new Color(255,255,255, a));
            g2.fillRect(Math.round(sx[i]), Math.round(sy[i]), 2, 2);
        }

        g2.dispose();
    }

    // ===== Card factory with hover-lift button & pill radios =====
    private JPanel makeModeCard(String banner, String cta, GameConfig.Mode mode, Color cTop, Color cBottom) {
        JPanel card = new JPanel() {
            @Override protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

                int w = getWidth(), h = getHeight();
                // soft drop shadow
                g2.setColor(new Color(0,0,0,110));
                g2.fillRoundRect(8, 12, w-16, h-18, 28, 28);

                // body gradient
                Shape rr = new RoundRectangle2D.Float(0, 4, w-16, h-22, 28, 28);
                g2.translate(8, 8);
                g2.setPaint(new GradientPaint(0, 0, cTop, 0, h, cBottom));
                g2.fill(rr);

                // inner glass wash
                g2.setPaint(new GradientPaint(0, 0, new Color(255,255,255,45), 0, h/2f, new Color(255,255,255,5)));
                g2.fill(new RoundRectangle2D.Float(2, 2, w-20, (h-26)*0.55f, 24, 24));

                // border hairline
                g2.setColor(new Color(255,255,255,40));
                g2.draw(new RoundRectangle2D.Float(0.5f, 4.5f, w-17, h-23, 28, 28));

                g2.dispose();
            }
        };
        card.setOpaque(false);
        card.setBorder(BorderFactory.createEmptyBorder(24, 28, 24, 28));
        card.setLayout(new BorderLayout(0, 16));

        // banner
        JLabel b = new JLabel(banner);
        b.setForeground(new Color(242,246,255));
        b.setFont(b.getFont().deriveFont(Font.BOLD, 22f));
        card.add(b, BorderLayout.NORTH);

        // difficulty pills
        JRadioButton rEasy   = pill("Easy", true);
        JRadioButton rMedium = pill("Medium", false);
        JRadioButton rHard   = pill("Hard", false);
        ButtonGroup group = new ButtonGroup();
        group.add(rEasy); group.add(rMedium); group.add(rHard);

        JPanel diff = new JPanel(new FlowLayout(FlowLayout.LEFT, 12, 2)) {{ setOpaque(false); }};
        JLabel lab = new JLabel("Difficulty:");
        lab.setForeground(new Color(232,235,246));
        lab.setFont(lab.getFont().deriveFont(Font.PLAIN, 15f));
        diff.add(lab); diff.add(rEasy); diff.add(rMedium); diff.add(rHard);
        card.add(diff, BorderLayout.CENTER);

        // start button
        JButton start = liftButton(cta);
        start.addActionListener(e -> {
            GameConfig.Difficulty d = rEasy.isSelected() ? GameConfig.Difficulty.EASY
                    : (rHard.isSelected() ? GameConfig.Difficulty.HARD : GameConfig.Difficulty.NORMAL);
            onStart.accept(new GameConfig(mode, d, false));
        });

        JPanel south = new JPanel(new BorderLayout()) {{ setOpaque(false); }};
        south.add(start, BorderLayout.EAST);
        card.add(south, BorderLayout.SOUTH);

        return card;
    }

    // pill radios
    private JRadioButton pill(String text, boolean sel) {
        JRadioButton r = new JRadioButton(text, sel) {
            @Override public void updateUI() { super.updateUI(); setOpaque(false); setIcon(null); setFocusPainted(false); }
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                int w = getWidth(), h = getHeight();
                Shape rr = new RoundRectangle2D.Float(0,0,w,h, h, h);
                if (isSelected()) {
                    g2.setColor(new Color(186, 209, 255));   // fill
                } else {
                    g2.setColor(new Color(255,255,255,28));
                }
                g2.fill(rr);
                g2.setColor(isSelected() ? new Color(27,46,106) : new Color(233,238,249));
                String s = getText();
                FontMetrics fm = g2.getFontMetrics();
                int x = (w - fm.stringWidth(s))/2;
                int y = (h + fm.getAscent() - fm.getDescent())/2;
                g2.drawString(s, x, y);
                g2.dispose();
            }
            @Override public Dimension getPreferredSize() { return new Dimension(86, 28); }
        };
        r.setForeground(new Color(233,238,249));
        return r;
    }

    // raised CTA button with hover lift
    private JButton liftButton(String txt) {
        JButton b = new JButton(txt) {
            private boolean hover = false;
            private boolean pressed = false;
            {
                setOpaque(false);
                setContentAreaFilled(false);
                setBorder(BorderFactory.createEmptyBorder(12, 20, 12, 20));
                setFocusPainted(false);
                setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
                addMouseListener(new MouseAdapter() {
                    @Override public void mouseEntered(MouseEvent e) { hover = true; repaint(); }
                    @Override public void mouseExited(MouseEvent e)  { hover = false; repaint(); }
                    @Override public void mousePressed(MouseEvent e) { pressed = true; repaint(); }
                    @Override public void mouseReleased(MouseEvent e){ pressed = false; repaint(); }
                });
            }
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

                int w = getWidth(), h = getHeight();
                int lift = hover ? (pressed ? 1 : -2) : 0;

                // drop shadow
                g2.setColor(new Color(0,0,0, pressed ? 90 : 120));
                g2.fillRoundRect(3, 6 + (pressed?2:0), w-6, h-6, 14, 14);

                // body
                Shape rr = new RoundRectangle2D.Float(0, lift, w, h, 14, 14);
                g2.setPaint(new GradientPaint(0, 0+lift, new Color(246,248,255),
                                              0, h+lift, new Color(221,230,255)));
                g2.fill(rr);

                // text
                g2.setColor(new Color(11,15,22, 200));
                g2.setFont(getFont().deriveFont(Font.BOLD, 16f));
                FontMetrics fm = g2.getFontMetrics();
                String s = getText();
                int x = (w - fm.stringWidth(s))/2;
                int y = (h + fm.getAscent() - fm.getDescent())/2 + lift;
                g2.drawString(s, x, y);

                // focus ring
                if (isFocusOwner()) {
                    g2.setColor(new Color(100,180,255,180));
                    g2.setStroke(new BasicStroke(2f));
                    g2.draw(rr);
                }

                g2.dispose();
            }
        };
        return b;
    }

    private JButton linkBtn(String label, Runnable action) {
        JButton b = new JButton(label);
        b.setFocusPainted(false);
        b.setOpaque(false);
        b.setContentAreaFilled(false);
        b.setForeground(new Color(229,234,247));
        b.setBorder(BorderFactory.createEmptyBorder(10,16,10,16));
        b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        b.addActionListener(e -> action.run());
        return b;
    }

    // ===== tiny starfield =====
    private void seedStars() {
        Random r = new Random();
        for (int i=0;i<STARS;i++) {
            sx[i] = r.nextInt(1400) - 100; // spawn a bit wider than panel; we reposition later
            sy[i] = r.nextInt(900)  - 100;
            sv[i] = 0.2f + r.nextFloat()*0.5f;
            ss[i] = 0.4f + r.nextFloat()*0.6f;
        }
    }
    private void stepStars() {
        for (int i=0;i<STARS;i++) {
            sy[i] += sv[i];
            if (sy[i] > getHeight()+20) {
                sy[i] = -10;
                sx[i] = (float)(Math.random()*getWidth());
            }
            // twinkle
            ss[i] += (Math.random()*0.08 - 0.04);
            if (ss[i] < 0.35f) ss[i] = 0.35f;
            if (ss[i] > 1.0f)  ss[i] = 1.0f;
        }
    }
}
