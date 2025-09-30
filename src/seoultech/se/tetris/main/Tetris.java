package seoultech.se.tetris.main;

import javax.swing.SwingUtilities;

import seoultech.se.tetris.component.Board;
import seoultech.se.tetris.ui.AppFrame;

public class Tetris {

	public static void main(String[] args) {
		Board main = new Board();
		main.setSize(400, 500);
		main.setVisible(true);

		SwingUtilities.invokeLater(() -> new AppFrame().setVisible(true));
	}
}