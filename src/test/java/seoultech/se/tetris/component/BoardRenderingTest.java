package seoultech.se.tetris.component;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.Insets;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;
import seoultech.se.tetris.Difficulty;
import seoultech.se.tetris.GameMode;
import seoultech.se.tetris.game.GamePhase;
import seoultech.se.tetris.game.GameSnapshot;

class BoardRenderingTest {
    private static final int IMAGE_WIDTH = 360;
    private static final int IMAGE_HEIGHT = 640;

    @Test
    void overlappingGhostNeverChangesActiveCellsButNonOverlappingGhostRemainsVisible()
            throws Exception {
        ExistingFeatureFixture.onEdt(() -> {
            Board board = new Board();
            board.setSize(IMAGE_WIDTH, IMAGE_HEIGHT);
            GameSnapshot withGhost = crossAtBottom(18);
            GameSnapshot withoutGhost = crossAtBottom(null);

            board.showSnapshot(withGhost);
            BufferedImage ghostImage = render(board);
            board.showSnapshot(withoutGhost);
            BufferedImage activeOnlyImage = render(board);

            for (int y = 17; y <= 18; y++) {
                for (int x = 3; x <= 4; x++) {
                    assertEquals(0, differentPixelsInCell(board, ghostImage, activeOnlyImage, x, y),
                        "active P cell must not retain pixels from the overlapping ghost");
                }
            }
            assertTrue(differentPixelsInCell(board, ghostImage, activeOnlyImage, 3, 19) > 4,
                "the non-overlapping bottom ghost cell must remain visible");
            assertTrue(differentPixelsInCell(board, ghostImage, activeOnlyImage, 4, 19) > 4,
                "the non-overlapping bottom ghost cell must remain visible");
            return null;
        });
    }

    private static GameSnapshot crossAtBottom(Integer ghostY) {
        GameSnapshot.Cell purpleP = new GameSnapshot.Cell(1, "P", 0xffb04cff);
        List<List<GameSnapshot.Cell>> pieceCells = List.of(
            List.of(purpleP, purpleP), List.of(purpleP, purpleP));
        return new GameSnapshot(10, 20, GameMode.ITEM, Difficulty.NORMAL, GamePhase.ACTIVE,
            false, emptyBoard(), new GameSnapshot.Piece("Cross", pieceCells), null,
            3, 17, ghostY, 0, 0, 1, 1_000_000_000L, List.of(), null);
    }

    private static List<List<GameSnapshot.Cell>> emptyBoard() {
        ArrayList<List<GameSnapshot.Cell>> rows = new ArrayList<>(20);
        for (int row = 0; row < 20; row++) {
            rows.add(new ArrayList<>(Collections.nCopies(10, null)));
        }
        return rows;
    }

    private static BufferedImage render(Board board) {
        BufferedImage image = new BufferedImage(IMAGE_WIDTH, IMAGE_HEIGHT,
            BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();
        try {
            board.paint(graphics);
        } finally {
            graphics.dispose();
        }
        return image;
    }

    private static int differentPixelsInCell(Board board, BufferedImage first,
            BufferedImage second, int boardX, int boardY) {
        FontMetrics metrics = board.getFontMetrics(new Font("Monospaced", Font.BOLD, 100));
        Insets insets = board.getInsets();
        int lineHeight = (int) (metrics.getHeight() * 0.8);
        double scaleX = (double) (board.getWidth() - insets.left - insets.right)
            / metrics.stringWidth(" ".repeat(12));
        double scaleY = (double) (board.getHeight() - insets.top - insets.bottom)
            / (lineHeight * 22);
        int textColumn = boardX + 1;
        int textRow = boardY + 1;
        int left = insets.left
            + (int) Math.floor(textColumn * metrics.stringWidth(" ") * scaleX);
        int right = insets.left
            + (int) Math.ceil((textColumn + 1) * metrics.stringWidth(" ") * scaleX);
        int top = insets.top + (int) Math.floor(textRow * lineHeight * scaleY);
        int bottom = insets.top + (int) Math.ceil((textRow + 1) * lineHeight * scaleY);
        int different = 0;
        for (int y = Math.max(0, top); y < Math.min(first.getHeight(), bottom); y++) {
            for (int x = Math.max(0, left); x < Math.min(first.getWidth(), right); x++) {
                if (first.getRGB(x, y) != second.getRGB(x, y)) different++;
            }
        }
        return different;
    }
}
