package component.config;

import component.ColorBlindPalette;
import component.GameConfig;

import javax.swing.*;
import javax.swing.border.Border;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.Color;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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

    public SettingsScreen(Settings settings, ApplyListener applyListener, Runnable goBack) {
        this.settings       = settings;
        this.localSettings  = new Settings(settings);
        this.applyListener  = applyListener;

        setLayout(new BorderLayout(12, 12));
        setBorder(BorderFactory.createEmptyBorder(16, 16, 16, 16));

        // 상단 타이틀 + Back
        JPanel top = new JPanel(new BorderLayout());
        JLabel title = new JLabel("Settings");
        title.setFont(title.getFont().deriveFont(Font.BOLD, 20f));
        JButton btnBack = new JButton("← Back");
        btnBack.addActionListener(e -> {
            // 변경사항 폐기: 원본 settings로 복원
            localSettings = new Settings(settings);
            loadFromSettings();
            validateKeys();
            goBack.run();
        });
        top.add(title, BorderLayout.WEST);
        top.add(btnBack, BorderLayout.EAST);

        // 폼
        JPanel form = new JPanel(new GridBagLayout());
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(6, 6, 6, 6);
        c.fill = GridBagConstraints.HORIZONTAL;
        c.gridx = 0;
        c.gridy = 0;
        c.weightx = 1.0;

        // Color-blind
        JPanel colorRow = new JPanel(new BorderLayout(8, 0));
        colorRow.add(new JLabel("Color-blind Mode"), BorderLayout.WEST);
        cbBlindMode = new JComboBox<>(ColorBlindPalette.Mode.values());
        colorRow.add(cbBlindMode, BorderLayout.CENTER);
        form.add(colorRow, c);

        // Screen Size
        c.gridy++;
        JPanel sizeRow = new JPanel(new BorderLayout(8, 0));
        sizeRow.add(new JLabel("Screen Size"), BorderLayout.WEST);
        cbScreen = new JComboBox<>(Settings.ScreenSize.values());
        sizeRow.add(cbScreen, BorderLayout.CENTER);
        form.add(sizeRow, c);

        // Key Bindings
        c.gridy++;
        JPanel keys = new JPanel(new GridBagLayout());
        keys.setBorder(BorderFactory.createTitledBorder("Key Bindings"));
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
        lblError.setForeground(new Color(180, 0, 0));
        form.add(lblError, c);

        // 버튼들
        JPanel bottom = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton btnDefaults    = new JButton("Reset to Defaults");
        JButton btnResetScore  = new JButton("Reset Scoreboard");
        btnApply               = new JButton("Apply");
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
            if (applyListener != null) applyListener.onApply(settings);
        });

        // ESC로 뒤로가기
        getInputMap(WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke("ESCAPE"), "back");
        getActionMap().put("back", new AbstractAction() {
            @Override public void actionPerformed(java.awt.event.ActionEvent e) {
                localSettings = new Settings(settings); // 원복
                loadFromSettings();
                validateKeys();
                goBack.run();
            }
        });

        JScrollPane scroll = new JScrollPane(form);
        scroll.setBorder(BorderFactory.createEmptyBorder());

        add(top, BorderLayout.NORTH);
        add(scroll, BorderLayout.CENTER);
        add(bottom, BorderLayout.SOUTH);

        loadFromSettings();
        validateKeys();
    }

    // Scoreboard Reset Dialog
    private void showScoreResetDialog() {
        JPanel panel = new JPanel(new GridBagLayout());
        GridBagConstraints gc = new GridBagConstraints();
        gc.insets = new Insets(4, 8, 4, 8);
        gc.anchor = GridBagConstraints.WEST;
        gc.gridx  = 0;
        gc.gridy  = 0;

        panel.add(new JLabel("Select which scoreboard(s) to reset:"), gc);

        gc.gridy++;
        final JCheckBox[][] boxes =
                new JCheckBox[GameConfig.Mode.values().length][GameConfig.Difficulty.values().length];

        for (GameConfig.Mode mode : GameConfig.Mode.values()) {
            for (GameConfig.Difficulty diff : GameConfig.Difficulty.values()) {
                JCheckBox cb = new JCheckBox(mode + " / " + diff);
                boxes[mode.ordinal()][diff.ordinal()] = cb;
                gc.gridy++;
                panel.add(cb, gc);
            }
        }

        gc.gridy++;
        final JCheckBox cbAll = new JCheckBox("Reset ALL");
        panel.add(cbAll, gc);

        // 전체 체크 토글 (익명 클래스 사용)
        cbAll.addActionListener(new java.awt.event.ActionListener() {
            @Override public void actionPerformed(java.awt.event.ActionEvent e) {
                boolean selected = cbAll.isSelected();
                for (JCheckBox[] row : boxes) {
                    for (JCheckBox b : row) b.setSelected(selected);
                }
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
        lc.gridx = 0; lc.gridy = row;
        lc.insets = in;
        lc.anchor = GridBagConstraints.LINE_END;
        panel.add(new JLabel(labelText), lc);

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
        private final Border normalBorder = UIManager.getBorder("TextField.border");

        KeyField(Runnable onChange) {
            super(12);
            this.onChange = onChange;
            setEditable(false);
            setHorizontalAlignment(SwingConstants.CENTER);
            setToolTipText("Click and press a key");

            addKeyListener(new KeyAdapter() {
                @Override public void keyPressed(KeyEvent e) {
                    keyCode = e.getKeyCode();
                    setText(KeyEvent.getKeyText(keyCode));
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
            setText(KeyEvent.getKeyText(code));
        }

        int getKeyCode() { return keyCode; }

        void setError(boolean error, String tooltip) {
            if (error) {
                setBorder(BorderFactory.createLineBorder(new Color(200, 0, 0), 2));
                if (tooltip != null) setToolTipText(tooltip);
            } else {
                setBorder(normalBorder);
                setToolTipText("Click and press a key");
            }
        }
    }
}
