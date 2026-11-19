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

/** Renders immutable engine snapshots and contains no game rules or timers. */
public final class Board extends JComponent {
    private static final long serialVersionUID = 2434035659171694595L;
    private static final int BORDER_COLUMNS = 12;
    private static final int BORDER_ROWS = 22;
    private static final Color GHOST_COLOR = Color.DARK_GRAY;

    private transient GameSnapshot snapshot;

    public Board() {
        setBackground(changeColor(Color.BLACK));
        CompoundBorder border = BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(changeColor(Color.GRAY), 10),
            BorderFactory.createLineBorder(changeColor(Color.DARK_GRAY), 5));
        setBorder(border);
    }

    void showSnapshot(GameSnapshot snapshot) {
        this.snapshot = java.util.Objects.requireNonNull(snapshot, "snapshot");
        repaint();
    }

    GameSnapshot snapshot() {
        return snapshot;
    }

    @Override
    protected void paintComponent(Graphics graphics) {
        super.paintComponent(graphics);
        Graphics2D g = (Graphics2D) graphics.create();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setColor(changeColor(Color.BLACK));
            g.fillRect(0, 0, getWidth(), getHeight());
            if (snapshot == null) return;

            Insets insets = getInsets();
            int availableWidth = getWidth() - insets.left - insets.right;
            int availableHeight = getHeight() - insets.top - insets.bottom;
            if (availableWidth <= 0 || availableHeight <= 0) return;

            Font font = new Font("Monospaced", Font.BOLD, 100);
            FontMetrics metrics = g.getFontMetrics(font);
            int lineHeight = (int) (metrics.getHeight() * 0.8);
            double scaleX = (double) availableWidth
                / metrics.stringWidth(" ".repeat(BORDER_COLUMNS));
            double scaleY = (double) availableHeight / (lineHeight * BORDER_ROWS);
            AffineTransform original = g.getTransform();
            g.setFont(font);
            g.translate(insets.left, insets.top);
            g.scale(scaleX, scaleY);

            drawBorder(g, metrics, lineHeight);
            drawFixedCells(g, metrics, lineHeight);
            drawGhost(g, metrics, lineHeight);
            drawCurrent(g, metrics, lineHeight);
            drawEffect(g, metrics, lineHeight);
            drawHighlights(g, metrics, lineHeight);
            g.setTransform(original);
        } finally {
            g.dispose();
        }
    }

    private void drawBorder(Graphics2D g, FontMetrics metrics, int lineHeight) {
        g.setColor(changeColor(Color.WHITE));
        String horizontal = "X".repeat(BORDER_COLUMNS);
        drawText(g, metrics, horizontal, 0, 0, lineHeight);
        for (int row = 0; row < snapshot.height(); row++) {
            drawText(g, metrics, "X", 0, row + 1, lineHeight);
            drawText(g, metrics, "X", snapshot.width() + 1, row + 1, lineHeight);
        }
        drawText(g, metrics, horizontal, 0, snapshot.height() + 1, lineHeight);
    }

    private void drawFixedCells(Graphics2D g, FontMetrics metrics, int lineHeight) {
        List<List<GameSnapshot.Cell>> rows = snapshot.fixedCells();
        for (int row = 0; row < rows.size(); row++) {
            for (int column = 0; column < rows.get(row).size(); column++) {
                drawCell(g, metrics, rows.get(row).get(column), column, row, lineHeight);
            }
        }
    }

    private void drawGhost(Graphics2D g, FontMetrics metrics, int lineHeight) {
        if (snapshot.current() == null || snapshot.x() == null || snapshot.ghostY() == null) return;
        List<List<GameSnapshot.Cell>> cells = snapshot.current().cells();
        for (int row = 0; row < cells.size(); row++) {
            for (int column = 0; column < cells.get(row).size(); column++) {
                if (cells.get(row).get(column) == null) continue;
                int boardX = snapshot.x() + column;
                int boardY = snapshot.ghostY() + row;
                if (cellOccupied(boardX, boardY) || activeCellOccupied(boardX, boardY)) continue;
                g.setColor(changeColor(GHOST_COLOR));
                drawText(g, metrics, "O", boardX + 1, boardY + 1, lineHeight);
            }
        }
    }

    private void drawCurrent(Graphics2D g, FontMetrics metrics, int lineHeight) {
        if (snapshot.current() == null || snapshot.x() == null || snapshot.y() == null) return;
        List<List<GameSnapshot.Cell>> cells = snapshot.current().cells();
        for (int row = 0; row < cells.size(); row++) {
            for (int column = 0; column < cells.get(row).size(); column++) {
                drawCell(g, metrics, cells.get(row).get(column), snapshot.x() + column,
                    snapshot.y() + row, lineHeight);
            }
        }
    }

    private void drawEffect(Graphics2D g, FontMetrics metrics, int lineHeight) {
        if (snapshot.effect() == null) return;
        for (GameSnapshot.OverlayCell cell : snapshot.effect().overlayCells()) {
            int x = metrics.stringWidth(" ".repeat(cell.x() + 1));
            int y = (cell.y() + 1) * lineHeight;
            g.setColor(changeColor(Color.BLACK));
            g.fillRect(x, y, metrics.stringWidth(" "), lineHeight);
            if (!cell.text().isEmpty()) {
                g.setColor(changeColor(new Color(cell.argb(), true)));
                drawText(g, metrics, cell.text(), cell.x() + 1, cell.y() + 1, lineHeight);
            }
        }
    }

    private void drawHighlights(Graphics2D g, FontMetrics metrics, int lineHeight) {
        if (snapshot.highlightedRows().isEmpty()) return;
        g.setColor(new Color(255, 255, 255, 160));
        int left = metrics.stringWidth(" ");
        int width = metrics.stringWidth(" ".repeat(snapshot.width()));
        for (int row : snapshot.highlightedRows()) {
            g.fillRect(left, (row + 1) * lineHeight, width, lineHeight);
        }
    }

    private void drawCell(Graphics2D g, FontMetrics metrics, GameSnapshot.Cell cell,
            int boardX, int boardY, int lineHeight) {
        if (cell == null || boardX < 0 || boardX >= snapshot.width()
                || boardY < 0 || boardY >= snapshot.height()) return;
        g.setColor(changeColor(new Color(cell.argb(), true)));
        drawText(g, metrics, cell.text(), boardX + 1, boardY + 1, lineHeight);
    }

    private boolean cellOccupied(int x, int y) {
        return y >= 0 && y < snapshot.height() && x >= 0 && x < snapshot.width()
            && snapshot.fixedCells().get(y).get(x) != null;
    }

    private boolean activeCellOccupied(int x, int y) {
        if (snapshot.current() == null || snapshot.x() == null || snapshot.y() == null) return false;
        int localX = x - snapshot.x();
        int localY = y - snapshot.y();
        List<List<GameSnapshot.Cell>> cells = snapshot.current().cells();
        return localY >= 0 && localY < cells.size()
            && localX >= 0 && localX < cells.get(localY).size()
            && cells.get(localY).get(localX) != null;
    }

    private static void drawText(Graphics2D g, FontMetrics metrics, String text,
            int column, int row, int lineHeight) {
        g.drawString(text, metrics.stringWidth(" ".repeat(column)),
            metrics.getAscent() + row * lineHeight);
    }
}
