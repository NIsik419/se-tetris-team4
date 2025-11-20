package versus;

import javax.swing.*;

public class VersusFrame extends JFrame {
    public VersusFrame(boolean itemMode) {
        super(itemMode ? "TETRIS - Item Battle" : "TETRIS - Versus");
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        setContentPane(new VersusPanel(itemMode));  
        setSize(1500, 900);
        setLocationRelativeTo(null);
        setVisible(true);
    }
}
