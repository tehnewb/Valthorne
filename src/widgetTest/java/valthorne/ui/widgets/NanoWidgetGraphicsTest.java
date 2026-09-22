package valthorne.ui.widgets;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.lwjgl.BufferUtils;
import valthorne.JGL;
import valthorne.JGLConfiguration;
import valthorne.Keyboard;
import valthorne.Mouse;
import valthorne.Window;
import valthorne.event.events.*;
import valthorne.graphics.Color;
import valthorne.ui.UINode;
import valthorne.ui.UIRoot;
import valthorne.ui.nodes.nano.*;
import valthorne.ui.nodes.nano.NanoPanel;
import valthorne.ui.theme.ProfessionalTheme;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.opengl.GL11.*;

/**
 * Hidden-window tests using real GL rendering, Yoga layout and published input.
 * These tests fail when the required graphics context cannot be created; they do
 * not silently replace rendering with mocks or skip pixel assertions.
 * @author Albert Beaupre
 */
@Tag("graphics")
class NanoWidgetGraphicsTest {
    @TempDir Path temporary; // Isolated local directory for browser integration tests.

    /**
     * Exercises the title-bar X through actual pointer and keyboard routing, checking
     * cancelled clicks, single notifications, retained content, and fixed-window closing.
     */
    @Test void windowCloseButtonClosesAndReopensWithoutLosingContent() {
        UIRoot root = new UIRoot();
        try (ProfessionalTheme theme = new ProfessionalTheme()) {
            root.setTheme(theme.create());
            var window = new valthorne.ui.nodes.nano.NanoWindow("Long window title that must leave room for the X")
                    .bounds(100, 100, 260, 200);
            NanoTextField editor = new NanoTextField("Name").text("Saved value");
            editor.getLayout().height(36); window.content(editor);
            int[] calls = {0};
            window.onClose(() -> {
                assertFalse(window.isVisible()); assertNull(root.getCaptured()); assertNull(root.getFocused()); calls[0]++;
            });
            root.add(window); draw(root);
            var frame = window.getFrame(); long nativeNode = editor.getYogaMemoryAddress();
            drag(root, 335, 120, 450, 160);
            assertTrue(window.isVisible()); assertEquals(frame, window.getFrame()); assertEquals(0, calls[0]);
            click(335, 120);
            assertFalse(window.isVisible()); assertEquals(1, calls[0]); assertFalse(glfwWindowShouldClose(Window.getAddress()));
            window.close(); assertEquals(1, calls[0]);
            window.open(); draw(root);
            assertEquals(frame, window.getFrame()); assertEquals("Saved value", editor.getText());
            assertEquals(nativeNode, editor.getYogaMemoryAddress()); assertSame(window.getTitleBar(), root.getFocused());
            window.bounds(80, 60, 320, 220).draggable(false).resizable(false); draw(root);
            click(375, 80); assertFalse(window.isVisible()); assertEquals(2, calls[0]);
            for (int code : new int[]{Keyboard.ENTER, Keyboard.SPACE}) {
                window.open(); root.setFocusTo(window.getCloseButton()); key(code);
                assertFalse(window.isVisible());
            }
            assertEquals(4, calls[0]);
            window.open(); window.setEnabled(false); draw(root); click(375, 80); window.close();
            assertTrue(window.isVisible()); assertEquals(4, calls[0]);
        } finally { root.dispose(); }
    }

    /**
     * Closing during movement or resizing must release capture immediately, restore
     * the cursor, and prevent later drag events from changing the reopened frame.
     * Closing another window must preserve focus outside the closed subtree.
     */
    @Test void windowCloseCancelsItsOwnInputAndAllowsRemovalCallbacks() {
        UIRoot root = new UIRoot();
        try (ProfessionalTheme theme = new ProfessionalTheme()) {
            root.setTheme(theme.create()); Mouse.setCursor(Mouse.CURSOR_ARROW);
            var window = new valthorne.ui.nodes.nano.NanoWindow("Close during capture").bounds(100, 100, 260, 200);
            NanoButton outside = place(new NanoButton("Outside"), 400, 40, 150, 36);
            root.add(window, outside); draw(root);
            for (int[] point : new int[][]{{150, 120}, {358, 200}}) {
                window.open(); draw(root); var frame = window.getFrame();
                hover(point[0], point[1]); JGL.publish(new MousePressEvent(0, 0, point[0], 480 - point[1]));
                assertNotNull(root.getCaptured()); window.close();
                assertNull(root.getCaptured()); assertNull(root.getFocused()); assertEquals(Mouse.CURSOR_ARROW, Mouse.getCursorShape());
                window.open();
                JGL.publish(new MouseDragEvent(0, 0, point[0], 480 - point[1], 500, 100));
                JGL.publish(new MouseReleaseEvent(0, 0, 500, 100)); draw(root);
                assertEquals(frame, window.getFrame());
            }
            root.setFocusTo(outside); window.close(); assertSame(outside, root.getFocused());
            window.open().onClose(() -> root.remove(window)); click(335, 120);
            assertNull(window.getParent()); assertNull(root.getCaptured()); draw(root);
        } finally { root.dispose(); Mouse.setCursor(Mouse.CURSOR_ARROW); }
    }

    /**
     * Publishes an uncaptured hover in top-left coordinates, exercising the native cursor path.
     * @param x horizontal screen coordinate
     * @param y top-based screen coordinate
     */
    private static void hover(int x, int y) {
        JGL.publish(new MouseMoveEvent(-1, 0, x, 480 - y, x, 480 - y));
    }

    /** Verifies hover, capture, orientation, and release cursor behavior on Nano dividers. */
    @Test void splitDividerShowsAxisResizeCursor() {
        UIRoot root = new UIRoot();
        try (ProfessionalTheme theme = new ProfessionalTheme()) {
            root.setTheme(theme.create()); Mouse.setCursor(Mouse.CURSOR_HAND);
            NanoSplitPane split = place(new NanoSplitPane(new NanoPanel(), new NanoPanel()), 100, 100, 300, 200);
            root.add(split); draw(root);
            hover(246, 150); assertEquals(Mouse.CURSOR_HRESIZE, Mouse.getCursorShape());
            JGL.publish(new MousePressEvent(0, 0, 246, 330));
            hover(450, 350); assertEquals(Mouse.CURSOR_HRESIZE, Mouse.getCursorShape());
            JGL.publish(new MouseReleaseEvent(0, 0, 450, 130));
            hover(450, 350); assertEquals(Mouse.CURSOR_HAND, Mouse.getCursorShape());
            split.vertical(true); draw(root);
            hover(200, 200); assertEquals(Mouse.CURSOR_VRESIZE, Mouse.getCursorShape());
            split.getDivider().setEnabled(false); hover(450, 350); hover(200, 200);
            assertEquals(Mouse.CURSOR_HAND, Mouse.getCursorShape());
        } finally { root.dispose(); Mouse.setCursor(Mouse.CURSOR_ARROW); }
    }

    /**
     * Exercises the installed native mouse producer's reused drag events, including
     * reversal during one held gesture at every corner. Native movement reports the
     * previous sample as its origin, while the window must retain the initial anchor.
     * @throws Exception if native callback initialization or cleanup cannot be invoked
     */
    @Test void nativeCornerDragsReverseWithoutLosingEitherAxis() throws Exception {
        var initialize = Mouse.class.getDeclaredMethod("init"); initialize.setAccessible(true); initialize.invoke(null);
        long nativeWindow = Window.getAddress();
        var cursor = glfwSetCursorPosCallback(nativeWindow, null);
        var buttons = glfwSetMouseButtonCallback(nativeWindow, null);
        glfwSetCursorPosCallback(nativeWindow, cursor); glfwSetMouseButtonCallback(nativeWindow, buttons);
        UIRoot root = new UIRoot();
        try (ProfessionalTheme theme = new ProfessionalTheme()) {
            root.setTheme(theme.create());
            var window = new valthorne.ui.nodes.nano.NanoWindow("Native corner test").bounds(100, 100, 260, 200);
            root.add(window); draw(root);
            for (int horizontal : new int[]{-1, 1}) for (int vertical : new int[]{-1, 1}) {
                window.bounds(100, 100, 260, 200); draw(root);
                int x = horizontal < 0 ? 110 : 350, y = vertical < 0 ? 102 : 298;
                cursor.invoke(nativeWindow, x, y); buttons.invoke(nativeWindow, GLFW_MOUSE_BUTTON_LEFT, GLFW_PRESS, 0);
                cursor.invoke(nativeWindow, x + horizontal * 30, y + vertical * 30); draw(root);
                assertEquals(290, window.getWidth()); assertEquals(230, window.getHeight());
                cursor.invoke(nativeWindow, x - horizontal * 20, y - vertical * 20); draw(root);
                assertEquals(240, window.getWidth()); assertEquals(180, window.getHeight());
                cursor.invoke(nativeWindow, x + horizontal * 10, y - vertical * 10); draw(root);
                assertEquals(270, window.getWidth()); assertEquals(190, window.getHeight());
                buttons.invoke(nativeWindow, GLFW_MOUSE_BUTTON_LEFT, GLFW_RELEASE, 0);
                assertNull(root.getCaptured());
            }
        } finally {
            root.dispose();
            var dispose = Mouse.class.getDeclaredMethod("dispose"); dispose.setAccessible(true); dispose.invoke(null);
        }
    }

    /**
     * Checks every resize cursor, diagonal border arms, captured cursor persistence,
     * policy changes, hidden/detached windows, and restoration of the application cursor.
     */
    @Test void windowResizeCursorsFollowHoverCaptureAndLifecycle() {
        UIRoot root = new UIRoot();
        try (ProfessionalTheme theme = new ProfessionalTheme()) {
            root.setTheme(theme.create()); Mouse.setCursor(Mouse.CURSOR_HAND);
            var window = new valthorne.ui.nodes.nano.NanoWindow("Cursor test").bounds(100, 100, 260, 200);
            root.add(window); draw(root);
            int[][] cases = {{102,200,Mouse.CURSOR_HRESIZE},{358,200,Mouse.CURSOR_HRESIZE},
                    {230,102,Mouse.CURSOR_VRESIZE},{230,298,Mouse.CURSOR_VRESIZE},
                    {102,110,Mouse.CURSOR_RESIZE_NWSE},{110,102,Mouse.CURSOR_RESIZE_NWSE},
                    {358,110,Mouse.CURSOR_RESIZE_NESW},{350,102,Mouse.CURSOR_RESIZE_NESW},
                    {102,290,Mouse.CURSOR_RESIZE_NESW},{110,298,Mouse.CURSOR_RESIZE_NESW},
                    {358,290,Mouse.CURSOR_RESIZE_NWSE},{350,298,Mouse.CURSOR_RESIZE_NWSE}};
            for (int[] sample : cases) {
                hover(sample[0], sample[1]); assertEquals(sample[2], Mouse.getCursorShape());
                assertEquals(GLFW_NO_ERROR, glfwGetError(null), "native resize cursor must be available");
            }
            hover(180, 150); assertEquals(Mouse.CURSOR_HAND, Mouse.getCursorShape(), "body restores application cursor");
            hover(358, 200); JGL.publish(new MousePressEvent(0, 0, 358, 280));
            JGL.publish(new MouseDragEvent(0, 0, 358, 280, 500, 40)); draw(root);
            assertEquals(Mouse.CURSOR_HRESIZE, Mouse.getCursorShape(), "captured drag keeps its edge cursor outside the frame");
            Mouse.setCursor(Mouse.CURSOR_CROSSHAIR); assertEquals(Mouse.CURSOR_HRESIZE, Mouse.getCursorShape());
            JGL.publish(new MouseReleaseEvent(0, 0, 500, 40)); assertEquals(Mouse.CURSOR_CROSSHAIR, Mouse.getCursorShape());
            window.bounds(100, 100, 260, 200); draw(root); hover(102, 110);
            window.resizable(false); draw(root); assertEquals(Mouse.CURSOR_CROSSHAIR, Mouse.getCursorShape());
            window.resizable(true); draw(root); hover(358, 290); window.setVisible(false); draw(root);
            assertEquals(Mouse.CURSOR_CROSSHAIR, Mouse.getCursorShape());
            window.setVisible(true); draw(root); hover(358, 290); root.remove(window);
            assertEquals(Mouse.CURSOR_CROSSHAIR, Mouse.getCursorShape());
            root.add(window); draw(root); hover(102, 110); root.cancelInput();
            assertEquals(Mouse.CURSOR_CROSSHAIR, Mouse.getCursorShape());
        } finally { root.dispose(); Mouse.setCursor(Mouse.CURSOR_ARROW); }
    }

    /**
     * Ensures cursor overrides preserve a custom image cursor and stale owners cannot
     * remove a newer owner's override. This exercises actual native cursor allocation.
     */
    @Test void temporaryResizeCursorsPreserveCustomCursorAndOwnerIdentity() {
        Object first = new Object(), second = new Object();
        var pixels = BufferUtils.createByteBuffer(4 * 4 * 4);
        while (pixels.hasRemaining()) pixels.put((byte) 255); pixels.flip();
        try {
            Mouse.setCursor(new valthorne.graphics.texture.TextureData(pixels, 4, 4), 0, 0);
            assertEquals(0, Mouse.getCursorShape());
            Mouse.overrideCursor(first, Mouse.CURSOR_RESIZE_NWSE);
            Mouse.overrideCursor(second, Mouse.CURSOR_RESIZE_NESW);
            Mouse.clearCursorOverride(first); assertEquals(Mouse.CURSOR_RESIZE_NESW, Mouse.getCursorShape());
            Mouse.clearCursorOverride(second); assertEquals(0, Mouse.getCursorShape());
            assertEquals(GLFW_NO_ERROR, glfwGetError(null));
        } finally { Mouse.clearCursorOverride(first); Mouse.clearCursorOverride(second); Mouse.setCursor(Mouse.CURSOR_ARROW); }
    }

    /**
     * Grabs the border near every corner and verifies outward, inward, and mixed-axis
     * diagonal movement rather than requiring an exact six-pixel corner square.
     */
    @Test void windowCornersResizeInBothDirectionsFromAdjacentBorders() {
        UIRoot root = new UIRoot();
        try (ProfessionalTheme theme = new ProfessionalTheme()) {
            root.setTheme(theme.create());
            var window = new valthorne.ui.nodes.nano.NanoWindow("Resize").bounds(100, 100, 260, 200).minimumSize(160, 100);
            root.add(window); draw(root);
            for (int horizontal : new int[]{-1, 1}) for (int vertical : new int[]{-1, 1}) {
                for (int growX : new int[]{-1, 1}) for (int growY : new int[]{-1, 1}) {
                    window.bounds(100, 100, 260, 200); draw(root);
                    int x = horizontal < 0 ? 102 : 358, y = vertical < 0 ? 110 : 290;
                    drag(root, x, y, x + horizontal * growX * 20, y + vertical * growY * 20);
                    assertEquals(260 + growX * 20, window.getWidth(), "corner width");
                    assertEquals(200 + growY * 20, window.getHeight(), "corner height");
                    assertEquals(horizontal < 0 ? 100 - growX * 20 : 100, window.getFrame().x());
                    assertEquals(vertical < 0 ? 100 - growY * 20 : 100, window.getFrame().y());
                }
            }
        } finally { root.dispose(); }
    }

    /**
     * Publishes a captured drag using top-left test coordinates, laying out during movement.
     * @param root active root
     * @param x initial horizontal coordinate
     * @param y initial top coordinate
     * @param toX final horizontal coordinate
     * @param toY final top coordinate
     */
    private static void drag(UIRoot root, int x, int y, int toX, int toY) {
        JGL.publish(new MousePressEvent(0, 0, x, 480 - y));
        JGL.publish(new MouseDragEvent(0, 0, x, 480 - y, toX, 480 - toY)); draw(root);
        JGL.publish(new MouseReleaseEvent(0, 0, toX, 480 - toY)); draw(root);
    }

    /**
     * Tests title capture across consecutive moves, every edge/corner, clamped opposite
     * edges, and content geometry following actual Yoga resizing.
     */
    @Test void windowMovesAndResizesEveryEdgeWithStableAnchors() {
        UIRoot root = new UIRoot();
        try (ProfessionalTheme theme = new ProfessionalTheme()) {
            root.setTheme(theme.create());
            var window = new valthorne.ui.nodes.nano.NanoWindow("Inspector").bounds(100, 100, 240, 180).minimumSize(180, 120).maximumSize(400, 300);
            root.add(window); draw(root);
            var events = new ArrayList<valthorne.ui.nodes.nano.NanoWindow.Frame>(); window.onChange(events::add);
            JGL.publish(new MousePressEvent(0, 0, 150, 360));
            JGL.publish(new MouseDragEvent(0, 0, 150, 360, 200, 330)); draw(root);
            JGL.publish(new MouseDragEvent(0, 0, 150, 360, 230, 300)); draw(root);
            JGL.publish(new MouseReleaseEvent(0, 0, 230, 300));
            assertEquals(new valthorne.ui.nodes.nano.NanoWindow.Frame(180, 160, 240, 180), window.getFrame());
            assertEquals(2, events.size()); assertNull(root.getCaptured());
            int[][] directions = {{-1,0},{1,0},{0,-1},{0,1},{-1,-1},{1,-1},{-1,1},{1,1}};
            for (int[] direction : directions) {
                window.bounds(100, 100, 240, 180); draw(root);
                int x = direction[0] < 0 ? 102 : direction[0] > 0 ? 338 : 220;
                int y = direction[1] < 0 ? 102 : direction[1] > 0 ? 278 : 190;
                drag(root, x, y, x + direction[0] * 20, y + direction[1] * 20);
                assertEquals(direction[0] < 0 ? 80 : 100, window.getFrame().x());
                assertEquals(direction[1] < 0 ? 80 : 100, window.getFrame().y());
                assertEquals(direction[0] == 0 ? 240 : 260, window.getWidth());
                assertEquals(direction[1] == 0 ? 180 : 200, window.getHeight());
                assertEquals(window.getWidth() - 12, window.getContentPane().getWidth(), 1);
            }
            window.bounds(100, 100, 240, 180); draw(root); drag(root, 102, 190, 400, 190);
            assertEquals(180, window.getWidth()); assertEquals(160, window.getFrame().x());
            assertEquals(340, window.getFrame().x() + window.getWidth(), "right edge remains anchored at minimum width");
        } finally { root.dispose(); }
    }

    /**
     * Confirms independent policy switches, keyboard movement, parent containment, and
     * cancellation when policy changes, content is removed, or a window is disabled.
     */
    @Test void windowPoliciesContainmentAndCaptureCancellationWork() {
        UIRoot root = new UIRoot();
        try (ProfessionalTheme theme = new ProfessionalTheme()) {
            root.setTheme(theme.create());
            NanoPanel parent = place(new NanoPanel(), 30, 40, 400, 300);
            var window = new valthorne.ui.nodes.nano.NanoWindow("Nested").bounds(20, 30, 220, 160); parent.add(window); root.add(parent); draw(root);
            window.draggable(false); drag(root, 100, 90, 150, 130); assertEquals(20, window.getFrame().x());
            drag(root, 268, 150, 288, 150); assertEquals(240, window.getWidth());
            window.draggable(true).resizable(false); draw(root); drag(root, 288, 150, 310, 150); assertEquals(240, window.getWidth());
            root.setFocusTo(window.getTitleBar()); key(Keyboard.RIGHT); draw(root); assertEquals(28, window.getFrame().x());
            drag(root, 110, 90, 900, 800); assertEquals(160, window.getFrame().x()); assertEquals(140, window.getFrame().y());
            window.bounds(20, 30, 220, 160).resizable(true); draw(root);
            JGL.publish(new MousePressEvent(0, 0, 100, 390)); window.draggable(false);
            JGL.publish(new MouseDragEvent(0, 0, 100, 390, 200, 300)); draw(root); assertEquals(20, window.getFrame().x());
            JGL.publish(new MouseReleaseEvent(0, 0, 200, 300));
            window.draggable(true); JGL.publish(new MousePressEvent(0, 0, 100, 390));
            assertNotNull(root.getCaptured()); parent.remove(window); assertNull(root.getCaptured());
            parent.add(window); draw(root); window.setEnabled(false); drag(root, 100, 90, 200, 140);
            assertEquals(20, window.getFrame().x());
        } finally { root.dispose(); }
    }

    /**
     * Checks stacking preserves native nodes and child input, then captures both themes
     * with overlapping windows for visual review of titles, client clipping, and frames.
     * @throws IOException if framebuffer evidence cannot be written
     */
    @Test void windowStackingKeepsChildFocusAndRendersBothThemes() throws IOException {
        for (boolean light : new boolean[]{false, true}) {
            UIRoot root = new UIRoot();
            try (ProfessionalTheme theme = new ProfessionalTheme(light, 1)) {
                root.setTheme(theme.create());
                NanoPanel background = place(new NanoPanel(), 0, 0, 640, 480); background.setStyleName("surface"); root.add(background);
                var first = new valthorne.ui.nodes.nano.NanoWindow("Scene inspector").bounds(30, 40, 330, 280);
                var second = new valthorne.ui.nodes.nano.NanoWindow("Properties (fixed size)").bounds(265, 165, 310, 220).resizable(false);
                NanoTextField editor = new NanoTextField("Object name").text("Player"); editor.getLayout().height(36);
                NanoButton action = new NanoButton("Apply"); action.getLayout().height(36); int[] clicks = {0}; action.action(button -> clicks[0]++);
                first.getContentPane().getLayout().gap(8); first.getContentPane().add(editor, action, new NanoLabel("Drag the title. Resize any border."));
                second.getContentPane().add(new NanoLabel("Content stays inside the window."), new NanoNumberSpinner(0, 100, 1, 25));
                root.add(first, second); draw(root);
                long node = editor.getYogaMemoryAddress();
                click(100, 100); assertSame(first, root.get(root.size() - 1)); assertSame(editor, root.getFocused());
                assertEquals(node, editor.getYogaMemoryAddress());
                click(100, 145); assertEquals(1, clicks[0]);
                second.bringToFront(); draw(root); capture(light ? "windows-light.png" : "windows-dark.png");
            } finally { root.dispose(); }
        }
    }

    /**
     * Checks scaled viewport deltas and confirms oversized client controls cannot receive
     * pointer input through the title or beyond the window's clipped body.
     */
    @Test void windowInputUsesViewportUnitsAndClipsOverflow() {
        UIRoot root = new UIRoot();
        try (ProfessionalTheme theme = new ProfessionalTheme()) {
            root.setTheme(theme.create());
            root.setViewport(new valthorne.viewport.StretchViewport(320, 240));
            var window = new valthorne.ui.nodes.nano.NanoWindow("A very long title that must stay inside its own frame")
                    .minimumSize(100, 80).bounds(30, 30, 180, 140);
            NanoButton overflow = new NanoButton("Wide content"); overflow.getLayout().width(500).height(500);
            int[] calls = {0}; overflow.action(button -> calls[0]++); window.content(overflow); root.add(window); draw(root);
            drag(root, 100, 100, 140, 120);
            assertEquals(50, window.getFrame().x(), .01); assertEquals(40, window.getFrame().y(), .01);
            click(600, 420); assertEquals(0, calls[0], "clipped content must not receive outside clicks");
            click(150, 200); assertEquals(1, calls[0]);
            window.resizable(false); root.setFocusTo(window.getTitleBar()); key(Keyboard.RIGHT); draw(root);
            assertEquals(58, window.getFrame().x(), .01);
        } finally { root.dispose(); }
    }

    /**
     * Verifies the reference layout's pane geometry and real tree/address navigation.
     * Tree disclosure and keyboard expansion must not accidentally activate a directory.
     * @throws IOException if temporary fixture or chooser creation fails
     */
    @Test void chooserHasTwoPanesAndTreeAndAddressNavigateTogether() throws IOException {
        Path folder = Files.createDirectory(temporary.resolve("Assets")).toRealPath();
        Files.writeString(folder.resolve("material.json"), "{}");
        UIRoot root = new UIRoot();
        try (ProfessionalTheme theme = new ProfessionalTheme(true, 1)) {
            root.setTheme(theme.create());
            NanoFileChooser chooser = place(new NanoFileChooser(temporary), 8, 8, 624, 464); root.add(chooser); draw(root);
            NanoDirectoryTree tree = chooser.getDirectoryTree(); NanoFileExplorer explorer = chooser.getExplorer();
            assertTrue(tree.getAbsoluteX() + tree.getWidth() <= explorer.getAbsoluteX());
            assertEquals(tree.getAbsoluteY(), explorer.getAbsoluteY(), 1);
            assertTrue(chooser.getFilenameField().getAbsoluteY() >= explorer.getAbsoluteY() + explorer.getHeight());
            tree.expand(temporary.toRealPath()); tree.reveal(folder); draw(root);
            int index = -1;
            for (int i = 0; i < tree.getVisibleBranches().size(); i++) if (tree.getVisibleBranches().get(i).path().equals(folder)) { index = i; break; }
            assertTrue(index >= 0); tree.getList().scrollToIndex(index); draw(root);
            UINode row = tree.getList().getItemNode(index); root.setFocusTo(row); key(Keyboard.ENTER); draw(root);
            assertEquals(folder, explorer.getDirectory());
            chooser.editAddress(); chooser.getLocationField().text(temporary.toString()); key(Keyboard.ENTER); draw(root);
            assertEquals(temporary.toRealPath(), explorer.getDirectory());
            root.setFocusTo(chooser.getFilenameField()); JGL.publish(new KeyPressEvent(Keyboard.LEFT, GLFW_MOD_ALT)); draw(root);
            assertEquals(folder, explorer.getDirectory());
            JGL.publish(new KeyPressEvent(Keyboard.L, GLFW_MOD_CONTROL)); assertSame(chooser.getLocationField(), root.getFocused());
            key(Keyboard.ESCAPE); assertSame(chooser.getFilenameField(), root.getFocused());
            capture("chooser-reference-layout.png");
        } finally { root.dispose(); }
    }

    /**
     * Routes pointer and keyboard edits through the wheel, verifies painted hue pixels,
     * and captures both themes for checking the gradient and selection marker.
     * @throws IOException if visual evidence cannot be saved
     */
    @Test void wheelRendersAndAcceptsPointerAndKeyboardInput() throws IOException {
        for (boolean light : new boolean[]{false, true}) {
            UIRoot root = new UIRoot();
            try (ProfessionalTheme theme = new ProfessionalTheme(light, 1)) {
                root.setTheme(theme.create());
                NanoPanel background = place(new NanoPanel(), 0, 0, 640, 480); background.setStyleName("surface"); root.add(background);
                NanoColorPicker picker = place(new NanoColorPicker().mode(NanoColorPicker.Mode.WHEEL), 20, 20, 300, 380);
                root.add(picker); draw(root);
                UINode wheel = picker.getWheel();
                int cx = Math.round(wheel.getAbsoluteX() + wheel.getWidth() / 2);
                int cy = Math.round(wheel.getAbsoluteY() + wheel.getHeight() / 2);
                int radius = Math.round(Math.min(wheel.getWidth(), wheel.getHeight()) / 2 - 8);
                click(cx + radius - 1, cy); assertTrue(picker.getColor().r() > .98f); assertTrue(picker.getColor().g() < .03f);
                click(cx, cy); assertEquals("#FFFFFFFF", picker.getColor().toHex());
                JGL.publish(new MousePressEvent(0, 0, cx, 480 - cy));
                JGL.publish(new MouseDragEvent(0, 0, cx, 480 - cy, cx + radius + 60, 480 - cy));
                JGL.publish(new MouseReleaseEvent(0, 0, cx + radius + 60, 480 - cy));
                assertEquals("#FFFF0000", picker.getColor().toHex(), "captured drags clamp beyond the wheel rim");
                root.setFocusTo(wheel); key(Keyboard.END); assertEquals("#FFFF0000", picker.getColor().toHex());
                root.setFocusTo(picker.getBrightness()); key(Keyboard.HOME); assertEquals("#FF000000", picker.getColor().toHex());
                key(Keyboard.END); assertEquals("#FFFF0000", picker.getColor().toHex());
                root.setFocusTo(picker.getChannel(3)); key(Keyboard.HOME);
                assertEquals("#00FF0000", picker.getColor().toHex());
                key(Keyboard.END);
                picker.selectHSV(2f / 3, .65f, 1); draw(root);
                var pixel = BufferUtils.createByteBuffer(4);
                glReadPixels(cx + radius - 10, 480 - cy, 1, 1, GL_RGBA, GL_UNSIGNED_BYTE, pixel);
                assertTrue((pixel.get(0) & 255) > 220); assertTrue((pixel.get(1) & 255) < 70);
                capture(light ? "wheel-light.png" : "wheel-dark.png");
            } finally { root.dispose(); }
        }
    }

    /**
     * Tests actual double-click directory navigation, filename Enter approval, modal
     * focus restoration, reopening, Escape cancellation, and both chooser palettes.
     * @throws IOException if filesystem setup or screenshot output fails
     */
    @Test void chooserDialogNavigatesApprovesAndRestoresFocus() throws IOException {
        Path folder = Files.createDirectory(temporary.resolve("folder")).toRealPath();
        Path file = Files.writeString(folder.resolve("scene.json"), "{}").toRealPath();
        for (boolean light : new boolean[]{false, true}) {
            UIRoot root = new UIRoot();
            try (ProfessionalTheme theme = new ProfessionalTheme(light, 1)) {
                root.setTheme(theme.create());
                NanoButton owner = place(new NanoButton("Choose file"), 10, 10, 160, 36); root.add(owner); draw(root); root.setFocusTo(owner);
                NanoFileChooser chooser = new NanoFileChooser(temporary);
                chooser.filters(List.of(new NanoFileChooser.Filter("Scenes (*.json)", List.of("json")), new NanoFileChooser.Filter("All files", List.of())));
                var results = new ArrayList<List<Path>>(); int[] cancellations = {0};
                chooser.onApprove(results::add).onCancel(() -> cancellations[0]++).showDialog(owner); draw(root);
                NanoVirtualList rows = chooser.getExplorer().getList();
                UINode first = rows.getItemNode(0);
                int x = Math.round(first.getAbsoluteX() + 40), y = Math.round(first.getAbsoluteY() + 16);
                capture("chooser-before-navigation.png");
                click(x, y);
                assertNotNull(chooser.getExplorer().getSelected(), "row click at " + x + "," + y + " focus=" + root.getFocused());
                click(x, y); draw(root);
                assertEquals(folder, chooser.getExplorer().getDirectory()); assertTrue(results.isEmpty());
                capture(light ? "chooser-light.png" : "chooser-dark.png");
                chooser.getFilenameField().text("scene.json"); root.setFocusTo(chooser.getFilenameField()); key(Keyboard.ENTER);
                assertEquals(List.of(List.of(file)), results); assertSame(owner, root.getFocused());
                chooser.showDialog(owner); draw(root); click(2, 240); key(Keyboard.ESCAPE);
                assertEquals(1, cancellations[0]); assertSame(owner, root.getFocused()); draw(root);
            } finally { root.dispose(); }
        }
    }

    /**
     * Exercises an actual filter popup inside a modal and pointer-driven overwrite
     * confirmation. Escape dismisses the nested popup without cancelling the chooser.
     * @throws IOException if fixture creation or screenshot output fails
     */
    @Test void chooserFilterPopupAndOverwriteConfirmationAreInteractive() throws IOException {
        Path file = Files.writeString(temporary.resolve("scene.json"), "unchanged").toRealPath();
        Files.writeString(temporary.resolve("notes.txt"), "notes");
        UIRoot root = new UIRoot();
        try (ProfessionalTheme theme = new ProfessionalTheme()) {
            root.setTheme(theme.create());
            NanoButton owner = place(new NanoButton("Save"), 10, 10, 100, 36); root.add(owner); draw(root); root.setFocusTo(owner);
            NanoFileChooser chooser = new NanoFileChooser(temporary).mode(NanoFileChooser.Mode.SAVE);
            chooser.filters(List.of(new NanoFileChooser.Filter("Scenes", List.of("json")), new NanoFileChooser.Filter("All files", List.of())));
            var results = new ArrayList<List<Path>>(); int[] cancelled = {0};
            chooser.onApprove(results::add).onCancel(() -> cancelled[0]++).showDialog(owner); draw(root);
            NanoComboBox<?> filters = chooser.getFilterBox();
            root.setFocusTo(filters); key(Keyboard.ENTER); draw(root); assertTrue(filters.isOpen());
            key(Keyboard.ESCAPE); assertFalse(filters.isOpen()); assertEquals(0, cancelled[0]);
            key(Keyboard.ENTER); key(Keyboard.END); key(Keyboard.ENTER); draw(root);
            assertEquals(2, chooser.getExplorer().getEntries().size());
            chooser.getFilenameField().text("scene.json"); root.setFocusTo(chooser.getFilenameField()); key(Keyboard.ENTER); draw(root);
            assertEquals(file, chooser.getPendingOverwrite()); assertTrue(results.isEmpty());
            capture("chooser-overwrite.png");
            NanoPanel prompt = chooser.getConfirmationPanel(); UINode replace = prompt.get(0);
            click(Math.round(replace.getAbsoluteX() + 40), Math.round(replace.getAbsoluteY() + 18));
            assertEquals(List.of(List.of(file)), results); assertSame(owner, root.getFocused());
            assertEquals("unchanged", Files.readString(file)); draw(root);
        } finally { root.dispose(); }
    }

    /**
     * Captures all new controls together in both palettes, then an open menu, so
     * visual review can detect clipped labels and layout defects beyond state assertions.
     * @throws IOException if fixture files or PNG output cannot be written
     */
    @Test void widgetGalleryProducesReviewableFrames() throws IOException {
        Files.createDirectory(temporary.resolve("Textures"));
        Files.writeString(temporary.resolve("scene.json"), "{}");
        Files.writeString(temporary.resolve("material.json"), "{}");
        for (boolean light : new boolean[]{false, true}) {
            UIRoot root = new UIRoot();
            try (ProfessionalTheme theme = new ProfessionalTheme(light, 1)) {
                root.setTheme(theme.create());
                NanoPanel background = place(new NanoPanel(), 0, 0, 640, 480);
                background.setStyleName("surface"); root.add(background);
                NanoMenuBar bar = place(new NanoMenuBar().addMenu("File", List.of(
                        new NanoPopupMenu.Item("Save scene", () -> {}, true),
                        new NanoPopupMenu.Item("Export (unavailable)", () -> {}, false))), 12, 8, 610, 36);
                NanoColorPicker picker = place(new NanoColorPicker().color(new Color(0x80336699)), 12, 60, 250, 220);
                NanoFileExplorer explorer = place(new NanoFileExplorer(temporary), 280, 60, 348, 360);
                NanoComboBox<String> combo = place(new NanoComboBox<String>().items(List.of("Low", "Medium", "High")).selectedIndex(1), 12, 300, 250, 36);
                NanoNumberSpinner spinner = place(new NanoNumberSpinner(0, 100, .5, 12.5), 12, 344, 250, 36);
                NanoRadioGroup radio = place(new NanoRadioGroup(List.of("Windowed", "Fullscreen")), 12, 394, 250, 64);
                root.add(bar, picker, explorer, combo, spinner, radio); draw(root);
                for (int i = 0; i < 4; i++) {
                    assertTrue(picker.getChannel(i).getAbsoluteX() >= picker.getAbsoluteX() + 56,
                            "channel labels reserve a consistent width after font measurement");
                }
                var outside = BufferUtils.createByteBuffer(4);
                var reference = BufferUtils.createByteBuffer(4);
                glReadPixels(635, 40, 1, 1, GL_RGBA, GL_UNSIGNED_BYTE, reference);
                for (int y = 60; y < 88; y++) {
                    glReadPixels(635, y, 1, 1, GL_RGBA, GL_UNSIGNED_BYTE, outside);
                    for (int channel = 0; channel < 3; channel++) assertEquals(reference.get(channel), outside.get(channel),
                            "long status paths must stay inside the explorer viewport");
                }
                capture(light ? "widgets-light.png" : "widgets-dark.png");
                bar.getMenu(0).showBelow(bar.getHeading(0)); draw(root);
                capture(light ? "menu-light.png" : "menu-dark.png");
                key(Keyboard.ESCAPE); assertFalse(bar.getMenu(0).isOpen());
                combo.open(); draw(root);
                capture(light ? "combo-light.png" : "combo-dark.png");
                combo.close();
            } finally { root.dispose(); }
        }
    }

    /**
     * Saves the current framebuffer with top-left image orientation for visual inspection.
     * @param name PNG basename under the ignored build report directory
     * @throws IOException if output creation fails
     */
    private static void capture(String name) throws IOException {
        var pixels = BufferUtils.createByteBuffer(640 * 480 * 4);
        glReadPixels(0, 0, 640, 480, GL_RGBA, GL_UNSIGNED_BYTE, pixels);
        var image = new java.awt.image.BufferedImage(640, 480, java.awt.image.BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < 480; y++) for (int x = 0; x < 640; x++) {
            int at = ((479 - y) * 640 + x) * 4;
            image.setRGB(x, y, (pixels.get(at) & 255) << 16 | (pixels.get(at + 1) & 255) << 8 | pixels.get(at + 2) & 255);
        }
        Path output = Path.of("build/reports/nano-widget-visuals", name);
        Files.createDirectories(output.getParent());
        javax.imageio.ImageIO.write(image, "png", output.toFile());
    }

    /**
     * Creates the owning native graphics context used by every test in this class.
     */
    @BeforeAll static void context() {
        assertTrue(glfwInit()); Window.init(new JGLConfiguration().size(640, 480).visible(false));
    }

    /**
     * Releases callbacks and the native window after all per-test roots are disposed.
     */
    @AfterAll static void shutdown() {
        if (Window.getAddress() != 0) {
            org.lwjgl.glfw.Callbacks.glfwFreeCallbacks(Window.getAddress());
            glfwDestroyWindow(Window.getAddress());
        }
        glfwTerminate();
    }

    /**
     * Places a node in top-left UI coordinates for deterministic hit testing.
     * @param node node to configure
     * @param x left coordinate
     * @param y top coordinate
     * @param width width in UI units
     * @param height height in UI units
     * @param <T> concrete node type
     * @return supplied node
     */
    private static <T extends UINode> T place(T node, float x, float y, float width, float height) {
        node.getLayout().absolute().left(x).top(y).width(width).height(height).noGrow().noShrink(); return node;
    }

    /**
     * Renders an actual frame and checks the GL error flag after completion.
     * @param root attached scene root
     */
    private static void draw(UIRoot root) {
        assertNanoChildren(root);
        root.layout(); root.update(0); root.layout(); glClearColor(0, 0, 0, 1); glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT | GL_STENCIL_BUFFER_BIT);
        root.draw(); glFinish(); assertEquals(GL_NO_ERROR, glGetError());
    }

    /** Rejects accidental texture controls inside Nano widgets, including open overlays. */
    private static void assertNanoChildren(valthorne.ui.UIContainer parent) {
        for (int i = 0; i < parent.size(); i++) {
            UINode child = parent.get(i);
            // UIRoot owns one backend-neutral overlay host implemented as a plain Panel.
            boolean rootOverlay = parent instanceof UIRoot && child.getClass() == valthorne.ui.nodes.Panel.class
                    && !child.isClickable() && !child.isFocusable() && !child.isScrollable();
            if (!rootOverlay) assertInstanceOf(valthorne.ui.nodes.nano.NanoNode.class, child, child.getClass().getName());
            if (child instanceof valthorne.ui.UIContainer container) assertNanoChildren(container);
        }
    }

    /**
     * Publishes a complete primary-button click in top-left logical UI coordinates.
     * @param x horizontal coordinate
     * @param topY vertical coordinate measured from the top
     */
    private static void click(int x, int topY) {
        JGL.publish(new MousePressEvent(0, 0, x, 480 - topY));
        JGL.publish(new MouseReleaseEvent(0, 0, x, 480 - topY));
    }

    /**
     * Publishes a real routed key press for the root's current focus scope.
     * @param code GLFW-compatible engine key code
     */
    private static void key(int code) { JGL.publish(new KeyPressEvent(code, 0)); }

    /**
     * Checks modal keyboard navigation, disabled-row skipping, callback ordering,
     * menu-bar removal, and restoration of the original heading focus.
     */
    @Test void menuKeyboardActivationRestoresFocusAndDetachmentCloses() {
        UIRoot root = new UIRoot();
        try (ProfessionalTheme theme = new ProfessionalTheme()) {
            root.setTheme(theme.create());
            int[] calls = {0};
            NanoMenuBar bar = place(new NanoMenuBar(), 10, 10, 300, 36);
            bar.addMenu("File", List.of(new NanoPopupMenu.Item("Disabled", () -> fail("disabled"), false),
                    new NanoPopupMenu.Item("One", () -> calls[0]++, true), new NanoPopupMenu.Item("Two", () -> calls[0] += 10, true)));
            bar.addMenu("Edit", List.of(new NanoPopupMenu.Item("Edit action", () -> calls[0] += 100, true)));
            root.add(bar); draw(root);
            click(40, 28); draw(root); assertTrue(bar.getMenu(0).isOpen());
            NanoVirtualList menuRows = (NanoVirtualList) bar.getMenu(0).get(0);
            assertEquals("menu-item", menuRows.getItemNode(0).getStyleName());
            click(100, 28); draw(root);
            assertFalse(bar.getMenu(0).isOpen()); assertTrue(bar.getMenu(1).isOpen(),
                    "a single click on another heading switches the open menu");
            click(100, 28); draw(root); assertFalse(bar.getMenu(1).isOpen());
            root.setFocusTo(bar.getHeading(0));
            key(Keyboard.ENTER); draw(root); assertTrue(bar.getMenu(0).isOpen());
            key(Keyboard.DOWN); key(Keyboard.ENTER);
            assertEquals(10, calls[0]); assertFalse(bar.getMenu(0).isOpen()); assertSame(bar.getHeading(0), root.getFocused());
            key(Keyboard.ENTER); key(Keyboard.RIGHT); assertFalse(bar.getMenu(0).isOpen()); assertTrue(bar.getMenu(1).isOpen());
            key(Keyboard.ESCAPE); assertSame(bar.getHeading(1), root.getFocused());
            root.setFocusTo(bar.getHeading(0));
            key(Keyboard.ENTER); assertTrue(bar.getMenu(0).isOpen()); root.remove(bar);
            assertFalse(bar.getMenu(0).isOpen()); draw(root);
        } finally { root.dispose(); }
    }

    /**
     * Tests actual pointer selection, outside dismissal, and a popup clamped near an edge.
     */
    @Test void comboMouseSelectionAndOutsideDismissalWorkInMixedTree() {
        UIRoot root = new UIRoot();
        try (ProfessionalTheme theme = new ProfessionalTheme()) {
            root.setTheme(theme.create());
            NanoPanel parent = place(new NanoPanel(), 0, 0, 640, 480);
            NanoComboBox<String> combo = place(new NanoComboBox<String>().items(List.of("Alpha", "Beta", "Gamma")), 20, 20, 180, 36);
            var selected = new ArrayList<String>(); combo.onChange(selected::add); parent.add(combo); root.add(parent); draw(root);
            click(60, 38); draw(root); assertTrue(combo.isOpen());
            click(60, 104); draw(root); assertEquals("Beta", combo.getSelected()); assertEquals(List.of("Beta"), selected);
            assertFalse(combo.isOpen()); assertSame(combo, root.getFocused());
            key(Keyboard.DOWN); draw(root); assertTrue(combo.isOpen());
            click(400, 300); assertFalse(combo.isOpen()); assertEquals(1, selected.size());
            place(combo, 530, 430, 100, 36); draw(root); combo.open(); draw(root);
            key(Keyboard.END); key(Keyboard.ENTER); assertEquals("Gamma", combo.getSelected());
            combo.open(); parent.remove(combo); assertFalse(combo.isOpen());
        } finally { root.dispose(); }
    }

    /**
     * Ensures Tab focus is used for command activation and nested removal does not
     * reenter layout through siblings whose native nodes were already freed.
     */
    @Test void tabbedMenuFocusAndNestedOwnerRemovalAreSafe() {
        UIRoot root = new UIRoot();
        try (ProfessionalTheme theme = new ProfessionalTheme()) {
            root.setTheme(theme.create());
            NanoPanel parent = place(new NanoPanel(), 0, 0, 500, 400);
            parent.add(new NanoLabel("Earlier sibling"));
            NanoComboBox<String> combo = place(new NanoComboBox<String>().items(List.of("A", "B", "C")), 20, 40, 180, 36);
            parent.add(combo); root.add(parent); draw(root); combo.open();
            key(Keyboard.TAB); key(Keyboard.ENTER); assertEquals("B", combo.getSelected());
            combo.open(); root.remove(parent); assertFalse(combo.isOpen()); draw(root);
        } finally { root.dispose(); }
    }

    /**
     * Reads actual preview pixels and routes keyboard edits through nested controls.
     */
    @Test void colorPreviewAndNumericRadioKeyboardInputRenderCorrectly() {
        UIRoot root = new UIRoot();
        try (ProfessionalTheme theme = new ProfessionalTheme()) {
            root.setTheme(theme.create());
            NanoColorPicker picker = place(new NanoColorPicker().color(Color.RED), 20, 20, 260, 220);
            NanoNumberSpinner spinner = place(new NanoNumberSpinner(0, 10, 1, 3), 310, 20, 220, 36);
            NanoRadioGroup radio = place(new NanoRadioGroup(List.of("A", "B")), 310, 80, 180, 64);
            root.add(picker, spinner, radio); draw(root);
            var pixel = BufferUtils.createByteBuffer(4); glReadPixels(30, 449, 1, 1, GL_RGBA, GL_UNSIGNED_BYTE, pixel);
            assertTrue((pixel.get(0) & 255) > 245); assertTrue((pixel.get(1) & 255) < 5);
            var colors = new ArrayList<String>(); picker.onChange(color -> colors.add(color.toHex()));
            root.setFocusTo(picker.getChannel(0)); key(Keyboard.HOME); draw(root);
            assertEquals("#FF000000", picker.getColor().toHex()); assertEquals(1, colors.size());
            root.setFocusTo(spinner.getEditor()); key(Keyboard.UP); assertEquals(4, spinner.getValue());
            spinner.getEditor().text("7.5"); key(Keyboard.ENTER); assertEquals(7.5, spinner.getValue());
            root.setFocusTo(radio.get(0)); key(Keyboard.DOWN); assertEquals(1, radio.getSelectedIndex());
            assertSame(radio.get(1), root.getFocused()); draw(root);
        } finally { root.dispose(); }
    }

    /**
     * Exercises virtualized directory navigation and breadcrumb callbacks in a real root.
     * @throws IOException if fixture setup or valid navigation fails
     */
    @Test void explorerVirtualizesRowsAndNavigatesWithKeyboardAndBreadcrumbs() throws IOException {
        Path sub = Files.createDirectory(temporary.resolve("folder"));
        for (int i = 0; i < 150; i++) Files.writeString(temporary.resolve(String.format("file%03d.txt", i)), "x");
        UIRoot root = new UIRoot();
        try (ProfessionalTheme theme = new ProfessionalTheme()) {
            root.setTheme(theme.create());
            NanoFileExplorer explorer = place(new NanoFileExplorer(temporary), 20, 20, 500, 380);
            root.add(explorer); draw(root);
            NanoVirtualList rows = explorer.getList();
            assertTrue(rows.getLiveItemCount() < 25, "directory rows must remain virtualized");
            root.setFocusTo(rows.getItemNode(0)); key(Keyboard.HOME); key(Keyboard.ENTER); draw(root);
            assertEquals(sub.toRealPath(), explorer.getDirectory());
            NanoScrollPanel trail = (NanoScrollPanel) explorer.get(1);
            NanoBreadcrumbBar crumbs = (NanoBreadcrumbBar) trail.getContent();
            NanoButton first = (NanoButton) crumbs.get(0); root.setFocusTo(first); key(Keyboard.ENTER); draw(root);
            assertEquals(temporary.toRealPath(), explorer.getDirectory());
            root.setFocusTo(rows.getItemNode(0)); key(Keyboard.END); draw(root);
            assertEquals("file149.txt", explorer.getSelected().getFileName().toString());
            assertTrue(rows.getLiveItemCount() < 25); assertNotNull(rows.getItemNode(150));
            var opened = new ArrayList<Path>(); explorer.onOpen(opened::add);
            root.setFocusTo(crumbs.get(0)); key(Keyboard.ENTER); draw(root);
            assertTrue(opened.isEmpty(), "breadcrumb Enter must not activate a selected file");
            assertNull(explorer.getSelected());
        } finally { root.dispose(); }
    }
}
