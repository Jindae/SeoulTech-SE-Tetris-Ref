package seoultech.se.tetris.component;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.Arrays;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import seoultech.se.tetris.GameMode;
import seoultech.se.tetris.attack.AttackEvent;
import seoultech.se.tetris.blocks.OBlock;
import seoultech.se.tetris.game.GameCommand;
import seoultech.se.tetris.game.GamePhase;

class GameControllerAttackTest {
    @Test
    void publishesOutgoingAttackBeforePromotingTheNextBlock() throws Exception {
        ExistingFeatureFixture.onEdt(() -> {
            try (ControllerFixture fixture = new ControllerFixture(GameMode.CLASSIC)) {
                int[][] grid = twoLineGrid();
                fixture.prepare(new OBlock(), new OBlock(), grid, 4, 18);
                AtomicReference<AttackEvent> delivered = new AtomicReference<>();
                AtomicReference<GamePhase> deliveryPhase = new AtomicReference<>();
                AtomicReference<Object> currentAtDelivery = new AtomicReference<>();
                fixture.controller.setAttackSink(attack -> {
                    delivered.set(attack);
                    deliveryPhase.set(fixture.engine.snapshot().phase());
                    currentAtDelivery.set(fixture.engine.snapshot().current());
                });

                fixture.controller.dispatch(GameCommand.HARD_DROP);

                assertNotNull(delivered.get());
                assertEquals(GamePhase.AWAITING_SPAWN, deliveryPhase.get());
                assertNull(currentAtDelivery.get());
                assertEquals(GamePhase.ACTIVE, fixture.controller.snapshot().phase());
            }
            return null;
        });
    }

    private static int[][] twoLineGrid() {
        int[][] grid = new int[20][10];
        Arrays.fill(grid[18], 1);
        Arrays.fill(grid[19], 1);
        grid[18][4] = 0;
        grid[18][5] = 0;
        grid[19][4] = 0;
        grid[19][5] = 0;
        return grid;
    }
}
