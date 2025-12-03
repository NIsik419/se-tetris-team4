package component.score;

import javax.swing.*;

import component.GameConfig;

import java.awt.*;
import java.util.function.IntConsumer;

/** 이름 입력 오버레이 UI만 담당 */
public final class NameInputOverlay {
    private final JComponent container; // Board의 dialogPanel
    private final ScoreBoard scoreBoard;
    private final IntConsumer onDone;   // rankIndex 전달
    private final Runnable onCancel;

    public NameInputOverlay(JComponent container, ScoreBoard scoreBoard,
                            IntConsumer onDone, Runnable onCancel) {
        this.container = container;
        this.scoreBoard = scoreBoard;
        this.onDone = onDone;
        this.onCancel = onCancel;
    }

    /**
     * Displays the name input overlay.
     *
     * [UI Modification Guide]
     * - Everything except the 'addActionListener' parts can be freely modified 
     *   (layout, colors, fonts, components, etc.).
     */
    public void show(int score, GameConfig.Mode mode, GameConfig.Difficulty diff) {
        container.removeAll();

        // 🔹 게임 화면이 그대로 보이게, dialogPanel 자체는 투명 처리
        container.setOpaque(false);
        container.setBackground(new Color(0, 0, 0, 0));
        container.setLayout(new GridBagLayout());

        // === 네이비 박스 (실제 모달) ===
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBackground(new Color(25, 30, 42) );           // 어두운 네이비
        panel.setPreferredSize(new Dimension(320, 180));
        panel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(180, 180, 180), 2), // 회색 테두리
                BorderFactory.createEmptyBorder(20, 24, 20, 24)
        ));

        JLabel subtitle = new JLabel("이름을 입력하세요:");
        subtitle.setFont(new Font("Apple SD Gothic Neo, 맑은 고딕, Dialog", Font.PLAIN, 14));
        subtitle.setForeground(Color.WHITE);
        subtitle.setAlignmentX(Component.CENTER_ALIGNMENT);

        JTextField nameField = new JTextField("PLAYER", 12);
        nameField.setMaximumSize(new Dimension(220, 32));
        nameField.setAlignmentX(Component.CENTER_ALIGNMENT);
        nameField.setBackground(new Color(245, 245, 245));    // 연한 회색
        nameField.setBorder(BorderFactory.createEmptyBorder(5, 8, 5, 8));

        JButton ok = new JButton("확인");
        ok.setAlignmentX(Component.CENTER_ALIGNMENT);

        panel.add(Box.createVerticalGlue());

        panel.add(subtitle);
        panel.add(Box.createVerticalStrut(10));
        panel.add(nameField);
        panel.add(Box.createVerticalStrut(15));
        panel.add(ok);

        panel.add(Box.createVerticalGlue());

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.gridy = 0;
        container.add(panel, gbc);

        container.revalidate();
        container.repaint();

        ok.addActionListener(e -> {
            String name = nameField.getText().isBlank() ? "PLAYER" : nameField.getText();
            int rankIndex = scoreBoard.addScore(name, score, mode, diff);
            onDone.accept(rankIndex);   
        });
    }
}
