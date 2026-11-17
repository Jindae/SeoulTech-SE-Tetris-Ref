package seoultech.se.tetris.component;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.image.BufferedImage;
import java.lang.reflect.Constructor;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Random;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import seoultech.se.tetris.Difficulty;
import seoultech.se.tetris.GameMode;
import seoultech.se.tetris.blocks.OBlock;
import seoultech.se.tetris.blocks.RowBlock;
import seoultech.se.tetris.game.GameCommand;
import seoultech.se.tetris.game.GameConfig;
import seoultech.se.tetris.game.GameEngineFixture;
import seoultech.se.tetris.game.GameSnapshot;
import seoultech.se.tetris.game.GameUpdate;

/** R2-11 acceptance boundaries not already covered by the effect timing suite. */
class LineItemAcceptanceTest {
    private static final GameConfig ITEM = new GameConfig(GameMode.ITEM, Difficulty.NORMAL);
    private final String originalDataDir = System.getProperty("tetris.dataDir");
    private final String originalPalette = Maincontainer.colorPalette;

    @TempDir Path temporary;

    @AfterEach
    void restoreGlobalSettings() {
        if (originalDataDir == null) System.clearProperty("tetris.dataDir");
        else System.setProperty("tetris.dataDir", originalDataDir);
        Maincontainer.colorPalette = originalPalette;
    }

    @Test
    @DisplayName("R2-11: L은 미완성 최상단·최하단 행도 삭제")
    void lineItemClearsIncompleteTopAndBottomRows() throws Exception {
        GameEngineFixture top = new GameEngineFixture(ITEM);
        RowBlock topItem = rowItem(0);
        top.seed(topItem, new OBlock(), 3, 0);
        top.fixed(9, 0);
        top.fixed(3, 1);
        GameSnapshot topResult = resolve(top).snapshot();
        assertTrue(topResult.fixedCells().get(0).stream().allMatch(java.util.Objects::isNull));
        assertEquals(100, topResult.score());
        assertEquals(1, topResult.clearedLines());

        GameEngineFixture bottom = new GameEngineFixture(ITEM);
        bottom.seed(rowItem(3), new OBlock(), 3, 19);
        bottom.fixed(9, 18);
        bottom.fixed(0, 19);
        GameSnapshot bottomResult = resolve(bottom).snapshot();
        assertTrue(bottomResult.fixedCells().get(19).get(9) != null);
        assertNull(bottomResult.fixedCells().get(19).get(0));
        assertEquals(100, bottomResult.score());
        assertEquals(1, bottomResult.clearedLines());
    }

    @Test
    @DisplayName("R2-11: L행과 비연속 완성 행을 한 번에 압축하고 문자 좌표도 이동")
    void nonAdjacentRowsCompressGridAndTextTogether() throws Exception {
        GameEngineFixture fixture = new GameEngineFixture(ITEM);
        fixture.seed(rowItem(2), new OBlock(), 3, 19);
        fixture.fixed(0, 2, "S", 0xffff00ff, 0);
        for (int column = 0; column < 10; column++) {
            fixture.fixed(column, 3);
            fixture.fixed(column, 10);
        }
        fixture.fixed(0, 19);

        GameSnapshot result = resolve(fixture).snapshot();

        assertEquals(600, result.score());
        assertEquals(3, result.clearedLines());
        assertEquals("S", result.fixedCells().get(5).get(0).text());
        assertEquals(0xffff00ff, result.fixedCells().get(5).get(0).argb());
        for (int row = 0; row < 3; row++) {
            assertTrue(result.fixedCells().get(row).stream().allMatch(java.util.Objects::isNull));
        }
    }

    @Test
    @DisplayName("R2-11: 모든 팔레트에서 L 문자와 일반 삭제 행 강조를 화면으로 구분")
    void markerAndClearHighlightRemainVisibleInEveryPalette() throws Exception {
        System.setProperty("tetris.dataDir", temporary.toString());
        for (String palette : new String[] {"Normal", "Protanopia", "Deuteranopia", "Tritanopia"}) {
            Maincontainer.colorPalette = palette;
            ExistingFeatureFixture.onEdt(() -> {
                RowBlock item = rowItem(1);
                try (ExistingFeatureFixture fixture = new ExistingFeatureFixture(Difficulty.NORMAL)) {
                    fixture.prepare(item, new OBlock(), new int[20][10], 3, 5);
                    BufferedImage marker = fixture.render(palette + "-L.png");
                    assertTrue(fixture.contrastingPixelsInCell(marker,
                        4 + item.getItemX(), 6 + item.getItemY()) > 4, palette);
                }

                try (ExistingFeatureFixture fixture = new ExistingFeatureFixture(Difficulty.NORMAL)) {
                    int[][] grid = new int[20][10];
                    Arrays.fill(grid[19], 1);
                    grid[19][3] = 0;
                    grid[19][4] = 0;
                    fixture.prepare(new OBlock(), new OBlock(), grid, 3, 18);
                    fixture.engineFixture.engine.handle(GameCommand.HARD_DROP);
                    fixture.game.controller().refreshSnapshot();
                    assertEquals(java.util.List.of(19), fixture.game.controller().snapshot().highlightedRows());
                    BufferedImage withHighlight = fixture.render(palette + "-clear.png");
                    fixture.engineFixture.engine.advance(210_000_000L);
                    fixture.game.controller().refreshSnapshot();
                    BufferedImage withoutHighlight = fixture.render(null);
                    assertTrue(differentPixels(withHighlight, withoutHighlight) > 500, palette);
                }
                return null;
            });
        }
    }

    private static GameUpdate resolve(GameEngineFixture fixture) {
        GameUpdate update = fixture.engine.handle(GameCommand.HARD_DROP);
        for (int frame = 0; update.lockResolution().isEmpty() && frame < 7; frame++) {
            update = fixture.engine.advance(50_000_000L);
        }
        assertTrue(update.lockResolution().isPresent(), "row effect must resolve");
        return update;
    }

    private static RowBlock rowItem(int attachment) throws Exception {
        Constructor<RowBlock> constructor = RowBlock.class.getDeclaredConstructor(Random.class);
        constructor.setAccessible(true);
        return constructor.newInstance(new SequenceRandom(0, attachment));
    }

    private static int differentPixels(BufferedImage first, BufferedImage second) {
        int count = 0;
        for (int y = 0; y < first.getHeight(); y++) {
            for (int x = 0; x < first.getWidth(); x++) {
                if (first.getRGB(x, y) != second.getRGB(x, y)) count++;
            }
        }
        return count;
    }

    private static final class SequenceRandom extends Random {
        private static final long serialVersionUID = 1L;
        private final int[] values;
        private int index;

        SequenceRandom(int... values) {
            this.values = values;
        }

        @Override
        public int nextInt(int bound) {
            int value = values[index++];
            if (value < 0 || value >= bound) throw new AssertionError("value outside bound: " + value);
            return value;
        }
    }
}
