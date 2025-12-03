package component;

import javax.swing.*;
import java.awt.*;

public class PausePanel extends JPanel {

    private final Runnable onResume;
    private final Runnable onRestart;
    private final Runnable onExit;
    private final JFrame parentFrame;

    public PausePanel(JFrame parent, Runnable onResume, Runnable onRestart, Runnable onExit) {
        this.parentFrame = parent;
        this.onResume = onResume;
        this.onRestart = onRestart;
        this.onExit = onExit;

        System.out.println("[DEBUG] PausePanel 생성됨. parent=" + parent);

        int width = (parent != null ? parent.getWidth() : 800);
        int height = (parent != null ? parent.getHeight() : 900);
        setOpaque(false);
        setLayout(new GridBagLayout());
        setVisible(false);
        setBounds(0, 0, width, height);

        System.out.println("[DEBUG] PausePanel 초기 설정 완료 (" + width + "x" + height + ")");

        // === 버튼 묶음 ===
        JPanel btnPanel = new JPanel();
        btnPanel.setLayout(new BoxLayout(btnPanel, BoxLayout.Y_AXIS));
        btnPanel.setOpaque(false);

        JButton continueBtn = createStitchedButton("▶ CONTINUE", new Color(80, 200, 120), () -> {
            hidePanel();
            onResume.run();
        });
        JButton restartBtn = createStitchedButton("🔄 RESTART", new Color(80, 160, 255), onRestart);
        JButton exitBtn = createStitchedButton("❌ EXIT", new Color(240, 100, 90), onExit);

        btnPanel.add(continueBtn);
        btnPanel.add(Box.createVerticalStrut(20));
        btnPanel.add(restartBtn);
        btnPanel.add(Box.createVerticalStrut(20));
        btnPanel.add(exitBtn);
        add(btnPanel, new GridBagConstraints());

        // === attach 시도 ===
        if (parent != null) {
            System.out.println("[DEBUG] parent 감지됨 → attachToParent 실행");
            attachToParent(parent);
        } else {
            System.out.println("[DEBUG] parent == null → invokeLater 예약");
            SwingUtilities.invokeLater(() -> {
                JFrame frame = (JFrame) SwingUtilities.getWindowAncestor(this);
                System.out.println("[DEBUG] invokeLater에서 frame=" + frame);
                if (frame != null)
                    attachToParent(frame);
                else
                    System.out.println("[DEBUG] invokeLater에서도 frame=null ❌");
            });
        }
    }

    /** parent의 LayeredPane에 안전하게 추가 */
    private void attachToParent(JFrame frame) {
        System.out.println("[DEBUG] attachToParent 호출됨: frame=" + frame.getTitle());
        frame.getLayeredPane().add(this, JLayeredPane.POPUP_LAYER);
        setBounds(0, 0, frame.getWidth(), frame.getHeight());
        System.out.println("[DEBUG] PausePanel LayeredPane에 추가됨. isShowing=" + isShowing());

        frame.addComponentListener(new java.awt.event.ComponentAdapter() {
            @Override
            public void componentResized(java.awt.event.ComponentEvent e) {
                setBounds(0, 0, frame.getWidth(), frame.getHeight());
                //System.out.println("[DEBUG] PausePanel 크기 갱신됨 → " + frame.getWidth() + "x" + frame.getHeight());
            }
        });
    }

    private JButton createStitchedButton(String text, Color baseColor, Runnable onClick) {
        JButton btn = new JButton(text) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                int w = getWidth(), h = getHeight();
                int arc = 20;

                // 그림자
                g2.setColor(new Color(0, 0, 0, 60));
                g2.fillRoundRect(4, 4, w - 4, h - 4, arc, arc);

                // 본체
                g2.setColor(getModel().isPressed() ? baseColor.darker() : baseColor);
                g2.fillRoundRect(0, 0, w - 4, h - 4, arc, arc);

                // 점선 테두리
                g2.setStroke(new BasicStroke(2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND, 1f,
                        new float[] { 8f, 6f }, 0f));
                g2.setColor(new Color(255, 255, 255, 180));
                g2.drawRoundRect(3, 3, w - 10, h - 10, arc - 5, arc - 5);

                // 텍스트
                FontMetrics fm = g2.getFontMetrics();
                int tx = (w - fm.stringWidth(text)) / 2;
                int ty = (h + fm.getAscent() - fm.getDescent()) / 2;
                g2.setColor(Color.WHITE);
                g2.drawString(text, tx, ty);

                g2.dispose();
            }
        };

        btn.setFocusPainted(false);
        btn.setContentAreaFilled(false);
        btn.setBorderPainted(false);
        btn.setOpaque(false);
        btn.setPreferredSize(new Dimension(220, 60));
        btn.setMaximumSize(new Dimension(220, 60));
        btn.setAlignmentX(Component.CENTER_ALIGNMENT);

        btn.addActionListener(e -> {
            if (onClick != null) {
                onClick.run();
            }
        });

        return btn;
    }

    //  일시정지 중 X 버튼 처리
    public void handleWindowClose() {
        if (parentFrame == null)
            return;

        //  다이얼로그만 표시, 아직 아무것도 실행 안 함
        int choice = JOptionPane.showOptionDialog(
                parentFrame,
                "게임을 종료하시겠습니까?",
                "종료 확인",
                JOptionPane.YES_NO_CANCEL_OPTION,
                JOptionPane.QUESTION_MESSAGE,
                null,
                new Object[] { "메인으로", "게임 종료", "취소" },
                "취소");

        if (choice == 0) {
            // 메인으로
            hidePanel();
            parentFrame.dispose(); // 프레임 먼저 닫기
            SwingUtilities.invokeLater(() -> {
                onExit.run(); // 그 다음 메인 메뉴
            });
        } else if (choice == 1) {
            // 완전 종료
            System.exit(0);
        } else {
            // 취소 (choice == 2 또는 -1) → 일시정지 상태 유지
            // 아무것도 안 함 (pausePanel은 여전히 visible)
        }
    }

    public void showPanel() {
        System.out.println("[DEBUG] showPanel 호출됨");
        setVisible(true);
        revalidate();
        repaint();
        System.out.println("[DEBUG] showPanel 완료. isShowing=" + isShowing());
    }

    public void hidePanel() {
        setVisible(false);
    }
}