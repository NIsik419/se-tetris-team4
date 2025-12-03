package blocks;

import java.awt.Color;

import component.ColorBlindPalette;

public class SBlock extends Block {
    public SBlock() {
        super(ColorBlindPalette.S, new int[][] {
            {0, 1, 1},
            {1, 1, 0}
        });
    }
}
