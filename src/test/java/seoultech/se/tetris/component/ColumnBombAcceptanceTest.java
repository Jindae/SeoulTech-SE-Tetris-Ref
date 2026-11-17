package seoultech.se.tetris.component;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.image.BufferedImage;
import java.lang.reflect.Constructor;
import java.nio.file.Path;
import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import seoultech.se.tetris.Difficulty;
import seoultech.se.tetris.GameMode;
import seoultech.se.tetris.blocks.AnchorBlock;
import seoultech.se.tetris.blocks.Block;
import seoultech.se.tetris.blocks.BombBlock;
import seoultech.se.tetris.blocks.ColumnBlock;
import seoultech.se.tetris.blocks.OBlock;
import seoultech.se.tetris.game.GameCommand;
import seoultech.se.tetris.game.GameConfig;
import seoultech.se.tetris.game.GameEngineFixture;
import seoultech.se.tetris.game.GameSnapshot;
import seoultech.se.tetris.game.GameUpdate;

/** R2-13 acceptance boundaries for the original column and bomb items. */
class ColumnBombAcceptanceTest {
    private static final GameConfig ITEM = new GameConfig(GameMode.ITEM, Difficulty.NORMAL);
    private final String originalDataDir = System.getProperty("tetris.dataDir");
    private String originalPalette;

    @TempDir Path temporary;

    @BeforeEach
    void isolatePersistentSettings() {
        originalPalette = Maincontainer.colorPalette;
        System.setProperty("tetris.dataDir", temporary.toString());
    }

    @AfterEach
    void restoreGlobalSettings() {
        Maincontainer.colorPalette = originalPalette;
        if (originalDataDir == null) System.clearProperty("tetris.dataDir");
        else System.setProperty("tetris.dataDir", originalDataDir);
    }

    @Test
    @DisplayName("R2-13: C가 있던 원래 열 전체만 격자·문자에서 삭제하고 점수와 줄 수는 유지")
    void columnClearsOnlyItsOriginalMarkedColumn() throws Exception {
        ColumnBlock item = columnBlock(0, 0);
        int effectColumn = 3 + item.getItemX();
        int outside = effectColumn + 1;
        GameEngineFixture fixture = new GameEngineFixture(ITEM);
        fixture.seed(item, new OBlock(), 3, 10);
        fixture.fixed(effectColumn, 2, "T", 0xff00ffff, 0);
        fixture.fixed(effectColumn, 17);
        fixture.fixed(outside, 2, "S", 0xffff00ff, 0);
        fixture.fixed(3, 11);

        GameSnapshot result = resolveColumn(fixture).snapshot();

        for (int row = 0; row < 20; row++) assertNull(result.fixedCells().get(row).get(effectColumn));
        assertEquals("S", result.fixedCells().get(18).get(outside).text());
        assertEquals("O", result.fixedCells().get(19).get(outside).text());
        assertEquals(0, result.score());
        assertEquals(0, result.clearedLines());
    }

    @Test
    @DisplayName("R2-13: C 블록 하드드롭은 실제 낙하 점수만 주고 다음 블록으로 한 번 전환")
    void columnHardDropAppliesEffectAndAdvancesOnce() throws Exception {
        ColumnBlock item = columnBlock(0, 0);
        int effectColumn = 3 + item.getItemX();
        GameEngineFixture fixture = new GameEngineFixture(ITEM, new OBlock());
        fixture.seed(item, new OBlock(), 3, 0);
        GameUpdate locked = resolveColumn(fixture);
        GameSnapshot result = fixture.engine.continueAfterLock(
            locked.lockResolution().orElseThrow().turnId()).snapshot();
        assertEquals(19, result.score());
        assertEquals(0, result.clearedLines());
        assertNull(result.fixedCells().get(19).get(effectColumn));
        assertEquals("O", result.current().kind());
        assertEquals(3, result.x());
        assertEquals(0, result.y());
    }

    @Test
    @DisplayName("R2-13: B 아이템은 회전하지 않는 3x4 형상에 정확히 열 칸을 점유")
    void bombHasSpecifiedUnrotatedShape() {
        BombBlock item = new BombBlock();
        assertEquals(3, item.width());
        assertEquals(4, item.height());
        assertEquals(10, occupiedCells(item));
        item.rotate();
        assertEquals(3, item.width());
        assertEquals(4, item.height());
        assertEquals(10, occupiedCells(item));
    }

    @ParameterizedTest(name = "x={0}, y={1}")
    @CsvSource({"0, 0, 5, 4, 6, 6", "7, 16, 14, 5, 13, 4"})
    @DisplayName("R2-13: B 효과는 모서리에서 형상 경계 밖 두 칸까지 잘라내고 바깥 격자·문자는 보존")
    void bombClearsClippedTwoCellMarginAtCorners(int x, int y, int insideRow,
            int insideColumn, int outsideRow, int outsideColumn) {
        GameEngineFixture fixture = new GameEngineFixture(ITEM);
        fixture.seed(new BombBlock(), new OBlock(), x, y);
        fixture.fixed(insideColumn, insideRow, "T", 0xffffa500, 0);
        fixture.fixed(outsideColumn, outsideRow, "S", 0xffff00ff, 0);
        if (y == 0) fixture.fixed(x, 4);

        GameSnapshot result = fixture.engine.handle(GameCommand.HARD_DROP).snapshot();

        assertNull(result.fixedCells().get(insideRow).get(insideColumn));
        assertEquals("S", result.fixedCells().get(19).get(outsideColumn).text());
        assertEquals(0, result.score());
        assertEquals(0, result.clearedLines());
    }

    @ParameterizedTest(name = "supportRow={0}")
    @CsvSource({"-1, 16, 14, 19", "10, 6, 4, 11"})
    @DisplayName("R2-13: B 블록은 바닥·쌓인 블록에서 효과를 한 번 적용하고 다음 블록으로 전환")
    void bombHardDropAppliesOneEffectAndAdvancesOnce(int supportRow, int expectedScore,
            int effectStartRow, int effectEndRow) {
        GameEngineFixture fixture = new GameEngineFixture(ITEM, new OBlock());
        fixture.seed(new BombBlock(), new OBlock(), 3, 0);
        fixture.fixed(0, 0, "S", 0xffff00ff, 0);
        if (supportRow >= 0) fixture.fixed(4, supportRow);

        GameUpdate locked = fixture.engine.handle(GameCommand.HARD_DROP);
        GameSnapshot result = fixture.engine.continueAfterLock(
            locked.lockResolution().orElseThrow().turnId()).snapshot();

        assertEquals(expectedScore, result.score());
        assertEquals("S", result.fixedCells().get(19).get(0).text());
        for (int row = effectStartRow; row <= effectEndRow; row++) {
            for (int column = 1; column < 8; column++) assertNull(result.fixedCells().get(row).get(column));
        }
        assertEquals("O", result.current().kind());
    }

    @Test
    @DisplayName("R2-13: B 범위 밖 완성 행은 일반 100점·1줄만 반영하고 정상 압축")
    void bombHardDropScoresOnlyOrdinaryCompletedRowsOutsideItsEffect() {
        GameEngineFixture fixture = new GameEngineFixture(ITEM, new OBlock());
        fixture.seed(new BombBlock(), new OBlock(), 3, 11);
        for (int column = 0; column < 10; column++) fixture.fixed(column, 10);
        fixture.fixed(0, 1, "S", 0xffff00ff, 0);
        GameUpdate locked = fixture.engine.handle(GameCommand.HARD_DROP);
        GameSnapshot result = fixture.engine.continueAfterLock(
            locked.lockResolution().orElseThrow().turnId()).snapshot();
        assertEquals(105, result.score());
        assertEquals(1, result.clearedLines());
        assertEquals("S", result.fixedCells().get(19).get(0).text());
        assertEquals("O", result.current().kind());
    }

    @Test
    @DisplayName("R2-13: 회전 불가 B 블록은 벽·바닥에서도 반복 회전 입력으로 위치가 바뀌지 않음")
    void bombRotationInputDoesNotKickAtWallOrFloor() {
        GameEngineFixture fixture = new GameEngineFixture(ITEM);
        fixture.seed(new BombBlock(), new OBlock(), 7, 16);
        for (int attempt = 0; attempt < 4; attempt++) {
            GameSnapshot snapshot = fixture.engine.handle(GameCommand.ROTATE).snapshot();
            assertEquals(7, snapshot.x());
            assertEquals(16, snapshot.y());
        }
    }

    @Test
    @DisplayName("R2-13: 모든 팔레트에서 C와 B 마커를 실제 화면으로 식별")
    void itemMarkersRemainVisibleInEveryPalette() throws Exception {
        for (String palette : List.of("Normal", "Protanopia", "Deuteranopia", "Tritanopia")) {
            Maincontainer.colorPalette = palette;
            ExistingFeatureFixture.onEdt(() -> {
                try (ExistingFeatureFixture fixture = new ExistingFeatureFixture(Difficulty.NORMAL)) {
                    ColumnBlock column = columnBlock(0, 0);
                    fixture.prepare(column, new OBlock(), new int[20][10], 3, 5);
                    BufferedImage columnImage = fixture.render(palette + "-column.png");
                    assertTrue(fixture.contrastingPixelsInCell(columnImage,
                        4 + column.getItemX(), 6 + column.getItemY()) > 4, palette);
                    BombBlock bomb = new BombBlock();
                    fixture.prepare(bomb, new OBlock(), new int[20][10], 3, 5);
                    BufferedImage bombImage = fixture.render(palette + "-bomb.png");
                    assertTrue(fixture.contrastingPixelsInCell(bombImage, 5, 8) > 4, palette);
                }
                return null;
            });
        }
    }

    @ParameterizedTest
    @CsvSource({"B", "C"})
    @DisplayName("B/C 삭제 후 각 열의 칸은 순서·문자·색을 유지하며 바닥까지 정착")
    void remainingCellsSettleWithTheirAppearance(String kind) throws Exception {
        Block item = kind.equals("B") ? new BombBlock() : columnBlock(0, 0);
        GameEngineFixture fixture = new GameEngineFixture(ITEM);
        fixture.seed(item, new OBlock(), 3, 16);
        fixture.fixed(0, 2, "S", 0xffff00ff, 0);
        fixture.fixed(0, 7, "T", 0xff00ffff, 0);
        if (kind.equals("C")) fixture.fixed(3, 17);
        GameSnapshot result = kind.equals("B")
            ? fixture.engine.handle(GameCommand.HARD_DROP).snapshot()
            : resolveColumn(fixture).snapshot();
        assertEquals("S", result.fixedCells().get(18).get(0).text());
        assertEquals(0xffff00ff, result.fixedCells().get(18).get(0).argb());
        assertEquals("T", result.fixedCells().get(19).get(0).text());
        assertEquals(0xff00ffff, result.fixedCells().get(19).get(0).argb());
        for (int row = 0; row < 18; row++) assertNull(result.fixedCells().get(row).get(0));
        assertEquals(0, result.score());
    }

    @Test
    @DisplayName("B 정착으로 새로 완성된 두 줄은 300점·2줄·아이템 공급 1회 반영")
    void settlingCompletesRowsAndAwardsSupplyOnce() {
        GameEngineFixture fixture = new GameEngineFixture(ITEM, new AnchorBlock());
        fixture.seed(new BombBlock(), new OBlock(), 3, 11);
        fixture.progress(0, 8, 1);
        for (int column = 0; column < 10; column++) {
            fixture.fixed(column, column % 3);
            fixture.fixed(column, 5 + column % 3);
        }
        GameUpdate locked = fixture.engine.handle(GameCommand.HARD_DROP);
        GameSnapshot result = fixture.engine.continueAfterLock(
            locked.lockResolution().orElseThrow().turnId()).snapshot();
        assertEquals(305, result.score());
        assertEquals(10, result.clearedLines());
        assertEquals(2, result.level());
        assertEquals("Anchor", result.next().kind());
        assertEquals(1, fixture.source.itemCalls);
    }

    private static GameUpdate resolveColumn(GameEngineFixture fixture) {
        GameUpdate update = fixture.engine.handle(GameCommand.HARD_DROP);
        for (int frame = 0; update.lockResolution().isEmpty() && frame < 12; frame++) {
            update = fixture.engine.advance(50_000_000L);
        }
        assertTrue(update.lockResolution().isPresent(), "column effect must resolve");
        return update;
    }

    private static ColumnBlock columnBlock(int type, int attachment) throws Exception {
        Constructor<ColumnBlock> constructor = ColumnBlock.class.getDeclaredConstructor(Random.class);
        constructor.setAccessible(true);
        return constructor.newInstance(new SequenceRandom(type, attachment));
    }

    private static int occupiedCells(Block block) {
        int occupied = 0;
        for (int row = 0; row < block.height(); row++) {
            for (int column = 0; column < block.width(); column++) {
                if (block.getShape(column, row) != 0) occupied++;
            }
        }
        return occupied;
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
            return Math.floorMod(values[index++], bound);
        }
    }
}
