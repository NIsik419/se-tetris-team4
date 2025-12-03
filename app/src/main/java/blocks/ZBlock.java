package blocks;

import java.awt.Color;

import component.ColorBlindPalette;

public class ZBlock extends Block {
    public ZBlock() {
        super(ColorBlindPalette.Z, new int[][] {
            {1, 1, 0},
            {0, 1, 1}
        });
    }
}
