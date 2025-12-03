package blocks;

import java.awt.Color;

import component.ColorBlindPalette;

public class JBlock extends Block {
    public JBlock() {
        super(ColorBlindPalette.J, new int[][] {
            {1, 0, 0},
            {1, 1, 1}
        });
    }
}
