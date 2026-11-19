package seoultech.se.tetris.component;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import seoultech.se.tetris.Difficulty;
import seoultech.se.tetris.GameMode;
import seoultech.se.tetris.blocks.OBlock;
import seoultech.se.tetris.blocks.TBlock;
import seoultech.se.tetris.game.GameCommand;
import seoultech.se.tetris.game.GameConfig;
import seoultech.se.tetris.game.GameEngineFixture;
import seoultech.se.tetris.game.GamePhase;

/** Requirement-focused checks against the headless game engine. */
class RequirementsRegressionTest {
    @Test
    @DisplayName("R13: 초기 자동 낙하 간격은 1초이고 생성만으로 시작하지 않음")
    void initialDropIntervalIsOneSecond() {
        GameEngineFixture fixture = new GameEngineFixture(
            new GameConfig(GameMode.CLASSIC, Difficulty.NORMAL));

        assertEquals(1_000_000_000L, fixture.engine.snapshot().dropIntervalNanos());
        assertEquals(GamePhase.NEW, fixture.engine.snapshot().phase());
        assertFalse(fixture.engine.snapshot().paused());
    }

    @Test
    @DisplayName("R14: 낙하 속도는 레벨 19까지 증가하고 상한에서 고정")
    void speedIncreasesThroughLevelNineteenAndThenStops() {
        for (Difficulty difficulty : Difficulty.values()) {
            GameEngineFixture fixture = fixture(difficulty, 1, 0);
            long previous = Long.MAX_VALUE;
            for (int level = 1; level <= 19; level++) {
                fixture.progress(0, (level - 1) * 10, level);
                long current = fixture.engine.snapshot().dropIntervalNanos();
                assertEquals(difficulty.dropIntervalMillis(level) * 1_000_000L, current);
                assertFalse(current > previous,
                    difficulty + " must not slow down at level " + level);
                previous = current;
            }
            fixture.progress(0, 190, 20);
            assertEquals(previous, fixture.engine.snapshot().dropIntervalNanos());
        }
    }

    @Test
    @DisplayName("R24·R25: 자동·수동·하드드롭은 레벨별 칸당 점수가 같음")
    void everyDropInputUsesTheSamePerCellScoreAtEachSpeed() {
        for (int level : new int[] {1, 2, 19}) {
            GameEngineFixture automatic = fixture(Difficulty.NORMAL, level, 17);
            automatic.engine.advance(automatic.engine.snapshot().dropIntervalNanos());
            assertEquals(level, automatic.score(), "자동 낙하 레벨 " + level);

            GameEngineFixture manual = fixture(Difficulty.NORMAL, level, 17);
            manual.engine.handle(GameCommand.SOFT_DROP);
            assertEquals(level, manual.score(), "수동 낙하 레벨 " + level);

            GameEngineFixture hard = fixture(Difficulty.NORMAL, level, 17);
            hard.engine.handle(GameCommand.HARD_DROP);
            assertEquals(level, hard.score(), "하드드롭 레벨 " + level);
        }
    }

    private static GameEngineFixture fixture(Difficulty difficulty, int level, int y) {
        GameEngineFixture fixture = new GameEngineFixture(
            new GameConfig(GameMode.CLASSIC, difficulty));
        fixture.seed(new OBlock(), new TBlock(), 3, y);
        fixture.progress(0, Math.max(0, level - 1) * 10, level);
        return fixture;
    }
}
