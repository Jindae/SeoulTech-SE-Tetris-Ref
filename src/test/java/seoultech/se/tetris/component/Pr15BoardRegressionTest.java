package seoultech.se.tetris.component;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import seoultech.se.tetris.Difficulty;
import seoultech.se.tetris.GameMode;
import seoultech.se.tetris.blocks.AnchorBlock;
import seoultech.se.tetris.blocks.Block;
import seoultech.se.tetris.blocks.ColumnBlock;
import seoultech.se.tetris.blocks.CrossBlock;
import seoultech.se.tetris.blocks.OBlock;
import seoultech.se.tetris.blocks.RowBlock;
import seoultech.se.tetris.game.GameCommand;
import seoultech.se.tetris.game.GameConfig;
import seoultech.se.tetris.game.GameEngineFixture;
import seoultech.se.tetris.game.GamePhase;
import seoultech.se.tetris.game.GameSnapshot;
import seoultech.se.tetris.game.GameUpdate;

/** PR-15 regressions expressed through the UI-independent engine contract. */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class Pr15BoardRegressionTest {
    private static final GameConfig ITEM = new GameConfig(GameMode.ITEM, Difficulty.NORMAL);

    @ParameterizedTest(name = "{0} pause retained")
    @MethodSource("animatedItems")
    @DisplayName("아이템 효과 도중 일시정지하면 완료 후에도 정지 상태 유지")
    void itemEffectKeepsPauseRequestedDuringResolution(
            String kind, Block item, int frames) {
        GameEngineFixture fixture = resolving(item);

        fixture.engine.handle(GameCommand.TOGGLE_PAUSE);
        GameUpdate committed = fixture.advanceEffectFrames(frames + 1);

        assertEquals(GamePhase.AWAITING_SPAWN, committed.snapshot().phase(), kind);
        assertTrue(committed.snapshot().paused(), kind);
        assertTrue(committed.lockResolution().isPresent(), kind);
    }

    @Test
    @DisplayName("BUG03-1: 열 효과 도중 재개하면 완료 뒤 다음 블록 중력이 동작")
    void columnEffectKeepsResumeRequestedDuringResolution() {
        GameEngineFixture fixture = resolving(new ColumnBlock());
        fixture.engine.handle(GameCommand.TOGGLE_PAUSE);
        fixture.engine.handle(GameCommand.TOGGLE_PAUSE);

        GameUpdate committed = fixture.advanceEffectFrames(11);
        GameUpdate active = fixture.engine.continueAfterLock(
            committed.lockResolution().orElseThrow().turnId());
        fixture.engine.advance(active.snapshot().dropIntervalNanos());

        assertFalse(fixture.engine.snapshot().paused());
        assertEquals(1, fixture.engine.snapshot().y());
        assertEquals(1, fixture.engine.snapshot().score());
    }

    @ParameterizedTest(name = "{0} cancelled by stop")
    @MethodSource("animatedItems")
    @DisplayName("BUG03-1: 메뉴 종료는 진행 중 효과와 후속 확정을 취소")
    void stopCancelsActiveItemEffectWithoutLateCommit(
            String kind, Block item, int frames) {
        GameEngineFixture fixture = resolving(item);

        fixture.engine.stop();
        fixture.engine.advance(5_000_000_000L);

        assertEquals(GamePhase.STOPPED, fixture.engine.snapshot().phase(), kind);
        assertNull(fixture.engine.snapshot().effect(), kind);
        assertEquals(0, fixture.score(), kind);
        assertEquals(0, fixture.clearedLines(), kind);
        assertEquals(0, fixture.pendingItems(), kind);
    }

    @ParameterizedTest(name = "{0} input guard")
    @MethodSource("animatedItems")
    @DisplayName("BUG03-1: 효과 중 블록 입력은 위치·점수·확정 상태를 변경하지 않음")
    void movementInputsAreIgnoredUntilItemEffectFinishes(
            String kind, Block item, int frames) {
        GameEngineFixture fixture = resolving(item);
        GameSnapshot before = fixture.engine.snapshot();

        for (GameCommand command : List.of(GameCommand.LEFT, GameCommand.RIGHT,
                GameCommand.ROTATE, GameCommand.SOFT_DROP, GameCommand.HARD_DROP)) {
            assertEquals(before, fixture.engine.handle(command).snapshot(), kind + "/" + command);
        }

        GameUpdate committed = fixture.advanceEffectFrames(frames + 1);
        assertTrue(committed.lockResolution().isPresent(), kind);
    }

    @ParameterizedTest(name = "{0} pause and resume")
    @MethodSource("animatedItems")
    @DisplayName("BUG03-1: 같은 효과 안에서 pause·resume해도 완료 전 중력 진행 없음")
    void pauseThenResumeDuringOneEffectDoesNotAdvanceGravity(
            String kind, Block item, int frames) {
        GameEngineFixture fixture = resolving(item);
        fixture.engine.handle(GameCommand.TOGGLE_PAUSE);
        fixture.advanceEffectFrames(1);
        fixture.engine.handle(GameCommand.TOGGLE_PAUSE);

        GameUpdate lastVisibleFrame = fixture.advanceEffectFrames(frames - 1);

        assertEquals(GamePhase.RESOLVING, lastVisibleFrame.snapshot().phase(), kind);
        assertEquals(0, lastVisibleFrame.snapshot().score(), kind);
        assertFalse(lastVisibleFrame.lockResolution().isPresent(), kind);
        assertFalse(lastVisibleFrame.snapshot().paused(), kind);
    }

    @Test
    @DisplayName("BUG03-1: 일반 삭제 강조는 바로 이어진 행 아이템 효과를 취소하지 않음")
    void clearHighlightDoesNotCancelImmediatelyFollowingRowEffect() {
        RowBlock row = new RowBlock();
        GameEngineFixture fixture = new GameEngineFixture(ITEM, new OBlock());
        fixture.seed(new OBlock(), row, 3, 18);
        fillRowExcept(fixture, 19, 3, 4);

        GameUpdate first = fixture.engine.handle(GameCommand.HARD_DROP);
        fixture.engine.continueAfterLock(first.lockResolution().orElseThrow().turnId());
        assertEquals(List.of(19), fixture.engine.snapshot().highlightedRows());

        GameUpdate second = fixture.engine.handle(GameCommand.HARD_DROP);
        assertEquals(GamePhase.RESOLVING, second.snapshot().phase());
        assertNotNull(second.snapshot().effect());
        fixture.engine.advance(210_000_000L);
        assertEquals(GamePhase.RESOLVING, fixture.engine.snapshot().phase());
        assertNotNull(fixture.engine.snapshot().effect());
    }

    @Test
    @DisplayName("BUG03-4: L행과 별도 완성 행의 합집합을 한 번 반영")
    void rowItemAndDifferentFullRowUseOneClearUnion() {
        RowBlock row = new RowBlock();
        GameEngineFixture fixture = new GameEngineFixture(ITEM);
        int landingY = 20 - row.height();
        fixture.seed(row, new OBlock(), 3, landingY);
        int itemRow = landingY + row.getItemY();
        int differentRow = itemRow == 19 ? 18 : 19;
        fillRow(fixture, differentRow);

        fixture.engine.handle(GameCommand.HARD_DROP);
        GameUpdate committed = fixture.advanceEffectFrames(6);

        assertEquals(300, committed.snapshot().score());
        assertEquals(2, committed.snapshot().clearedLines());
        assertEquals(List.of(Math.min(itemRow, differentRow), Math.max(itemRow, differentRow)),
            committed.lockResolution().orElseThrow().clearBatches().get(0).rows());
    }

    @Test
    @DisplayName("BUG03-4: 완성 행과 겹친 L행은 한 번만 세고 10행 경계 아이템 지급")
    void overlappingRowItemCountsOnceAndTriggersBoundaryItem() {
        RowBlock row = new RowBlock();
        AnchorBlock awarded = new AnchorBlock();
        GameEngineFixture fixture = new GameEngineFixture(ITEM, awarded);
        int landingY = 20 - row.height();
        fixture.seed(row, new OBlock(), 3, landingY);
        fixture.progress(0, 9, 1);
        int itemRow = landingY + row.getItemY();
        fillRowExceptBlock(fixture, itemRow, row, itemRow - landingY, 3);

        fixture.engine.handle(GameCommand.HARD_DROP);
        GameUpdate committed = fixture.advanceEffectFrames(6);

        assertEquals(100, committed.snapshot().score());
        assertEquals(10, committed.snapshot().clearedLines());
        assertEquals(1, fixture.pendingItems());
        GameUpdate active = fixture.engine.continueAfterLock(
            committed.lockResolution().orElseThrow().turnId());
        assertEquals("Anchor", active.snapshot().next().kind());
    }

    @ParameterizedTest(name = "{0} level interval")
    @EnumSource(Difficulty.class)
    @DisplayName("BUG03-5: 엔진은 난이도별 레벨 속도 계수를 적용")
    void actualLevelUsesDifficultySpeedFactor(Difficulty difficulty) {
        GameEngineFixture fixture = new GameEngineFixture(
            new GameConfig(GameMode.CLASSIC, difficulty));
        fixture.seed(new OBlock(), new OBlock(), 3, 0);

        fixture.progress(0, 0, 1);
        assertEquals(1_000_000_000L, fixture.engine.snapshot().dropIntervalNanos());
        fixture.progress(0, 10, 2);
        assertEquals(difficulty.dropIntervalMillis(2) * 1_000_000L,
            fixture.engine.snapshot().dropIntervalNanos());
        fixture.progress(0, 20, 3);
        assertEquals(difficulty.dropIntervalMillis(3) * 1_000_000L,
            fixture.engine.snapshot().dropIntervalNanos());
    }

    @Test
    @DisplayName("BUG03-6: 바닥 접촉 앵커는 좌우 이동과 회전 불가")
    void anchorCannotMoveOrRotateAfterFloorContact() {
        GameEngineFixture fixture = anchorAt(18);

        fixture.engine.handle(GameCommand.LEFT);
        fixture.engine.handle(GameCommand.RIGHT);
        fixture.engine.handle(GameCommand.ROTATE);

        assertPosition(fixture, 3, 18);
    }

    @Test
    @DisplayName("BUG03-6: 블록 접촉 공중 앵커는 반복 입력에도 고정")
    void anchorCannotEscapeBlockContactWithRepeatedInput() {
        GameEngineFixture fixture = anchorAt(8);
        fixture.fixed(3, 10);

        fixture.engine.handle(GameCommand.LEFT);
        fixture.engine.handle(GameCommand.RIGHT);
        fixture.engine.handle(GameCommand.LEFT);
        fixture.engine.handle(GameCommand.ROTATE);

        assertPosition(fixture, 3, 8);
    }

    @Test
    @DisplayName("BUG03-6: 접촉 전 앵커는 좌우 이동 가능")
    void anchorCanMoveBeforeContact() {
        GameEngineFixture fixture = anchorAt(5);

        fixture.engine.handle(GameCommand.LEFT);
        assertPosition(fixture, 2, 5);
        fixture.engine.handle(GameCommand.RIGHT);
        assertPosition(fixture, 3, 5);
    }

    @Test
    @DisplayName("BUG03-6: 앵커 확정 후 다음 블록의 접촉 잠금은 초기화")
    void advancingFromAnchorResetsContactForNextBlock() {
        GameEngineFixture fixture = new GameEngineFixture(ITEM, new OBlock());
        fixture.seed(new AnchorBlock(), new OBlock(), 3, 8);
        fixture.fixed(3, 10);
        fixture.engine.handle(GameCommand.LEFT);

        GameUpdate locked = fixture.engine.handle(GameCommand.HARD_DROP);
        fixture.engine.continueAfterLock(locked.lockResolution().orElseThrow().turnId());
        fixture.engine.handle(GameCommand.LEFT);

        assertPosition(fixture, 2, 0);
    }

    @Test
    @DisplayName("BUG03-6: 앵커 접촉 잠금은 장애물 제거 뒤에도 유지되고 다음 블록에서 해제")
    void anchorContactLatchPersistsAfterObstacleRemovalAndClearsOnAdvance() {
        GameEngineFixture fixture = new GameEngineFixture(ITEM, new OBlock());
        fixture.seed(new AnchorBlock(), new OBlock(), 3, 8);
        fixture.fixed(3, 10);
        fixture.engine.handle(GameCommand.LEFT);
        fixture.clear(3, 10);

        fixture.engine.handle(GameCommand.RIGHT);
        assertPosition(fixture, 3, 8);
        GameUpdate locked = fixture.engine.handle(GameCommand.HARD_DROP);
        fixture.engine.continueAfterLock(locked.lockResolution().orElseThrow().turnId());
        fixture.engine.handle(GameCommand.RIGHT);
        assertPosition(fixture, 4, 0);
    }

    Stream<Arguments> animatedItems() {
        return Stream.of(
            Arguments.of("ROW", new RowBlock(), 5),
            Arguments.of("COLUMN", new ColumnBlock(), 10),
            Arguments.of("CROSS", new CrossBlock(), 10));
    }

    private static GameEngineFixture resolving(Block item) {
        GameEngineFixture fixture = new GameEngineFixture(ITEM, new OBlock());
        fixture.seed(item, new OBlock(), 3, 20 - item.height());
        GameUpdate started = fixture.engine.handle(GameCommand.HARD_DROP);
        assertEquals(GamePhase.RESOLVING, started.snapshot().phase());
        return fixture;
    }

    private static GameEngineFixture anchorAt(int y) {
        GameEngineFixture fixture = new GameEngineFixture(ITEM);
        fixture.seed(new AnchorBlock(), new OBlock(), 3, y);
        return fixture;
    }

    private static void assertPosition(GameEngineFixture fixture, int x, int y) {
        assertEquals(x, fixture.engine.snapshot().x());
        assertEquals(y, fixture.engine.snapshot().y());
    }

    private static void fillRow(GameEngineFixture fixture, int row) {
        for (int column = 0; column < 10; column++) fixture.fixed(column, row);
    }

    private static void fillRowExcept(
            GameEngineFixture fixture, int row, int firstGap, int secondGap) {
        for (int column = 0; column < 10; column++) {
            if (column != firstGap && column != secondGap) fixture.fixed(column, row);
        }
    }

    private static void fillRowExceptBlock(
            GameEngineFixture fixture, int row, Block block, int blockRow, int x) {
        for (int column = 0; column < 10; column++) {
            int blockColumn = column - x;
            boolean occupiedByBlock = blockColumn >= 0 && blockColumn < block.width()
                && block.getShape(blockColumn, blockRow) != 0;
            if (!occupiedByBlock) fixture.fixed(column, row);
        }
    }
}
