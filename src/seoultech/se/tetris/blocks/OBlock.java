package seoultech.se.tetris.blocks;

import java.awt.Color;

public class OBlock extends Block {

	public OBlock() {
		shape = new int[][] { 
			{1, 1}, 
			{1, 1}
		};
		color = Color.YELLOW;
	}
}
