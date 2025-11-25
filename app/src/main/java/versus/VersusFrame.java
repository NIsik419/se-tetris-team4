package versus;

import javax.swing.*;

import component.GameConfig;

public class VersusFrame extends JFrame {
    public VersusFrame(GameConfig p1Config, GameConfig p2Config) {
        super(makeTitle(p1Config)); 

        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        setContentPane(new VersusPanel(p1Config, p2Config));
        setSize(1500, 900);
        setLocationRelativeTo(null);
        setVisible(true);
    }

    private static String makeTitle(GameConfig config) {
        return switch (config.mode()) {
            case TIME_ATTACK -> "TETRIS - Time Attack Battle";
            case ITEM        -> "TETRIS - Item Battle";
            case AI          -> "TETRIS - AI Battle";
            default          -> "TETRIS - Versus";
        };
    }
}
