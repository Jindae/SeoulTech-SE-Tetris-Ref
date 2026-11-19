package seoultech.se.tetris.component;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.lang.reflect.Field;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import javax.swing.JPanel;
import javax.swing.JTextPane;
import javax.swing.JToggleButton;
import javax.swing.text.StyleConstants;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;
import seoultech.se.tetris.Difficulty;
import seoultech.se.tetris.GameMode;
import seoultech.se.tetris.score.ScoreStore;
import seoultech.se.tetris.game.GameEngineFixture;
import sun.misc.Unsafe;

@Timeout(10)
class ModeScoreUiTest {
    private static final Color HIGHLIGHT = new Color(255, 225, 100);

    private final String originalDataDir = System.getProperty("tetris.dataDir");

    @TempDir
    Path temporary;

    @BeforeEach
    void useIsolatedDataDirectory() {
        System.setProperty("tetris.dataDir", temporary.toString());
    }

    @AfterEach
    void restoreDataDirectory() {
        if (originalDataDir == null) System.clearProperty("tetris.dataDir");
        else System.setProperty("tetris.dataDir", originalDataDir);
    }

    @Test
    void scoreboardFiltersMixedRecordsThroughButtonsAndKeyboard() throws Exception {
        ScoreStore.append("Legacy", 10_000);
        ScoreStore.append("OldDiff", 9_999, Difficulty.HARD);
        for (int i = 0; i < 11; i++) {
            ScoreStore.append("Classic" + i, 1_000 - i, Difficulty.EASY, GameMode.CLASSIC);
            ScoreStore.append("Item" + i, 2_000 - i, Difficulty.NORMAL, GameMode.ITEM);
        }

        ExistingFeatureFixture.onEdt(() -> {
            TestMaincontainer owner = owner();
            Scoreboardpanel panel = new Scoreboardpanel(owner, null);
            String all = scoreText(panel);
            assertTrue(all.contains("Legacy: 10000\n"));
            assertTrue(all.contains("OldDiff: 9999 [Hard]\n"));
            assertFalse(all.contains("Legacy: 10000 [Classic]"));

            JToggleButton allButton = toggle(panel, "All");
            JToggleButton classicButton = toggle(panel, "Classic");
            JToggleButton itemButton = toggle(panel, "Item");
            assertTrue(allButton.isSelected());
            assertFalse(allButton.isFocusable());
            assertFalse(classicButton.isFocusable());
            assertFalse(itemButton.isFocusable());

            classicButton.doClick();
            String classics = scoreText(panel);
            assertTrue(classicButton.isSelected());
            assertEquals(10, occurrences(classics, "[Classic]"));
            assertTrue(classics.contains("Classic0: 1000 [Easy] [Classic]"));
            assertFalse(classics.contains("Classic10:"));
            assertFalse(classics.contains("Legacy:"));

            press(panel, KeyEvent.VK_RIGHT);
            String items = scoreText(panel);
            assertTrue(itemButton.isSelected());
            assertEquals(10, occurrences(items, "[Item]"));
            assertTrue(items.contains("Item0: 2000 [Normal] [Item]"));
            assertFalse(items.contains("Item10:"));

            press(panel, KeyEvent.VK_LEFT);
            assertTrue(classicButton.isSelected());
            press(panel, KeyEvent.VK_RIGHT);
            ScoreStore.clear();
            ScoreStore.append("OnlyItem", 7, Difficulty.HARD, GameMode.ITEM);
            panel.drawScore();
            String sparse = scoreText(panel);
            assertTrue(sparse.contains("OnlyItem: 7 [Hard] [Item]"));
            assertEquals(9, occurrences(sparse, "###: 0"));
            press(panel, KeyEvent.VK_ENTER);
            assertTrue(owner.scoreboardReturnRequested);
            return null;
        });
    }

    @Test
    void highlightUsesFullModeDifficultyIdentityAndLatestDuplicate() throws Exception {
        ScoreStore.append("SAME", 20, Difficulty.EASY, GameMode.CLASSIC);
        ScoreStore.append("SAME", 20, Difficulty.HARD, GameMode.ITEM);
        ScoreStore.append("SAME", 20, Difficulty.HARD, GameMode.CLASSIC);
        ScoreStore.append("SAME", 20, Difficulty.HARD, GameMode.CLASSIC);

        ExistingFeatureFixture.onEdt(() -> {
            Scoreboardpanel panel = new Scoreboardpanel(owner(), null);
            panel.highlightScore("SAME", 20, Difficulty.HARD, GameMode.CLASSIC);
            JTextPane scores = scorePane(panel);
            List<Integer> rows = occurrenceOffsets(scores.getText(), "SAME: 20");
            assertEquals(4, rows.size());
            for (int i = 0; i < rows.size() - 1; i++) {
                assertFalse(HIGHLIGHT.equals(backgroundAt(scores, rows.get(i))));
            }
            assertEquals(HIGHLIGHT, backgroundAt(scores, rows.get(rows.size() - 1)));
            return null;
        });
    }

    @Test
    void gameOverQualificationUsesOnlyCurrentModeTopTenAndRejectsTenthPlaceTie()
            throws Exception {
        for (int i = 0; i < 10; i++) {
            ScoreStore.append("Classic" + i, 1_000 - i * 100,
                Difficulty.NORMAL, GameMode.CLASSIC);
        }
        for (int i = 0; i < 12; i++) ScoreStore.append("Legacy" + i, 10_000 - i);

        ExistingFeatureFixture.onEdt(() -> {
            TestMaincontainer owner = owner();
            Gamepanel tiedClassic = new Gamepanel(owner, GameMode.CLASSIC);
            tiedClassic.startGame();
            try {
                new GameEngineFixture(tiedClassic.controller().engine()).progress(100, 0, 1);
                tiedClassic.controller().refreshSnapshot();
                tiedClassic.gameOver();
                assertEquals(1, owner.scoreboardTransitions);
                assertNull(owner.savedName);
                assertEquals(100, owner.savedScore);
                assertEquals(GameMode.CLASSIC, owner.savedMode);
                assertNull(field(tiedClassic, "gameOverPanel", JPanel.class));
            } finally {
                stopGame(tiedClassic);
            }

            ScoreStore.clear();
            for (int i = 0; i < 10; i++) {
                ScoreStore.append("ClassicOnly" + i, 5_000 - i,
                    Difficulty.HARD, GameMode.CLASSIC);
            }
            for (int i = 0; i < 15; i++) ScoreStore.append("LegacyOnly" + i, 9_000 - i);

            TestMaincontainer itemOwner = owner();
            Gamepanel firstItem = new Gamepanel(itemOwner, GameMode.ITEM);
            firstItem.startGame();
            try {
                new GameEngineFixture(firstItem.controller().engine()).progress(1, 0, 1);
                firstItem.controller().refreshSnapshot();
                firstItem.gameOver();
                assertEquals(0, itemOwner.scoreboardTransitions);
                assertNotNull(field(firstItem, "gameOverPanel", JPanel.class));
            } finally {
                stopGame(firstItem);
            }
            return null;
        });
    }

    @Test
    void userNameListenerSavesFrozenModeAndDifficultyAndUsesFourArgumentCallback()
            throws Exception {
        ExistingFeatureFixture.onEdt(() -> {
            TestMaincontainer owner = owner();
            owner.setDifficulty(Difficulty.EASY);
            Gamepanel game = new Gamepanel(owner, GameMode.ITEM);
            game.startGame();
            try {
                assertEquals(GameMode.ITEM, game.getGameMode());
                assertEquals(Difficulty.EASY, game.getDifficulty());
                owner.setDifficulty(Difficulty.HARD);
                new GameEngineFixture(game.controller().engine()).progress(321, 0, 1);
                game.controller().refreshSnapshot();
                game.gameOver();

                type(game, 'N', KeyEvent.VK_N);
                type(game, 'E', KeyEvent.VK_E);
                type(game, 'O', KeyEvent.VK_O);
                press(game, KeyEvent.VK_ENTER);

                assertEquals(1, owner.scoreboardTransitions);
                assertEquals("NEO", owner.savedName);
                assertEquals(321, owner.savedScore);
                assertEquals(Difficulty.EASY, owner.savedDifficulty);
                assertEquals(GameMode.ITEM, owner.savedMode);

                List<ScoreStore.Score> saved = ScoreStore.readRanked(GameMode.ITEM);
                assertEquals(1, saved.size());
                assertEquals(new ScoreStore.Score("NEO", 321, Difficulty.EASY, GameMode.ITEM),
                    saved.get(0));
                assertTrue(ScoreStore.readRanked(GameMode.CLASSIC).isEmpty());
            } finally {
                stopGame(game);
            }
            return null;
        });
    }

    @Test
    void settingsResetClearsEveryModeAndScoreboardRedrawsPlaceholders() throws Exception {
        ScoreStore.append("Legacy", 30);
        ScoreStore.append("Classic", 20, Difficulty.EASY, GameMode.CLASSIC);
        ScoreStore.append("Item", 10, Difficulty.HARD, GameMode.ITEM);

        ExistingFeatureFixture.onEdt(() -> {
            TestMaincontainer owner = owner();
            Scoreboardpanel itemView = new Scoreboardpanel(owner, GameMode.ITEM);
            assertTrue(scoreText(itemView).contains("Item: 10 [Hard] [Item]"));

            Settingpanel settings = new Settingpanel(owner);
            press(settings, KeyEvent.VK_DOWN);
            press(settings, KeyEvent.VK_DOWN);
            press(settings, KeyEvent.VK_ENTER);

            assertTrue(ScoreStore.readRanked().isEmpty());
            assertTrue(ScoreStore.readRanked(GameMode.CLASSIC).isEmpty());
            assertTrue(ScoreStore.readRanked(GameMode.ITEM).isEmpty());
            itemView.drawScore();
            assertEquals(10, occurrences(scoreText(itemView), "###: 0"));
            assertFalse(scoreText(itemView).contains("Item:"));
            return null;
        });
    }

    private static TestMaincontainer owner() throws Exception {
        Field unsafeField = Unsafe.class.getDeclaredField("theUnsafe");
        unsafeField.setAccessible(true);
        TestMaincontainer owner = (TestMaincontainer) ((Unsafe) unsafeField.get(null))
            .allocateInstance(TestMaincontainer.class);
        owner.setting = owner.new Setting();
        owner.setDifficulty(Difficulty.parse(owner.setting.getDifficulty()).orElse(Difficulty.NORMAL));
        return owner;
    }

    private static final class TestMaincontainer extends Maincontainer {
        private static final long serialVersionUID = 1L;
        private int scoreboardTransitions;
        private String savedName;
        private int savedScore;
        private Difficulty savedDifficulty;
        private GameMode savedMode;
        private boolean scoreboardReturnRequested;

        @Override
        protected void exitGameEnterScoreboard(String name, int score, Difficulty difficulty,
                GameMode mode) {
            scoreboardTransitions++;
            savedName = name;
            savedScore = score;
            savedDifficulty = difficulty;
            savedMode = mode;
        }

        @Override
        protected void exitScoreboardEnterStart() {
            scoreboardReturnRequested = true;
        }
    }

    private static void press(JPanel panel, int keyCode) {
        KeyEvent event = new KeyEvent(panel, KeyEvent.KEY_PRESSED, System.currentTimeMillis(), 0,
            keyCode, KeyEvent.CHAR_UNDEFINED);
        for (KeyListener listener : panel.getKeyListeners()) listener.keyPressed(event);
    }

    private static void type(JPanel panel, char value, int keyCode) {
        KeyEvent event = new KeyEvent(panel, KeyEvent.KEY_PRESSED, System.currentTimeMillis(), 0,
            keyCode, value);
        for (KeyListener listener : panel.getKeyListeners()) listener.keyPressed(event);
    }

    private static void stopGame(Gamepanel game) {
        game.stopGame();
    }

    private static String scoreText(Scoreboardpanel panel) throws Exception {
        return scorePane(panel).getText();
    }

    private static JTextPane scorePane(Scoreboardpanel panel) throws Exception {
        return field(panel, "scorepanel", JTextPane.class);
    }

    private static JToggleButton toggle(Container parent, String text) {
        for (Component component : parent.getComponents()) {
            if (component instanceof JToggleButton button && text.equals(button.getText())) {
                return button;
            }
            if (component instanceof Container child) {
                try {
                    return toggle(child, text);
                } catch (AssertionError ignored) {
                    // Continue through sibling containers.
                }
            }
        }
        throw new AssertionError("Missing toggle button: " + text);
    }

    private static Color backgroundAt(JTextPane pane, int offset) {
        return StyleConstants.getBackground(
            pane.getStyledDocument().getCharacterElement(offset).getAttributes());
    }

    private static List<Integer> occurrenceOffsets(String text, String needle) {
        List<Integer> offsets = new ArrayList<>();
        int offset = 0;
        while ((offset = text.indexOf(needle, offset)) >= 0) {
            offsets.add(offset);
            offset += needle.length();
        }
        return offsets;
    }

    private static int occurrences(String text, String needle) {
        return occurrenceOffsets(text, needle).size();
    }

    private static <T> T field(Object target, String name, Class<T> type) throws Exception {
        Field field = findField(target.getClass(), name);
        field.setAccessible(true);
        return type.cast(field.get(target));
    }

    private static Field findField(Class<?> type, String name) throws NoSuchFieldException {
        for (Class<?> current = type; current != null; current = current.getSuperclass()) {
            try {
                return current.getDeclaredField(name);
            } catch (NoSuchFieldException ignored) {
                // Continue into the superclass.
            }
        }
        throw new NoSuchFieldException(name);
    }
}
