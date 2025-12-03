package component;

import org.junit.Before;
import org.junit.Test;

import javax.swing.*;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.Assert.*;

public class PausePanelTest {

    private JFrame frame;
    private PausePanel panel;

    private AtomicBoolean resumed;
    private AtomicBoolean restarted;
    private AtomicBoolean exited;

    @Before
    public void setup() {
        SwingUtilities.invokeLater(() -> {
            frame = new JFrame();
            resumed = new AtomicBoolean(false);
            restarted = new AtomicBoolean(false);
            exited = new AtomicBoolean(false);

            panel = new PausePanel(frame,
                    () -> resumed.set(true),
                    () -> restarted.set(true),
                    () -> exited.set(true));
            frame.setSize(400, 300);
            frame.add(panel);
            frame.setVisible(true);
        });
    }

    @Test
    public void testVisibilityToggle() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            panel.showPanel();
            assertTrue(panel.isVisible());
            panel.hidePanel();
            assertFalse(panel.isVisible());
        });
    }

    @Test
    public void testButtonActionsRunCallbacks() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            for (var c : panel.getComponents()) {
                if (c instanceof JPanel inner) {
                    for (var b : inner.getComponents()) {
                        if (b instanceof JButton btn) {
                            btn.doClick();
                        }
                    }
                }
            }
        });
        Thread.sleep(100);
        assertTrue(resumed.get() || restarted.get() || exited.get());
    }
}
