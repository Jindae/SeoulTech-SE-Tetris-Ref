package seoultech.se.tetris.blocks;

import java.awt.Color;

public class JBlock extends Block {
	
	public JBlock() {
		shape = new int[][] { 
				{1, 1, 1},
				{0, 0, 1}
		};
		color = Color.BLUE;
	}
}
