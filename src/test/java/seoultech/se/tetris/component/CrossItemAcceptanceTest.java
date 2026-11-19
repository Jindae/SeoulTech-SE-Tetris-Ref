package seoultech.se.tetris.component;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.image.BufferedImage;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import seoultech.se.tetris.Difficulty;
import seoultech.se.tetris.GameMode;
import seoultech.se.tetris.blocks.CrossBlock;
import seoultech.se.tetris.blocks.OBlock;
import seoultech.se.tetris.game.GameCommand;
import seoultech.se.tetris.game.GameConfig;
import seoultech.se.tetris.game.GameEngineFixture;
import seoultech.se.tetris.game.GameSnapshot;
import seoultech.se.tetris.game.GameUpdate;

/** R2-13 acceptance boundaries for the two-row/two-column item. */
class CrossItemAcceptanceTest {
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

    @ParameterizedTest(name = "x={0}")
    @ValueSource(ints = {0, 8})
    @DisplayName("R2-13: 좌우 모서리에서 원래 두 행·두 열만 삭제하고 격자와 문자를 함께 압축")
    void clearsOriginalRowsAndColumnsAtBothCorners(int itemX) {
        GameEngineFixture fixture = new GameEngineFixture(ITEM);
        fixture.seed(new CrossBlock(), new OBlock(), itemX, 18);
        int outside = itemX == 0 ? 2 : 7;
        fixture.fixed(itemX, 5);
        fixture.fixed(outside, 5, "S", 0xffff00ff, 0);

        GameSnapshot result = resolve(fixture).snapshot();

        assertNull(result.fixedCells().get(7).get(itemX));
        assertEquals("S", result.fixedCells().get(7).get(outside).text());
        assertEquals(300, result.score());
        assertEquals(2, result.clearedLines());
    }

    @Test
    @DisplayName("R2-13: 최상단 고정에서도 원래 0·1행을 삭제하고 아래 행의 높이는 유지")
    void clearsTopRowsWithoutMovingTheRemainingHeight() {
        GameEngineFixture fixture = new GameEngineFixture(ITEM);
        fixture.seed(new CrossBlock(), new OBlock(), 3, 0);
        fixture.fixed(3, 2);
        fixture.fixed(2, 2);

        GameSnapshot result = resolve(fixture).snapshot();

        assertTrue(result.fixedCells().get(0).stream().allMatch(java.util.Objects::isNull));
        assertTrue(result.fixedCells().get(1).stream().allMatch(java.util.Objects::isNull));
        assertNull(result.fixedCells().get(2).get(3));
        assertTrue(result.fixedCells().get(2).get(2) != null);
        assertEquals(300, result.score());
        assertEquals(2, result.clearedLines());
    }

    @Test
    @DisplayName("R2-13: 쌓인 블록 위 고정 시 실제 낙하 점수와 효과 점수를 합산하고 다음 블록으로 한 번 전환")
    void hardDropOntoStackPreservesDropPointsAndAdvancesOnce() {
        GameEngineFixture fixture = new GameEngineFixture(ITEM, new OBlock());
        fixture.seed(new CrossBlock(), new OBlock(), 4, 0);
        fixture.fixed(4, 5);
        fixture.fixed(3, 10);

        GameUpdate locked = resolve(fixture);
        GameSnapshot result = fixture.engine.continueAfterLock(
            locked.lockResolution().orElseThrow().turnId()).snapshot();

        assertEquals(303, result.score());
        assertEquals(2, result.clearedLines());
        assertNull(result.fixedCells().get(5).get(4));
        assertTrue(result.fixedCells().get(10).get(3) != null);
        assertEquals("O", result.current().kind());
        assertEquals(3, result.x());
        assertEquals(0, result.y());
    }

    @Test
    @DisplayName("R2-13: 아이템 행과 기존 완성 행의 합집합을 중복 없이 3줄 콤보로 계산")
    void completedRowsAndItemRowsAreScoredAsOneDeduplicatedUnion() {
        GameEngineFixture fixture = new GameEngineFixture(ITEM);
        fixture.seed(new CrossBlock(), new OBlock(), 4, 18);
        for (int column = 0; column < 10; column++) {
            fixture.fixed(column, 10);
            if (column != 4 && column != 5) {
                fixture.fixed(column, 18);
                fixture.fixed(column, 19);
            }
        }

        GameSnapshot result = resolve(fixture).snapshot();

        assertEquals(600, result.score());
        assertEquals(3, result.clearedLines());
        assertTrue(result.fixedCells().get(19).stream().allMatch(java.util.Objects::isNull));
    }

    @Test
    @DisplayName("R2-13: 모든 팔레트에서 2x2 P 마커 네 칸을 실제 화면으로 식별")
    void markerRemainsVisibleInEveryPalette() throws Exception {
        for (String palette : List.of("Normal", "Protanopia", "Deuteranopia", "Tritanopia")) {
            Maincontainer.colorPalette = palette;
            ExistingFeatureFixture.onEdt(() -> {
                try (ExistingFeatureFixture fixture = new ExistingFeatureFixture(Difficulty.NORMAL)) {
                    CrossBlock item = new CrossBlock();
                    fixture.prepare(item, item, new int[20][10], 3, 5);
                    Nextblockpanel preview = ExistingFeatureFixture.get(
                        fixture.game, "nextblockpanel", Nextblockpanel.class);
                    long previewMarkers = preview.piece().cells().stream().flatMap(List::stream)
                        .filter(cell -> cell != null && cell.text().equals("P")).count();
                    assertEquals(4, previewMarkers);
                    BufferedImage rendered = fixture.render(palette + "-cross-P.png");
                    for (int row = 0; row < 2; row++) {
                        for (int column = 0; column < 2; column++) {
                            assertTrue(fixture.contrastingPixelsInCell(
                                rendered, 4 + column, 6 + row) > 4, palette);
                        }
                    }
                }
                return null;
            });
        }
    }

    private static GameUpdate resolve(GameEngineFixture fixture) {
        GameUpdate update = fixture.engine.handle(GameCommand.HARD_DROP);
        for (int frame = 0; update.lockResolution().isEmpty() && frame < 12; frame++) {
            update = fixture.engine.advance(50_000_000L);
        }
        assertTrue(update.lockResolution().isPresent(), "cross effect must resolve");
        return update;
    }
}
