package seoultech.se.tetris.component;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Component;
import java.awt.Container;
import java.awt.event.KeyEvent;
import java.lang.reflect.Field;
import java.nio.file.Path;
import java.util.List;
import javax.swing.JLabel;
import javax.swing.JTextPane;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import seoultech.se.tetris.Difficulty;
import seoultech.se.tetris.score.ScoreStore;
import sun.misc.Unsafe;

class DifficultyUiTest {
    private final String originalDataDir = System.getProperty("tetris.dataDir");

    @TempDir
    Path temporary;

    @AfterEach
    void restoreDataDirectory() {
        if (originalDataDir == null) System.clearProperty("tetris.dataDir");
        else System.setProperty("tetris.dataDir", originalDataDir);
    }

    @Test
    void startMenuKeepsSixActionsAndSavedDifficultyRoundTrips() throws Exception {
        System.setProperty("tetris.dataDir", temporary.toString());
        TestMaincontainer owner = owner();
        Startpanel start = new Startpanel(owner);

        assertEquals(6, menuSize(start));
        for (String action : List.of("CLASSIC", "ITEM", "TIME LIMIT (TEMP)", "MENU", "SCORE", "EXIT")) {
            assertEquals(action, label(start, action).getText());
        }

        assertEquals("Easy", owner.setting.getDifficulty());
        assertEquals("Normal", owner.setting.nextDifficulty());

        owner.setting.exportSetting();
        assertEquals("Normal", owner.new Setting().getDifficulty());
    }

    @Test
    void settingsMenuCyclesSavesResetsAndAppliesDifficultyToNewGame() throws Exception {
        System.setProperty("tetris.dataDir", temporary.toString());
        TestMaincontainer owner = owner();
        Settingpanel settings = new Settingpanel(owner);

        assertEquals(8, menuSize(settings));
        assertEquals("Difficulty: ", label(settings, "Difficulty: ").getText());
        assertEquals("Easy", label(settings, "Easy").getText());

        for (int i = 0; i < 4; i++) press(settings, KeyEvent.VK_DOWN);
        for (String difficulty : List.of("Normal", "Hard", "Easy", "Normal", "Hard")) {
            press(settings, KeyEvent.VK_ENTER);
            assertEquals(difficulty, owner.setting.getDifficulty());
            assertEquals(difficulty, label(settings, difficulty).getText());
        }

        press(settings, KeyEvent.VK_DOWN);
        press(settings, KeyEvent.VK_DOWN);
        press(settings, KeyEvent.VK_DOWN);
        press(settings, KeyEvent.VK_ENTER);
        assertTrue(owner.settingsExitRequested);
        Maincontainer reloadedOwner = owner();
        assertEquals("Hard", reloadedOwner.setting.getDifficulty());
        assertEquals(Difficulty.HARD, reloadedOwner.getDifficulty());
        Gamepanel game = new Gamepanel(reloadedOwner);
        try {
            assertEquals(Difficulty.HARD, game.getDifficulty());
        } finally {
            stopBoardTimer(game);
        }

        press(settings, KeyEvent.VK_UP);
        press(settings, KeyEvent.VK_ENTER);
        assertEquals("Easy", owner.setting.getDifficulty());
        assertEquals("Easy", label(settings, "Easy").getText());
        owner.settingsExitRequested = false;
        press(settings, KeyEvent.VK_DOWN);
        press(settings, KeyEvent.VK_ENTER);
        assertTrue(owner.settingsExitRequested);
        assertEquals("Easy", owner().setting.getDifficulty());
    }

    @Test
    void runningGamesKeepIndependentDifficultyAndBoardState() throws Exception {
        System.setProperty("tetris.dataDir", temporary.toString());
        ExistingFeatureFixture.onEdt(() -> {
            TestMaincontainer owner = owner();
            owner.setDifficulty(Difficulty.EASY);
            Gamepanel easyGame = new Gamepanel(owner);
            easyGame.startGame();
            Board easyBoard = ExistingFeatureFixture.get(easyGame, "board", Board.class);
            easyBoard.timer.stop();
            owner.setDifficulty(Difficulty.HARD);
            Gamepanel hardGame = new Gamepanel(owner);
            hardGame.startGame();
            Board hardBoard = ExistingFeatureFixture.get(hardGame, "board", Board.class);
            hardBoard.timer.stop();
            try {
                owner.setDifficulty(Difficulty.NORMAL);
                assertEquals(Difficulty.EASY, easyGame.getDifficulty());
                assertEquals(Difficulty.HARD, hardGame.getDifficulty());
                ExistingFeatureFixture.invoke(easyBoard, "levelUp");
                ExistingFeatureFixture.invoke(hardBoard, "levelUp");
                assertEquals(827, easyBoard.timer.getDelay());
                assertEquals(761, hardBoard.timer.getDelay());
                assertTrue(easyBoard.moveDown());
                assertEquals(2, easyBoard.getScore());
                assertEquals(0, hardBoard.getScore());
            } finally {
                easyBoard.stopAnimations();
                hardBoard.stopAnimations();
                easyBoard.timer.stop();
                hardBoard.timer.stop();
            }
            return null;
        });
    }

    @Test
    void scoreboardLabelsOnlyRecordsThatContainDifficultyMetadata() throws Exception {
        System.setProperty("tetris.dataDir", temporary.toString());
        ScoreStore.append("Legacy", 10);
        ScoreStore.append("Modern", 20, Difficulty.EASY);

        Scoreboardpanel panel = new Scoreboardpanel(owner());
        Field scoreField = Scoreboardpanel.class.getDeclaredField("scorepanel");
        scoreField.setAccessible(true);
        String text = ((JTextPane) scoreField.get(panel)).getText();

        assertTrue(text.contains("Modern: 20 [Easy]"));
        assertTrue(text.contains("Legacy: 10"));
        assertFalse(text.contains("Legacy: 10 [Normal]"));
    }

    @Test
    void duplicateScoreHighlightsTheNewlyAppendedRow() throws Exception {
        System.setProperty("tetris.dataDir", temporary.toString());
        ScoreStore.append("SAME", 20, Difficulty.NORMAL);
        ScoreStore.append("SAME", 20, Difficulty.NORMAL);
        Scoreboardpanel panel = new Scoreboardpanel(owner());
        panel.highlightScore("SAME", 20, Difficulty.NORMAL);
        Field scoreField = Scoreboardpanel.class.getDeclaredField("scorepanel");
        scoreField.setAccessible(true);
        JTextPane scores = (JTextPane) scoreField.get(panel);
        String text = scores.getText();
        int first = text.indexOf("SAME");
        int last = text.lastIndexOf("SAME");
        var document = scores.getStyledDocument();
        assertFalse(new java.awt.Color(255, 225, 100).equals(javax.swing.text.StyleConstants.getBackground(
            document.getCharacterElement(first).getAttributes())));
        assertEquals(new java.awt.Color(255, 225, 100), javax.swing.text.StyleConstants.getBackground(
            document.getCharacterElement(last).getAttributes()));
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
        private boolean settingsExitRequested;

        @Override
        protected void exitSettingEnterStart() {
            settingsExitRequested = true;
        }
    }

    private static void stopBoardTimer(Gamepanel game) throws Exception {
        Field boardField = Gamepanel.class.getDeclaredField("board");
        boardField.setAccessible(true);
        ((Board) boardField.get(game)).timer.stop();
    }

    private static void press(javax.swing.JPanel panel, int keyCode) {
        KeyEvent event = new KeyEvent(panel, KeyEvent.KEY_PRESSED, System.currentTimeMillis(), 0,
            keyCode, KeyEvent.CHAR_UNDEFINED);
        for (var listener : panel.getKeyListeners()) listener.keyPressed(event);
    }

    private static int menuSize(javax.swing.JPanel panel) throws Exception {
        Field menuField = panel.getClass().getDeclaredField("menu");
        menuField.setAccessible(true);
        Object menu = menuField.get(panel);
        Field buttonsField = menu.getClass().getDeclaredField("buttons");
        buttonsField.setAccessible(true);
        return ((List<?>) buttonsField.get(menu)).size();
    }

    private static JLabel label(Container parent, String prefix) {
        for (Component component : parent.getComponents()) {
            if (component instanceof JLabel candidate && candidate.getText().startsWith(prefix)) {
                return candidate;
            }
            if (component instanceof Container child) {
                try {
                    return label(child, prefix);
                } catch (AssertionError ignored) {
                    // Continue searching sibling containers.
                }
            }
        }
        throw new AssertionError("Missing label with prefix: " + prefix);
    }
}
