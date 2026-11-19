package seoultech.se.tetris.component;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import seoultech.se.tetris.Difficulty;
import seoultech.se.tetris.GameMode;
import seoultech.se.tetris.blocks.AnchorBlock;
import seoultech.se.tetris.blocks.BombBlock;
import seoultech.se.tetris.blocks.OBlock;
import seoultech.se.tetris.game.GameCommand;
import seoultech.se.tetris.game.GameConfig;
import seoultech.se.tetris.game.GameEngineFixture;
import seoultech.se.tetris.game.GamePhase;
import seoultech.se.tetris.game.GameSnapshot;
import seoultech.se.tetris.game.GameUpdate;

/** R2-10 acceptance boundaries for line-count based item supply. */
class ItemSupplyAcceptanceTest {
    private static final GameConfig ITEM = new GameConfig(GameMode.ITEM, Difficulty.NORMAL);
    private static final GameConfig CLASSIC = new GameConfig(GameMode.CLASSIC, Difficulty.NORMAL);
    private final String originalDataDir = System.getProperty("tetris.dataDir");

    @TempDir Path temporary;

    @BeforeEach
    void isolatePersistentSettings() {
        System.setProperty("tetris.dataDir", temporary.toString());
    }

    @AfterEach
    void restoreGlobalSettings() {
        if (originalDataDir == null) System.clearProperty("tetris.dataDir");
        else System.setProperty("tetris.dataDir", originalDataDir);
    }

    @ParameterizedTest(name = "clearedBefore={0}")
    @ValueSource(ints = {9, 19})
    @DisplayName("R2-10: 아이템 모드는 10줄 경계를 넘긴 고정에서 다음 미리보기에 아이템 하나를 공급")
    void crossingEachTenLineBoundarySuppliesOneNextItem(int clearedBefore) {
        GameEngineFixture fixture = new GameEngineFixture(ITEM, new AnchorBlock());
        prepareSingleLineClear(fixture, clearedBefore);

        GameSnapshot result = lockAndContinue(fixture);

        assertEquals(clearedBefore + 1, result.clearedLines());
        assertEquals("O", result.current().kind());
        assertEquals("Anchor", result.next().kind());
        assertEquals(0, fixture.pendingItems());
        assertEquals(1, fixture.source.itemCalls);
        Nextblockpanel preview = new Nextblockpanel();
        preview.drawNextBoard(result.next());
        assertEquals(result.next(), preview.piece());
    }

    @Test
    @DisplayName("R2-10: 복수 공급 권리는 일반 삭제 강조 종료 후에도 남아 다음 고정에서 순차 소비")
    void multipleEntitlementsAreQueuedAndConsumedOnePerLock() {
        GameEngineFixture fixture = new GameEngineFixture(ITEM, new AnchorBlock(), new BombBlock());
        fixture.seed(new OBlock(), new OBlock(), 3, 18);
        fixture.progress(0, 9, 1);
        for (int row = 8; row < 20; row++) {
            for (int column = 0; column < 10; column++) {
                if (row >= 18 && (column == 3 || column == 4)) continue;
                fixture.fixed(column, row);
            }
        }

        GameSnapshot first = lockAndContinue(fixture);
        assertEquals(21, first.clearedLines());
        assertEquals("Anchor", first.next().kind());
        assertEquals(1, fixture.pendingItems());

        fixture.engine.advance(210_000_000L);
        assertEquals(1, fixture.pendingItems());
        GameSnapshot second = lockAndContinue(fixture);
        assertEquals("Anchor", second.current().kind());
        assertEquals("Bomb", second.next().kind());
        assertEquals(0, fixture.pendingItems());
        assertEquals(2, fixture.source.itemCalls);
    }

    @Test
    @DisplayName("R2-10: 일반 이동 타이머 틱은 이미 공급한 아이템 미리보기를 중복 교체하지 않음")
    void movementTickDoesNotReplaceAwardedPreview() {
        GameEngineFixture fixture = new GameEngineFixture(ITEM, new AnchorBlock());
        prepareSingleLineClear(fixture, 9);
        GameSnapshot supplied = lockAndContinue(fixture);
        GameSnapshot moved = fixture.engine.handle(GameCommand.SOFT_DROP).snapshot();
        assertEquals(supplied.next(), moved.next());
        assertEquals(1, fixture.source.itemCalls);
        assertEquals(0, fixture.pendingItems());
    }

    @Test
    @DisplayName("R2-10: 클래식 모드는 10줄 경계를 넘어도 일반 블록만 공급")
    void classicModeNeverSuppliesAnItemAtThreshold() {
        GameEngineFixture fixture = new GameEngineFixture(CLASSIC, new OBlock());
        prepareSingleLineClear(fixture, 9);
        GameSnapshot result = lockAndContinue(fixture);
        assertEquals("O", result.current().kind());
        assertFalse(result.next().kind().equals("Anchor"));
        assertEquals(0, fixture.source.itemCalls);
        assertEquals(1, fixture.source.normalCalls);
        assertEquals(0, fixture.pendingItems());
    }

    @Test
    @DisplayName("R2-10: 새 게임은 이전 게임의 미공급 아이템 권리를 보유하지 않음")
    void resetClearsPendingEntitlements() {
        GameEngineFixture previous = new GameEngineFixture(ITEM);
        previous.pendingItems(2);
        previous.engine.stop();
        GameEngineFixture fresh = new GameEngineFixture(ITEM, new OBlock(), new OBlock());
        fresh.engine.start();
        assertEquals(0, fresh.pendingItems());
        assertFalse(fresh.engine.snapshot().current().kind().equals("Anchor"));
        assertFalse(fresh.engine.snapshot().next().kind().equals("Anchor"));
    }

    @Test
    @DisplayName("R2-10: 게임 종료는 이전 게임의 미공급 아이템 권리를 제거")
    void stoppingGameClearsPendingEntitlements() {
        GameEngineFixture fixture = new GameEngineFixture(ITEM);
        fixture.pendingItems(2);
        fixture.engine.stop();
        assertEquals(0, fixture.pendingItems());
        assertEquals(GamePhase.STOPPED, fixture.engine.snapshot().phase());
    }

    @Test
    @DisplayName("R2-10: 실제 생성 충돌 게임오버에서 지급 권리를 버리고 새 게임은 누계 없이 시작")
    void spawnCollisionDiscardsEntitlementsAndNewGameStartsClean() {
        GameEngineFixture fixture = new GameEngineFixture(ITEM, new AnchorBlock());
        fixture.seed(new OBlock(), new OBlock(), 3, 18);
        fixture.progress(0, 9, 1);
        fillBottomExcept(fixture);
        fixture.fixed(3, 0);
        GameUpdate locked = fixture.engine.handle(GameCommand.HARD_DROP);
        GameSnapshot ended = fixture.engine.continueAfterLock(
            locked.lockResolution().orElseThrow().turnId()).snapshot();

        assertEquals(GamePhase.GAME_OVER, ended.phase());
        assertEquals(0, fixture.pendingItems());
        assertEquals(0, fixture.source.itemCalls);
        assertEquals(0, fixture.source.normalCalls);

        GameEngineFixture fresh = new GameEngineFixture(ITEM, new OBlock(), new OBlock());
        fresh.engine.start();
        assertEquals(0, fresh.pendingItems());
        assertEquals("O", fresh.engine.snapshot().current().kind());
        assertEquals("O", fresh.engine.snapshot().next().kind());
    }

    private static void prepareSingleLineClear(GameEngineFixture fixture, int clearedBefore) {
        fixture.seed(new OBlock(), new OBlock(), 3, 18);
        fixture.progress(0, clearedBefore, Math.min(19, clearedBefore / 10 + 1));
        fillBottomExcept(fixture);
    }

    private static void fillBottomExcept(GameEngineFixture fixture) {
        for (int column = 0; column < 10; column++) {
            if (column != 3 && column != 4) fixture.fixed(column, 19);
        }
    }

    private static GameSnapshot lockAndContinue(GameEngineFixture fixture) {
        GameUpdate locked = fixture.engine.handle(GameCommand.HARD_DROP);
        return fixture.engine.continueAfterLock(
            locked.lockResolution().orElseThrow().turnId()).snapshot();
    }
}
