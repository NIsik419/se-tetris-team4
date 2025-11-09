package versus;

import javax.swing.*;

public class VersusFrame extends JFrame {
    public VersusFrame() {
        super("TETRIS - Versus");
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        setContentPane(new VersusPanel());   // 테스트 패널
        setSize(1500, 900);
        setLocationRelativeTo(null);
        setVisible(true);
    }
}
