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
import java.awt.Rectangle;
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
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.JScrollPane;
import javax.swing.KeyStroke;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.Timer;

import logic.SoundManager;
import versus.VersusFrame;

public class MenuPanel extends JPanel {

    public enum MenuItem {
        SETTINGS, SCOREBOARD, EXIT
    }

    public enum NavInput {
        LEFT, RIGHT, UP, DOWN, NEXT
    }

    private enum ScreenSize {
        SMALL, MEDIUM, LARGE, FULLSCREEN
    }

    private void applyScreenPreset(ScreenSize preset) {
        java.awt.Window w = SwingUtilities.getWindowAncestor(this);
        if (!(w instanceof JFrame f))
            return;

        f.setExtendedState(JFrame.NORMAL);
        f.setResizable(true);
        switch (preset) {
            case SMALL -> f.setSize(new Dimension(600, 480));
            case MEDIUM -> f.setSize(new Dimension(900, 720));
            case LARGE -> f.setSize(new Dimension(1200, 840));
            case FULLSCREEN -> {
                // Maximize the frame to fill the screen (keeps OS chrome)
                f.setExtendedState(f.getExtendedState() | JFrame.MAXIMIZED_BOTH);
                f.toFront();
                f.requestFocus();
                return;
            }
        }
        f.setLocationRelativeTo(null);
    }

    private final Consumer<GameConfig> onStart;
    private final Consumer<MenuItem> onSelect;
    private final SoundManager sound = SoundManager.getInstance();
    private boolean bgmPlaying = false;

    // Starry background
    private static final int STARS = 80;
    private final float[] sx = new float[STARS], sy = new float[STARS], sv = new float[STARS], ss = new float[STARS];
    private final Timer anim;

    // Colors for falling blocks (Tetris-like)
    private static final Color[] BLOCK_COLORS = {
            new Color(0xFF4B4B), // red
            new Color(0xFFD93B), // yellow
            new Color(0x3BFF9C), // green
            new Color(0x3BB9FF), // blue
            new Color(0xC63BFF) // purple
    };
    private final int[] blockColorIndex = new int[STARS];

    // Title animation
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

    // Main menu & sub-sections
    private JPanel menuColumn;
    private JPanel individualSub;
    private JPanel individualNormalRow;
    private JPanel individualItemRow;
    private JPanel multiplayerSub;
    private JPanel multiplayerItemRow;
    private JPanel individualAIRow;

    private JPanel onlineP2PSub;
    private JPanel onlineNormalRow;
    private JPanel onlineItemRow;
    private JPanel onlineTimeRow;
    private JPanel local2PSub;
    private JPanel localNormalRow;
    private JPanel localItemRow;
    private JPanel localTimeRow;

    // Keyboard nav state
    private final List<JButton> navOrder = new ArrayList<>();
    private int navIndex = 0;

    public MenuPanel(Consumer<GameConfig> onStart, Consumer<MenuItem> onSelect) {
        this.onStart = onStart;
        this.onSelect = onSelect;

        startMenuBGM();

        setOpaque(true);
        setBackground(new Color(0x0A0F18));
        setLayout(new GridBagLayout());

        // Stars + title animation
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

        // Keyboard shortcuts
        InputMap im = getInputMap(WHEN_IN_FOCUSED_WINDOW);
        ActionMap am = getActionMap();
        im.put(KeyStroke.getKeyStroke("ESCAPE"), "exit");
        im.put(KeyStroke.getKeyStroke('S'), "score");
        im.put(KeyStroke.getKeyStroke('T'), "settings");

        // Arrow keys and enter
        im.put(KeyStroke.getKeyStroke("UP"), "up");
        im.put(KeyStroke.getKeyStroke("DOWN"), "down");
        im.put(KeyStroke.getKeyStroke("LEFT"), "left");
        im.put(KeyStroke.getKeyStroke("RIGHT"), "right");
        im.put(KeyStroke.getKeyStroke("ENTER"), "enter");
        im.put(KeyStroke.getKeyStroke("SPACE"), "enter");

        // SCREEN SIZE HOTKEYS
        im.put(KeyStroke.getKeyStroke('1'), "sizeSmall");
        im.put(KeyStroke.getKeyStroke('2'), "sizeMedium");
        im.put(KeyStroke.getKeyStroke('3'), "sizeLarge");
        im.put(KeyStroke.getKeyStroke('4'), "sizeFull");
        im.put(KeyStroke.getKeyStroke("F11"), "sizeFull");

        am.put("sizeSmall", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                applyScreenPreset(ScreenSize.SMALL);
            }
        });
        am.put("sizeMedium", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                applyScreenPreset(ScreenSize.MEDIUM);
            }
        });
        am.put("sizeLarge", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                applyScreenPreset(ScreenSize.LARGE);
            }
        });
        am.put("sizeFull", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                applyScreenPreset(ScreenSize.FULLSCREEN);
            }
        });

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

        // Arrow behavior
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

    // UI CONSTRUCTION
    private void buildUI() {
        GridBagConstraints gb = new GridBagConstraints();
        gb.gridx = 0;
        gb.gridy = 0;
        gb.insets = new Insets(0, 0, 8, 0);
        gb.anchor = GridBagConstraints.PAGE_START;
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

        // MAIN COLUMN (scrollable)
        gb.gridy++;
        gb.insets = new Insets(40, 0, 0, 0);
        gb.weighty = 1.0;
        gb.fill = GridBagConstraints.BOTH; // allow it to expand

        menuColumn = new JPanel();
        menuColumn.setOpaque(false);
        menuColumn.setLayout(new BoxLayout(menuColumn, BoxLayout.Y_AXIS));

        // Wrap the column in a scroll pane so mouse wheel works
        JScrollPane scroll = new JScrollPane(menuColumn);
        scroll.setBorder(BorderFactory.createEmptyBorder());
        scroll.setOpaque(false);
        scroll.getViewport().setOpaque(false);
        // smoother scrolling speed
        scroll.getVerticalScrollBar().setUnitIncrement(16);

        add(scroll, gb);

        // ===== INDIVIDUAL =====
        JButton btnIndividual = makeMainButton("INDIVIDUAL", () -> togglePanel(individualSub));
        btnIndividual.setAlignmentX(CENTER_ALIGNMENT);
        menuColumn.add(btnIndividual);
        menuColumn.add(Box.createVerticalStrut(12));

        individualSub = makeSubPanel();
        individualSub.setAlignmentX(CENTER_ALIGNMENT);

        individualSub.add(makeSubButton("Normal Game", () -> togglePanel(individualNormalRow)));
        individualSub.add(Box.createVerticalStrut(9));

        individualNormalRow = makeDifficultyRowFor(GameConfig.Mode.CLASSIC);
        individualNormalRow.setVisible(false);
        individualSub.add(individualNormalRow);

        individualSub.add(individualNormalRow);
        
        individualSub.add(makeSubButton("Item", () -> togglePanel(individualItemRow)));

        individualSub.add(Box.createVerticalStrut(9));
        individualItemRow = makeItemDifficultyRowForSingle(); // option buttons for ITEM
        individualItemRow.setVisible(false);
        individualSub.add(individualItemRow);
        individualSub.setVisible(false);

        
        individualSub.add(makeSubButton("AI Battle", () -> togglePanel(individualAIRow)));
        individualSub.add(Box.createVerticalStrut(9));

        individualAIRow = makeAIDifficultyRow();
        individualAIRow.setVisible(false);
        individualSub.add(individualAIRow);
        individualSub.add(Box.createVerticalStrut(9));

        menuColumn.add(individualSub);
        menuColumn.add(Box.createVerticalStrut(18));

        // ===== MULTIPLAYER =====
        JButton btnMultiplayer = makeMainButton("MULTIPLAYER", () -> togglePanel(multiplayerSub));
        btnMultiplayer.setAlignmentX(CENTER_ALIGNMENT);
        menuColumn.add(btnMultiplayer);
        menuColumn.add(Box.createVerticalStrut(12));

        multiplayerSub = makeSubPanel();
        multiplayerSub.setAlignmentX(CENTER_ALIGNMENT);

        // Online P2P Battle
        multiplayerSub.add(makeSubButton("Online P2P Battle", () -> togglePanel(onlineP2PSub)));
        multiplayerSub.add(Box.createVerticalStrut(9));

        onlineP2PSub = makeSubPanel();
        onlineP2PSub.setAlignmentX(CENTER_ALIGNMENT);

        onlineP2PSub.add(makeSubButton("NORMAL", () -> togglePanel(onlineNormalRow)));
        onlineP2PSub.add(Box.createVerticalStrut(7));

        onlineNormalRow = makeOnlineP2PRowFor(GameConfig.Mode.CLASSIC);
        onlineNormalRow.setVisible(false);
        onlineP2PSub.add(onlineNormalRow);
        onlineP2PSub.add(Box.createVerticalStrut(7));

        onlineP2PSub.add(makeSubButton("ITEM", () -> togglePanel(onlineItemRow)));
        onlineP2PSub.add(Box.createVerticalStrut(7));

        onlineItemRow = makeOnlineP2PRowFor(GameConfig.Mode.ITEM);
        onlineItemRow.setVisible(false);
        onlineP2PSub.add(onlineItemRow);
        onlineP2PSub.add(Box.createVerticalStrut(7));

        onlineP2PSub.add(makeSubButton("TIME", () -> togglePanel(onlineTimeRow)));
        onlineP2PSub.add(Box.createVerticalStrut(7));

        onlineTimeRow = makeOnlineP2PRowFor(GameConfig.Mode.TIME_ATTACK);
        onlineTimeRow.setVisible(false);
        onlineP2PSub.add(onlineTimeRow);

        onlineP2PSub.setVisible(false);
        multiplayerSub.add(onlineP2PSub);
        multiplayerSub.add(Box.createVerticalStrut(12));
        // Local 2P (Same PC)
        multiplayerSub.add(makeSubButton("Local 2P (Same PC)", () -> togglePanel(local2PSub)));
        multiplayerSub.add(Box.createVerticalStrut(9));

        local2PSub = makeSubPanel();
        local2PSub.setAlignmentX(CENTER_ALIGNMENT);

        local2PSub.add(makeSubButton("NORMAL", () -> togglePanel(localNormalRow)));
        local2PSub.add(Box.createVerticalStrut(7));

        localNormalRow = makeLocal2PRowFor(false);
        localNormalRow.setVisible(false);
        local2PSub.add(localNormalRow);
        local2PSub.add(Box.createVerticalStrut(7));

        local2PSub.add(makeSubButton("ITEM", () -> togglePanel(localItemRow)));
        local2PSub.add(Box.createVerticalStrut(7));

        localItemRow = makeLocal2PRowFor(true);
        localItemRow.setVisible(false);
        local2PSub.add(localItemRow);
        local2PSub.add(Box.createVerticalStrut(7));

        local2PSub.add(makeSubButton("TIME", () -> togglePanel(localTimeRow)));
        local2PSub.add(Box.createVerticalStrut(7));

        localTimeRow = makeLocal2PTimeRow();
        localTimeRow.setVisible(false);
        local2PSub.add(localTimeRow);

        local2PSub.setVisible(false);
        multiplayerSub.add(local2PSub);

        multiplayerSub.setVisible(false);
        menuColumn.add(multiplayerSub);
        menuColumn.add(Box.createVerticalStrut(22));

        // ===== SIMPLE ENTRIES =====
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

        // Stubs
        cardsContainer = new JPanel(new GridLayout(1, 2, 26, 0));
        cardsContainer.setOpaque(false);
        bottomPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 24, 0));
        bottomPanel.setOpaque(false);
    }

    // Toggle panel visibility
    private void togglePanel(JPanel p) {
        if (p == null)
            return;
        p.setVisible(!p.isVisible());
        rebuildNavOrder();
        revalidate();
        repaint();
    }

    // Difficulty row for single player NORMAL mode
    private JPanel makeDifficultyRowFor(GameConfig.Mode mode) {
        JPanel row = new JPanel(new FlowLayout(FlowLayout.CENTER, 8, 0));
        row.setOpaque(false);
        row.setAlignmentX(CENTER_ALIGNMENT);
        row.add(makeGlassSmallButton("EASY",
                () -> {
                    stopMenuBGM();
                    onStart.accept(new GameConfig(mode, GameConfig.Difficulty.EASY, false));
                }));
        row.add(makeGlassSmallButton("MEDIUM",
                () -> {
                    stopMenuBGM();
                    onStart.accept(new GameConfig(mode, GameConfig.Difficulty.NORMAL, false));
                }));
        row.add(makeGlassSmallButton("HARD",
                () -> {
                    stopMenuBGM();
                    onStart.accept(new GameConfig(mode, GameConfig.Difficulty.HARD, false));
                }));
        return row;
    }

    private JPanel makeOnlineP2PRowFor(GameConfig.Mode modeIgnored) {
        JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        row.setOpaque(false);
        row.setAlignmentX(LEFT_ALIGNMENT);
        row.add(makeGlassSmallButton("START", () -> {
            // GameConfig 생성 (VERSUS 모드)
            GameConfig config = new GameConfig(
                    GameConfig.Mode.VERSUS, // P2P 대전 모드
                    GameConfig.Difficulty.EASY, // 기본 난이도
                    false // colorBlindMode
            );

            // GameLauncher의 onGameConfigSelect 콜백 호출
            onStart.accept(config);
        }));
        // // MEDIUM P2P
        // row.add(makeGlassSmallButton("MEDIUM", () -> {
        //     GameConfig config = new GameConfig(
        //             GameConfig.Mode.VERSUS,
        //             GameConfig.Difficulty.NORMAL,
        //             false);
        //     onStart.accept(config);
        // }));

        // // HARD P2P
        // row.add(makeGlassSmallButton("HARD", () -> {
        //     GameConfig config = new GameConfig(
        //             GameConfig.Mode.VERSUS,
        //             GameConfig.Difficulty.HARD,
        //             false);
        //     onStart.accept(config);
        // }));

        return row;
    }

    // E/M/H row for ITEM single-player (explicit per request)
    private JPanel makeItemDifficultyRowForSingle() {
        JPanel row = new JPanel(new FlowLayout(FlowLayout.CENTER, 8, 0));
        row.setOpaque(false);
        row.setAlignmentX(CENTER_ALIGNMENT);
        row.add(makeGlassSmallButton("EASY",
                () -> onStart.accept(new GameConfig(GameConfig.Mode.ITEM, GameConfig.Difficulty.EASY, false))));
        row.add(makeGlassSmallButton("MEDIUM",
                () -> onStart.accept(new GameConfig(GameConfig.Mode.ITEM, GameConfig.Difficulty.NORMAL, false))));
        row.add(makeGlassSmallButton("HARD",
                () -> onStart.accept(new GameConfig(GameConfig.Mode.ITEM, GameConfig.Difficulty.HARD, false))));
        return row;
    }

    private JPanel makeItemDifficultyRowForMulti() {
        JPanel row = new JPanel(new FlowLayout(FlowLayout.CENTER, 8, 0));
        row.setOpaque(false);
        row.setAlignmentX(CENTER_ALIGNMENT);

        row.add(makeGlassSmallButton("EASY", () -> {
            GameConfig p1 = new GameConfig(GameConfig.Mode.ITEM, GameConfig.Difficulty.EASY, false);
            GameConfig p2 = new GameConfig(GameConfig.Mode.ITEM, GameConfig.Difficulty.EASY, false);
            new VersusFrame(p1, p2, "ITEM");
        }));

        row.add(makeGlassSmallButton("MEDIUM", () -> {
            GameConfig p1 = new GameConfig(GameConfig.Mode.ITEM, GameConfig.Difficulty.NORMAL, false);
            GameConfig p2 = new GameConfig(GameConfig.Mode.ITEM, GameConfig.Difficulty.NORMAL, false);
            new VersusFrame(p1, p2, "ITEM");
        }));

        row.add(makeGlassSmallButton("HARD", () -> {
            GameConfig p1 = new GameConfig(GameConfig.Mode.ITEM, GameConfig.Difficulty.HARD, false);
            GameConfig p2 = new GameConfig(GameConfig.Mode.ITEM, GameConfig.Difficulty.HARD, false);
            new VersusFrame(p1, p2, "ITEM");
        }));

        return row;
    }

    // Local 2P – NORMAL / ITEM rows
    private JPanel makeLocal2PRowFor(boolean itemMode) {
        JPanel row = new JPanel(new FlowLayout(FlowLayout.CENTER, 8, 0));
        row.setOpaque(false);
        row.setAlignmentX(CENTER_ALIGNMENT);

        GameConfig.Mode mode = itemMode ? GameConfig.Mode.ITEM : GameConfig.Mode.CLASSIC;

        row.add(makeGlassSmallButton("EASY", () -> {
            stopMenuBGM();

            GameConfig p1 = new GameConfig(mode, GameConfig.Difficulty.EASY, false);
            GameConfig p2 = new GameConfig(mode, GameConfig.Difficulty.EASY, false);
            String gameRule = itemMode ? "ITEM" : "NORMAL";

            // ← 메뉴 프레임 숨기기
            JFrame menuFrame = (JFrame) SwingUtilities.getWindowAncestor(MenuPanel.this);
            if (menuFrame != null) {
                menuFrame.setVisible(false);
            }

            VersusFrame versus = new VersusFrame(p1, p2, gameRule);

            // ← 대전창이 닫힐 때 메뉴 복귀
            if (menuFrame != null) {
                versus.addWindowListener(new java.awt.event.WindowAdapter() {
                    @Override
                    public void windowClosed(java.awt.event.WindowEvent e) {
                        menuFrame.setVisible(true);
                        SwingUtilities.invokeLater(() -> {
                            menuFrame.toFront();
                            menuFrame.requestFocusInWindow();
                        });
                    }

                    @Override
                    public void windowClosing(java.awt.event.WindowEvent e) {
                        versus.dispose();
                    }
                });
            }
        }));

        row.add(makeGlassSmallButton("MEDIUM", () -> {
            stopMenuBGM();

            GameConfig p1 = new GameConfig(mode, GameConfig.Difficulty.NORMAL, false);
            GameConfig p2 = new GameConfig(mode, GameConfig.Difficulty.NORMAL, false);
            String gameRule = itemMode ? "ITEM" : "NORMAL";

            JFrame menuFrame = (JFrame) SwingUtilities.getWindowAncestor(MenuPanel.this);
            if (menuFrame != null) {
                menuFrame.setVisible(false);
            }

            VersusFrame versus = new VersusFrame(p1, p2, gameRule);

            if (menuFrame != null) {
                versus.addWindowListener(new java.awt.event.WindowAdapter() {
                    @Override
                    public void windowClosed(java.awt.event.WindowEvent e) {
                        menuFrame.setVisible(true);
                        SwingUtilities.invokeLater(() -> {
                            menuFrame.toFront();
                            menuFrame.requestFocusInWindow();
                        });
                    }

                    @Override
                    public void windowClosing(java.awt.event.WindowEvent e) {
                        versus.dispose();
                    }
                });
            }
        }));

        row.add(makeGlassSmallButton("HARD", () -> {
            stopMenuBGM();

            GameConfig p1 = new GameConfig(mode, GameConfig.Difficulty.HARD, false);
            GameConfig p2 = new GameConfig(mode, GameConfig.Difficulty.HARD, false);
            String gameRule = itemMode ? "ITEM" : "NORMAL";

            JFrame menuFrame = (JFrame) SwingUtilities.getWindowAncestor(MenuPanel.this);
            if (menuFrame != null) {
                menuFrame.setVisible(false);
            }

            VersusFrame versus = new VersusFrame(p1, p2, gameRule);

            if (menuFrame != null) {
                versus.addWindowListener(new java.awt.event.WindowAdapter() {
                    @Override
                    public void windowClosed(java.awt.event.WindowEvent e) {
                        menuFrame.setVisible(true);
                        SwingUtilities.invokeLater(() -> {
                            menuFrame.toFront();
                            menuFrame.requestFocusInWindow();
                        });
                    }

                    @Override
                    public void windowClosing(java.awt.event.WindowEvent e) {
                        versus.dispose();
                    }
                });
            }
        }));

        return row;
    }

    // Local 2P (Same PC) – TIME row
    private JPanel makeLocal2PTimeRow() {
        JPanel row = new JPanel(new FlowLayout(FlowLayout.CENTER, 8, 0));
        row.setOpaque(false);
        row.setAlignmentX(CENTER_ALIGNMENT);

        row.add(makeGlassSmallButton("START", () -> {
            stopMenuBGM();

            GameConfig p1 = new GameConfig(
                    GameConfig.Mode.TIME_ATTACK,
                    GameConfig.Difficulty.NORMAL,
                    false);

            GameConfig p2 = new GameConfig(
                    GameConfig.Mode.TIME_ATTACK,
                    GameConfig.Difficulty.NORMAL,
                    false);

            String gameRule = "TIME_ATTACK";

            JFrame menuFrame = (JFrame) SwingUtilities.getWindowAncestor(MenuPanel.this);
            if (menuFrame != null) {
                menuFrame.setVisible(false);
            }

            VersusFrame versus = new VersusFrame(p1, p2, gameRule);

            if (menuFrame != null) {
                versus.addWindowListener(new java.awt.event.WindowAdapter() {
                    @Override
                    public void windowClosed(java.awt.event.WindowEvent e) {
                        menuFrame.setVisible(true);
                        SwingUtilities.invokeLater(() -> {
                            menuFrame.toFront();
                            menuFrame.requestFocusInWindow();
                        });
                    }

                    @Override
                    public void windowClosing(java.awt.event.WindowEvent e) {
                        versus.dispose();
                    }
                });
            }
        }));

        return row;
    }

    private JPanel makeAIDifficultyRow() {
        JPanel row = new JPanel(new FlowLayout(FlowLayout.CENTER, 8, 0));
        row.setOpaque(false);
        row.setAlignmentX(CENTER_ALIGNMENT);

        row.add(makeGlassSmallButton("EASY", () -> {
            stopMenuBGM();
            GameConfig aiConfig = new GameConfig(
                    GameConfig.Mode.AI,
                    GameConfig.Difficulty.AI_EASY,
                    false);
            onStart.accept(aiConfig);
        }));

        row.add(makeGlassSmallButton("MEDIUM", () -> {
            stopMenuBGM();
            GameConfig aiConfig = new GameConfig(
                    GameConfig.Mode.AI,
                    GameConfig.Difficulty.AI_NORMAL,
                    false);
            onStart.accept(aiConfig);
        }));

        row.add(makeGlassSmallButton("HARD", () -> {
            stopMenuBGM();
            GameConfig aiConfig = new GameConfig(
                    GameConfig.Mode.AI,
                    GameConfig.Difficulty.AI_HARD,
                    false);
            onStart.accept(aiConfig);
        }));

        return row;
    }

    private VersusFrame openVersus() {
        JFrame f = (JFrame) SwingUtilities.getWindowAncestor(MenuPanel.this);
        if (f != null)
            f.dispose();

        // NORMAL 난이도 고정
        GameConfig p1 = new GameConfig(GameConfig.Mode.ITEM, GameConfig.Difficulty.NORMAL, false);
        GameConfig p2 = new GameConfig(GameConfig.Mode.ITEM, GameConfig.Difficulty.NORMAL, false);

        String gameRule = "ITEM";

        return new VersusFrame(p1, p2, gameRule);
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
        sound.play(SoundManager.Sound.MENU_MOVE, 0.3f);
        navIndex = (navIndex + delta + navOrder.size()) % navOrder.size();
        setSelection(navIndex);
    }

    private void setSelection(int idx) {
        if (navOrder.isEmpty())
            return;

        for (int i = 0; i < navOrder.size(); i++) {
            JComponent c = navOrder.get(i);
            c.putClientProperty("nav.selected", i == idx);
            c.repaint();
        }

        // ⬇ automatically scroll selected item into view
        SwingUtilities.invokeLater(() -> {
            JComponent selected = navOrder.get(idx);
            Rectangle rect = selected.getBounds();
            selected.scrollRectToVisible(rect);
        });
    }

    private void activateSelection() {
        if (navOrder.isEmpty())
            return;
        sound.play(SoundManager.Sound.MENU_SELECT, 0.5f);
        navOrder.get(navIndex).doClick();
    }

    // Background paint
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
            int x = Math.round(sx[i]); // block position
            int y = Math.round(sy[i]); //
            int size = 14 + (int) (ss[i] * 10); // block size based on ss

            Color base = BLOCK_COLORS[blockColorIndex[i]]; // choose base color
            int alpha = (int) (180 * ss[i]); // alpha from ss
            Color fill = new Color(
                    base.getRed(),
                    base.getGreen(),
                    base.getBlue(),
                    Math.min(255, Math.max(40, alpha)));
            // outer block
            g2.setColor(fill);
            g2.fillRoundRect(x, y, size, size, 6, 6);

            // inner highlight
            g2.setColor(new Color(255, 255, 255, 70));
            g2.fillRoundRect(x + 3, y + 3, size - 6, size - 6, 6, 6);

            // outline
            g2.setColor(new Color(0, 0, 0, 90));
            g2.drawRoundRect(x, y, size, size, 6, 6);
        }
        g2.dispose();
    }

    // Button renderers
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
                addMouseListener(new MouseAdapter() {
                    @Override
                    public void mouseEntered(MouseEvent e) {
                        over = true;
                        sound.play(SoundManager.Sound.MENU_HOVER, 0.2f); // 조용하게
                    }

                    @Override
                    public void mouseExited(MouseEvent e) {
                        over = false;
                    }
                });
                addActionListener(e -> {
                    sound.play(SoundManager.Sound.MENU_SELECT, 0.5f);
                    action.run();
                });
            }

            @Override
            public Dimension getPreferredSize() {
                return new Dimension(240, 52);
            }

            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                int w = getWidth(), h = getHeight();
                boolean sel = Boolean.TRUE.equals(getClientProperty("nav.selected"));

                Shape rr = new RoundRectangle2D.Float(0, 0, w - 1, h - 1, 10, 10);
                GradientPaint bg = new GradientPaint(0, 0, new Color(60, 110, 140, (int) (140 + 60 * hover)),
                        0, h, new Color(30, 60, 90, (int) (140 + 60 * hover)));
                g2.setPaint(bg);
                g2.fill(rr);

                int borderA = sel ? 255 : (int) (100 + 100 * hover);
                float stroke = sel ? 3.2f : (2f + hover);
                g2.setColor(new Color(120, 190, 255, borderA));
                g2.setStroke(new BasicStroke(stroke));
                g2.draw(rr);

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
        Dimension d = new Dimension(240, 52);
        b.setPreferredSize(d);
        b.setMaximumSize(d); // prevent BoxLayout from expanding it
        b.setAlignmentX(CENTER_ALIGNMENT);
        // keep the rest (listeners, painting) as you had
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
                addMouseListener(new MouseAdapter() {
                    @Override
                    public void mouseEntered(MouseEvent e) {
                        over = true;
                        sound.play(SoundManager.Sound.MENU_HOVER, 0.15f); // 더 조용하게
                    }

                    @Override
                    public void mouseExited(MouseEvent e) {
                        over = false;
                    }
                });

                addActionListener(e -> {
                    sound.play(SoundManager.Sound.MENU_CLICK, 0.4f);
                    action.run();
                });
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
        Dimension d = new Dimension(200, 40);
        b.setPreferredSize(d);
        b.setMaximumSize(d); // stop width growth
        b.setAlignmentX(CENTER_ALIGNMENT);
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
                setBorder(BorderFactory.createEmptyBorder(5, 14, 5, 14));
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
                addMouseListener(new MouseAdapter() {
                    @Override
                    public void mouseEntered(MouseEvent e) {
                        over = true;
                        sound.play(SoundManager.Sound.MENU_HOVER, 0.15f);
                    }

                    @Override
                    public void mouseExited(MouseEvent e) {
                        over = false;
                    }
                });

                addActionListener(e -> {
                    sound.play(SoundManager.Sound.MENU_CLICK, 0.4f);
                    action.run();
                });
            }

            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                int w = getWidth(), h = getHeight();
                boolean sel = Boolean.TRUE.equals(getClientProperty("nav.selected"));

                Shape rr = new RoundRectangle2D.Float(0, 0, w - 1, h - 1, 10, 10);
                GradientPaint bg = new GradientPaint(0, 0, new Color(60, 110, 140, (int) (140 + 60 * hover)),
                        0, h, new Color(30, 60, 90, (int) (140 + 60 * hover)));
                g2.setPaint(bg);
                g2.fill(rr);

                int borderA = sel ? 255 : (int) (100 + 100 * hover);
                g2.setColor(new Color(120, 190, 255, borderA));
                g2.setStroke(new BasicStroke(sel ? 3f : (2f + hover)));
                g2.draw(rr);

                g2.setFont(getFont().deriveFont(Font.BOLD, 12.5f));
                g2.setFont(getFont().deriveFont(Font.BOLD, 12.5f));
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
        Dimension d = new Dimension(96, 30); // clearly smaller than 200x40
        b.setPreferredSize(d);
        b.setMaximumSize(d);
        b.setAlignmentX(CENTER_ALIGNMENT);
        return b;
    }

    // star helpers
    private void seedStars() {
        Random r = new Random();
        for (int i = 0; i < STARS; i++) {
            sx[i] = r.nextInt(1400) - 100;
            sy[i] = r.nextInt(900);
            sv[i] = 3.0f + r.nextFloat() * 4.0f;
            ss[i] = 0.6f + r.nextFloat() * 0.7f;

            blockColorIndex[i] = r.nextInt(BLOCK_COLORS.length); // give each block a color
        }
    }

    private void stepStars() {
        int w = getWidth(), h = getHeight();
        if (w == 0 || h == 0)
            return;

        for (int i = 0; i < STARS; i++) {
            sy[i] += sv[i];

            // when block goes below screen, respawn at top with new random params
            if (sy[i] > h + 40) { // respawn logic
                sx[i] = (float) (Math.random() * w);
                sy[i] = -20;
                sv[i] = 3.0f + (float) Math.random() * 4.0f;
                ss[i] = 0.6f + (float) Math.random() * 0.7f;
                blockColorIndex[i] = (int) (Math.random() * BLOCK_COLORS.length);
            }
        }
    }

    private void startMenuBGM() {
        if (!bgmPlaying) {
            sound.playBGM(SoundManager.BGM.MENU);
            bgmPlaying = true;
            System.out.println("[MenuPanel] BGM started");
        }
    }

    public void stopMenuBGM() {
        if (bgmPlaying) {
            sound.stopBGM();
            bgmPlaying = false;
            System.out.println("[MenuPanel] BGM stopped");
        }
    }
}
