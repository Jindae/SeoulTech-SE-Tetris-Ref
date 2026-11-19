package seoultech.se.tetris.game;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
import seoultech.se.tetris.Difficulty;
import seoultech.se.tetris.GameMode;
import seoultech.se.tetris.blocks.CrossBlock;
import seoultech.se.tetris.blocks.OBlock;
import seoultech.se.tetris.blocks.TBlock;

class GameEngineIsolationTest {
    @Test
    void cancelingOneEffectDoesNotCancelTheOtherEnginesEffectOrItemSupply() {
        var first = new GameEngineFixture(new GameConfig(GameMode.ITEM, Difficulty.EASY), new CrossBlock());
        var second = new GameEngineFixture(new GameConfig(GameMode.ITEM, Difficulty.HARD), new CrossBlock());
        for (var fixture : new GameEngineFixture[] {first, second}) {
            fixture.seed(new CrossBlock(), new OBlock(), 3, 18);
            fixture.progress(0, 9, 1);
            fixture.engine.handle(GameCommand.HARD_DROP);
        }
        var secondBefore = second.engine.snapshot();
        first.engine.handle(GameCommand.TOGGLE_PAUSE);
        first.engine.advance(50_000_000L);
        first.engine.stop();
        var stopped = first.engine.snapshot();
        assertEquals(secondBefore, second.engine.snapshot());
        assertEquals(GamePhase.STOPPED, stopped.phase());
        assertEquals(0, first.source.itemCalls);
        assertEquals(0, first.pendingItems());

        var completed = second.advanceEffectFrames(11);
        assertEquals(300, completed.snapshot().score());
        assertEquals(11, completed.snapshot().clearedLines());
        assertEquals(2, completed.snapshot().level());
        second.engine.continueAfterLock(completed.lockResolution().orElseThrow().turnId());
        assertEquals(1, second.source.itemCalls);
        assertEquals(761_000_000L, second.engine.snapshot().dropIntervalNanos());
        assertEquals(stopped, first.engine.advance(Long.MAX_VALUE).snapshot());
    }

    @Test
    void pausingAndEndingOneEngineLeavesTheOthersGravityAndSourceUntouched() {
        var first = new GameEngineFixture(new TBlock());
        var second = new GameEngineFixture(new TBlock());
        first.seed(new OBlock(), new OBlock(), 3, 18);
        second.seed(new OBlock(), new OBlock(), 3, 0);
        first.fixed(3, 0);
        first.engine.handle(GameCommand.TOGGLE_PAUSE);
        second.engine.advance(1_000_000_000L);
        assertEquals(1, second.engine.snapshot().y());
        assertEquals(18, first.engine.snapshot().y());
        var other = second.engine.snapshot();
        first.engine.handle(GameCommand.TOGGLE_PAUSE);
        var locked = first.engine.handle(GameCommand.HARD_DROP);
        assertTrue(first.engine.continueAfterLock(locked.lockResolution().orElseThrow().turnId())
            .gameEnd().isPresent());
        assertEquals(other, second.engine.snapshot());
        second.engine.advance(1_000_000_000L);
        assertEquals(2, second.engine.snapshot().y());
        assertEquals(0, first.source.normalCalls);
        assertEquals(0, second.source.normalCalls);
    }
}
