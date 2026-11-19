package seoultech.se.tetris.blocks;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import org.junit.jupiter.api.Test;
import seoultech.se.tetris.Difficulty;

class BlockGeneratorConsumptionTest {
    private static final long SEED = 12_345L;
    private static final Class<?>[] TYPES = {
        IBlock.class, JBlock.class, LBlock.class, ZBlock.class,
        SBlock.class, TBlock.class, OBlock.class
    };

    @Test
    void normalGenerationConsumesOneBoundedDrawAndReturnsFreshDefaultBlocks() {
        CountingRandom actualRandom = new CountingRandom(SEED);
        Random expectedRandom = new Random(SEED);
        BlockGenerator generator = new BlockGenerator(actualRandom);
        assertEquals(0, actualRandom.calls, "construction must not consume randomness");

        Set<Block> identities = java.util.Collections.newSetFromMap(new IdentityHashMap<>());
        Map<Class<?>, Block> previousByType = new java.util.HashMap<>();
        Set<Class<?>> observedTypes = new java.util.HashSet<>();

        for (int draw = 0; draw < 210; draw++) {
            Class<?> expectedType = TYPES[expectedRandom.nextInt(7)];
            Block block = generator.nextBlock(Difficulty.NORMAL);

            assertEquals(expectedType, block.getClass(), "seeded draw " + draw);
            assertTrue(identities.add(block), "every draw must return a fresh instance");
            Block previous = previousByType.put(block.getClass(), block);
            if (previous != null) assertNotSame(previous, block);
            assertEquals(defaultShape(block.getClass()), shape(block),
                "a previous instance rotation must not leak into a later draw");
            observedTypes.add(block.getClass());
            block.rotate();
        }

        assertEquals(210, actualRandom.calls);
        assertTrue(actualRandom.bounds.stream().allMatch(bound -> bound == 7));
        assertEquals(Set.of(TYPES), observedTypes);
    }

    private static String defaultShape(Class<?> type) {
        Block fresh;
        if (type == IBlock.class) fresh = new IBlock();
        else if (type == JBlock.class) fresh = new JBlock();
        else if (type == LBlock.class) fresh = new LBlock();
        else if (type == ZBlock.class) fresh = new ZBlock();
        else if (type == SBlock.class) fresh = new SBlock();
        else if (type == TBlock.class) fresh = new TBlock();
        else if (type == OBlock.class) fresh = new OBlock();
        else throw new AssertionError("unexpected type: " + type);
        return shape(fresh);
    }

    private static String shape(Block block) {
        int[][] cells = new int[block.height()][block.width()];
        for (int row = 0; row < block.height(); row++) {
            for (int column = 0; column < block.width(); column++) {
                cells[row][column] = block.getShape(column, row);
            }
        }
        return Arrays.deepToString(cells);
    }

    private static final class CountingRandom extends Random {
        private static final long serialVersionUID = 1L;
        private final Random delegate;
        private final java.util.List<Integer> bounds = new java.util.ArrayList<>();
        private int calls;

        CountingRandom(long seed) {
            delegate = new Random(seed);
        }

        @Override
        public int nextInt(int bound) {
            calls++;
            bounds.add(bound);
            return delegate.nextInt(bound);
        }
    }
}
