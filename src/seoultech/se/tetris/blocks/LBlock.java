package seoultech.se.tetris.blocks;

import java.awt.Color;

public class LBlock extends Block {
	
	public LBlock() {
		shape = new int[][] { 
			{1, 1, 1},
			{1, 0, 0}
		};
		color = Color.ORANGE;
	}
}
