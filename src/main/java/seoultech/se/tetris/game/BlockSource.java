package seoultech.se.tetris.game;

import seoultech.se.tetris.Difficulty;
import seoultech.se.tetris.blocks.Block;

/** Supplies a fresh, exclusively owned block for one engine. */
public interface BlockSource {
    Block nextBlock(Difficulty difficulty);

    Block nextItemBlock();
}
