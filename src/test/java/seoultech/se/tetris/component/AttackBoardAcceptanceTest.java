package seoultech.se.tetris.component;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;
import seoultech.se.tetris.Difficulty;
import seoultech.se.tetris.GameMode;
import seoultech.se.tetris.attack.AttackEvent;
import seoultech.se.tetris.attack.AttackRow;
import seoultech.se.tetris.blocks.OBlock;

class AttackBoardAcceptanceTest {
    @Test
    void normalTwoLineClearProducesSameAttackInClassicAndItemModes() throws Exception {
        AttackEvent classic = normalTwoLineAttack(GameMode.CLASSIC);
        AttackEvent item = normalTwoLineAttack(GameMode.ITEM);

        assertEquals(classic, item);
        assertEquals(2, item.rows().size());
    }

    @Test
    void incomingRowsWaitUntilLockThenApplyOnceBeforeSpawn() throws Exception {
        try (ExistingFeatureFixture fixture = new ExistingFeatureFixture(Difficulty.NORMAL, GameMode.CLASSIC)) {
            int[][] grid = new int[20][10];
            grid[17][0] = 7;
            fixture.prepare(new OBlock(), new OBlock(), grid, 3, 18);
            AttackEvent attack = new AttackEvent(List.of(
                new AttackRow(new boolean[] {true, false, true, true, true, true, true, true, true, true}),
                new AttackRow(new boolean[] {true, true, false, true, true, true, true, true, true, true})));

            fixture.board.receiveAttack(attack);
            assertEquals(2, fixture.board.pendingAttackRows().size());
            assertEquals(7, grid[17][0], "falling input must not apply an attack immediately");

            fixture.board.hardDrop();

            int[][] applied = ExistingFeatureFixture.get(fixture.board, "board", int[][].class);
            assertEquals(0, fixture.board.pendingAttackRows().size());
            assertEquals(7, applied[15][0], "existing cells move upward by the attack height");
            assertFalse(applied[18][1] != 0, "first attack row keeps its real hole");
            assertTrue(applied[18][0] != 0);
            assertFalse(applied[19][2] != 0, "row order is preserved at the bottom");
        }
    }

    private static AttackEvent normalTwoLineAttack(GameMode mode) throws Exception {
        try (ExistingFeatureFixture fixture = new ExistingFeatureFixture(Difficulty.NORMAL, mode)) {
            int[][] grid = new int[20][10];
            Arrays.fill(grid[18], 1);
            Arrays.fill(grid[19], 1);
            grid[18][4] = 0;
            grid[18][5] = 0;
            grid[19][4] = 0;
            grid[19][5] = 0;
            fixture.prepare(new OBlock(), new OBlock(), grid, 4, 18);
            List<AttackEvent> attacks = new ArrayList<>();
            fixture.board.setAttackSink(attacks::add);

            fixture.board.eraseLine();

            assertEquals(1, attacks.size(), mode + " must emit one attack for a normal two-line clear");
            return attacks.getFirst();
        }
    }
}
