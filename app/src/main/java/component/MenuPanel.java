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
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
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

    private static final int STARS = 80;
    private final float[] sx = new float[STARS], sy = new float[STARS], sv = new float[STARS], ss = new float[STARS];
    private final Timer anim;

    private JLabel title;
    private JPanel cardsContainer;
    private JPanel bottomPanel;
    private boolean isCompactMode = false;

    // Title animation
    private float titleGlowPhase = 0f;
    private float titleFloat = 0f;

    public MenuPanel(Consumer<GameConfig> onStart, Consumer<MenuItem> onSelect) {
        this.onStart = onStart;
        this.onSelect = onSelect;

        setOpaque(true);
        setBackground(new Color(0x0A0F18));
        setLayout(new GridBagLayout());

        seedStars();
        anim = new Timer(33, e -> {
            stepStars();
            titleGlowPhase += 0.03f;
            titleFloat += 0.02f;
            title.repaint();
            repaint();
        });
        anim.start();

        // Keyboard shortcuts
        InputMap im = getInputMap(WHEN_IN_FOCUSED_WINDOW);
        ActionMap am = getActionMap();
        im.put(KeyStroke.getKeyStroke("ESCAPE"), "exit");
        im.put(KeyStroke.getKeyStroke('S'), "score");
        im.put(KeyStroke.getKeyStroke('T'), "settings");
        am.put("exit", new AbstractAction() { @Override public void actionPerformed(ActionEvent e){ onSelect.accept(MenuItem.EXIT); }});
        am.put("score", new AbstractAction() { @Override public void actionPerformed(ActionEvent e){ onSelect.accept(MenuItem.SCOREBOARD); }});
        am.put("settings", new AbstractAction() { @Override public void actionPerformed(ActionEvent e){ onSelect.accept(MenuItem.SETTINGS); }});

        buildUI();

        // Listen for size changes to adjust layout
        addComponentListener(new ComponentAdapter() {
            @Override
            public void componentResized(ComponentEvent e) {
                handleResize();
            }
        });
    }

    private void buildUI() {
        GridBagConstraints gb = new GridBagConstraints();
        gb.gridx = 0; gb.gridy = 0;
        gb.weightx = 1.0; gb.weighty = 0;
        gb.fill = GridBagConstraints.HORIZONTAL;
        gb.insets = new Insets(10, 16, 16, 16);

        // Title
        title = new JLabel("TETRIS", SwingConstants.CENTER) {
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
                g2.setFont(getFont());
                String txt = getText();
                FontMetrics fm = g2.getFontMetrics();
                int x = (getWidth() - fm.stringWidth(txt)) / 2;
                int baseY = (getHeight() + fm.getAscent() - fm.getDescent()) / 2;

                // Floating animation with easing
                float floatOffset = (float)Math.sin(titleFloat) * 4f;
                int y = baseY + (int)floatOffset;

                // Pulsing glow intensity
                float glowIntensity = 0.6f + (float)Math.sin(titleGlowPhase) * 0.4f;

                // Rotating hue shift for dynamic color
                float hueShift = (titleGlowPhase * 0.5f) % (float)(Math.PI * 2);
                int glowR = 80 + (int)(40 * Math.sin(hueShift));
                int glowG = 160 + (int)(40 * Math.sin(hueShift + Math.PI * 0.66));
                int glowB = 255;

                // Outer radial glow (expanded range)
                for (int i = 16; i > 0; i--) {
                    float distance = i / 16f;
                    int alpha = (int)(glowIntensity * 20 * (1 - distance * distance));
                    g2.setColor(new Color(glowR, glowG, glowB, alpha));
                    for (int angle = 0; angle < 360; angle += 30) {
                        float rad = (float)Math.toRadians(angle);
                        int offsetX = (int)(Math.cos(rad) * i * 0.7f);
                        int offsetY = (int)(Math.sin(rad) * i * 0.7f);
                        g2.drawString(txt, x + offsetX, y + offsetY);
                    }
                }

                // Deep shadow for depth
                g2.setColor(new Color(0, 0, 0, 180));
                g2.drawString(txt, x+4, y+5);
                g2.setColor(new Color(0, 0, 0, 120));
                g2.drawString(txt, x+3, y+4);
                g2.setColor(new Color(0, 0, 0, 60));
                g2.drawString(txt, x+2, y+3);

                // Chromatic aberration effect
                g2.setColor(new Color(255, 100, 150, (int)(30 * glowIntensity)));
                g2.drawString(txt, x-1, y);
                g2.setColor(new Color(100, 255, 255, (int)(30 * glowIntensity)));
                g2.drawString(txt, x+1, y);

                // Main text
                g2.setColor(new Color(255, 255, 255, 255));
                g2.drawString(txt, x, y);

                // Animated top highlight with shimmer
                float shimmer = (float)Math.sin(titleGlowPhase * 2) * 0.3f + 0.7f;
                g2.setColor(new Color(200, 230, 255, (int)(180 * shimmer)));
                g2.drawString(txt, x, y-1);

                // Subtle inner glow
                g2.setColor(new Color(glowR, glowG, glowB, (int)(50 * glowIntensity)));
                g2.drawString(txt, x, y);

                g2.dispose();
            }
        };

        title.setFont(title.getFont().deriveFont(Font.BOLD, 48f));
        add(title, gb);

        // Cards container with dynamic layout
        gb.gridy++;
        gb.weighty = 1.0;
        gb.fill = GridBagConstraints.BOTH;
        cardsContainer = new JPanel();
        cardsContainer.setOpaque(false);
        cardsContainer.setLayout(new GridLayout(1, 2, 26, 0));

        cardsContainer.add(makeModeCard("Traditional Tetris", "Normal Game Start", GameConfig.Mode.CLASSIC,
                new Color(0x2B3F8C), new Color(0x1B2A55)));
        cardsContainer.add(makeModeCard("Power-ups & Items", "Start With Items", GameConfig.Mode.ITEM,
                new Color(0x485822), new Color(0x2E3A17)));
        add(cardsContainer, gb);

        // Bottom actions
        gb.gridy++;
        gb.weighty = 0;
        gb.fill = GridBagConstraints.HORIZONTAL;
        bottomPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 24, 0));
        bottomPanel.setOpaque(false);
        bottomPanel.add(linkBtn("Settings (T)", () -> onSelect.accept(MenuItem.SETTINGS)));
        bottomPanel.add(linkBtn("Scoreboard (S)", () -> onSelect.accept(MenuItem.SCOREBOARD)));
        bottomPanel.add(linkBtn("Exit (Esc)", () -> onSelect.accept(MenuItem.EXIT)));
        add(bottomPanel, gb);
    }

    private void handleResize() {
        int width = getWidth();
        int height = getHeight();

        // Adjust title font size based on width
        float titleSize = Math.max(24f, Math.min(64f, width * 0.08f));
        title.setFont(title.getFont().deriveFont(Font.BOLD, titleSize));

        // Switch to vertical layout on narrow screens
        boolean shouldBeCompact = width < 700 || height < 500;
        if (shouldBeCompact != isCompactMode) {
            isCompactMode = shouldBeCompact;
            if (isCompactMode) {
                cardsContainer.setLayout(new GridLayout(2, 1, 0, 16));
                bottomPanel.setLayout(new FlowLayout(FlowLayout.CENTER, 12, 8));
            } else {
                cardsContainer.setLayout(new GridLayout(1, 2, 26, 0));
                bottomPanel.setLayout(new FlowLayout(FlowLayout.CENTER, 24, 0));
            }
            revalidate();
        }

        // Force repaint of all components to update their responsive fonts
        repaint();
    }

    @Override protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        int w = getWidth(), h = getHeight();

        // Deep diagonal gradient
        GradientPaint sky = new GradientPaint(0, 0, new Color(12,17,26), w, h, new Color(18, 28, 44));
        g2.setPaint(sky); g2.fillRect(0, 0, w, h);

        // Soft radial glow
        Point2D center = new Point2D.Float(w * 0.50f, h * 0.42f);
        float radius = Math.max(w, h) * 0.6f;
        float[] dist = {0f, 1f};
        Color[] cols = { new Color(255,255,255,50), new Color(255,255,255,0) };
        RadialGradientPaint glow = new RadialGradientPaint(center, radius, dist, cols);
        g2.setPaint(glow); g2.fillRect(0,0,w,h);

        // Dotted grid (scale gap with size)
        g2.setColor(new Color(255,255,255,22));
        int gap = Math.max(12, Math.min(30, w / 40));
        int yStart = (int)(h*0.55);
        int margin = Math.max(20, w / 30);
        for (int y=yStart; y<h-margin; y+=gap)
            for (int x=margin; x<w-margin; x+=gap)
                g2.fill(new RoundRectangle2D.Float(x, y, 2, 2, 2, 2));

        // Vignette
        Paint vignette = new RadialGradientPaint(new Point2D.Float(w/2f, h/2f),
                Math.max(w,h), new float[]{0.75f, 1f},
                new Color[]{new Color(0,0,0,0), new Color(0,0,0,140)});
        g2.setPaint(vignette); g2.fillRect(0,0,w,h);

        // Twinkle particles
        g2.setComposite(AlphaComposite.SrcOver);
        for (int i=0;i<STARS;i++) {
            int a = (int)(120 * ss[i]);
            g2.setColor(new Color(255,255,255, a));
            g2.fillRect(Math.round(sx[i]), Math.round(sy[i]), 2, 2);
        }
        g2.dispose();
    }

    private JPanel makeModeCard(String banner, String cta, GameConfig.Mode mode, Color cTop, Color cBottom) {
        JPanel card = new JPanel() {
            private boolean hover = false;
            private float hoverProgress = 0f;
            private Timer hoverAnim;

            {
                hoverAnim = new Timer(16, e -> {
                    if (hover && hoverProgress < 1f) {
                        hoverProgress = Math.min(1f, hoverProgress + 0.08f);
                        repaint();
                    } else if (!hover && hoverProgress > 0f) {
                        hoverProgress = Math.max(0f, hoverProgress - 0.08f);
                        repaint();
                    }
                });
                hoverAnim.start();

                addMouseListener(new MouseAdapter() {
                    @Override public void mouseEntered(MouseEvent e) { hover = true; }
                    @Override public void mouseExited(MouseEvent e) { hover = false; }
                });
            }

            @Override protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

                int w = getWidth(), h = getHeight();
                Shape rr = new RoundRectangle2D.Float(0, 0, w-1, h-1, 20, 20);

                // Animated glow on hover
                if (hoverProgress > 0) {
                    int glowSize = (int)(12 * hoverProgress);
                    g2.setColor(new Color(cTop.getRed(), cTop.getGreen(), cTop.getBlue(), (int)(80 * hoverProgress)));
                    g2.fillRoundRect(-glowSize/2, -glowSize/2, w+glowSize, h+glowSize, 24, 24);
                }

                // Gradient background
                GradientPaint gradient = new GradientPaint(
                    0, 0, new Color(cTop.getRed(), cTop.getGreen(), cTop.getBlue(), 30 + (int)(25 * hoverProgress)),
                    0, h, new Color(cBottom.getRed(), cBottom.getGreen(), cBottom.getBlue(), 20 + (int)(20 * hoverProgress))
                );
                g2.setPaint(gradient);
                g2.fill(rr);

                // Animated border with gradient
                float borderAlpha = 0.3f + (0.4f * hoverProgress);
                GradientPaint borderGrad = new GradientPaint(
                    0, 0, new Color(cTop.getRed(), cTop.getGreen(), cTop.getBlue(), (int)(255 * borderAlpha)),
                    0, h, new Color(cBottom.getRed(), cBottom.getGreen(), cBottom.getBlue(), (int)(200 * borderAlpha))
                );
                g2.setPaint(borderGrad);
                g2.setStroke(new BasicStroke(2f + hoverProgress));
                g2.draw(rr);

                // Top shine effect
                GradientPaint shine = new GradientPaint(
                    0, 0, new Color(255, 255, 255, (int)(40 + 30 * hoverProgress)),
                    0, h/3, new Color(255, 255, 255, 0)
                );
                g2.setPaint(shine);
                Shape shineShape = new RoundRectangle2D.Float(1, 1, w-2, h/3, 18, 18);
                g2.fill(shineShape);

                // Bottom accent line
                g2.setColor(new Color(cBottom.getRed(), cBottom.getGreen(), cBottom.getBlue(), (int)(150 + 100 * hoverProgress)));
                g2.setStroke(new BasicStroke(3f));
                g2.drawLine(20, h-1, w-20, h-1);

                g2.dispose();
            }

            @Override public Dimension getPreferredSize() {
                Dimension d = super.getPreferredSize();
                return new Dimension(Math.max(300, d.width), Math.max(200, d.height));
            }
        };
        card.setOpaque(false);
        card.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

        card.setBorder(BorderFactory.createEmptyBorder(16, 20, 16, 20));
        card.setLayout(new BorderLayout(0, 12));

        // Banner with responsive font
        JLabel b = new JLabel(banner) {
            @Override protected void paintComponent(Graphics g) {
                int w = MenuPanel.this.getWidth();
                float size = Math.max(18f, Math.min(26f, w * 0.03f));
                setFont(getFont().deriveFont(Font.BOLD, size));
                super.paintComponent(g);
            }
        };
        b.setForeground(new Color(255, 255, 255, 250));
        b.setFont(b.getFont().deriveFont(Font.BOLD, 22f));

        JPanel topPanel = new JPanel(new BorderLayout());
        topPanel.setOpaque(false);
        topPanel.add(b, BorderLayout.WEST);

        // Mode icon/badge
        JLabel badge = new JLabel(mode == GameConfig.Mode.CLASSIC ? "●" : "★");
        badge.setFont(badge.getFont().deriveFont(Font.BOLD, 24f));
        badge.setForeground(new Color(cTop.getRed(), cTop.getGreen(), cTop.getBlue(), 200));
        topPanel.add(badge, BorderLayout.EAST);

        card.add(topPanel, BorderLayout.NORTH);

        // Difficulty pills
        JRadioButton rEasy   = pill("Easy", true);
        JRadioButton rMedium = pill("Medium", false);
        JRadioButton rHard   = pill("Hard", false);
        ButtonGroup group = new ButtonGroup();
        group.add(rEasy); group.add(rMedium); group.add(rHard);

        JPanel diff = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        diff.setOpaque(false);
        JLabel lab = new JLabel("Difficulty:") {
            @Override protected void paintComponent(Graphics g) {
                int w = MenuPanel.this.getWidth();
                float size = Math.max(11f, Math.min(14f, w * 0.018f));
                setFont(getFont().deriveFont(Font.PLAIN, size));
                super.paintComponent(g);
            }
        };
        lab.setForeground(new Color(232,235,246));
        lab.setFont(lab.getFont().deriveFont(Font.PLAIN, 13f));
        diff.add(lab); diff.add(rEasy); diff.add(rMedium); diff.add(rHard);

        // Start button
        JButton start = liftButton(cta);
        start.addActionListener(e -> {
            GameConfig.Difficulty d = rEasy.isSelected() ? GameConfig.Difficulty.EASY
                    : (rHard.isSelected() ? GameConfig.Difficulty.HARD : GameConfig.Difficulty.NORMAL);
            onStart.accept(new GameConfig(mode, d, false));
        });

        // Center panel combining difficulty and button
        JPanel centerPanel = new JPanel(new BorderLayout(0, 8));
        centerPanel.setOpaque(false);
        centerPanel.add(diff, BorderLayout.NORTH);

        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
        buttonPanel.setOpaque(false);
        buttonPanel.add(start);
        centerPanel.add(buttonPanel, BorderLayout.SOUTH);

        card.add(centerPanel, BorderLayout.CENTER);

        return card;
    }

    private JRadioButton pill(String text, boolean sel) {
        JRadioButton r = new JRadioButton(text, sel) {
            @Override public void updateUI() {
                super.updateUI();
                setOpaque(false);
                setIcon(null);
                setFocusPainted(false);
            }

            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

                // Responsive font
                int menuWidth = MenuPanel.this.getWidth();
                float fontSize = Math.max(11f, Math.min(14f, menuWidth * 0.018f));
                g2.setFont(getFont().deriveFont(Font.PLAIN, fontSize));

                int w = getWidth(), h = getHeight();
                Shape rr = new RoundRectangle2D.Float(0, 0, w-1, h-1, h, h);

                if (isSelected()) {
                    g2.setColor(new Color(100, 180, 255, 200));
                    g2.fill(rr);
                    g2.setColor(new Color(255, 255, 255, 255));
                } else {
                    g2.setColor(new Color(255, 255, 255, 15));
                    g2.fill(rr);
                    g2.setColor(new Color(255, 255, 255, 160));
                    g2.setStroke(new BasicStroke(1.2f));
                    g2.draw(rr);
                    g2.setColor(new Color(200, 210, 230));
                }

                String s = getText();
                FontMetrics fm = g2.getFontMetrics();
                int x = (w - fm.stringWidth(s))/2;
                int y = (h + fm.getAscent() - fm.getDescent())/2;
                g2.drawString(s, x, y);
                g2.dispose();
            }

            @Override public Dimension getPreferredSize() {
                int w = Math.max(65, Math.min(90, MenuPanel.this.getWidth() / 14));
                return new Dimension(w, 28);
            }
        };
        r.setFont(r.getFont().deriveFont(Font.PLAIN, 13f));
        r.setForeground(new Color(200, 210, 230));
        return r;
    }

    private JButton liftButton(String txt) {
        JButton b = new JButton(txt) {
            private float hoverProgress = 0f;
            private boolean hover = false;
            private boolean pressed = false;
            private Timer transition;

            {
                setOpaque(false);
                setContentAreaFilled(false);
                setBorder(BorderFactory.createEmptyBorder(8, 20, 8, 20));
                setFocusPainted(false);
                setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

                transition = new Timer(16, e -> {
                    if (hover && hoverProgress < 1f) {
                        hoverProgress = Math.min(1f, hoverProgress + 0.1f);
                        repaint();
                    } else if (!hover && hoverProgress > 0f) {
                        hoverProgress = Math.max(0f, hoverProgress - 0.1f);
                        repaint();
                    }
                });
                transition.start();

                addMouseListener(new MouseAdapter() {
                    @Override public void mouseEntered(MouseEvent e) { hover = true; }
                    @Override public void mouseExited(MouseEvent e)  { hover = false; }
                    @Override public void mousePressed(MouseEvent e) { pressed = true; repaint(); }
                    @Override public void mouseReleased(MouseEvent e){ pressed = false; repaint(); }
                });
            }

            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

                int w = getWidth(), h = getHeight();

                // Smooth transition scale
                float scale = 1f + (hoverProgress * 0.05f);
                if (pressed) scale = 0.98f;

                int offset = (int)((1f - scale) * h / 2);
                g2.translate(offset, offset);

                int sw = (int)(w * scale);
                int sh = (int)(h * scale);

                // Animated glow
                if (hoverProgress > 0) {
                    int glowSize = (int)(8 * hoverProgress);
                    g2.setColor(new Color(100, 180, 255, (int)(60 * hoverProgress)));
                    g2.fillRoundRect(-glowSize/2, -glowSize/2, sw+glowSize, sh+glowSize, 18, 18);
                }

                // Main button shape
                Shape rr = new RoundRectangle2D.Float(0, 0, sw, sh, 14, 14);

                // Background with interpolated color
                int r = 246 - (int)(20 * hoverProgress);
                int gb = 248 - (int)(28 * hoverProgress);
                g2.setColor(new Color(r, gb, 255));
                g2.fill(rr);

                // Border
                g2.setColor(new Color(100, 180, 255, 80 + (int)(120 * hoverProgress)));
                g2.setStroke(new BasicStroke(2f));
                g2.draw(rr);

                // Text
                float fontSize = Math.max(13f, Math.min(16f, MenuPanel.this.getWidth() * 0.02f));
                g2.setColor(new Color(11, 15, 22, 220));
                g2.setFont(getFont().deriveFont(Font.BOLD, fontSize));
                FontMetrics fm = g2.getFontMetrics();
                String s = getText();
                int x = (sw - fm.stringWidth(s))/2;
                int y = (sh + fm.getAscent() - fm.getDescent())/2;
                g2.drawString(s, x, y);

                g2.dispose();
            }
        };
        return b;
    }

    private JButton linkBtn(String label, Runnable action) {
        JButton b = new JButton(label) {
            private float hoverProgress = 0f;
            private boolean hover = false;
            private Timer transition;

            {
                setFocusPainted(false);
                setOpaque(false);
                setContentAreaFilled(false);
                setBorder(BorderFactory.createEmptyBorder(10, 16, 10, 16));
                setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

                transition = new Timer(16, e -> {
                    if (hover && hoverProgress < 1f) {
                        hoverProgress = Math.min(1f, hoverProgress + 0.12f);
                        repaint();
                    } else if (!hover && hoverProgress > 0f) {
                        hoverProgress = Math.max(0f, hoverProgress - 0.12f);
                        repaint();
                    }
                });
                transition.start();

                addMouseListener(new MouseAdapter() {
                    @Override public void mouseEntered(MouseEvent e) { hover = true; }
                    @Override public void mouseExited(MouseEvent e) { hover = false; }
                });
            }

            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

                int w = getWidth(), h = getHeight();

                // Animated underline
                if (hoverProgress > 0) {
                    int lineWidth = (int)(w * 0.7f * hoverProgress);
                    int lineX = (w - lineWidth) / 2;
                    g2.setColor(new Color(100, 180, 255, (int)(200 * hoverProgress)));
                    g2.setStroke(new BasicStroke(2f));
                    g2.drawLine(lineX, h - 6, lineX + lineWidth, h - 6);
                }

                // Text with color transition
                int r = 229 - (int)(129 * hoverProgress);
                int gr = 234 - (int)(54 * hoverProgress);
                int bl = 247 - (int)(0 * hoverProgress);
                g2.setColor(new Color(r, gr, bl));

                float size = Math.max(11f, Math.min(14f, MenuPanel.this.getWidth() * 0.018f));
                g2.setFont(getFont().deriveFont(Font.PLAIN, size));

                FontMetrics fm = g2.getFontMetrics();
                String txt = getText();
                int x = (w - fm.stringWidth(txt)) / 2;
                int y = (h + fm.getAscent() - fm.getDescent()) / 2 - 2;
                g2.drawString(txt, x, y);

                g2.dispose();
            }

            @Override public void setFont(Font font) {
                float size = Math.max(11f, Math.min(14f, MenuPanel.this.getWidth() * 0.018f));
                super.setFont(font.deriveFont(Font.PLAIN, size));
            }
        };
        b.addActionListener(e -> action.run());
        return b;
    }

    private void seedStars() {
        Random r = new Random();
        for (int i=0;i<STARS;i++) {
            sx[i] = r.nextInt(1400) - 100;
            sy[i] = r.nextInt(900)  - 100;
            sv[i] = 0.2f + r.nextFloat()*0.5f;
            ss[i] = 0.4f + r.nextFloat()*0.6f;
        }
    }

    private void stepStars() {
        int w = getWidth();
        int h = getHeight();
        if (w == 0 || h == 0) return;

        for (int i=0;i<STARS;i++) {
            sy[i] += sv[i];
            if (sy[i] > h+20) {
                sy[i] = -10;
                sx[i] = (float)(Math.random()*w);
            }
            // Twinkle
            ss[i] += (Math.random()*0.08 - 0.04);
            if (ss[i] < 0.35f) ss[i] = 0.35f;
            if (ss[i] > 1.0f)  ss[i] = 1.0f;
        }
    }
}