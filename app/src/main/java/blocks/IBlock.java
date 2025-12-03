package blocks;

import java.awt.Color;

import component.ColorBlindPalette;

public class IBlock extends Block {
	
	public IBlock() {
		super(ColorBlindPalette.I, new int[][] {
			{1, 1, 1, 1}
		});

	}
}
