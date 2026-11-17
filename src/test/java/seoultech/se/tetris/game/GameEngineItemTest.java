package seoultech.se.tetris.game;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import seoultech.se.tetris.Difficulty;
import seoultech.se.tetris.GameMode;
import seoultech.se.tetris.blocks.AnchorBlock;
import seoultech.se.tetris.blocks.BombBlock;
import seoultech.se.tetris.blocks.OBlock;

class GameEngineItemTest {
    private static final GameConfig ITEM = new GameConfig(GameMode.ITEM, Difficulty.NORMAL);

    @Test
    void tenLineBoundaryQueuesAndConsumesOneItemOnlyAfterSpawnCheck() {
        AnchorBlock awarded = new AnchorBlock();
        GameEngineFixture fixture = new GameEngineFixture(ITEM, awarded);
        fixture.seed(new OBlock(), new OBlock(), 3, 18);
        fixture.progress(0, 9, 1);
        fillBottomExcept(fixture, 3, 4);

        GameUpdate locked = fixture.engine.handle(GameCommand.HARD_DROP);
        assertEquals(1, fixture.pendingItems());
        assertEquals(0, fixture.source.itemCalls);

        GameUpdate continued = fixture.engine.continueAfterLock(
            locked.lockResolution().orElseThrow().turnId());

        assertEquals("O", continued.snapshot().current().kind());
        assertEquals("Anchor", continued.snapshot().next().kind());
        assertEquals(1, fixture.source.itemCalls);
        assertEquals(0, fixture.source.normalCalls);
        assertEquals(0, fixture.pendingItems());
    }

    @Test
    void multipleTenLineEntitlementsAreConsumedOnePerLaterLock() {
        GameEngineFixture fixture = new GameEngineFixture(ITEM,
            new AnchorBlock(), new BombBlock());
        fixture.seed(new OBlock(), new OBlock(), 3, 18);
        fixture.progress(0, 9, 1);
        for (int row = 8; row < 20; row++) {
            for (int column = 0; column < 10; column++) {
                if (row >= 18 && (column == 3 || column == 4)) continue;
                fixture.fixed(column, row);
            }
        }

        GameUpdate firstLock = fixture.engine.handle(GameCommand.HARD_DROP);
        fixture.engine.continueAfterLock(firstLock.lockResolution().orElseThrow().turnId());

        assertEquals(21, fixture.clearedLines());
        assertEquals(1, fixture.pendingItems());
        assertEquals("Anchor", fixture.engine.snapshot().next().kind());

        GameUpdate secondLock = fixture.engine.handle(GameCommand.HARD_DROP);
        fixture.engine.continueAfterLock(secondLock.lockResolution().orElseThrow().turnId());

        assertEquals(0, fixture.pendingItems());
        assertEquals("Anchor", fixture.engine.snapshot().current().kind());
        assertEquals("Bomb", fixture.engine.snapshot().next().kind());
        assertEquals(2, fixture.source.itemCalls);
    }

    @Test
    void spawnCollisionDiscardsQueuedItemsWithoutCallingSource() {
        GameEngineFixture fixture = new GameEngineFixture(ITEM, new AnchorBlock());
        fixture.seed(new OBlock(), new OBlock(), 3, 18);
        fixture.progress(0, 9, 1);
        fillBottomExcept(fixture, 3, 4);
        fixture.fixed(3, 0);

        GameUpdate locked = fixture.engine.handle(GameCommand.HARD_DROP);
        GameUpdate ended = fixture.engine.continueAfterLock(
            locked.lockResolution().orElseThrow().turnId());

        assertEquals(GamePhase.GAME_OVER, ended.snapshot().phase());
        assertEquals(0, fixture.pendingItems());
        assertEquals(0, fixture.source.itemCalls);
        assertEquals(0, fixture.source.normalCalls);
    }

    @Test
    void anchorLatchesOnContactClearsItsCorridorAndRelocatesToBottom() {
        GameEngineFixture fixture = new GameEngineFixture(ITEM);
        fixture.seed(new AnchorBlock(), new OBlock(), 3, 8);
        fixture.fixed(3, 10, "A", 0xff123456, 0);
        fixture.fixed(2, 15, "X", 0xffabcdef, 0);

        fixture.engine.handle(GameCommand.LEFT);
        fixture.clear(3, 10);
        fixture.engine.handle(GameCommand.RIGHT);
        assertEquals(3, fixture.engine.snapshot().x());
        fixture.fixed(3, 15, "Y", 0xff111111, 0);

        GameUpdate locked = fixture.engine.handle(GameCommand.HARD_DROP);
        assertEquals(GamePhase.AWAITING_SPAWN, locked.snapshot().phase());
        assertEquals(5, locked.snapshot().score());
        assertEquals("X", locked.snapshot().fixedCells().get(15).get(2).text());
        assertNull(locked.snapshot().fixedCells().get(15).get(3));
        assertNull(locked.snapshot().fixedCells().get(18).get(3));
        assertEquals("O", locked.snapshot().fixedCells().get(18).get(4).text());
        for (int column = 3; column < 7; column++) {
            assertTrue(locked.snapshot().fixedCells().get(19).get(column) != null);
        }
    }

    @Test
    void bombClearsClippedAreaAndSettlesRemainingAppearanceInOrder() {
        GameEngineFixture fixture = new GameEngineFixture(ITEM);
        fixture.seed(new BombBlock(), new OBlock(), 3, 16);
        fixture.fixed(0, 0, "A", 0xff010203, 0);
        fixture.fixed(0, 5, "B", 0xff040506, 0);
        fixture.fixed(1, 0, "X", 0xff070809, 0);

        GameUpdate locked = fixture.engine.handle(GameCommand.HARD_DROP);

        assertEquals(GamePhase.AWAITING_SPAWN, locked.snapshot().phase());
        assertEquals("A", locked.snapshot().fixedCells().get(18).get(0).text());
        assertEquals(0xff010203, locked.snapshot().fixedCells().get(18).get(0).argb());
        assertEquals("B", locked.snapshot().fixedCells().get(19).get(0).text());
        assertEquals("X", locked.snapshot().fixedCells().get(19).get(1).text());
        for (int column = 3; column < 6; column++) {
            assertNull(locked.snapshot().fixedCells().get(19).get(column));
        }
    }

    @Test
    void bombSettlementCompletesRowsLevelsUpAndAwardsOneItem() {
        GameEngineFixture fixture = new GameEngineFixture(ITEM, new AnchorBlock());
        fixture.seed(new BombBlock(), new OBlock(), 3, 11);
        fixture.progress(0, 8, 1);
        for (int column = 0; column < 10; column++) {
            fixture.fixed(column, column % 3);
            fixture.fixed(column, 5 + column % 3);
        }

        GameUpdate locked = fixture.engine.handle(GameCommand.HARD_DROP);

        assertEquals(305, locked.snapshot().score());
        assertEquals(10, locked.snapshot().clearedLines());
        assertEquals(2, locked.snapshot().level());
        assertEquals(ClearBatch.Cause.BOMB_ITEM,
            locked.lockResolution().orElseThrow().clearBatches().get(0).cause());
        assertEquals(1, fixture.pendingItems());

        fixture.engine.continueAfterLock(locked.lockResolution().orElseThrow().turnId());
        assertEquals("Anchor", fixture.engine.snapshot().next().kind());
        assertEquals(1, fixture.source.itemCalls);
        assertEquals(0, fixture.pendingItems());
    }

    private static void fillBottomExcept(GameEngineFixture fixture, int firstGap, int secondGap) {
        for (int column = 0; column < 10; column++) {
            if (column != firstGap && column != secondGap) fixture.fixed(column, 19);
        }
    }
}
