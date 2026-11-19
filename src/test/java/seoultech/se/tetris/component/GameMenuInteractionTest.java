package seoultech.se.tetris.component;

import static org.junit.jupiter.api.Assertions.*;

import java.awt.Component;
import java.awt.Point;
import java.awt.event.InputEvent;
import java.awt.event.MouseEvent;
import java.util.Arrays;
import javax.swing.JButton;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;
import seoultech.se.tetris.GameMode;
import seoultech.se.tetris.blocks.OBlock;
import seoultech.se.tetris.game.GameCommand;
import seoultech.se.tetris.game.GamePhase;

class GameMenuInteractionTest {
    @Test void canceledMenuPressPreservesGameInputAndCompletedClickExitsAfterResume() throws Exception {
        ExistingFeatureFixture.onEdt(() -> {
            for (GameMode mode : GameMode.values()) {
                try (ControllerFixture f = new ControllerFixture(mode)) {
                    f.game.setSize(500, 600);
                    f.prepare(new OBlock(), new OBlock(), new int[20][10], 3, 0);
                    JButton menu = Arrays.stream(f.game.getComponents())
                        .filter(JButton.class::isInstance).map(JButton.class::cast)
                        .filter(button -> "MENU".equals(button.getText())).findFirst().orElseThrow();

                    f.controller.dispatch(GameCommand.TOGGLE_PAUSE);
                    assertNotNull(ExistingFeatureFixture.get(f.game, "pausepanel", JPanel.class));
                    f.controller.dispatch(GameCommand.TOGGLE_PAUSE);
                    assertNull(ExistingFeatureFixture.get(f.game, "pausepanel", JPanel.class));

                    Point center = SwingUtilities.convertPoint(menu,
                        menu.getWidth() / 2, menu.getHeight() / 2, f.game);
                    assertSame(menu, SwingUtilities.getDeepestComponentAt(f.game, center.x, center.y));
                    assertFalse(menu.isFocusable());

                    mouse(menu, MouseEvent.MOUSE_PRESSED, true);
                    assertTrue(menu.getModel().isPressed());
                    mouse(menu, MouseEvent.MOUSE_EXITED, false);
                    mouse(menu, MouseEvent.MOUSE_DRAGGED, false);
                    mouse(menu, MouseEvent.MOUSE_RELEASED, false);
                    assertFalse(menu.getModel().isPressed());
                    assertFalse(f.owner.exitCalled);
                    assertEquals(GamePhase.ACTIVE, f.controller.snapshot().phase());

                    int x = f.controller.snapshot().x();
                    f.controller.handleKey(Maincontainer.LEFT);
                    assertEquals(x - 1, f.controller.snapshot().x());

                    mouse(menu, MouseEvent.MOUSE_ENTERED, true);
                    mouse(menu, MouseEvent.MOUSE_PRESSED, true);
                    assertTrue(menu.getModel().isPressed());
                    mouse(menu, MouseEvent.MOUSE_RELEASED, true);
                    assertTrue(f.owner.exitCalled);
                    assertEquals(GamePhase.STOPPED, f.controller.snapshot().phase());
                    assertFalse(f.controller.timer().isRunning());
                }
            }
            return null;
        });
    }

    private static void mouse(Component target, int id, boolean inside) {
        int x = inside ? target.getWidth() / 2 : -10;
        int y = inside ? target.getHeight() / 2 : -10;
        int modifiers = id == MouseEvent.MOUSE_RELEASED || id == MouseEvent.MOUSE_ENTERED
            ? 0 : InputEvent.BUTTON1_DOWN_MASK;
        target.dispatchEvent(new MouseEvent(target, id, 1L, modifiers,
            x, y, x, y, 1, false, MouseEvent.BUTTON1));
    }
}
