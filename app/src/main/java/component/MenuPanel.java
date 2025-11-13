package component;

import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Container;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.function.Consumer;

import javax.swing.AbstractAction;
import javax.swing.ActionMap;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.InputMap;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.KeyStroke;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.Timer; // ADDED

import versus.VersusFrame;
import component.network.websocket.P2PFrame;

public class MenuPanel extends JPanel {

    public enum MenuItem {
        SETTINGS, SCOREBOARD, EXIT
    }

    public enum NavInput {
        LEFT, RIGHT, UP, DOWN, NEXT
    }

    private final Consumer<GameConfig> onStart;
    private final Consumer<MenuItem> onSelect;
    // starry background
    private static final int STARS = 80;
    private final float[] sx = new float[STARS], sy = new float[STARS], sv = new float[STARS], ss = new float[STARS];
    private final Timer anim;
    // Title w/ glow animation
    private JLabel title;
    private float titleGlowPhase = 0f;
    private float titleFloat = 0f;

    private JPanel cardsContainer;
    private JPanel bottomPanel;
    private boolean isCompactMode = false;

    private int selectedIndex = 0;
    private final JPanel[] modeCards = new JPanel[2];
    private final JRadioButton[][] diffButtons = new JRadioButton[2][3];
    private final JButton[] startButtons = new JButton[2];

    // main menu & sub-sections
    private JPanel menuColumn;
    private JPanel individualSub;
    private JPanel individualItemRow;
    private JPanel individualNormalRow;
    private JPanel multiplayerSub;
    private JPanel multiplayerItemRow;

    // Keyboard nav state
    private final List<JButton> navOrder = new ArrayList<>();
    private int navIndex = 0;

    public MenuPanel(Consumer<GameConfig> onStart, Consumer<MenuItem> onSelect) {
        this.onStart = onStart;
        this.onSelect = onSelect;

        setOpaque(true);
        setBackground(new Color(0x0A0F18));
        setLayout(new GridBagLayout());

        // stars + title animation
        seedStars();
        anim = new Timer(33, e -> {
            stepStars();
            titleGlowPhase += 0.03f;
            titleFloat += 0.02f;
            if (title != null)
                title.repaint();
            repaint();
        });
        anim.start();

        // keyboard shortcuts
        InputMap im = getInputMap(WHEN_IN_FOCUSED_WINDOW);
        ActionMap am = getActionMap();
        im.put(KeyStroke.getKeyStroke("ESCAPE"), "exit");
        im.put(KeyStroke.getKeyStroke('S'), "score");
        im.put(KeyStroke.getKeyStroke('T'), "settings");

        // arrow keys and enter wired to nav
        im.put(KeyStroke.getKeyStroke("UP"), "up");
        im.put(KeyStroke.getKeyStroke("DOWN"), "down");
        im.put(KeyStroke.getKeyStroke("LEFT"), "left");
        im.put(KeyStroke.getKeyStroke("RIGHT"), "right");
        im.put(KeyStroke.getKeyStroke("ENTER"), "enter");
        im.put(KeyStroke.getKeyStroke("SPACE"), "enter");

        am.put("exit", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                onSelect.accept(MenuItem.EXIT);
            }
        });
        am.put("score", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                onSelect.accept(MenuItem.SCOREBOARD);
            }
        });
        am.put("settings", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                onSelect.accept(MenuItem.SETTINGS);
            }
        });

        // Arrow behavior = change selected button in navOrder
        am.put("up", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                moveSelection(-1);
            }
        });
        am.put("down", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                moveSelection(+1);
            }
        });
        am.put("left", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                moveSelection(-1);
            }
        });
        am.put("right", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                moveSelection(+1);
            }
        });
        am.put("enter", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                activateSelection();
            }
        });

        buildUI();
        highlightCard(0); // compat
        rebuildNavOrder();
        setSelection(0);
    }

    public void handleMenuInput(NavInput input) {
    }

    public void switchMenu(int delta) {
    }

    public void setSelectedMode(GameConfig.Mode mode) {
    }

    public GameConfig.Mode getSelectedMode() {
        return GameConfig.Mode.CLASSIC;
    }

    public GameConfig.Difficulty getSelectedDifficulty() {
        return GameConfig.Difficulty.NORMAL;
    }

    public GameConfig getCurrentConfig() {
        return new GameConfig(GameConfig.Mode.CLASSIC, GameConfig.Difficulty.NORMAL, false);
    }

    private void highlightCard(int index) {
        repaint();
    }

    private int getSelectedIndex(JRadioButton[] group) {
        return 0;
    }

    // UI CONSTRUCTION
    private void buildUI() {
        GridBagConstraints gb = new GridBagConstraints();
        gb.gridx = 0;
        gb.gridy = 0;
        gb.insets = new Insets(0, 0, 8, 0);
        gb.anchor = GridBagConstraints.CENTER;
        gb.fill = GridBagConstraints.NONE;
        gb.weightx = 1.0;
        gb.weighty = 0.0;

        // Title
        title = new JLabel("TETRIS", SwingConstants.CENTER) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
                g2.setFont(getFont());
                String txt = getText();
                FontMetrics fm = g2.getFontMetrics();
                int x = (getWidth() - fm.stringWidth(txt)) / 2;
                int baseY = (getHeight() + fm.getAscent() - fm.getDescent()) / 2;
                int y = baseY + (int) (Math.sin(titleFloat) * 4f);

                float glowIntensity = 0.6f + (float) Math.sin(titleGlowPhase) * 0.4f;
                float hueShift = (titleGlowPhase * 0.5f) % (float) (Math.PI * 2);
                int glowR = 80 + (int) (40 * Math.sin(hueShift));
                int glowG = 160 + (int) (40 * Math.sin(hueShift + Math.PI * 0.66));
                int glowB = 255;

                for (int i = 16; i > 0; i--) {
                    float distance = i / 16f;
                    int alpha = (int) (glowIntensity * 20 * (1 - distance * distance));
                    g2.setColor(new Color(glowR, glowG, glowB, alpha));
                    for (int angle = 0; angle < 360; angle += 30) {
                        double rad = Math.toRadians(angle);
                        int dx = (int) (Math.cos(rad) * i * 0.7f);
                        int dy = (int) (Math.sin(rad) * i * 0.7f);
                        g2.drawString(txt, x + dx, y + dy);
                    }
                }
                g2.setColor(new Color(0, 0, 0, 160));
                g2.drawString(txt, x + 2, y + 3);
                g2.setColor(Color.WHITE);
                g2.drawString(txt, x, y);
                g2.dispose();
            }
        };
        title.setFont(title.getFont().deriveFont(Font.BOLD, 48f));
        add(title, gb);

        // MAIN COLUMN
        gb.gridy++;
        gb.insets = new Insets(0, 0, 0, 0);
        gb.weighty = 1.0;

        menuColumn = new JPanel();
        menuColumn.setOpaque(false);
        menuColumn.setLayout(new BoxLayout(menuColumn, BoxLayout.Y_AXIS));
        add(menuColumn, gb);

        // INDIVIDUAL
        JButton btnIndividual = makeMainButton("INDIVIDUAL", () -> togglePanel(individualSub));
        btnIndividual.setAlignmentX(CENTER_ALIGNMENT);
        menuColumn.add(btnIndividual);
        menuColumn.add(Box.createVerticalStrut(12));

        individualSub = makeSubPanel();
        individualSub.setAlignmentX(CENTER_ALIGNMENT);

        individualSub.add(makeSubButton("Normal Game", () -> togglePanel(individualNormalRow)));
        individualSub.add(Box.createVerticalStrut(8));

        individualNormalRow = makeDifficultyRowFor(GameConfig.Mode.CLASSIC);
        individualNormalRow.setVisible(false);
        individualSub.add(individualNormalRow);
        individualSub.add(Box.createVerticalStrut(8));

        individualSub.add(makeSubButton("Item",
                () -> onStart.accept(new GameConfig(GameConfig.Mode.ITEM, GameConfig.Difficulty.NORMAL, false))));

        individualSub.setVisible(false);
        menuColumn.add(individualSub);
        menuColumn.add(Box.createVerticalStrut(18));

        // MULTIPLAYER
        JButton btnMultiplayer = makeMainButton("MULTIPLAYER", () -> togglePanel(multiplayerSub));
        btnMultiplayer.setAlignmentX(CENTER_ALIGNMENT);
        menuColumn.add(btnMultiplayer);
        menuColumn.add(Box.createVerticalStrut(12));

        multiplayerSub = makeSubPanel();
        multiplayerSub.setAlignmentX(CENTER_ALIGNMENT);
        // P2P 대전 모드
        multiplayerSub.add(makeSubButton("Online P2P Battle", () -> {
            int choice = JOptionPane.showConfirmDialog(
                    MenuPanel.this,
                    "서버로 시작하시겠습니까?",
                    "P2P 대전 모드",
                    JOptionPane.YES_NO_OPTION);
            boolean isServer = (choice == JOptionPane.YES_OPTION);

            // 🔹 실제 프레임 실행
            JFrame f = (JFrame) SwingUtilities.getWindowAncestor(MenuPanel.this);
            new component.network.websocket.P2PFrame(isServer); // ← WebSocket 기반 대전 프레임
            if (f != null)
                f.dispose();
        }));
        multiplayerSub.add(Box.createVerticalStrut(8));

        // 로컬 2P 대전 (옵션)
        multiplayerSub.add(makeSubButton("Local 2P (Same PC)", () -> {
            JFrame f = (JFrame) SwingUtilities.getWindowAncestor(MenuPanel.this);
            new VersusFrame(false); // ← 오프라인 2인용 대전
            if (f != null)
                f.dispose();
        }));
        multiplayerSub.add(Box.createVerticalStrut(8));

        multiplayerSub.add(makeSubButton("Item", () -> {
            JFrame f = (JFrame) SwingUtilities.getWindowAncestor(MenuPanel.this);
            new VersusFrame(true); // 아이템 모드 대전
            if (f != null) f.dispose();
        }));
        multiplayerSub.add(Box.createVerticalStrut(8));

        multiplayerSub.add(makeSubButton("TIME ATTACK", () -> onStart
                .accept(new GameConfig(GameConfig.Mode.TIME_ATTACK, GameConfig.Difficulty.NORMAL, false))));

        multiplayerSub.setVisible(false);
        menuColumn.add(multiplayerSub);
        menuColumn.add(Box.createVerticalStrut(22));

        // SIMPLE ENTRIES
        JButton btnSetting = makeMainButton("SETTING", () -> onSelect.accept(MenuItem.SETTINGS));
        btnSetting.setAlignmentX(CENTER_ALIGNMENT);
        menuColumn.add(btnSetting);
        menuColumn.add(Box.createVerticalStrut(12));

        JButton btnScore = makeMainButton("SCOREBOARD", () -> onSelect.accept(MenuItem.SCOREBOARD));
        btnScore.setAlignmentX(CENTER_ALIGNMENT);
        menuColumn.add(btnScore);
        menuColumn.add(Box.createVerticalStrut(12));

        JButton btnExit = makeMainButton("EXIT", () -> onSelect.accept(MenuItem.EXIT));
        btnExit.setAlignmentX(CENTER_ALIGNMENT);
        menuColumn.add(btnExit);

        // stubs
        cardsContainer = new JPanel(new GridLayout(1, 2, 26, 0));
        cardsContainer.setOpaque(false);
        bottomPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 24, 0));
        bottomPanel.setOpaque(false);
    }

    // expand/collapse calls rebuildNavOrder()
    private void togglePanel(JPanel p) {
        if (p == null)
            return;
        p.setVisible(!p.isVisible());
        rebuildNavOrder();
        revalidate();
        repaint();
    }

    // row of E/M/H launches NORMAL MODE with that difficulty
    private JPanel makeDifficultyRowFor(GameConfig.Mode mode) {
        JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        row.setOpaque(false);
        row.add(makeGlassSmallButton("EASY",
                () -> onStart.accept(new GameConfig(mode, GameConfig.Difficulty.EASY, false))));
        row.add(makeGlassSmallButton("MEDIUM",
                () -> onStart.accept(new GameConfig(mode, GameConfig.Difficulty.NORMAL, false))));
        row.add(makeGlassSmallButton("HARD",
                () -> onStart.accept(new GameConfig(mode, GameConfig.Difficulty.HARD, false))));
        return row;
    }

    // NAV LIST management
    private void rebuildNavOrder() {
        navOrder.clear();
        collectButtons(menuColumn, navOrder);
        if (navOrder.isEmpty())
            return;
        if (navIndex >= navOrder.size())
            navIndex = navOrder.size() - 1;
        setSelection(navIndex);
    }

    private void collectButtons(Container c, List<JButton> out) {
        for (var comp : c.getComponents()) {
            if (!comp.isVisible())
                continue;
            if (comp instanceof JButton b)
                out.add(b);
            if (comp instanceof Container child)
                collectButtons(child, out);
        }
    }

    private void moveSelection(int delta) {
        if (navOrder.isEmpty())
            return;
        navIndex = (navIndex + delta + navOrder.size()) % navOrder.size();
        setSelection(navIndex);
    }

    private void setSelection(int idx) {
        if (navOrder.isEmpty())
            return;
        for (int i = 0; i < navOrder.size(); i++) {
            JComponent c = navOrder.get(i);
            c.putClientProperty("nav.selected", i == idx); // ADDED flag
            c.repaint();
        }
    }

    private void activateSelection() {
        if (navOrder.isEmpty())
            return;
        navOrder.get(navIndex).doClick();
    }

    // background paint
    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        int w = getWidth(), h = getHeight();

        GradientPaint sky = new GradientPaint(0, 0, new Color(12, 17, 26), w, h, new Color(18, 28, 44));
        g2.setPaint(sky);
        g2.fillRect(0, 0, w, h);

        Point2D center = new Point2D.Float(w * 0.50f, h * 0.42f);
        float radius = Math.max(w, h) * 0.6f;
        float[] dist = { 0f, 1f };
        Color[] cols = { new Color(255, 255, 255, 50), new Color(255, 255, 255, 0) };
        RadialGradientPaint glow = new RadialGradientPaint(center, radius, dist, cols);
        g2.setPaint(glow);
        g2.fillRect(0, 0, w, h);

        g2.setColor(new Color(255, 255, 255, 22));
        int gap = Math.max(12, Math.min(30, w / 40));
        int yStart = (int) (h * 0.55);
        int margin = Math.max(20, w / 30);
        for (int y = yStart; y < h - margin; y += gap)
            for (int x = margin; x < w - margin; x += gap)
                g2.fill(new RoundRectangle2D.Float(x, y, 2, 2, 2, 2));

        Paint vignette = new RadialGradientPaint(new Point2D.Float(w / 2f, h / 2f),
                Math.max(w, h), new float[] { 0.75f, 1f },
                new Color[] { new Color(0, 0, 0, 0), new Color(0, 0, 0, 140) });
        g2.setPaint(vignette);
        g2.fillRect(0, 0, w, h);

        g2.setComposite(AlphaComposite.SrcOver);
        for (int i = 0; i < STARS; i++) {
            int a = (int) (120 * ss[i]);
            g2.setColor(new Color(255, 255, 255, a));
            g2.fillRect(Math.round(sx[i]), Math.round(sy[i]), 2, 2);
        }
        g2.dispose();
    }

    // button renderers with "selected" glow
    private JButton makeMainButton(String text, Runnable action) {
        JButton b = new JButton(text) {
            private float hover = 0f;
            private boolean over = false;
            private final Timer t = new Timer(16, e -> {
                float tgt = over ? 1f : 0f;
                if (Math.abs(hover - tgt) > 0.01f) {
                    hover += (tgt - hover) * 0.25f;
                    repaint();
                }
            });
            {
                setOpaque(false);
                setContentAreaFilled(false);
                setBorder(BorderFactory.createEmptyBorder(10, 28, 10, 28));
                setFocusPainted(false);
                setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
                t.start();
                addMouseListener(new MouseAdapter() {
                    @Override
                    public void mouseEntered(MouseEvent e) {
                        over = true;
                    }

                    @Override
                    public void mouseExited(MouseEvent e) {
                        over = false;
                    }
                });
                addActionListener(e -> action.run());
            }

            @Override
            public Dimension getPreferredSize() {
                return new Dimension(220, 46);
            }

            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                int w = getWidth(), h = getHeight();
                boolean sel = Boolean.TRUE.equals(getClientProperty("nav.selected")); // ADDED

                Shape rr = new RoundRectangle2D.Float(0, 0, w - 1, h - 1, 10, 10);
                GradientPaint bg = new GradientPaint(0, 0, new Color(60, 110, 140, (int) (140 + 60 * hover)),
                        0, h, new Color(30, 60, 90, (int) (140 + 60 * hover)));
                g2.setPaint(bg);
                g2.fill(rr);

                // brighter border if selected
                int borderA = sel ? 255 : (int) (100 + 100 * hover);
                float stroke = sel ? 3.2f : (2f + hover);
                g2.setColor(new Color(120, 190, 255, borderA));
                g2.setStroke(new BasicStroke(stroke));
                g2.draw(rr);

                // subtle outer glow if selected
                if (sel) {
                    g2.setColor(new Color(120, 190, 255, 120));
                    g2.draw(new RoundRectangle2D.Float(-2, -2, w + 3, h + 3, 12, 12));
                }

                g2.setFont(getFont().deriveFont(Font.BOLD, 16f));
                FontMetrics fm = g2.getFontMetrics();
                int tx = (w - fm.stringWidth(getText())) / 2;
                int ty = (h + fm.getAscent() - fm.getDescent()) / 2;
                g2.setColor(new Color(0, 0, 0, 160));
                g2.drawString(getText(), tx + 1, ty + 1);
                g2.setColor(new Color(235, 245, 255));
                g2.drawString(getText(), tx, ty);
                g2.dispose();
            }
        };
        return b;
    }

    private JButton makeSubButton(String text, Runnable action) {
        JButton b = new JButton(text) {
            private float hover = 0f;
            private boolean over = false;
            private final Timer t = new Timer(16, e -> {
                float tgt = over ? 1f : 0f;
                if (Math.abs(hover - tgt) > 0.01f) {
                    hover += (tgt - hover) * 0.25f;
                    repaint();
                }
            });
            {
                setOpaque(false);
                setContentAreaFilled(false);
                setBorder(BorderFactory.createEmptyBorder(8, 20, 8, 20));
                setFocusPainted(false);
                setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
                t.start();
                addMouseListener(new MouseAdapter() {
                    @Override
                    public void mouseEntered(MouseEvent e) {
                        over = true;
                    }

                    @Override
                    public void mouseExited(MouseEvent e) {
                        over = false;
                    }
                });
                addActionListener(e -> action.run());
            }

            @Override
            public Dimension getPreferredSize() {
                return new Dimension(200, 40);
            }

            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                int w = getWidth(), h = getHeight();
                boolean sel = Boolean.TRUE.equals(getClientProperty("nav.selected")); // ADDED

                Shape rr = new RoundRectangle2D.Float(14, 0, w - 1 - 14, h - 1, 10, 10);
                GradientPaint bg = new GradientPaint(0, 0, new Color(50, 90, 120, (int) (120 + 60 * hover)),
                        0, h, new Color(25, 50, 80, (int) (120 + 60 * hover)));
                g2.setPaint(bg);
                g2.fill(rr);

                int borderA = sel ? 240 : (int) (90 + 110 * hover);
                g2.setColor(new Color(120, 190, 255, borderA));
                g2.setStroke(new BasicStroke(sel ? 2.6f : 1.8f));
                g2.draw(rr);

                g2.setFont(getFont().deriveFont(Font.BOLD, 14f));
                FontMetrics fm = g2.getFontMetrics();
                int tx = 14 + (w - 14 - fm.stringWidth(getText())) / 2;
                int ty = (h + fm.getAscent() - fm.getDescent()) / 2;
                g2.setColor(new Color(0, 0, 0, 150));
                g2.drawString(getText(), tx + 1, ty + 1);
                g2.setColor(new Color(230, 240, 255));
                g2.drawString(getText(), tx, ty);
                g2.dispose();
            }
        };
        return b;
    }

    private JPanel makeSubPanel() {
        JPanel p = new JPanel();
        p.setOpaque(false);
        p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));
        return p;
    }

    private JButton makeGlassSmallButton(String text, Runnable action) {
        JButton b = new JButton(text) {
            private float hover = 0f;
            private boolean over = false;
            private final Timer t = new Timer(16, e -> {
                float target = over ? 1f : 0f;
                if (Math.abs(hover - target) > 0.01f) {
                    hover += (target - hover) * 0.25f;
                    repaint();
                }
            });
            {
                setOpaque(false);
                setContentAreaFilled(false);
                setBorder(BorderFactory.createEmptyBorder(8, 22, 8, 22));
                setFocusPainted(false);
                setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
                t.start();
                addMouseListener(new MouseAdapter() {
                    @Override
                    public void mouseEntered(MouseEvent e) {
                        over = true;
                    }

                    @Override
                    public void mouseExited(MouseEvent e) {
                        over = false;
                    }
                });
                addActionListener(e -> action.run());
            }

            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                int w = getWidth(), h = getHeight();
                boolean sel = Boolean.TRUE.equals(getClientProperty("nav.selected")); // ADDED

                Shape rr = new RoundRectangle2D.Float(0, 0, w - 1, h - 1, 10, 10);
                GradientPaint bg = new GradientPaint(0, 0, new Color(60, 110, 140, (int) (140 + 60 * hover)),
                        0, h, new Color(30, 60, 90, (int) (140 + 60 * hover)));
                g2.setPaint(bg);
                g2.fill(rr);

                int borderA = sel ? 255 : (int) (100 + 100 * hover);
                g2.setColor(new Color(120, 190, 255, borderA));
                g2.setStroke(new BasicStroke(sel ? 3f : (2f + hover)));
                g2.draw(rr);

                g2.setFont(getFont().deriveFont(Font.BOLD, 15f));
                FontMetrics fm = g2.getFontMetrics();
                int tx = (w - fm.stringWidth(getText())) / 2;
                int ty = (h + fm.getAscent() - fm.getDescent()) / 2;
                g2.setColor(new Color(0, 0, 0, 160));
                g2.drawString(getText(), tx + 1, ty + 1);
                g2.setColor(new Color(235, 245, 255));
                g2.drawString(getText(), tx, ty);
                g2.dispose();
            }

            @Override
            public Dimension getPreferredSize() {
                return new Dimension(120, 40);
            }
        };
        return b;
    }

    // star helpers
    private void seedStars() {
        Random r = new Random();
        for (int i = 0; i < STARS; i++) {
            sx[i] = r.nextInt(1400) - 100;
            sy[i] = r.nextInt(900) - 100;
            sv[i] = 0.2f + r.nextFloat() * 0.5f;
            ss[i] = 0.4f + r.nextFloat() * 0.6f;
        }
    }

    private void stepStars() {
        int w = getWidth(), h = getHeight();
        if (w == 0 || h == 0)
            return;
        for (int i = 0; i < STARS; i++) {
            sy[i] += sv[i];
            if (sy[i] > h + 20) {
                sy[i] = -10;
                sx[i] = (float) (Math.random() * w);
            }
            ss[i] += (Math.random() * 0.08 - 0.04);
            if (ss[i] < 0.35f)
                ss[i] = 0.35f;
            if (ss[i] > 1.0f)
                ss[i] = 1.0f;
        }
    }

    public void stopAllTimers() {
        // 메인 배경 애니메이션 Timer
        if (anim != null && anim.isRunning()) {
            anim.stop();
        }

        // 모든 버튼에 있는 hover 타이머 제거
        stopTimersRecursive(this);
    }

    private void stopTimersRecursive(Container container) {
        for (var comp : container.getComponents()) {
            if (comp instanceof JButton b) {
                Object timerObj = b.getClientProperty("hoverTimer");
                if (timerObj instanceof Timer t) {
                    if (t.isRunning())
                        t.stop();
                }
            }
            if (comp instanceof Container c) {
                stopTimersRecursive(c);
            }
        }
    }

}
