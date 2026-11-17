package seoultech.se.tetris.component;

import java.awt.Component;
import java.awt.Graphics2D;
import java.awt.event.KeyEvent;
import java.awt.image.BufferedImage;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicReference;
import javax.imageio.ImageIO;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import seoultech.se.tetris.score.ScoreStore;
import seoultech.se.tetris.Difficulty;
import seoultech.se.tetris.GameMode;
import seoultech.se.tetris.blocks.CrossBlock;
import seoultech.se.tetris.blocks.ItemBlock;

import seoultech.se.tetris.game.GameEngineFixture;
import seoultech.se.tetris.game.GamePhase;

/** Real JFrame lifecycle with synthetic listener input; no OS installation/physical-key claim. */
public final class DesktopSmoke {
    private static Object field(Object object, String name) throws ReflectiveOperationException {
        Field field = object.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.get(object);
    }

    private static void key(Component component, int code) {
        KeyEvent event = new KeyEvent(component, KeyEvent.KEY_PRESSED,
            System.currentTimeMillis(), 0, code, KeyEvent.CHAR_UNDEFINED);
        for (var listener : component.getKeyListeners()) listener.keyPressed(event);
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    private static void capture(JFrame frame, Path path) throws Exception {
        frame.validate();
        BufferedImage image = new BufferedImage(frame.getWidth(), frame.getHeight(), BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();
        try { frame.paint(graphics); }
        finally { graphics.dispose(); }
        ImageIO.write(image, "png", path.toFile());
    }

    // Let the real Swing timer finish the asynchronous item animation without blocking the EDT.
    private static void awaitEffect(Gamepanel game) {
        var loop = java.awt.Toolkit.getDefaultToolkit().getSystemEventQueue().createSecondaryLoop();
        long deadline = System.nanoTime() + 3_000_000_000L;
        var poll = new javax.swing.Timer(10, event -> {
            if (game.controller().snapshot().phase() != GamePhase.RESOLVING
                    || System.nanoTime() >= deadline) loop.exit();
        });
        poll.start();
        try { loop.enter(); } finally { poll.stop(); }
        check(game.controller().snapshot().phase() == GamePhase.ACTIVE,
            "item animation must finish through the real controller timer");
    }

    private static void crossItemFlow(Maincontainer frame, Path evidence) throws Exception {
        frame.exitStartEnterGame("Item");
        Gamepanel game = (Gamepanel) field(frame, "gamePanel");
        GameController controller = game.controller();
        controller.timer().stop();
        var setup = new GameEngineFixture(controller.engine());
        setup.seed(new CrossBlock(), new CrossBlock(), 4, 18);
        setup.progress(0, 9, 1);
        controller.refreshSnapshot();
        var preview = controller.snapshot().next();
        capture(frame, evidence.resolve("cross-item-and-preview.png"));

        key(game, Maincontainer.HARD_DROP);
        awaitEffect(game);

        check(controller.snapshot().score() == 300, "P floor lock must score exactly two cleared rows");
        check(controller.snapshot().clearedLines() == 11, "P rows must count toward supply");
        check(controller.snapshot().current().equals(preview), "P preview must become the current block unchanged");
        check(java.util.Set.of("Row", "Column", "Anchor", "Bomb", "Cross").contains(controller.snapshot().next().kind()), "crossing ten rows must supply an item preview");
        key(game, KeyEvent.VK_ESCAPE);
        check(game.getParent() == null && !controller.timer().isRunning(), "P game must cleanly return to the menu");
    }

    private static void savedHardDropItemFlow(Maincontainer frame, Path evidence) throws Exception {
        check(Maincontainer.HARD_DROP == KeyEvent.VK_B,
            "recreated frame must apply the saved hard-drop key");
        check(frame.getDifficulty() == Difficulty.HARD,
            "recreated frame must apply the saved Hard difficulty");

        frame.exitStartEnterGame("Item");
        Gamepanel game = (Gamepanel) field(frame, "gamePanel");
        GameController controller = game.controller();
        controller.timer().stop();
        var setup = new GameEngineFixture(controller.engine());
        setup.seed(new CrossBlock(), new CrossBlock(), 4, 18);
        setup.progress(0, 9, 1);
        controller.refreshSnapshot();
        var preview = controller.snapshot().next();

        key(game, KeyEvent.VK_B);
        awaitEffect(game);

        check(controller.snapshot().score() == 300,
            "saved B hard-drop key must lock P and score its two rows");
        check(controller.snapshot().clearedLines() == 11,
            "saved-key P lock must cross the ten-row supply boundary");
        check(controller.snapshot().current().equals(preview),
            "saved-key P lock must advance the preview once");
        check(java.util.Set.of("Row", "Column", "Anchor", "Bomb", "Cross").contains(controller.snapshot().next().kind()),
            "saved-key P lock must supply the earned item preview");
        capture(frame, evidence.resolve("final-saved-key-p-lock.png"));

        controller.timer().stop();

        game.gameOver();
        key(game, KeyEvent.VK_ENTER);
        Scoreboardpanel scoreboard = (Scoreboardpanel) field(frame, "scoreboardPanel");
        check(ScoreStore.readRanked(GameMode.ITEM).equals(java.util.List.of(
                new ScoreStore.Score("PLAYER", 300, Difficulty.HARD, GameMode.ITEM))),
            "Hard Item result must retain its mode and difficulty metadata");
        check(((javax.swing.JTextPane) field(scoreboard, "scorepanel")).getText()
                .contains("PLAYER: 300 [Hard] [Item]"),
            "Hard Item result must be visible in the Item scoreboard");
        capture(frame, evidence.resolve("final-hard-item-score.png"));
        key(scoreboard, KeyEvent.VK_ENTER);
        check(scoreboard.getParent() == null,
            "Hard Item result must return from the scoreboard to the menu");
    }

    private static void configureAndRender(Maincontainer frame, Path evidence,
            String size, String palette, String mode, String screenshot) throws Exception {
        frame.exitStartEnterSetting();
        for (int attempt = 0; attempt < 3 && !frame.setting.getWindowSize().equals(size); attempt++) {
            frame.setting.nextWindowSize();
        }
        check(frame.setting.getWindowSize().equals(size), "configured size must exist: " + size);
        for (int attempt = 0; attempt < 4 && !frame.setting.getColorPalette().equals(palette); attempt++) {
            frame.setting.nextColorPalette();
        }
        check(frame.setting.getColorPalette().equals(palette), "configured palette must exist: " + palette);
        frame.setting.exportSetting();
        frame.exitSettingEnterStart();

        String[] dimensions = size.split("X");
        check(frame.getConfiguredHeight() == Integer.parseInt(dimensions[0])
                && frame.getConfiguredWidth() == Integer.parseInt(dimensions[1]),
            "actual frame must apply configured size " + size);
        check(frame.getContentPane().getHeight() == Integer.parseInt(dimensions[0])
                && frame.getContentPane().getWidth() == Integer.parseInt(dimensions[1]),
            "rendered content must use configured size " + size);
        check(frame.setting.getColorPalette().equals(palette)
                && Maincontainer.colorPalette.equals(palette),
            "actual frame must apply configured palette " + palette);

        frame.exitStartEnterGame(mode);
        Gamepanel game = (Gamepanel) field(frame, "gamePanel");
        GameController controller = game.controller();
        controller.timer().stop();
        int originalY = controller.snapshot().y();
        key(game, Maincontainer.DOWN);
        check(controller.snapshot().y() == originalY + 1 && controller.snapshot().score() == 1,
            palette + " representative game must accept synthetic movement");
        capture(frame, evidence.resolve(screenshot));
        key(game, KeyEvent.VK_ESCAPE);
        check(game.getParent() == null && !controller.timer().isRunning(),
            palette + " representative game must cleanly return to the menu");
    }

    public static void main(String[] args) throws Exception {
        Path data = Files.createTempDirectory("tetris-desktop-data-");
        Path evidence = Path.of("build/reports/desktop");
        Files.createDirectories(evidence);
        System.setProperty("tetris.dataDir", data.toString());
        AtomicReference<Throwable> failure = new AtomicReference<>();
        SwingUtilities.invokeAndWait(() -> {
            Maincontainer frame = null;
            try {
                frame = new Maincontainer();
                frame.enterStart();
                check(frame.isShowing(), "real game window must be visible");
                check(frame.getContentPane().getWidth() > 0, "window must have a content area");
                JPanel start = (JPanel) field(frame, "startPanel");
                for (int turn = 0; turn < 40; turn++) key(start, KeyEvent.VK_DOWN);
                key(start, KeyEvent.VK_ENTER); // 40 is a multiple of the five menu choices.
                Gamepanel game = (Gamepanel) field(frame, "gamePanel");
                GameController controller = game.controller();
                controller.timer().stop();
                int originalY = controller.snapshot().y();
                for (int turn = 0; turn < 5; turn++) key(game, Maincontainer.DOWN);
                check(controller.snapshot().y() == originalY + 5 && controller.snapshot().score() == 5,
                    "repeated down input must move and score once per event");
                key(game, Maincontainer.PAUSE);
                int pausedY = controller.snapshot().y();
                for (int turn = 0; turn < 20; turn++) key(game, Maincontainer.DOWN);
                check(controller.snapshot().y() == pausedY && controller.snapshot().paused(), "pause must block repeated movement");
                key(game, Maincontainer.PAUSE);
                controller.timer().stop();
                check(!controller.snapshot().paused(), "pause key must resume the game");
                key(game, KeyEvent.VK_ESCAPE);
                check(game.getParent() == null && !controller.timer().isRunning(), "active game exit must remove game and timer");

                crossItemFlow(frame, evidence);

                frame.exitStartEnterSetting();
                frame.setting.nextWindowSize();
                frame.setting.nextColorPalette();
                frame.setting.exportSetting();
                frame.exitSettingEnterStart();
                check(frame.getConfiguredHeight() == 750 && frame.getConfiguredWidth() == 625,
                    "changed window size must apply to the actual frame");
                check(frame.new Setting().getColorPalette().equals(frame.setting.getColorPalette()),
                    "saved settings must reload");
                ScoreStore.append("DESKTOP", 123);
                frame.exitStartEnterScoreboard();
                Scoreboardpanel scoreboard = (Scoreboardpanel) field(frame, "scoreboardPanel");
                check(((javax.swing.JTextPane) field(scoreboard, "scorepanel")).getText().contains("DESKTOP: 123"),
                    "saved score must be displayed");
                frame.validate();
                BufferedImage image = new BufferedImage(frame.getWidth(), frame.getHeight(), BufferedImage.TYPE_INT_ARGB);
                Graphics2D graphics = image.createGraphics();
                frame.paint(graphics);
                graphics.dispose();
                ImageIO.write(image, "png", evidence.resolve("scoreboard.png").toFile());
                key(scoreboard, KeyEvent.VK_ENTER);
                check(scoreboard.getParent() == null, "scoreboard must return to menu");
                frame.exitStartEnterGame();
                game = (Gamepanel) field(frame, "gamePanel");
                controller = game.controller();
                controller.timer().stop();

                new GameEngineFixture(controller.engine()).progress(321, 0, 1);
                controller.refreshSnapshot();
                game.gameOver();
                key(game, KeyEvent.VK_ENTER);
                scoreboard = (Scoreboardpanel) field(frame, "scoreboardPanel");
                check(scoreboard.isShowing(), "game-over name entry must open the actual scoreboard");
                check(((javax.swing.JTextPane) field(scoreboard, "scorepanel")).getText().contains("PLAYER: 321 [Easy]"),
                    "new score must include its difficulty");
                key(scoreboard, KeyEvent.VK_ENTER);
                ScoreStore.clear();
                for (int rank = 1; rank <= 12; rank++) {
                    ScoreStore.append("RANK" + rank, rank * 1000, Difficulty.EASY, GameMode.CLASSIC);
                }
                frame.exitStartEnterGame();
                game = (Gamepanel) field(frame, "gamePanel");
                controller = game.controller();
                controller.timer().stop();

                game.gameOver();
                scoreboard = (Scoreboardpanel) field(frame, "scoreboardPanel");
                check(scoreboard.isShowing() && game.getParent() == null,
                    "nonqualifying score must open rankings without name entry");
                check(ScoreStore.readRanked().size() == 12, "nonqualifying score must not append a record");
                capture(frame, evidence.resolve("classic-scores.png"));
                key(scoreboard, KeyEvent.VK_ENTER);
                frame.exitStartEnterGame("Item");
                game = (Gamepanel) field(frame, "gamePanel");
                controller = game.controller();
                controller.timer().stop();

                new GameEngineFixture(controller.engine()).progress(111, 0, 1);
                controller.refreshSnapshot();
                game.gameOver();
                key(game, KeyEvent.VK_ENTER);
                scoreboard = (Scoreboardpanel) field(frame, "scoreboardPanel");
                check(ScoreStore.readRanked(GameMode.ITEM).size() == 1,
                    "full Classic rankings must not block the first Item record");
                check(((javax.swing.JTextPane) field(scoreboard, "scorepanel")).getText()
                    .contains("PLAYER: 111 [Easy] [Item]"), "result screen must select the played mode");
                ScoreStore.append("LONGNAME99", Integer.MAX_VALUE, Difficulty.NORMAL, GameMode.ITEM);
                scoreboard.drawScore();
                capture(frame, evidence.resolve("item-scores.png"));
                key(scoreboard, KeyEvent.VK_LEFT);
                check(((javax.swing.JTextPane) field(scoreboard, "scorepanel")).getText().contains("RANK12"),
                    "left key must switch Item rankings to Classic");
                check(!((javax.swing.JTextPane) field(scoreboard, "scorepanel")).getText().contains("PLAYER: 111"),
                    "Classic filter must omit Item records");
                key(scoreboard, KeyEvent.VK_ENTER);
                frame.exitStartEnterSetting();
                frame.setting.clearSetting();
                frame.exitSettingEnterStart();
                check(frame.getConfiguredWidth() == 500 && frame.getConfiguredHeight() == 600,
                    "minimum window size must be restored for long ranking labels");
                frame.exitStartEnterScoreboard();
                scoreboard = (Scoreboardpanel) field(frame, "scoreboardPanel");
                check(((javax.swing.JTextPane) field(scoreboard, "scorepanel")).getText()
                    .contains("LONGNAME99: 2147483647 [Normal] [Item]"),
                    "reopening the scoreboard must reload mode and difficulty from disk");
                capture(frame, evidence.resolve("all-scores.png"));
                key(scoreboard, KeyEvent.VK_ENTER);
                frame.setting.clearSetting();
                frame.setting.exportSetting();
                ScoreStore.clear();
                check(ScoreStore.readRanked().isEmpty(), "reset must remove saved scores");

                Integer[] customKeys = frame.setting.getKeyCodes();
                customKeys[5] = KeyEvent.VK_B;
                frame.setting.updateKeyCodes(customKeys);
                frame.setting.nextDifficulty();
                frame.setting.nextDifficulty();
                frame.setting.exportSetting();
                frame.dispose();

                frame = new Maincontainer();
                frame.enterStart();
                savedHardDropItemFlow(frame, evidence);

                configureAndRender(frame, evidence, "600X500", "Normal", "Classic",
                    "final-600x500-normal-classic.png");
                configureAndRender(frame, evidence, "750X625", "Protanopia", "Item",
                    "final-750x625-protanopia-item.png");
                configureAndRender(frame, evidence, "900X750", "Deuteranopia", "Classic",
                    "final-900x750-deuteranopia-classic.png");
                configureAndRender(frame, evidence, "600X500", "Tritanopia", "Item",
                    "final-600x500-tritanopia-item.png");

                frame.exitStartEnterSetting();
                frame.setting.clearSetting();
                frame.setting.exportSetting();
                frame.exitSettingEnterStart();
                ScoreStore.clear();
                check(Maincontainer.HARD_DROP == KeyEvent.VK_SPACE
                        && Maincontainer.colorPalette.equals("Normal")
                        && frame.getDifficulty() == Difficulty.EASY
                        && frame.getConfiguredWidth() == 500
                        && frame.getConfiguredHeight() == 600,
                    "desktop smoke must restore global settings before exit");
                Files.writeString(evidence.resolve("result.txt"),
                    "PASS real JFrame, menu loop, repeated input, pause/resume, active exit, P item/preview/300-point lock/ten-row supply, settings save/reload, ranking/reset, game-over to scoreboard, mode filters and mode-specific qualification/save/reload; saved B hard-drop applied after frame recreation; Hard Item P lock/game-over/metadata/menu return; all 3 configured sizes and all 4 palettes rendered across representative Classic/Item games with synthetic movement\n"
                    + "OS=" + System.getProperty("os.name") + " " + System.getProperty("os.version")
                    + " arch=" + System.getProperty("os.arch") + " Java=" + System.getProperty("java.version") + "\n"
                    + "Input=synthetic KeyListener events; physical keyboard and clean target installation remain separate\n");
                System.out.println("DESKTOP SMOKE PASSED");
            } catch (Throwable problem) { failure.set(problem); }
            finally {
                for (java.awt.Window window : JFrame.getWindows()) window.dispose();
                if (frame != null) {
                    try {
                        Gamepanel game = (Gamepanel) field(frame, "gamePanel");
                        if (game != null) {
                            GameController controller = game.controller();
                            game.stopGame();
                        }
                    } catch (ReflectiveOperationException ignored) { }
                }
            }
        });
        if (failure.get() != null) throw new AssertionError("Desktop smoke failed", failure.get());
    }
}
