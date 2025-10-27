package component.score;

import javax.swing.*;
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
    public void show(int score) {
        container.removeAll();
        container.setLayout(new BorderLayout(8, 8));

        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBackground(new Color(255, 255, 255, 230));
        panel.setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));

        JLabel subtitle = new JLabel("이름을 입력하세요:");
        subtitle.setFont(new Font("Arial", Font.PLAIN, 14));
        subtitle.setAlignmentX(Component.CENTER_ALIGNMENT);

        JTextField nameField = new JTextField("PLAYER", 12);
        nameField.setMaximumSize(new Dimension(200, 30));
        nameField.setAlignmentX(Component.CENTER_ALIGNMENT);

        JButton ok = new JButton("확인");
        JButton cancel = new JButton("취소");
        ok.setAlignmentX(Component.CENTER_ALIGNMENT);
        cancel.setAlignmentX(Component.CENTER_ALIGNMENT);

        panel.add(subtitle);
        panel.add(Box.createVerticalStrut(5));
        panel.add(nameField);
        panel.add(Box.createVerticalStrut(10));
        panel.add(ok);
        panel.add(Box.createVerticalStrut(5));
        panel.add(cancel);

        container.add(panel, BorderLayout.CENTER);
        container.revalidate();
        container.repaint();

        ok.addActionListener(e -> {
            String name = nameField.getText().isBlank() ? "PLAYER" : nameField.getText();
            int rankIndex = scoreBoard.addScore(name, score);
            onDone.accept(rankIndex);
        });
        cancel.addActionListener(e -> onCancel.run());
    }
}
