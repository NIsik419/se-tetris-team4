package blocks;

import java.awt.Color;

import component.ColorBlindPalette;

public class OBlock extends Block {
    public OBlock() {
        super(ColorBlindPalette.O, new int[][] {
            {1, 1},
            {1, 1}
        });
    }
}
