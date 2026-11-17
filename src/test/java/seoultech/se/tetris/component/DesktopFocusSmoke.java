package seoultech.se.tetris.component;

import java.awt.AWTEvent;
import java.awt.Component;
import java.awt.Container;
import java.awt.Desktop;
import java.awt.Dimension;
import java.awt.KeyboardFocusManager;
import java.awt.Point;
import java.awt.Robot;
import java.awt.Toolkit;
import java.awt.event.AWTEventListener;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.function.Predicate;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollBar;
import javax.swing.JScrollPane;
import javax.swing.JTextPane;
import javax.swing.JToggleButton;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import seoultech.se.tetris.Difficulty;
import seoultech.se.tetris.GameMode;
import seoultech.se.tetris.blocks.TBlock;
import seoultech.se.tetris.score.ScoreStore;

import static seoultech.se.tetris.component.ExistingFeatureFixture.get;
import static seoultech.se.tetris.component.ExistingFeatureFixture.onEdt;
import static seoultech.se.tetris.component.ExistingFeatureFixture.set;

import seoultech.se.tetris.game.GameEngineFixture;
import seoultech.se.tetris.blocks.OBlock;

/** Real-window focus regression; default Robot input, or synthetic Swing input with the "swing" flag. */
public final class DesktopFocusSmoke {
    private static boolean swingInput;
    private static void await(Robot robot, Callable<Boolean> condition, String message) throws Exception {
        long deadline = System.nanoTime() + 3_000_000_000L;
        do {
            robot.waitForIdle();
            if (onEdt(condition)) return;
            robot.delay(50);
        } while (System.nanoTime() < deadline);
        String focus = onEdt(() -> {
            Component owner = KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusOwner();
            return owner == null ? "none" : owner.getClass().getName();
        });
        throw new AssertionError(message + "; focus owner=" + focus);
    }

    private static void key(Robot robot, int code) throws Exception {
        if (swingInput) {
            onEdt(() -> {
                KeyboardFocusManager manager = KeyboardFocusManager.getCurrentKeyboardFocusManager();
                Component owner = manager.getFocusOwner();
                if (owner == null) throw new AssertionError("No focus owner for key " + code);
                char character = code >= KeyEvent.VK_A && code <= KeyEvent.VK_Z
                    ? (char) code : KeyEvent.CHAR_UNDEFINED;
                for (int id : new int[] {KeyEvent.KEY_PRESSED, KeyEvent.KEY_RELEASED}) {
                    manager.dispatchEvent(new KeyEvent(owner, id, System.currentTimeMillis(), 0, code, character));
                }
                return null;
            });
        } else {
            robot.keyPress(code);
            robot.keyRelease(code);
        }
        robot.waitForIdle();
    }

    private static void mouse(Component target, int id, Point screenPoint) throws Exception {
        onEdt(() -> {
            Point origin = target.getLocationOnScreen();
            int modifiers = id == MouseEvent.MOUSE_PRESSED || id == MouseEvent.MOUSE_DRAGGED
                ? InputEvent.BUTTON1_DOWN_MASK : 0;
            target.dispatchEvent(new MouseEvent(target, id, System.currentTimeMillis(), modifiers,
                screenPoint.x - origin.x, screenPoint.y - origin.y, screenPoint.x, screenPoint.y,
                1, false, MouseEvent.BUTTON1));
            return null;
        });
    }

    private static void settleFocus(Robot robot) {
        // Mouse UI delegates request real native focus asynchronously, even with synthetic input.
        robot.delay(250);
        robot.waitForIdle();
    }

    private static Point point(Component component, double xRatio, double yRatio) throws Exception {
        return onEdt(() -> {
            Point result = component.getLocationOnScreen();
            java.awt.Rectangle visible = component instanceof JComponent swing
                ? swing.getVisibleRect()
                : new java.awt.Rectangle(0, 0, component.getWidth(), component.getHeight());
            if (visible.isEmpty()) throw new AssertionError("Click target is not visible: " + component);
            result.translate(visible.x + (int) (visible.width * xRatio),
                visible.y + (int) (visible.height * yRatio));
            return result;
        });
    }

    private static void click(Robot robot, Component component, double xRatio, double yRatio)
            throws Exception {
        Point target = point(component, xRatio, yRatio);
        if (swingInput) {
            mouse(component, MouseEvent.MOUSE_PRESSED, target);
            mouse(component, MouseEvent.MOUSE_RELEASED, target);
            settleFocus(robot);
        } else {
            robot.mouseMove(target.x, target.y);
            robot.mousePress(InputEvent.BUTTON1_DOWN_MASK);
            robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);
        }
        robot.waitForIdle();
    }

    private static boolean hitsTarget(AWTEvent event, Component target) {
        if (!(event instanceof MouseEvent mouse) || !(event.getSource() instanceof Component source)) {
            return false;
        }
        java.awt.Window window = SwingUtilities.getWindowAncestor(target);
        if (source != window && SwingUtilities.getWindowAncestor(source) != window) return false;
        Point topLeft = target.getLocationOnScreen();
        Point eventPoint = mouse.getLocationOnScreen();
        return eventPoint.x >= topLeft.x && eventPoint.x < topLeft.x + target.getWidth()
            && eventPoint.y >= topLeft.y && eventPoint.y < topLeft.y + target.getHeight();
    }

    private static void checkedClick(Robot robot, Component component, String message) throws Exception {
        checkedClick(robot, component, 0.5, 0.5, message);
    }

    private static void checkedClick(Robot robot, Component component, double xRatio, double yRatio,
            String message) throws Exception {
        int[] presses = {0};
        int[] releases = {0};
        StringBuilder trace = new StringBuilder();
        AWTEventListener listener = event -> {
            if (event instanceof MouseEvent mouse
                    && (event.getID() == MouseEvent.MOUSE_PRESSED
                        || event.getID() == MouseEvent.MOUSE_RELEASED)) {
                trace.append(mouse.paramString()).append(" source=")
                    .append(mouse.getSource().getClass().getName())
                    .append(" targetHit=").append(hitsTarget(event, component)).append('\n');
            }
            if (event instanceof MouseEvent mouse && SwingUtilities.isLeftMouseButton(mouse)
                    && hitsTarget(event, component)) {
                if (event.getID() == MouseEvent.MOUSE_PRESSED) presses[0]++;
                if (event.getID() == MouseEvent.MOUSE_RELEASED) releases[0]++;
            }
        };
        Toolkit.getDefaultToolkit().addAWTEventListener(listener, AWTEvent.MOUSE_EVENT_MASK);
        try {
            click(robot, component, xRatio, yRatio);
            await(robot, () -> presses[0] > 0 && releases[0] > 0, message
                + " (both press and release required)");
        } catch (AssertionError failure) {
            System.err.println(onEdt(() -> "Click diagnostics: target=" + component
                + " screen=" + component.getLocationOnScreen()
                + " pointer=" + java.awt.MouseInfo.getPointerInfo().getLocation()
                + " active=" + SwingUtilities.getWindowAncestor(component).isActive()
                + " windowEnabled=" + SwingUtilities.getWindowAncestor(component).isEnabled()
                + " presses=" + presses[0] + " releases=" + releases[0] + "\n" + trace));
            throw failure;
        } finally {
            Toolkit.getDefaultToolkit().removeAWTEventListener(listener);
        }
    }

    private static <T extends Component> T find(Component root, Class<T> type,
            Predicate<T> predicate) {
        if (type.isInstance(root)) {
            T match = type.cast(root);
            if (predicate.test(match)) return match;
        }
        if (root instanceof Container container) {
            for (Component child : container.getComponents()) {
                T match = find(child, type, predicate);
                if (match != null) return match;
            }
        }
        return null;
    }

    private static JLabel label(Component root, String text) throws Exception {
        return onEdt(() -> find(root, JLabel.class, candidate -> text.equals(candidate.getText())));
    }

    private static <T extends Component> T component(Component root, Class<T> type) throws Exception {
        return onEdt(() -> find(root, type, candidate -> true));
    }

    private static void focus(Component target) throws Exception {
        onEdt(() -> { target.requestFocusInWindow(); return null; });
    }

    private static void checkStartAndSettings(Robot robot, Maincontainer frame) throws Exception {
        Startpanel start = onEdt(() -> get(frame, "startPanel", Startpanel.class));
        focus(start);
        await(robot, start::isFocusOwner, "Start: panel must initially own keyboard focus");
        checkedClick(robot, label(start, "TETRIS"), "Start: title click must be delivered");
        checkedClick(robot, start, 0.02, 0.02, "Start: background click must be delivered");
        checkedClick(robot, label(start, "CLASSIC"), "Start: menu label click must be delivered");
        await(robot, start::isFocusOwner, "Start: content clicks must preserve keyboard focus");
        key(robot, KeyEvent.VK_DOWN);
        key(robot, KeyEvent.VK_ENTER);
        await(robot, () -> {
            Gamepanel game = get(frame, "gamePanel", Gamepanel.class);
            return game != null && game.isShowing() && game.getGameMode() == GameMode.ITEM;
        },
            "Start: native Down/Enter after clicks must open Item mode");
        key(robot, KeyEvent.VK_ESCAPE);
        await(robot, () -> {
            Startpanel current = get(frame, "startPanel", Startpanel.class);
            return current != null && current.isShowing();
        }, "Start: native Escape must restore the menu before Settings opens");

        onEdt(() -> { frame.exitStartEnterSetting(); return null; });
        Settingpanel settings = onEdt(() -> get(frame, "settingPanel", Settingpanel.class));
        focus(settings);
        await(robot, settings::isFocusOwner, "Settings: panel must initially own keyboard focus");
        checkedClick(robot, label(settings, "Window Option: "),
            "Settings: ordinary option label click must be delivered");
        JPanel settingList = onEdt(() -> get(settings, "settingPanel", JPanel.class));
        checkedClick(robot, settingList, 0.02, 0.02,
            "Settings: option-list background click must be delivered");
        await(robot, settings::isFocusOwner, "Settings: ordinary clicks must preserve keyboard focus");
        key(robot, KeyEvent.VK_DOWN);
        key(robot, KeyEvent.VK_ENTER);

        await(robot, () -> {
            JPanel current = find(settings, JPanel.class,
                candidate -> candidate.getClass().getSimpleName().equals("ChangeKeyPanel"));
            return current != null && current.isShowing();
        },
            "Settings: native Down/Enter must open Change Keys");
        JPanel changeKeys = onEdt(() -> find(settings, JPanel.class,
            candidate -> candidate.getClass().getSimpleName().equals("ChangeKeyPanel")));
        await(robot, changeKeys::isFocusOwner, "Change Keys: submenu must own keyboard focus");
        checkedClick(robot, label(changeKeys, "Rotate: "),
            "Change Keys: option label click must be delivered");
        await(robot, changeKeys::isFocusOwner, "Change Keys: option click must preserve focus");
        key(robot, KeyEvent.VK_ENTER);
        await(robot, () -> {
            JLabel current = find(changeKeys, JLabel.class,
                candidate -> "Press Key to Change".equals(candidate.getText()));
            return current != null && current.isShowing();
        },
            "Change Keys: native Enter must open key-capture prompt");
        JLabel prompt = label(changeKeys, "Press Key to Change");
        checkedClick(robot, prompt, "Change Keys: capture prompt click must be delivered");
        await(robot, changeKeys::isFocusOwner, "Change Keys: prompt click must preserve capture focus");
        key(robot, KeyEvent.VK_F2);
        await(robot, () -> label(changeKeys, "F2") != null,
            "Change Keys: native F2 must update the selected binding");
        key(robot, KeyEvent.VK_UP);
        key(robot, KeyEvent.VK_ENTER);
        await(robot, settings::isFocusOwner, "Settings: leaving Change Keys must restore focus");
        key(robot, KeyEvent.VK_UP);
        key(robot, KeyEvent.VK_UP);
        key(robot, KeyEvent.VK_ENTER);
        await(robot, () -> {
            Startpanel current = get(frame, "startPanel", Startpanel.class);
            return current != null && current.isShowing() && current.isFocusOwner();
        }, "Settings: Save and Exit must restore Start focus");
        System.out.println("PASS Start, Settings and Change Keys click/focus paths");
    }

    private static Gamepanel preparedGame(Maincontainer frame, String mode) throws Exception {
        return onEdt(() -> {
            frame.exitStartEnterGame(mode);
            Gamepanel game = get(frame, "gamePanel", Gamepanel.class);
            game.controller().timer().stop();
            new GameEngineFixture(game.controller().engine()).seed(new TBlock(), new OBlock(), 4, 4);
            game.controller().refreshSnapshot();
            return game;
        });
    }

    private static void checkGame(Robot robot, Maincontainer frame, String mode) throws Exception {
        Gamepanel game = preparedGame(frame, mode);
        Board board = onEdt(() -> get(game, "board", Board.class));
        Component preview = onEdt(() -> get(game, "nextblockpanel", Nextblockpanel.class));
        JTextPane score = onEdt(() -> get(game, "scorepanel", JTextPane.class));
        focus(game);
        await(robot, game::isFocusOwner, mode + ": game must initially own keyboard focus");
        for (Component target : new Component[] {board, preview, score}) {
            String targetName = target.getClass().getSimpleName();
            checkedClick(robot, target, mode + ": " + targetName + " click must be delivered");
            await(robot, game::isFocusOwner, mode + ": game child click must preserve focus");
            int previousY = onEdt(() -> game.controller().snapshot().y());
            key(robot, Maincontainer.DOWN);
            await(robot, () -> game.controller().snapshot().y() >= previousY + 1,
                mode + ": native movement must continue after child click");
        }

        key(robot, Maincontainer.PAUSE);
        await(robot, () -> game.controller().snapshot().paused(), mode + ": native pause must work");
        JPanel pause = onEdt(() -> get(game, "pausepanel", JPanel.class));
        checkedClick(robot, label(pause, "- PAUSED -"), mode + ": pause overlay click must be delivered");
        checkedClick(robot, score, mode + ": paused score click must be delivered");
        await(robot, game::isFocusOwner, mode + ": paused content clicks must preserve focus");
        key(robot, Maincontainer.PAUSE);
        await(robot, () -> !game.controller().snapshot().paused(), mode + ": native resume must work after clicks");
        onEdt(() -> { game.controller().timer().stop(); return null; });

        JButton menu = onEdt(() -> find(game, JButton.class, button -> "MENU".equals(button.getText())));
        int[] presses = {0};
        StringBuilder mouseTrace = new StringBuilder();
        AWTEventListener listener = event -> {
            if (event instanceof MouseEvent mouse && event.getID() == MouseEvent.MOUSE_PRESSED) {
                mouseTrace.append("source=").append(mouse.getSource().getClass().getName())
                    .append(" screen=").append(mouse.getLocationOnScreen())
                    .append(" button=").append(mouse.getButton())
                    .append(" consumed=").append(mouse.isConsumed()).append('\n');
            }
            if (event.getID() == MouseEvent.MOUSE_PRESSED && hitsTarget(event, menu)) presses[0]++;
        };
        Toolkit.getDefaultToolkit().addAWTEventListener(listener, AWTEvent.MOUSE_EVENT_MASK);
        try {
            Point from = point(menu, 0.5, 0.5);
            Point to = point(board, 0.5, 0.5);
            if (swingInput) mouse(menu, MouseEvent.MOUSE_PRESSED, from);
            else {
                robot.mouseMove(from.x, from.y);
                robot.mousePress(InputEvent.BUTTON1_DOWN_MASK);
            }
            await(robot, () -> menu.getModel().isPressed(), mode + ": MENU native press must arm the button");
            if (swingInput) {
                mouse(menu, MouseEvent.MOUSE_EXITED, to);
                mouse(menu, MouseEvent.MOUSE_DRAGGED, to);
                mouse(menu, MouseEvent.MOUSE_RELEASED, to);
                settleFocus(robot);
            } else {
                robot.mouseMove(to.x, to.y);
                robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);
            }
            robot.waitForIdle();
            await(robot, () -> presses[0] > 0 && game.getParent() != null,
                mode + ": canceled MENU click must leave the game open");
        } catch (AssertionError failure) {
            System.err.println(onEdt(() -> {
                Point center = SwingUtilities.convertPoint(menu, menu.getWidth() / 2,
                    menu.getHeight() / 2, game);
                Component hit = SwingUtilities.getDeepestComponentAt(game, center.x, center.y);
                return "MENU diagnostics: presses=" + presses[0] + " enabled=" + menu.isEnabled()
                    + " showing=" + menu.isShowing() + " pressed=" + menu.getModel().isPressed()
                    + " armed=" + menu.getModel().isArmed()
                    + " hit=" + hit + " listeners=" + Arrays.toString(menu.getMouseListeners())
                    + "\n" + mouseTrace;
            }));
            throw failure;
        } finally {
            if (!swingInput) robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);
            Toolkit.getDefaultToolkit().removeAWTEventListener(listener);
        }
        int previousX = onEdt(() -> game.controller().snapshot().x());
        key(robot, Maincontainer.LEFT);
        await(robot, () -> game.controller().snapshot().x() == previousX - 1,
            mode + ": canceled MENU click must not steal game input");
        key(robot, KeyEvent.VK_ESCAPE);
        await(robot, () -> game.getParent() == null, mode + ": native Escape must return to Start");
        System.out.println("PASS " + mode + " game child, pause and canceled MENU click paths");
    }

    private static Scoreboardpanel checkNameEntry(Robot robot, Maincontainer frame) throws Exception {
        ScoreStore.clear();
        Gamepanel game = preparedGame(frame, "Classic");
        Board board = onEdt(() -> get(game, "board", Board.class));
        onEdt(() -> { game.stopGame(); game.gameOver(); return null; });
        focus(game);
        await(robot, game::isFocusOwner, "Name entry: game panel must own capture focus");
        JLabel name = onEdt(() -> get(game, "setUserNameText", JLabel.class));
        checkedClick(robot, name, "Name entry: overlay click must be delivered");
        await(robot, game::isFocusOwner, "Name entry: overlay click must preserve capture focus");
        key(robot, KeyEvent.VK_A);
        key(robot, KeyEvent.VK_B);
        key(robot, KeyEvent.VK_BACK_SPACE);
        key(robot, KeyEvent.VK_C);
        await(robot, () -> "AC".equalsIgnoreCase(name.getText()), "Name entry: native edit keys must produce AC");
        String typedName = onEdt(name::getText);
        key(robot, KeyEvent.VK_ENTER);
        await(robot, () -> {
            Scoreboardpanel current = get(frame, "scoreboardPanel", Scoreboardpanel.class);
            return current != null && current.isShowing()
                && ScoreStore.readRanked(GameMode.CLASSIC).stream()
                    .anyMatch(entry -> entry.username().equals(typedName));
        },
            "Name entry: native Enter must save AC and open the scoreboard");
        return onEdt(() -> get(frame, "scoreboardPanel", Scoreboardpanel.class));
    }

    private static void dragBar(Robot robot, JScrollBar bar, String message) throws Exception {
        onEdt(() -> { bar.setValue(0); return null; });
        settleFocus(robot);
        int before = onEdt(bar::getValue);
        boolean vertical = bar.getOrientation() == JScrollBar.VERTICAL;
        Point from = point(bar, vertical ? 0.5 : 0.3, vertical ? 0.3 : 0.5);
        Point to = point(bar, vertical ? 0.5 : 0.85, vertical ? 0.85 : 0.5);
        if (swingInput) {
            mouse(bar, MouseEvent.MOUSE_PRESSED, from);
            mouse(bar, MouseEvent.MOUSE_DRAGGED, to);
            mouse(bar, MouseEvent.MOUSE_RELEASED, to);
            settleFocus(robot);
        } else {
            robot.mouseMove(from.x, from.y);
            robot.mousePress(InputEvent.BUTTON1_DOWN_MASK);
            robot.mouseMove(to.x, to.y);
            robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);
        }
        robot.waitForIdle();
        await(robot, () -> bar.getValue() != before, message);
    }

    private static void checkScoreboard(Robot robot, Maincontainer frame, Scoreboardpanel scoreboard)
            throws Exception {
        for (int index = 0; index < 20; index++) {
            ScoreStore.append("LONG".repeat(50) + index, 20_000 - index,
                Difficulty.HARD, index % 2 == 0 ? GameMode.CLASSIC : GameMode.ITEM);
        }
        JScrollPane rankings = onEdt(() -> component(scoreboard, JScrollPane.class));
        JTextPane text = onEdt(() -> get(scoreboard, "scorepanel", JTextPane.class));
        onEdt(() -> {
            scoreboard.drawScore();
            rankings.setBounds(rankings.getX(), rankings.getY(), rankings.getWidth(), 105);
            rankings.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_ALWAYS);
            rankings.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_ALWAYS);
            text.setPreferredSize(new Dimension(rankings.getWidth() * 3, 360));
            rankings.doLayout();
            rankings.getViewport().doLayout();
            scoreboard.revalidate();
            scoreboard.repaint();
            return null;
        });
        focus(scoreboard);
        await(robot, scoreboard::isFocusOwner, "Scoreboard: panel must initially own keyboard focus");
        checkedClick(robot, text, "Scoreboard: ranking text click must be delivered");
        await(robot, scoreboard::isFocusOwner, "Scoreboard: text click must preserve focus");

        JToggleButton classic = onEdt(() -> find(scoreboard, JToggleButton.class,
            button -> "Classic".equals(button.getText())));
        checkedClick(robot, classic, "Scoreboard: Classic filter click must be delivered");
        await(robot, classic::isSelected, "Scoreboard: Classic click must apply the filter");
        await(robot, scoreboard::isFocusOwner, "Scoreboard: filter click must restore panel focus");
        key(robot, KeyEvent.VK_RIGHT);
        JToggleButton item = onEdt(() -> find(scoreboard, JToggleButton.class,
            button -> "Item".equals(button.getText())));
        await(robot, item::isSelected, "Scoreboard: native Right must switch to Item");

        JScrollBar vertical = rankings.getVerticalScrollBar();
        JScrollBar horizontal = rankings.getHorizontalScrollBar();
        await(robot, () -> vertical.isShowing() && horizontal.isShowing(),
            "Scoreboard: constrained long rankings must expose both scrollbars");
        dragBar(robot, vertical, "Scoreboard: vertical scrollbar drag must scroll");
        await(robot, scoreboard::isFocusOwner,
            "Scoreboard: vertical scrollbar drag must preserve keyboard focus");
        key(robot, KeyEvent.VK_LEFT);
        await(robot, classic::isSelected, "Scoreboard: Left must work after vertical scrolling");
        dragBar(robot, horizontal, "Scoreboard: horizontal scrollbar drag must scroll");
        await(robot, scoreboard::isFocusOwner,
            "Scoreboard: horizontal scrollbar drag must preserve keyboard focus");
        key(robot, KeyEvent.VK_RIGHT);
        await(robot, item::isSelected, "Scoreboard: Right must work after horizontal scrolling");
        key(robot, KeyEvent.VK_ENTER);
        await(robot, () -> scoreboard.getParent() == null,
            "Scoreboard: Enter must return to Start after scrollbar use");
        System.out.println("PASS name entry and scoreboard text, filters and scrollbar paths");
    }

    public static void main(String[] args) throws Exception {
        swingInput = Arrays.asList(args).contains("swing");
        Set<String> groups = Set.copyOf(Arrays.stream(args).filter(arg -> !arg.equals("swing")).toList());
        boolean all = groups.isEmpty() || groups.contains("all");
        for (String group : groups) {
            if (!Set.of("all", "start", "game", "scoreboard").contains(group)) {
                throw new IllegalArgumentException("Unknown group: " + group
                    + " (expected start, game, scoreboard or all)");
            }
        }
        Path data = Files.createTempDirectory("tetris-focus-data-");
        Files.createFile(data.resolve("tetris_score.txt"));
        System.setProperty("tetris.dataDir", data.toString());
        Path result = Path.of("build/reports/desktop-focus/result.txt");
        Files.createDirectories(result.getParent());
        Files.deleteIfExists(result);
        Files.deleteIfExists(result.resolveSibling("failure.png"));
        Robot robot = new Robot();
        robot.setAutoDelay(70);
        Maincontainer frame = onEdt(() -> {
            Maincontainer created = new Maincontainer();
            created.setLocationRelativeTo(null);
            created.setAlwaysOnTop(true);
            created.enterStart();
            return created;
        });
        try {
            onEdt(() -> {
                if (Desktop.isDesktopSupported()
                        && Desktop.getDesktop().isSupported(Desktop.Action.APP_REQUEST_FOREGROUND)) {
                    Desktop.getDesktop().requestForeground(true);
                }
                frame.toFront();
                return null;
            });
            Startpanel initial = onEdt(() -> get(frame, "startPanel", Startpanel.class));
            focus(initial);
            await(robot, () -> frame.isActive() && initial.isFocusOwner(),
                "Startup: native window must be active before scenarios");
            key(robot, KeyEvent.VK_DOWN);
            await(robot, () -> get(initial, "menu", MenuButtons.class).selectedIndex() == 1,
                "Startup: native menu input must work before scenarios");
            key(robot, KeyEvent.VK_UP);
            await(robot, () -> get(initial, "menu", MenuButtons.class).selectedIndex() == 0,
                "Startup: restore initial menu selection");
            if (all || groups.contains("start")) checkStartAndSettings(robot, frame);
            if (all || groups.contains("game")) {
                checkGame(robot, frame, "Classic");
                checkGame(robot, frame, "Item");
            }
            if (all || groups.contains("scoreboard")) {
                checkScoreboard(robot, frame, checkNameEntry(robot, frame));
            }
            String report = "PASS " + (swingInput ? "synthetic Swing events with real focus routing" : "native AWT Robot events")
                + " click/focus groups "
                + (all ? "[start, game, scoreboard]" : groups) + ".\n"
                + "OS=" + System.getProperty("os.name") + " " + System.getProperty("os.version")
                + " Java=" + System.getProperty("java.version")
                + " LookAndFeel=" + UIManager.getLookAndFeel().getName() + "\n";
            Files.writeString(result, report);
            System.out.print(report);
        } catch (Exception | AssertionError failure) {
            try {
                javax.imageio.ImageIO.write(robot.createScreenCapture(onEdt(frame::getBounds)),
                    "png", result.resolveSibling("failure.png").toFile());
            } catch (Exception captureFailure) {
                failure.addSuppressed(captureFailure);
            }
            throw failure;
        } finally {
            onEdt(() -> {
                Gamepanel game = get(frame, "gamePanel", Gamepanel.class);
                if (game != null) {
                    Board board = get(game, "board", Board.class);
                    game.stopGame();
                }
                frame.dispose();
                return null;
            });
        }
    }
}
