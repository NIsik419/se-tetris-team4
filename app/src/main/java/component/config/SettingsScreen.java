package component.config;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.RenderingHints;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.swing.AbstractAction;
import javax.swing.BorderFactory;
import javax.swing.DefaultListCellRenderer;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.KeyStroke;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.border.Border;
import javax.swing.text.JTextComponent;

import component.ColorBlindPalette;
import component.GameConfig;

public class SettingsScreen extends JPanel {

    public interface ApplyListener { void onApply(Settings s); }

    private final Settings settings;
    private Settings localSettings;
    private final ApplyListener applyListener;

    private JComboBox<ColorBlindPalette.Mode> cbBlindMode;
    private JComboBox<Settings.ScreenSize>     cbScreen;

    // 키 바인딩 관련
    private final Map<Settings.Action, KeyField> keyFields = new EnumMap<>(Settings.Action.class);
    private JLabel  lblError;
    private JButton btnApply;

    // screen size label text (same as MenuPanel)
    private String getScreenLabel(Settings.ScreenSize size) {
        if (size == null) return "";
        switch (size) {
            case SMALL:      // MenuPanel: 600 x 480
                return "SMALL  (600 × 480) ";
            case MEDIUM:     // MenuPanel: 900 x 720
                return "MEDIUM (900 × 720) ";
            case LARGE:      // MenuPanel: 1200 x 840
                return "LARGE  (1200 × 840) ";
            default:
                return size.name();
        }
    }
    // unified display text for keys (arrows + rotate)
    private static String keyToDisplay(int keyCode) {                     
        return switch (keyCode) {                                         
            case KeyEvent.VK_LEFT  -> "⬅";                             
            case KeyEvent.VK_RIGHT ->"➡";    
            case KeyEvent.VK_UP    -> "↻";  
            case KeyEvent.VK_DOWN  -> "⬇";                           
            default -> KeyEvent.getKeyText(keyCode);             
        };                                                   
    }     


    // apply screen size to current window
    private void applyScreenSize(Settings.ScreenSize size) {         
        java.awt.Window w = SwingUtilities.getWindowAncestor(this);
        if (!(w instanceof JFrame f) || size == null) {               
            return;
        }

        f.setExtendedState(JFrame.NORMAL);
        f.setResizable(true);

        // 🔹 enum 에 정의된 해상도 사용
        Dimension d = size.toDimension();                            
        f.setMinimumSize(d);                                        
        f.setSize(d);                                               

        f.setLocationRelativeTo(null);
    }

    

    public SettingsScreen(Settings settings, ApplyListener applyListener, Runnable goBack) {
        this.settings       = settings;
        this.localSettings  = new Settings(settings);
        this.applyListener  = applyListener;

        // dark, game-like background
        setOpaque(true);
        setBackground(new Color(0x141B2A)); // slightly lighter than MenuPanel

        setLayout(new BorderLayout(12, 12));
        setBorder(BorderFactory.createEmptyBorder(16, 16, 16, 16));
        // 상단 타이틀 + Back
        JPanel top = new JPanel(new BorderLayout());
        top.setOpaque(false);
        JLabel title = new JLabel("SETTINGS");
        title.setFont(title.getFont().deriveFont(Font.BOLD, 26f));
        title.setForeground(new Color(235, 245, 255));    

        JButton btnBack = new JButton("← Back");
        // style Back button
        styleSecondaryButton(btnBack); // use same style helper
        btnBack.addActionListener(e -> {
            // 변경사항 폐기: 원본 settings로 복원
            localSettings = new Settings(settings);
            loadFromSettings();
            validateKeys();
            goBack.run();
        });
        top.add(title, BorderLayout.WEST);
        top.add(btnBack, BorderLayout.EAST);

        // 메인 카드형 패널 (폼 감싸는 컨테이너) /Main card-type panel (foam-wrapped container)
        JPanel card = new JPanel(new BorderLayout());
        card.setOpaque(false);
        card.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(70, 100, 150, 200), 1),
                BorderFactory.createEmptyBorder(12, 16, 16, 16)
        ));

        // 폼
        JPanel form = new JPanel(new GridBagLayout());
        form.setOpaque(false); 
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(6, 6, 6, 6);
        c.fill = GridBagConstraints.HORIZONTAL;
        c.gridx = 0;
        c.gridy = 0;
        c.weightx = 1.0;

        // Color-blind
        JPanel colorRow = new JPanel(new BorderLayout(8, 0));
        colorRow.setOpaque(false); 
        JLabel lblColor = new JLabel("Color-blind Mode");
        lblColor.setForeground(new Color(215, 225, 240));
        colorRow.add(lblColor, BorderLayout.WEST);

        cbBlindMode = new JComboBox<>(ColorBlindPalette.Mode.values());
        // dark combo box, bright text
        cbBlindMode.setBackground(new Color(245, 247, 250));
        cbBlindMode.setForeground(Color.BLACK); 
        cbBlindMode.setOpaque(true);
        cbBlindMode.setBorder(BorderFactory.createEmptyBorder(4, 10, 4, 10));
        cbBlindMode.setFont(cbBlindMode.getFont().deriveFont(14f));
        

        // renderer for popup list (dark text on light background)
        cbBlindMode.setRenderer(new DefaultListCellRenderer() {          
            @Override
            public java.awt.Component getListCellRendererComponent(
                    JList<?> list, Object value, int index,
                    boolean isSelected, boolean cellHasFocus) {

                JLabel lbl = (JLabel) super.getListCellRendererComponent(
                        list, value, index, isSelected, cellHasFocus);

                lbl.setBackground(isSelected ? new Color(220, 230, 245)
                                             : Color.WHITE);
                lbl.setForeground(Color.BLACK);
                return lbl;
            }
        });
        // ensure selected text (editor) is also black on white
        cbBlindMode.setEditable(true);                                   
        JTextComponent blindEditor = (JTextComponent) cbBlindMode.getEditor().getEditorComponent();
        blindEditor.setForeground(Color.BLACK);                               
        blindEditor.setBackground(Color.WHITE);                               

        colorRow.add(cbBlindMode, BorderLayout.CENTER);
        form.add(colorRow, c); 
         

        // Screen Size
        c.gridy++;
        JPanel sizeRow = new JPanel(new BorderLayout(8, 0));
        sizeRow.setOpaque(false);
        JLabel lblScreen = new JLabel("Screen Size");
        lblScreen.setForeground(new Color(215, 225, 240));
        sizeRow.add(lblScreen, BorderLayout.WEST);

        cbScreen = new JComboBox<>(Settings.ScreenSize.values());

        // dark combo, bright text
        cbScreen.setBackground(new Color(245, 247, 250));
        cbScreen.setForeground(Color.BLACK);
        cbScreen.setOpaque(true);
        cbScreen.setBorder(BorderFactory.createEmptyBorder(4, 10, 4, 10));
        cbScreen.setFont(cbScreen.getFont().deriveFont(14f));
        
        // custom renderer so list items show friendly labels & bright text
        cbScreen.setRenderer(new DefaultListCellRenderer() {
        @Override
        public Component getListCellRendererComponent(
                JList<?> list, Object value, int index,
                boolean isSelected, boolean cellHasFocus) {

            JLabel lbl = (JLabel) super.getListCellRendererComponent(
                    list, value, index, isSelected, cellHasFocus);

            if (value instanceof Settings.ScreenSize sz) {
                    lbl.setText(getScreenLabel(sz));
                }

                lbl.setBackground(isSelected ? new Color(220, 230, 245)
                                             : Color.WHITE);
                lbl.setForeground(Color.BLACK);
                return lbl;
            }
        });

        // editor text (selected value) → black on white
        cbScreen.setEditable(true);                                       
        JTextField editor2 = (JTextField) cbScreen.getEditor().getEditorComponent();
        editor2.setForeground(Color.BLACK);                               
        editor2.setBackground(Color.WHITE);      
        
        cbScreen.addActionListener(e -> {
            Settings.ScreenSize sz =
                    (Settings.ScreenSize) cbScreen.getSelectedItem();
            
            // just update localSettings, no resize yet
            if (sz != null) {
                localSettings.screenSize = sz;   // optional but nice
            }
        });


        sizeRow.add(cbScreen, BorderLayout.CENTER);
        form.add(sizeRow, c);
        

        // Key Bindings
        c.gridy++;
        JPanel keys = new JPanel(new GridBagLayout());
        keys.setOpaque(false);

        // custom titled border with light title color
        javax.swing.border.TitledBorder tb = BorderFactory.createTitledBorder(
                BorderFactory.createLineBorder(new Color(60, 90, 130, 200)),
                "Key Bindings"
        ); 
        tb.setTitleColor(new Color(220, 230, 245));          // light text color
        tb.setTitleFont(tb.getTitleFont().deriveFont(15f));  // a bit larger if you like
        keys.setBorder(tb);                                  //  use our custom border


        int r = 0;
        r = addKeyRow(keys, r, Settings.Action.Left,     "Left");
        r = addKeyRow(keys, r, Settings.Action.Right,    "Right");
        r = addKeyRow(keys, r, Settings.Action.SoftDrop, "Soft Drop");
        r = addKeyRow(keys, r, Settings.Action.HardDrop, "Hard Drop");
        r = addKeyRow(keys, r, Settings.Action.Rotate,   "Rotate");
        form.add(keys, c);

        // 오류 표기
        c.gridy++;
        lblError = new JLabel(" ");
        lblError.setForeground(new Color(255, 120,120));
        lblError.setFont(lblError.getFont().deriveFont(Font.PLAIN, 12f));
        form.add(lblError, c);

        // 버튼들
        JPanel bottom = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        bottom.setOpaque(false);

        JButton btnDefaults    = new JButton("Reset to Defaults");
        JButton btnResetScore  = new JButton("Reset Scoreboard");
        btnApply               = new JButton("Apply");

        //style buttons
        styleSecondaryButton(btnDefaults);
        styleSecondaryButton(btnResetScore);
        stylePrimaryButton(btnApply);

        bottom.add(btnDefaults);
        bottom.add(btnResetScore);
        bottom.add(btnApply);

        // Defaults
        btnDefaults.addActionListener(e -> {
            localSettings.resetToDefaults();
            loadFromSettings();
            validateKeys();
        });

        // Reset Scoreboard → 체크박스 다이얼로그
        btnResetScore.addActionListener(e -> showScoreResetDialog());

        // Apply
        btnApply.addActionListener(e -> {
            if (!validateKeys()) return; // 혹시 모를 찰나 누름 방지
            saveToSettings();

            // Apply 누를 때도 Screen Size 바로 적용
            Settings.ScreenSize sz =
                    (Settings.ScreenSize) cbScreen.getSelectedItem();     
            if (sz != null) {                                             
                applyScreenSize(sz);                                      
            }                                                            

            if (applyListener != null) applyListener.onApply(settings);
        });


        // ESC로 뒤로가기
        getInputMap(WHEN_IN_FOCUSED_WINDOW)
                .put(KeyStroke.getKeyStroke("ESCAPE"), "back");
        getActionMap().put("back", new AbstractAction() {
            @Override public void actionPerformed(java.awt.event.ActionEvent e) {
                localSettings = new Settings(settings); // 원복
                loadFromSettings();
                validateKeys();
                goBack.run();
            }
        });

        // scroll with transparent background
        JScrollPane scroll = new JScrollPane(form);
        scroll.setBorder(BorderFactory.createEmptyBorder());
        scroll.setOpaque(false);
        scroll.getViewport().setOpaque(false);
        scroll.getVerticalScrollBar().setUnitIncrement(16); // smooth

        card.add(scroll, BorderLayout.CENTER);

        add(top, BorderLayout.NORTH);
        add(card, BorderLayout.CENTER);
        add(bottom, BorderLayout.SOUTH);

        loadFromSettings();
        validateKeys();
    }

     // small helper to style primary button
    private void stylePrimaryButton(JButton b) {
        b.setFocusPainted(false);
        b.setFont(b.getFont().deriveFont(Font.BOLD, 13f));
        b.setBackground(new Color(70, 130, 190));
        b.setForeground(new Color(235, 245, 255));
        b.setBorder(BorderFactory.createEmptyBorder(8, 18, 8, 18));
    }

    // small helper to style secondary buttons
    private void styleSecondaryButton(JButton b) {
        b.setFocusPainted(false);
        b.setFont(b.getFont().deriveFont(Font.PLAIN, 13f));
        b.setBackground(new Color(35, 55, 90));
        b.setForeground(new Color(220, 230, 245));
        b.setBorder(BorderFactory.createEmptyBorder(6, 14, 6, 14));
    }

    // Scoreboard Reset Dialog
    private void showScoreResetDialog() {
        JPanel panel = new JPanel(new GridBagLayout());
        // EDIT: use light background + dark text for better contrast
        panel.setOpaque(true);                   
        panel.setBackground(Color.WHITE);   

        GridBagConstraints gc = new GridBagConstraints();
        gc.insets = new Insets(4, 8, 4, 8);
        gc.anchor = GridBagConstraints.WEST;
        gc.gridx  = 0;
        gc.gridy  = 0;

        JLabel lbl = new JLabel("Select which scoreboard(s) to reset:");
        lbl.setForeground(new Color(30, 30, 30));         // EDIT: dark text
        panel.add(lbl, gc);

        gc.gridy++;
        final JCheckBox[][] boxes =
                new JCheckBox[GameConfig.Mode.values().length][GameConfig.Difficulty.values().length];

        for (GameConfig.Mode mode : GameConfig.Mode.values()) {
            for (GameConfig.Difficulty diff : GameConfig.Difficulty.values()) {
                JCheckBox cb = new JCheckBox(mode + " / " + diff);
                cb.setOpaque(false);                     
                cb.setForeground(new Color(30, 30, 30));  
                boxes[mode.ordinal()][diff.ordinal()] = cb;
                gc.gridy++;
                panel.add(cb, gc);
            }
        }

        gc.gridy++;
        final JCheckBox cbAll = new JCheckBox("Reset ALL");
        cbAll.setOpaque(false);
        cbAll.setForeground(new Color(30, 30, 30));       // EDIT: dark text
        panel.add(cbAll, gc);

        // 전체 체크 토글
        cbAll.addActionListener(e -> {
            boolean selected = cbAll.isSelected();
            for (JCheckBox[] row : boxes) {
                for (JCheckBox b : row) b.setSelected(selected);
            }
        });

        int result = JOptionPane.showConfirmDialog(
                this, panel, "Reset Scoreboard",
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE
        );

        if (result == JOptionPane.OK_OPTION) {
            boolean any = false;

            // ALL 우선 처리
            if (cbAll.isSelected()) {
                settings.resetScoreBoardAll();
                any = true;
            } else {
                for (GameConfig.Mode m : GameConfig.Mode.values()) {
                    for (GameConfig.Difficulty d : GameConfig.Difficulty.values()) {
                        if (boxes[m.ordinal()][d.ordinal()].isSelected()) {
                            settings.resetScoreBoard(m, d);
                            any = true;
                        }
                    }
                }
            }

            JOptionPane.showMessageDialog(
                    this,
                    any ? "Selected scoreboard(s) have been reset."
                        : "No scoreboard selected.",
                    any ? "Done" : "Info",
                    JOptionPane.INFORMATION_MESSAGE
            );
        }
    }


    // 유틸 메서드들
    private int addKeyRow(JPanel panel, int row, Settings.Action action, String labelText) {
        Insets in = new Insets(4, 8, 4, 8);

        GridBagConstraints lc = new GridBagConstraints();
        lc.gridx = 0; 
        lc.gridy = row;
        lc.insets = in;
        lc.anchor = GridBagConstraints.LINE_END;

        JLabel lbl = new JLabel(labelText);
        lbl.setForeground(new Color(215, 225, 240)); 
        panel.add(lbl, lc);

        GridBagConstraints fc = new GridBagConstraints();
        fc.gridx = 1; fc.gridy = row;
        fc.insets = in;
        fc.fill = GridBagConstraints.HORIZONTAL;
        fc.weightx = 1.0;

        KeyField field = new KeyField(this::validateKeys);
        keyFields.put(action, field);
        panel.add(field, fc);

        return row + 1;
    }

    private void loadFromSettings() {
        cbBlindMode.setSelectedItem(localSettings.colorBlindMode);
        cbScreen.setSelectedItem(localSettings.screenSize);
        for (var e : keyFields.entrySet()) {
            Integer code = localSettings.keymap.get(e.getKey());
            if (code != null) e.getValue().setKeyCode(code);
        }
    }
    

    private void saveToSettings() {
        localSettings.update(s -> {
            s.colorBlindMode = (ColorBlindPalette.Mode) cbBlindMode.getSelectedItem();
            s.screenSize = (Settings.ScreenSize) cbScreen.getSelectedItem();
            for (var e : keyFields.entrySet()) {
                s.keymap.put(e.getKey(), e.getValue().getKeyCode());
            }
        });
        settings.update(s -> {
            s.colorBlindMode = localSettings.colorBlindMode;
            s.screenSize = localSettings.screenSize;
            s.keymap.clear();
            s.keymap.putAll(localSettings.keymap);
        });
    }

    /** 중복 키 검증 */
    private boolean validateKeys() {
        for (KeyField f : keyFields.values()) f.setError(false, null);

        Map<Integer, Settings.Action> used = new HashMap<>();
        List<String> dups = new ArrayList<>();

        for (var e : keyFields.entrySet()) {
            Settings.Action a = e.getKey();
            KeyField f = e.getValue();
            int code = f.getKeyCode();
            if (code == KeyEvent.VK_UNDEFINED) continue;

            if (used.containsKey(code)) {
                Settings.Action clash = used.get(code);
                String msg = String.format("'%s' is already used by %s",
                        KeyEvent.getKeyText(code), clash);
                f.setError(true, msg);
                keyFields.get(clash).setError(true,
                        String.format("'%s' is also used by %s",
                                KeyEvent.getKeyText(code), a));
                dups.add(String.format("%s ↔ %s (%s)", clash, a, KeyEvent.getKeyText(code)));
            } else {
                used.put(code, a);
            }
        }

        boolean ok = dups.isEmpty();
        btnApply.setEnabled(ok);
        lblError.setText(ok ? " " : ("Duplicate keys: " + String.join(", ", dups)));
        return ok;
    }

    // 키캡처 전용 텍스트필드 (변경 시 콜백 호출 가능)
    private static class KeyField extends JTextField {
        private int keyCode = KeyEvent.VK_UNDEFINED;
        private final Runnable onChange;
        private final Border normalBorder;

        KeyField(Runnable onChange) {
            super(12);
            this.onChange = onChange;
            setEditable(false);
            setHorizontalAlignment(SwingConstants.CENTER);
            setToolTipText("Click and press a key");

            // dark theme style
            setBackground(new Color(32, 40, 58));   // dark but visible
            setForeground(new Color(235, 245, 255)); // bright white text
            setCaretColor(new Color(235, 245, 255));
            setOpaque(true);
            normalBorder = BorderFactory.createMatteBorder(    
                    0, 0, 1, 0, new Color(70, 90, 140));    
            setBorder(normalBorder);                          
            setFont(getFont().deriveFont(Font.BOLD, 18f));    
            addKeyListener(new KeyAdapter() {
                @Override public void keyPressed(KeyEvent e) {
                    keyCode = e.getKeyCode();
                    setText(SettingsScreen.keyToDisplay(keyCode));  
                    if (onChange != null) onChange.run();
                    e.consume();
                }
            });

            addMouseListener(new java.awt.event.MouseAdapter() {
                @Override public void mousePressed(java.awt.event.MouseEvent e) { requestFocusInWindow(); }
            });
        }

        void setKeyCode(int code) {
            keyCode = code;
            setText(SettingsScreen.keyToDisplay(code));   
        }

        int getKeyCode() { return keyCode; }

        void setError(boolean error, String tooltip) {
            if (error) {
                setBorder(BorderFactory.createLineBorder(new Color(200, 60, 60), 2));
                if (tooltip != null) setToolTipText(tooltip);
            } else {
                setBorder(normalBorder);
                setToolTipText("Click and press a key");
            }
        }
    }
    // paint gradient background (similar vibe to menu)
    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        int w = getWidth(), h = getHeight();

        GradientPaint sky = new GradientPaint(
                0, 0, new Color(20, 26, 40),
                0, h, new Color(24, 34, 56)
        );
        g2.setPaint(sky);
        g2.fillRect(0, 0, w, h);

        g2.dispose();
    }
}
