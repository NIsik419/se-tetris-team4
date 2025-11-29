package versus;

import javax.swing.*;

import component.GameConfig;

public class VersusFrame extends JFrame {
    public VersusFrame(GameConfig p1Config, GameConfig p2Config, String gameRule) {
        super(makeTitle(p1Config)); 

        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);

        VersusPanel panel = new VersusPanel(p1Config, p2Config, gameRule);
        setContentPane(panel);

        pack();           
        setLocationRelativeTo(null);
        setVisible(true);

        panel.attachOverlayToFrame(this);
    }

    public VersusFrame(GameConfig p1Config, GameConfig p2Config) {
        this(p1Config, p2Config, "Normal");   
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
