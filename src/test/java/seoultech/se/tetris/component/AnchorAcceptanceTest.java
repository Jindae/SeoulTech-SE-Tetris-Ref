package seoultech.se.tetris.component;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import seoultech.se.tetris.Difficulty;
import seoultech.se.tetris.GameMode;
import seoultech.se.tetris.blocks.AnchorBlock;
import seoultech.se.tetris.blocks.Block;
import seoultech.se.tetris.blocks.BombBlock;
import seoultech.se.tetris.blocks.ColumnBlock;
import seoultech.se.tetris.blocks.OBlock;
import seoultech.se.tetris.blocks.RowBlock;
import seoultech.se.tetris.game.GameCommand;
import seoultech.se.tetris.game.GameConfig;
import seoultech.se.tetris.game.GameEngineFixture;
import seoultech.se.tetris.game.GameSnapshot;
import seoultech.se.tetris.game.GameUpdate;

class AnchorAcceptanceTest {
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
    @Timeout(10)
    @DisplayName("R2-12: 무게추는 모든 색상 모드에서 너비 4와 고유 형상으로 식별")
    void anchorKeepsDistinctGeometryAndVisibleCellsAcrossEveryPalette() throws Exception {
        AnchorBlock anchor = new AnchorBlock();
        assertEquals(4, anchor.width());
        assertEquals(2, anchor.height());
        assertEquals(List.of(" OO ", "OOOO"), rows(anchor));
        assertTrue(rows(new RowBlock()).stream().anyMatch(row -> row.contains("L")));
        assertTrue(rows(new ColumnBlock()).stream().anyMatch(row -> row.contains("C")));
        assertTrue(rows(new BombBlock()).stream().anyMatch(row -> row.contains("B")));

        for (String palette : List.of("Normal", "Protanopia", "Deuteranopia", "Tritanopia")) {
            Maincontainer.colorPalette = palette;
            assertFalse(Maincontainer.changeColor(anchor.getColor())
                .equals(Maincontainer.changeColor(Color.BLACK)), palette);
            ExistingFeatureFixture.onEdt(() -> {
                try (ExistingFeatureFixture fixture = new ExistingFeatureFixture(Difficulty.NORMAL)) {
                    fixture.prepare(anchor, new OBlock(), new int[20][10], 3, 5);
                    BufferedImage rendered = fixture.render(palette + "-anchor.png");
                    for (int row = 0; row < anchor.height(); row++) {
                        for (int column = 0; column < anchor.width(); column++) {
                            if (anchor.getShape(column, row) != 0) {
                                assertTrue(fixture.contrastingPixelsInCell(
                                    rendered, 4 + column, 6 + row) > 4, palette);
                            }
                        }
                    }
                }
                return null;
            });
        }
    }

    @ParameterizedTest(name = "x={0}")
    @ValueSource(ints = {0, 6})
    @DisplayName("R2-12: 좌우 벽에서 효과 폭 4칸 내부만 제거하고 바깥 블록은 보존")
    void effectClearsOnlyItsFourColumnsAtBothWalls(int anchorX) {
        GameEngineFixture fixture = new GameEngineFixture(ITEM);
        fixture.seed(new AnchorBlock(), new OBlock(), anchorX, 2);
        int outside = anchorX == 0 ? 4 : 5;
        fixture.fixed(anchorX, 1);
        fixture.fixed(anchorX, 4);
        for (int row : new int[] {6, 10, 17}) {
            for (int column = anchorX; column < anchorX + 4; column++) fixture.fixed(column, row);
            fixture.fixed(outside, row);
        }

        GameSnapshot result = fixture.engine.handle(GameCommand.HARD_DROP).snapshot();

        assertEquals(0, result.score());
        assertTrue(result.fixedCells().get(1).get(anchorX) != null);
        for (int row : new int[] {6, 10, 17}) {
            for (int column = anchorX; column < anchorX + 4; column++) {
                assertNull(result.fixedCells().get(row).get(column));
            }
            assertTrue(result.fixedCells().get(row).get(outside) != null);
        }
        assertAnchorAtBottom(result, anchorX);
    }

    @ParameterizedTest(name = "level={0}")
    @ValueSource(ints = {1, 2, 19})
    @DisplayName("R2-12: 쌓인 블록 접촉 뒤 효과·최종 정지·다음 블록 전환은 한 번만 진행")
    void hardDropRemovesStackThenAdvancesAndScoresOnlyActualDropDistance(int level) {
        GameEngineFixture fixture = new GameEngineFixture(ITEM, new OBlock());
        fixture.seed(new AnchorBlock(), new OBlock(), 3, 0);
        fixture.progress(0, 0, level);
        for (int[] cell : new int[][] {{3, 4}, {4, 7}, {5, 11}, {6, 16}, {2, 4}, {8, 11}}) {
            fixture.fixed(cell[0], cell[1]);
        }

        GameUpdate locked = fixture.engine.handle(GameCommand.HARD_DROP);
        GameSnapshot continued = fixture.engine.continueAfterLock(
            locked.lockResolution().orElseThrow().turnId()).snapshot();

        assertEquals(2 * level, continued.score());
        assertEquals("O", continued.current().kind());
        assertEquals(3, continued.x());
        assertEquals(0, continued.y());
        assertTrue(continued.fixedCells().get(4).get(2) != null);
        assertTrue(continued.fixedCells().get(11).get(8) != null);
        assertAnchorAtBottom(continued, 3);
    }

    @Test
    @DisplayName("R2-12: 빈 보드의 하드드롭은 18칸 낙하만 계산하고 바닥에 정지")
    void emptyBoardDropKeepsNormalDropPointsWithoutAnEffectBonus() {
        GameEngineFixture fixture = new GameEngineFixture(ITEM, new OBlock());
        fixture.seed(new AnchorBlock(), new OBlock(), 3, 0);
        GameUpdate locked = fixture.engine.handle(GameCommand.HARD_DROP);
        GameSnapshot continued = fixture.engine.continueAfterLock(
            locked.lockResolution().orElseThrow().turnId()).snapshot();
        assertEquals(18, continued.score());
        assertEquals("O", continued.current().kind());
        assertAnchorAtBottom(continued, 3);
    }

    private static List<String> rows(Block block) {
        java.util.ArrayList<String> rows = new java.util.ArrayList<>();
        for (int y = 0; y < block.height(); y++) {
            StringBuilder row = new StringBuilder();
            for (int x = 0; x < block.width(); x++) row.append(block.getText(x, y));
            rows.add(row.toString());
        }
        return rows;
    }

    private static void assertAnchorAtBottom(GameSnapshot snapshot, int x) {
        assertNull(snapshot.fixedCells().get(18).get(x));
        assertTrue(snapshot.fixedCells().get(18).get(x + 1) != null);
        assertTrue(snapshot.fixedCells().get(18).get(x + 2) != null);
        assertNull(snapshot.fixedCells().get(18).get(x + 3));
        for (int column = x; column < x + 4; column++) {
            assertTrue(snapshot.fixedCells().get(19).get(column) != null);
        }
    }
}
