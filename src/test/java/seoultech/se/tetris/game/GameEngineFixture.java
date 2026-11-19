package seoultech.se.tetris.game;

import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Queue;
import seoultech.se.tetris.Difficulty;
import seoultech.se.tetris.GameMode;
import seoultech.se.tetris.blocks.Block;

public final class GameEngineFixture {
    public final QueueBlockSource source;
    public final GameEngine engine;

    public GameEngineFixture(Block... blocks) {
        this(new GameConfig(GameMode.CLASSIC, Difficulty.NORMAL), blocks);
    }

    public GameEngineFixture(GameConfig config, Block... blocks) {
        source = new QueueBlockSource(blocks);
        engine = new GameEngine(config, source);
    }

    public GameEngineFixture(GameEngine engine) {
        this.source = null;
        this.engine = engine;
    }

    public void seed(Block current, Block next, int x, int y) {
        GameState state = engine.state;
        for (GameState.FixedCell[] row : state.fixed) Arrays.fill(row, null);
        state.current = current;
        state.next = next;
        state.x = x;
        state.y = y;
        state.score = 0;
        state.clearedLines = 0;
        state.level = 1;
        state.paused = false;
        state.phase = GamePhase.ACTIVE;
        state.pendingLock = null;
        state.itemEffect = null;
        state.pendingItems = 0;
        state.anchorTouched = false;
        state.highlightedRows.clear();
        state.gravityElapsedNanos = 0;
        state.highlightElapsedNanos = 0;
    }

    public void fixed(int x, int y) {
        fixed(x, y, "O", 0xff808080, 0);
    }

    public void fixed(int x, int y, String text, int argb, long originTurnId) {
        engine.state.fixed[y][x] = new GameState.FixedCell(1, text, argb, originTurnId);
    }

    public void clear(int x, int y) {
        engine.state.fixed[y][x] = null;
    }

    public void seedGrid(CellSeed[][] grid) {
        if (grid.length != GameState.HEIGHT) throw new IllegalArgumentException("grid height must be 20");
        for (int y = 0; y < GameState.HEIGHT; y++) {
            if (grid[y].length != GameState.WIDTH) {
                throw new IllegalArgumentException("grid width must be 10");
            }
            for (int x = 0; x < GameState.WIDTH; x++) {
                CellSeed cell = grid[y][x];
                engine.state.fixed[y][x] = cell == null ? null
                    : new GameState.FixedCell(cell.value(), cell.text(), cell.argb(), cell.originTurnId());
            }
        }
    }

    public void seedGrid(int[][] grid) {
        if (grid.length != GameState.HEIGHT) throw new IllegalArgumentException("grid height must be 20");
        for (int y = 0; y < GameState.HEIGHT; y++) {
            if (grid[y].length != GameState.WIDTH) {
                throw new IllegalArgumentException("grid width must be 10");
            }
            for (int x = 0; x < GameState.WIDTH; x++) {
                engine.state.fixed[y][x] = grid[y][x] == 0 ? null
                    : new GameState.FixedCell(grid[y][x], "O", 0xff808080, 0);
            }
        }
    }

    public void progress(int score, int clearedLines, int level) {
        engine.state.score = score;
        engine.state.clearedLines = clearedLines;
        engine.state.level = level;
    }

    public int score() {
        return engine.state.score;
    }

    public int clearedLines() {
        return engine.state.clearedLines;
    }

    public int level() {
        return engine.state.level;
    }

    public int pendingItems() {
        return engine.state.pendingItems;
    }

    public void pendingItems(int count) {
        if (count < 0) throw new IllegalArgumentException("count must be nonnegative");
        engine.state.pendingItems = count;
    }

    public String cellText(int x, int y) {
        GameState.FixedCell cell = engine.state.fixed[y][x];
        return cell == null ? null : cell.text();
    }

    public int cellArgb(int x, int y) {
        GameState.FixedCell cell = engine.state.fixed[y][x];
        if (cell == null) throw new IllegalStateException("cell is empty");
        return cell.argb();
    }

    public GameUpdate advanceEffectFrames(int count) {
        if (count < 0) throw new IllegalArgumentException("count must be nonnegative");
        GameUpdate update = engine.advance(0);
        for (int frame = 0; frame < count; frame++) {
            update = engine.advance(ItemEffectState.FRAME_NANOS);
        }
        return update;
    }

    public record CellSeed(int value, String text, int argb, long originTurnId) {}

    public static final class QueueBlockSource implements BlockSource {
        private final Queue<Block> queue = new ArrayDeque<>();
        public int normalCalls;
        public int itemCalls;

        public QueueBlockSource(Block... blocks) {
            queue.addAll(Arrays.asList(blocks));
        }

        @Override
        public Block nextBlock(Difficulty difficulty) {
            normalCalls++;
            Block block = queue.poll();
            if (block == null) throw new AssertionError("No scripted normal block remains");
            return block;
        }

        @Override
        public Block nextItemBlock() {
            itemCalls++;
            Block block = queue.poll();
            if (block == null) throw new AssertionError("No scripted item block remains");
            return block;
        }
    }
}
