package blocks;

import java.awt.Color;
import component.ColorBlindPalette;

public class TBlock extends Block {
    public TBlock() {
        super(ColorBlindPalette.T, new int[][] {
            {0, 1, 0},
            {1, 1, 1}
        });
    }
}
