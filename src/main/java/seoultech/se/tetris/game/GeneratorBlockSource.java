package seoultech.se.tetris.game;

import java.util.Objects;
import seoultech.se.tetris.Difficulty;
import seoultech.se.tetris.blocks.Block;
import seoultech.se.tetris.blocks.BlockGenerator;

/** Production block source preserving the existing generator call order. */
public final class GeneratorBlockSource implements BlockSource {
    private final BlockGenerator generator;

    public GeneratorBlockSource() {
        this(new BlockGenerator());
    }

    GeneratorBlockSource(BlockGenerator generator) {
        this.generator = Objects.requireNonNull(generator, "generator");
    }

    @Override
    public Block nextBlock(Difficulty difficulty) {
        return generator.nextBlock(difficulty);
    }

    @Override
    public Block nextItemBlock() {
        return generator.nextItemBlock();
    }
}
