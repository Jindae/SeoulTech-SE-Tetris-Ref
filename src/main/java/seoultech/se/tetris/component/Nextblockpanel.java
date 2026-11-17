package seoultech.se.tetris.component;

import static seoultech.se.tetris.component.Maincontainer.changeColor;

import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Insets;
import java.awt.RenderingHints;
import java.awt.geom.AffineTransform;
import java.util.List;
import javax.swing.BorderFactory;
import javax.swing.JComponent;
import javax.swing.border.CompoundBorder;
import seoultech.se.tetris.game.GameSnapshot;

/** Renders the immutable next-piece copy published by the engine. */
public final class Nextblockpanel extends JComponent {
    private static final long serialVersionUID = 1L;
    private static final int GRID_SIZE = 6;

    private transient GameSnapshot.Piece piece;

    public Nextblockpanel() {
        setBackground(changeColor(Color.BLACK));
        CompoundBorder border = BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(changeColor(Color.GRAY), 10),
            BorderFactory.createLineBorder(changeColor(Color.DARK_GRAY), 5));
        setBorder(border);
    }

    void drawNextBoard(GameSnapshot.Piece piece) {
        this.piece = piece;
        repaint();
    }

    GameSnapshot.Piece piece() {
        return piece;
    }

    @Override
    protected void paintComponent(Graphics graphics) {
        super.paintComponent(graphics);
        Graphics2D g = (Graphics2D) graphics.create();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setColor(changeColor(Color.BLACK));
            g.fillRect(0, 0, getWidth(), getHeight());
            if (piece == null) return;

            Insets insets = getInsets();
            int availableWidth = getWidth() - insets.left - insets.right;
            int availableHeight = getHeight() - insets.top - insets.bottom;
            if (availableWidth <= 0 || availableHeight <= 0) return;

            Font font = new Font("Monospaced", Font.BOLD, 100);
            FontMetrics metrics = g.getFontMetrics(font);
            int lineHeight = (int) (metrics.getHeight() * 0.8);
            double scaleX = (double) availableWidth / metrics.stringWidth(" ".repeat(GRID_SIZE));
            double scaleY = (double) availableHeight / (lineHeight * GRID_SIZE);
            AffineTransform original = g.getTransform();
            g.setFont(font);
            g.translate(insets.left, insets.top);
            g.scale(scaleX, scaleY);
            drawPiece(g, metrics, lineHeight);
            g.setTransform(original);
        } finally {
            g.dispose();
        }
    }

    private void drawPiece(Graphics2D g, FontMetrics metrics, int lineHeight) {
        List<List<GameSnapshot.Cell>> cells = piece.cells();
        for (int row = 0; row < cells.size(); row++) {
            for (int column = 0; column < cells.get(row).size(); column++) {
                GameSnapshot.Cell cell = cells.get(row).get(column);
                if (cell == null) continue;
                g.setColor(changeColor(new Color(cell.argb(), true)));
                int x = metrics.stringWidth(" ".repeat(column + 1));
                int y = metrics.getAscent() + (row + 2) * lineHeight;
                g.drawString(cell.text(), x, y);
            }
        }
    }
}
