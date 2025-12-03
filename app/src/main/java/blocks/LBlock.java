package blocks;

import java.awt.Color;
import component.ColorBlindPalette;

public class LBlock extends Block {
    public LBlock() {
        super(ColorBlindPalette.L, new int[][] {
            {0, 0, 1},
            {1, 1, 1}
        });
    }
}
