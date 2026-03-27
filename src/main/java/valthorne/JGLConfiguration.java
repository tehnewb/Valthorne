package valthorne;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.lwjgl.glfw.GLFW.*;

/**
 * <p>
 * {@code JGLConfiguration} is the central fluent configuration object used to define
 * how Valthorne should create and initialize its GLFW window and OpenGL context.
 * It collects window metadata, framebuffer settings, context creation flags,
 * platform-specific hint strings, swap behavior, and any extra custom GLFW hints
 * before those settings are finally applied through {@link #applyWindowHints()}.
 * </p>
 *
 * <p>
 * This class is designed to be a builder-style configuration container. Rather than
 * forcing callers to pass a very large constructor argument list, it exposes a large
 * collection of small fluent setter methods such as {@link #title(String)},
 * {@link #size(int, int)}, {@link #samples(int)}, {@link #contextVersion(int, int)},
 * and {@link #swapInterval(SwapInterval)}. Each of these methods mutates the current
 * configuration and returns the same instance so configuration code remains readable
 * and chainable.
 * </p>
 *
 * <p>
 * The configuration stores values for several distinct categories of GLFW state:
 * </p>
 *
 * <ul>
 *     <li>window presentation settings such as title, size, visibility, decorations, and resizability</li>
 *     <li>framebuffer precision settings such as color bits, depth bits, stencil bits, and accumulation bits</li>
 *     <li>OpenGL context settings such as version, profile, debug flags, and robustness behavior</li>
 *     <li>platform-specific string hints for Cocoa and X11</li>
 *     <li>swap interval preferences through {@link SwapInterval}</li>
 *     <li>arbitrary extra GLFW hints stored in {@link #extraHints}</li>
 * </ul>
 *
 * <p>
 * The class also exposes a shared static default instance through {@link #defaults()}.
 * That object can be used as a convenient baseline configuration when callers want a
 * single reusable configuration template. Since that instance is mutable, it should
 * be treated carefully in shared code so one caller does not unexpectedly affect
 * another caller's startup behavior.
 * </p>
 *
 * <p>
 * The actual application of stored values happens inside {@link #applyWindowHints()}.
 * That method resets GLFW to its default hint state by calling
 * {@code glfwDefaultWindowHints()} and then pushes all stored configuration values
 * into GLFW through the appropriate {@code glfwWindowHint} and
 * {@code glfwWindowHintString} calls.
 * </p>
 *
 * <h2>Example Usage</h2>
 *
 * <pre>{@code
 * JGLConfiguration configuration = new JGLConfiguration()
 *         .title("My Game")
 *         .size(1920, 1080)
 *         .samples(4)
 *         .resizable(true)
 *         .visible(true)
 *         .contextVersion(4, 6)
 *         .openglProfile(GLFW_OPENGL_CORE_PROFILE)
 *         .openglDebugContext(true)
 *         .swapInterval(SwapInterval.VSYNC)
 *         .depthBits(24)
 *         .stencilBits(8)
 *         .srgbCapable(true);
 *
 * JGL.init(application, configuration);
 * }</pre>
 *
 * <p>
 * This example demonstrates the intended full usage pattern for the class:
 * create a configuration, customize the properties you care about, inspect them
 * if needed, and finally apply the stored values before creating the GLFW window.
 * </p>
 *
 * @author Albert Beaupre
 * @since March 27th, 2026
 */
public final class JGLConfiguration {

    private String title = "Valthorne"; // Title text used for the created window
    private int width = 1280; // Initial window width in pixels
    private int height = 720; // Initial window height in pixels
    private int samples = 0; // Requested multisample anti-aliasing sample count
    private boolean visible = true; // Whether the window should start visible
    private boolean resizable = true; // Whether the window can be resized by the user
    private boolean doubleBuffer = true; // Whether double buffering should be enabled
    private boolean contextNoError = true; // Whether the OpenGL context should use no-error mode when supported
    private boolean fullscreen = false; // Whether the window should be created in fullscreen mode
    private boolean maximized = false; // Whether the window should start maximized
    private boolean focused = true; // Whether the window should start focused
    private boolean focusOnShow = true; // Whether showing the window should also focus it
    private boolean decorated = true; // Whether normal window decorations should be shown
    private boolean floating = false; // Whether the window should float above normal windows
    private boolean transparentFramebuffer = false; // Whether the framebuffer should support transparency
    private boolean scaleToMonitor = false; // Whether the window content should scale to the monitor
    private boolean srgbCapable = false; // Whether an sRGB-capable framebuffer should be requested
    private boolean cocoaRetinaFramebuffer = true; // Whether Retina framebuffers should be enabled on macOS
    private int refreshRate = GLFW_DONT_CARE; // Requested monitor refresh rate or GLFW_DONT_CARE
    private SwapInterval swapInterval = SwapInterval.VSYNC; // Preferred swap interval behavior after context creation
    private int redBits = GLFW_DONT_CARE; // Requested red channel precision in bits
    private int greenBits = GLFW_DONT_CARE; // Requested green channel precision in bits
    private int blueBits = GLFW_DONT_CARE; // Requested blue channel precision in bits
    private int alphaBits = GLFW_DONT_CARE; // Requested alpha channel precision in bits
    private int depthBits = GLFW_DONT_CARE; // Requested depth buffer precision in bits
    private int stencilBits = GLFW_DONT_CARE; // Requested stencil buffer precision in bits
    private int accumRedBits = GLFW_DONT_CARE; // Requested accumulation red channel precision in bits
    private int accumGreenBits = GLFW_DONT_CARE; // Requested accumulation green channel precision in bits
    private int accumBlueBits = GLFW_DONT_CARE; // Requested accumulation blue channel precision in bits
    private int accumAlphaBits = GLFW_DONT_CARE; // Requested accumulation alpha channel precision in bits
    private int auxBuffers = GLFW_DONT_CARE; // Requested number of auxiliary buffers
    private int contextVersionMajor = 4; // Requested OpenGL context major version
    private int contextVersionMinor = 6; // Requested OpenGL context minor version
    private int openglProfile = GLFW_OPENGL_COMPAT_PROFILE; // Requested OpenGL profile hint
    private int openglForwardCompat = GLFW_FALSE; // Whether forward-compatible OpenGL context mode should be requested
    private int openglDebugContext = GLFW_FALSE; // Whether an OpenGL debug context should be requested
    private int openglRobustness = GLFW_NO_ROBUSTNESS; // Requested OpenGL robustness behavior
    private int contextReleaseBehavior = GLFW_ANY_RELEASE_BEHAVIOR; // Requested context release behavior hint
    private int contextCreationApi = GLFW_NATIVE_CONTEXT_API; // Requested context creation API
    private int clientApi = GLFW_OPENGL_API; // Requested GLFW client API
    private String cocoaFrameName; // Optional Cocoa frame autosave name on macOS
    private String x11ClassName; // Optional X11 class name hint
    private String x11InstanceName; // Optional X11 instance name hint
    private final Map<Integer, Integer> extraHints = new LinkedHashMap<>(); // Extra raw GLFW integer hints applied after the standard hints

    /**
     * Shared mutable default configuration instance returned by {@link #defaults()}.
     */
    private static final JGLConfiguration defaults = new JGLConfiguration();

    /**
     * <p>
     * Returns the shared default configuration instance.
     * </p>
     *
     * <p>
     * This method does not create a new object. It returns the same static instance
     * every time. Because that instance is mutable, changes made through one caller
     * will remain visible to future callers unless explicitly reset.
     * </p>
     *
     * @return the shared default configuration instance
     */
    public static JGLConfiguration defaults() {
        return defaults;
    }

    /**
     * <p>
     * Returns the configured window title.
     * </p>
     *
     * @return the configured title string
     */
    public String getTitle() {
        return title;
    }

    /**
     * <p>
     * Returns the configured initial window width.
     * </p>
     *
     * @return the configured width in pixels
     */
    public int getWidth() {
        return width;
    }

    /**
     * <p>
     * Returns the configured initial window height.
     * </p>
     *
     * @return the configured height in pixels
     */
    public int getHeight() {
        return height;
    }

    /**
     * <p>
     * Returns the requested multisample sample count.
     * </p>
     *
     * @return the configured sample count
     */
    public int getSamples() {
        return samples;
    }

    /**
     * <p>
     * Returns whether the window should start visible.
     * </p>
     *
     * @return {@code true} if the window should start visible
     */
    public boolean isVisible() {
        return visible;
    }

    /**
     * <p>
     * Returns whether the window should be resizable.
     * </p>
     *
     * @return {@code true} if resizing is enabled
     */
    public boolean isResizable() {
        return resizable;
    }

    /**
     * <p>
     * Returns whether double buffering should be enabled.
     * </p>
     *
     * @return {@code true} if double buffering is enabled
     */
    public boolean isDoubleBuffer() {
        return doubleBuffer;
    }

    /**
     * <p>
     * Returns whether the context should request no-error mode.
     * </p>
     *
     * @return {@code true} if no-error mode is requested
     */
    public boolean isContextNoError() {
        return contextNoError;
    }

    /**
     * <p>
     * Returns whether fullscreen mode is requested.
     * </p>
     *
     * <p>
     * This class stores the fullscreen preference, although actual fullscreen window
     * creation is normally handled at the point where the window is created.
     * </p>
     *
     * @return {@code true} if fullscreen mode is requested
     */
    public boolean isFullscreen() {
        return fullscreen;
    }

    /**
     * <p>
     * Returns whether the window should start maximized.
     * </p>
     *
     * @return {@code true} if the window should start maximized
     */
    public boolean isMaximized() {
        return maximized;
    }

    /**
     * <p>
     * Returns whether the window should start focused.
     * </p>
     *
     * @return {@code true} if initial focus is requested
     */
    public boolean isFocused() {
        return focused;
    }

    /**
     * <p>
     * Returns whether the window should receive focus when shown.
     * </p>
     *
     * @return {@code true} if focus-on-show is enabled
     */
    public boolean isFocusOnShow() {
        return focusOnShow;
    }

    /**
     * <p>
     * Returns whether the window should use normal decorations.
     * </p>
     *
     * @return {@code true} if decorations are enabled
     */
    public boolean isDecorated() {
        return decorated;
    }

    /**
     * <p>
     * Returns whether the window should float above normal windows.
     * </p>
     *
     * @return {@code true} if floating mode is enabled
     */
    public boolean isFloating() {
        return floating;
    }

    /**
     * <p>
     * Returns whether a transparent framebuffer is requested.
     * </p>
     *
     * @return {@code true} if a transparent framebuffer is requested
     */
    public boolean isTransparentFramebuffer() {
        return transparentFramebuffer;
    }

    /**
     * <p>
     * Returns whether scale-to-monitor behavior is requested.
     * </p>
     *
     * @return {@code true} if scale-to-monitor is enabled
     */
    public boolean isScaleToMonitor() {
        return scaleToMonitor;
    }

    /**
     * <p>
     * Returns whether an sRGB-capable framebuffer is requested.
     * </p>
     *
     * @return {@code true} if sRGB capability is requested
     */
    public boolean isSrgbCapable() {
        return srgbCapable;
    }

    /**
     * <p>
     * Returns whether Retina framebuffer support is enabled for Cocoa.
     * </p>
     *
     * @return {@code true} if Cocoa Retina framebuffer support is enabled
     */
    public boolean isCocoaRetinaFramebuffer() {
        return cocoaRetinaFramebuffer;
    }

    /**
     * <p>
     * Returns the requested refresh rate hint.
     * </p>
     *
     * @return the configured refresh rate, or {@code GLFW_DONT_CARE}
     */
    public int getRefreshRate() {
        return refreshRate;
    }

    /**
     * <p>
     * Returns the configured swap interval preference.
     * </p>
     *
     * @return the configured swap interval
     */
    public SwapInterval getSwapInterval() {
        return swapInterval;
    }

    /**
     * <p>
     * Returns the requested red channel precision.
     * </p>
     *
     * @return the requested red channel bits
     */
    public int getRedBits() {
        return redBits;
    }

    /**
     * <p>
     * Returns the requested green channel precision.
     * </p>
     *
     * @return the requested green channel bits
     */
    public int getGreenBits() {
        return greenBits;
    }

    /**
     * <p>
     * Returns the requested blue channel precision.
     * </p>
     *
     * @return the requested blue channel bits
     */
    public int getBlueBits() {
        return blueBits;
    }

    /**
     * <p>
     * Returns the requested alpha channel precision.
     * </p>
     *
     * @return the requested alpha channel bits
     */
    public int getAlphaBits() {
        return alphaBits;
    }

    /**
     * <p>
     * Returns the requested depth buffer precision.
     * </p>
     *
     * @return the requested depth buffer bits
     */
    public int getDepthBits() {
        return depthBits;
    }

    /**
     * <p>
     * Returns the requested stencil buffer precision.
     * </p>
     *
     * @return the requested stencil buffer bits
     */
    public int getStencilBits() {
        return stencilBits;
    }

    /**
     * <p>
     * Returns the requested accumulation red channel precision.
     * </p>
     *
     * @return the requested accumulation red bits
     */
    public int getAccumRedBits() {
        return accumRedBits;
    }

    /**
     * <p>
     * Returns the requested accumulation green channel precision.
     * </p>
     *
     * @return the requested accumulation green bits
     */
    public int getAccumGreenBits() {
        return accumGreenBits;
    }

    /**
     * <p>
     * Returns the requested accumulation blue channel precision.
     * </p>
     *
     * @return the requested accumulation blue bits
     */
    public int getAccumBlueBits() {
        return accumBlueBits;
    }

    /**
     * <p>
     * Returns the requested accumulation alpha channel precision.
     * </p>
     *
     * @return the requested accumulation alpha bits
     */
    public int getAccumAlphaBits() {
        return accumAlphaBits;
    }

    /**
     * <p>
     * Returns the requested auxiliary buffer count.
     * </p>
     *
     * @return the requested auxiliary buffer count
     */
    public int getAuxBuffers() {
        return auxBuffers;
    }

    /**
     * <p>
     * Returns the requested OpenGL context major version.
     * </p>
     *
     * @return the configured major version
     */
    public int getContextVersionMajor() {
        return contextVersionMajor;
    }

    /**
     * <p>
     * Returns the requested OpenGL context minor version.
     * </p>
     *
     * @return the configured minor version
     */
    public int getContextVersionMinor() {
        return contextVersionMinor;
    }

    /**
     * <p>
     * Returns the requested OpenGL profile hint.
     * </p>
     *
     * @return the configured GLFW OpenGL profile constant
     */
    public int getOpenglProfile() {
        return openglProfile;
    }

    /**
     * <p>
     * Returns the raw GLFW forward-compatibility hint value.
     * </p>
     *
     * @return {@code GLFW_TRUE} or {@code GLFW_FALSE}
     */
    public int getOpenglForwardCompat() {
        return openglForwardCompat;
    }

    /**
     * <p>
     * Returns the raw GLFW debug-context hint value.
     * </p>
     *
     * @return {@code GLFW_TRUE} or {@code GLFW_FALSE}
     */
    public int getOpenglDebugContext() {
        return openglDebugContext;
    }

    /**
     * <p>
     * Returns the configured OpenGL robustness hint.
     * </p>
     *
     * @return the configured GLFW robustness constant
     */
    public int getOpenglRobustness() {
        return openglRobustness;
    }

    /**
     * <p>
     * Returns the configured context release behavior hint.
     * </p>
     *
     * @return the configured GLFW context release behavior constant
     */
    public int getContextReleaseBehavior() {
        return contextReleaseBehavior;
    }

    /**
     * <p>
     * Returns the configured context creation API hint.
     * </p>
     *
     * @return the configured GLFW context creation API constant
     */
    public int getContextCreationApi() {
        return contextCreationApi;
    }

    /**
     * <p>
     * Returns the configured client API hint.
     * </p>
     *
     * @return the configured GLFW client API constant
     */
    public int getClientApi() {
        return clientApi;
    }

    /**
     * <p>
     * Returns the optional Cocoa frame name hint.
     * </p>
     *
     * @return the Cocoa frame name, or {@code null} if none is set
     */
    public String getCocoaFrameName() {
        return cocoaFrameName;
    }

    /**
     * <p>
     * Returns the optional X11 class name hint.
     * </p>
     *
     * @return the X11 class name, or {@code null} if none is set
     */
    public String getX11ClassName() {
        return x11ClassName;
    }

    /**
     * <p>
     * Returns the optional X11 instance name hint.
     * </p>
     *
     * @return the X11 instance name, or {@code null} if none is set
     */
    public String getX11InstanceName() {
        return x11InstanceName;
    }

    /**
     * <p>
     * Returns the map of extra raw GLFW hints that will be applied after the built-in hints.
     * </p>
     *
     * <p>
     * The returned map is the live backing map, so callers may add, remove, or replace
     * entries directly if they want full manual control over additional hint values.
     * </p>
     *
     * @return the live extra hint map
     */
    public Map<Integer, Integer> getExtraHints() {
        return extraHints;
    }

    /**
     * <p>
     * Sets the window title.
     * </p>
     *
     * @param title the new title string
     * @return this configuration instance
     */
    public JGLConfiguration title(String title) {
        this.title = title;
        return this;
    }

    /**
     * <p>
     * Sets the initial window size.
     * </p>
     *
     * @param width the desired window width in pixels
     * @param height the desired window height in pixels
     * @return this configuration instance
     */
    public JGLConfiguration size(int width, int height) {
        this.width = width;
        this.height = height;
        return this;
    }

    /**
     * <p>
     * Sets the requested multisample anti-aliasing sample count.
     * </p>
     *
     * <p>
     * Negative values are clamped to {@code 0}.
     * </p>
     *
     * @param samples the requested sample count
     * @return this configuration instance
     */
    public JGLConfiguration samples(int samples) {
        this.samples = Math.max(0, samples);
        return this;
    }

    /**
     * <p>
     * Sets whether the window should start visible.
     * </p>
     *
     * @param visible whether the window should start visible
     * @return this configuration instance
     */
    public JGLConfiguration visible(boolean visible) {
        this.visible = visible;
        return this;
    }

    /**
     * <p>
     * Sets whether the window should be resizable.
     * </p>
     *
     * @param resizable whether the window should be resizable
     * @return this configuration instance
     */
    public JGLConfiguration resizable(boolean resizable) {
        this.resizable = resizable;
        return this;
    }

    /**
     * <p>
     * Sets whether double buffering should be enabled.
     * </p>
     *
     * @param doubleBuffer whether double buffering should be enabled
     * @return this configuration instance
     */
    public JGLConfiguration doubleBuffer(boolean doubleBuffer) {
        this.doubleBuffer = doubleBuffer;
        return this;
    }

    /**
     * <p>
     * Sets whether the OpenGL context should request no-error mode.
     * </p>
     *
     * @param contextNoError whether no-error mode should be requested
     * @return this configuration instance
     */
    public JGLConfiguration contextNoError(boolean contextNoError) {
        this.contextNoError = contextNoError;
        return this;
    }

    /**
     * <p>
     * Sets whether fullscreen mode should be requested.
     * </p>
     *
     * <p>
     * This value is stored in the configuration and is typically interpreted later
     * by whatever code is actually responsible for choosing the monitor and creating
     * the GLFW window.
     * </p>
     *
     * @param fullscreen whether fullscreen mode should be requested
     * @return this configuration instance
     */
    public JGLConfiguration fullscreen(boolean fullscreen) {
        this.fullscreen = fullscreen;
        return this;
    }

    /**
     * <p>
     * Sets whether the window should start maximized.
     * </p>
     *
     * @param maximized whether the window should start maximized
     * @return this configuration instance
     */
    public JGLConfiguration maximized(boolean maximized) {
        this.maximized = maximized;
        return this;
    }

    /**
     * <p>
     * Sets whether the window should start focused.
     * </p>
     *
     * @param focused whether the window should start focused
     * @return this configuration instance
     */
    public JGLConfiguration focused(boolean focused) {
        this.focused = focused;
        return this;
    }

    /**
     * <p>
     * Sets whether the window should receive focus when shown.
     * </p>
     *
     * @param focusOnShow whether focus-on-show should be enabled
     * @return this configuration instance
     */
    public JGLConfiguration focusOnShow(boolean focusOnShow) {
        this.focusOnShow = focusOnShow;
        return this;
    }

    /**
     * <p>
     * Sets whether the window should use standard decorations.
     * </p>
     *
     * @param decorated whether window decorations should be enabled
     * @return this configuration instance
     */
    public JGLConfiguration decorated(boolean decorated) {
        this.decorated = decorated;
        return this;
    }

    /**
     * <p>
     * Sets whether the window should float above normal windows.
     * </p>
     *
     * @param floating whether the window should float
     * @return this configuration instance
     */
    public JGLConfiguration floating(boolean floating) {
        this.floating = floating;
        return this;
    }

    /**
     * <p>
     * Sets whether the framebuffer should support transparency.
     * </p>
     *
     * @param transparentFramebuffer whether a transparent framebuffer should be requested
     * @return this configuration instance
     */
    public JGLConfiguration transparentFramebuffer(boolean transparentFramebuffer) {
        this.transparentFramebuffer = transparentFramebuffer;
        return this;
    }

    /**
     * <p>
     * Sets whether the window should scale to the monitor.
     * </p>
     *
     * @param scaleToMonitor whether scale-to-monitor should be enabled
     * @return this configuration instance
     */
    public JGLConfiguration scaleToMonitor(boolean scaleToMonitor) {
        this.scaleToMonitor = scaleToMonitor;
        return this;
    }

    /**
     * <p>
     * Sets whether an sRGB-capable framebuffer should be requested.
     * </p>
     *
     * @param srgbCapable whether sRGB framebuffer capability should be requested
     * @return this configuration instance
     */
    public JGLConfiguration srgbCapable(boolean srgbCapable) {
        this.srgbCapable = srgbCapable;
        return this;
    }

    /**
     * <p>
     * Sets whether Cocoa Retina framebuffers should be enabled.
     * </p>
     *
     * @param cocoaRetinaFramebuffer whether Retina framebuffer support should be enabled
     * @return this configuration instance
     */
    public JGLConfiguration cocoaRetinaFramebuffer(boolean cocoaRetinaFramebuffer) {
        this.cocoaRetinaFramebuffer = cocoaRetinaFramebuffer;
        return this;
    }

    /**
     * <p>
     * Sets the requested refresh rate hint.
     * </p>
     *
     * @param refreshRate the desired refresh rate or {@code GLFW_DONT_CARE}
     * @return this configuration instance
     */
    public JGLConfiguration refreshRate(int refreshRate) {
        this.refreshRate = refreshRate;
        return this;
    }

    /**
     * <p>
     * Sets the preferred swap interval mode.
     * </p>
     *
     * @param swapInterval the desired swap interval behavior
     * @return this configuration instance
     */
    public JGLConfiguration swapInterval(SwapInterval swapInterval) {
        this.swapInterval = swapInterval;
        return this;
    }

    /**
     * <p>
     * Sets the requested framebuffer color channel precision.
     * </p>
     *
     * @param redBits requested red channel bits
     * @param greenBits requested green channel bits
     * @param blueBits requested blue channel bits
     * @param alphaBits requested alpha channel bits
     * @return this configuration instance
     */
    public JGLConfiguration colorBits(int redBits, int greenBits, int blueBits, int alphaBits) {
        this.redBits = redBits;
        this.greenBits = greenBits;
        this.blueBits = blueBits;
        this.alphaBits = alphaBits;
        return this;
    }

    /**
     * <p>
     * Sets the requested depth buffer precision.
     * </p>
     *
     * @param depthBits the requested depth buffer bits
     * @return this configuration instance
     */
    public JGLConfiguration depthBits(int depthBits) {
        this.depthBits = depthBits;
        return this;
    }

    /**
     * <p>
     * Sets the requested stencil buffer precision.
     * </p>
     *
     * @param stencilBits the requested stencil buffer bits
     * @return this configuration instance
     */
    public JGLConfiguration stencilBits(int stencilBits) {
        this.stencilBits = stencilBits;
        return this;
    }

    /**
     * <p>
     * Sets the requested accumulation buffer precision.
     * </p>
     *
     * @param redBits requested accumulation red bits
     * @param greenBits requested accumulation green bits
     * @param blueBits requested accumulation blue bits
     * @param alphaBits requested accumulation alpha bits
     * @return this configuration instance
     */
    public JGLConfiguration accumBits(int redBits, int greenBits, int blueBits, int alphaBits) {
        this.accumRedBits = redBits;
        this.accumGreenBits = greenBits;
        this.accumBlueBits = blueBits;
        this.accumAlphaBits = alphaBits;
        return this;
    }

    /**
     * <p>
     * Sets the requested auxiliary buffer count.
     * </p>
     *
     * @param auxBuffers the requested auxiliary buffer count
     * @return this configuration instance
     */
    public JGLConfiguration auxBuffers(int auxBuffers) {
        this.auxBuffers = auxBuffers;
        return this;
    }

    /**
     * <p>
     * Sets the requested OpenGL context version.
     * </p>
     *
     * @param major the requested major version
     * @param minor the requested minor version
     * @return this configuration instance
     */
    public JGLConfiguration contextVersion(int major, int minor) {
        this.contextVersionMajor = major;
        this.contextVersionMinor = minor;
        return this;
    }

    /**
     * <p>
     * Sets the requested OpenGL profile hint.
     * </p>
     *
     * @param openglProfile the GLFW profile constant to request
     * @return this configuration instance
     */
    public JGLConfiguration openglProfile(int openglProfile) {
        this.openglProfile = openglProfile;
        return this;
    }

    /**
     * <p>
     * Sets whether a forward-compatible OpenGL context should be requested.
     * </p>
     *
     * <p>
     * The stored value is converted into {@code GLFW_TRUE} or {@code GLFW_FALSE}
     * because that is what GLFW expects when hints are applied.
     * </p>
     *
     * @param value whether forward compatibility should be enabled
     * @return this configuration instance
     */
    public JGLConfiguration openglForwardCompat(boolean value) {
        this.openglForwardCompat = value ? GLFW_TRUE : GLFW_FALSE;
        return this;
    }

    /**
     * <p>
     * Sets whether an OpenGL debug context should be requested.
     * </p>
     *
     * <p>
     * The stored value is converted into {@code GLFW_TRUE} or {@code GLFW_FALSE}.
     * </p>
     *
     * @param value whether a debug context should be requested
     * @return this configuration instance
     */
    public JGLConfiguration openglDebugContext(boolean value) {
        this.openglDebugContext = value ? GLFW_TRUE : GLFW_FALSE;
        return this;
    }

    /**
     * <p>
     * Sets the requested OpenGL robustness hint.
     * </p>
     *
     * @param openglRobustness the GLFW robustness constant to request
     * @return this configuration instance
     */
    public JGLConfiguration openglRobustness(int openglRobustness) {
        this.openglRobustness = openglRobustness;
        return this;
    }

    /**
     * <p>
     * Sets the requested context release behavior hint.
     * </p>
     *
     * @param contextReleaseBehavior the GLFW context release behavior constant
     * @return this configuration instance
     */
    public JGLConfiguration contextReleaseBehavior(int contextReleaseBehavior) {
        this.contextReleaseBehavior = contextReleaseBehavior;
        return this;
    }

    /**
     * <p>
     * Sets the requested context creation API hint.
     * </p>
     *
     * @param contextCreationApi the GLFW context creation API constant
     * @return this configuration instance
     */
    public JGLConfiguration contextCreationApi(int contextCreationApi) {
        this.contextCreationApi = contextCreationApi;
        return this;
    }

    /**
     * <p>
     * Sets the requested client API hint.
     * </p>
     *
     * @param clientApi the GLFW client API constant
     * @return this configuration instance
     */
    public JGLConfiguration clientApi(int clientApi) {
        this.clientApi = clientApi;
        return this;
    }

    /**
     * <p>
     * Sets the optional Cocoa frame name hint.
     * </p>
     *
     * @param cocoaFrameName the frame autosave name to use on macOS
     * @return this configuration instance
     */
    public JGLConfiguration cocoaFrameName(String cocoaFrameName) {
        this.cocoaFrameName = cocoaFrameName;
        return this;
    }

    /**
     * <p>
     * Sets the optional X11 class name hint.
     * </p>
     *
     * @param x11ClassName the X11 class name to advertise
     * @return this configuration instance
     */
    public JGLConfiguration x11ClassName(String x11ClassName) {
        this.x11ClassName = x11ClassName;
        return this;
    }

    /**
     * <p>
     * Sets the optional X11 instance name hint.
     * </p>
     *
     * @param x11InstanceName the X11 instance name to advertise
     * @return this configuration instance
     */
    public JGLConfiguration x11InstanceName(String x11InstanceName) {
        this.x11InstanceName = x11InstanceName;
        return this;
    }

    /**
     * <p>
     * Adds or replaces an extra raw GLFW integer hint.
     * </p>
     *
     * <p>
     * Extra hints are applied after all built-in hints inside
     * {@link #applyWindowHints()}, which means they can be used to augment or even
     * override a previously assigned standard hint if the same GLFW hint constant is used.
     * </p>
     *
     * @param hint the GLFW hint constant
     * @param value the GLFW hint value
     * @return this configuration instance
     */
    public JGLConfiguration hint(int hint, int value) {
        this.extraHints.put(hint, value);
        return this;
    }

    /**
     * <p>
     * Applies all stored configuration values to GLFW window hints.
     * </p>
     *
     * <p>
     * This method begins by resetting GLFW to its default hint state through
     * {@code glfwDefaultWindowHints()}. It then applies all currently stored fields
     * through the matching GLFW hint calls. String hints for Cocoa and X11 are only
     * applied when the corresponding value is non-null. Finally, all entries stored
     * in {@link #extraHints} are applied in insertion order.
     * </p>
     *
     * <p>
     * This method does not create the window itself. It only prepares GLFW's global
     * hint state so subsequent window creation uses the desired configuration.
     * </p>
     */
    void applyWindowHints() {
        glfwDefaultWindowHints();
        glfwWindowHint(GLFW_SAMPLES, samples);
        glfwWindowHint(GLFW_VISIBLE, visible ? GLFW_TRUE : GLFW_FALSE);
        glfwWindowHint(GLFW_RESIZABLE, resizable ? GLFW_TRUE : GLFW_FALSE);
        glfwWindowHint(GLFW_DOUBLEBUFFER, doubleBuffer ? GLFW_TRUE : GLFW_FALSE);
        glfwWindowHint(GLFW_CONTEXT_NO_ERROR, contextNoError ? GLFW_TRUE : GLFW_FALSE);
        glfwWindowHint(GLFW_MAXIMIZED, maximized ? GLFW_TRUE : GLFW_FALSE);
        glfwWindowHint(GLFW_FOCUSED, focused ? GLFW_TRUE : GLFW_FALSE);
        glfwWindowHint(GLFW_FOCUS_ON_SHOW, focusOnShow ? GLFW_TRUE : GLFW_FALSE);
        glfwWindowHint(GLFW_DECORATED, decorated ? GLFW_TRUE : GLFW_FALSE);
        glfwWindowHint(GLFW_FLOATING, floating ? GLFW_TRUE : GLFW_FALSE);
        glfwWindowHint(GLFW_TRANSPARENT_FRAMEBUFFER, transparentFramebuffer ? GLFW_TRUE : GLFW_FALSE);
        glfwWindowHint(GLFW_SCALE_TO_MONITOR, scaleToMonitor ? GLFW_TRUE : GLFW_FALSE);
        glfwWindowHint(GLFW_SRGB_CAPABLE, srgbCapable ? GLFW_TRUE : GLFW_FALSE);
        glfwWindowHint(GLFW_COCOA_RETINA_FRAMEBUFFER, cocoaRetinaFramebuffer ? GLFW_TRUE : GLFW_FALSE);
        glfwWindowHint(GLFW_REFRESH_RATE, refreshRate);
        glfwWindowHint(GLFW_RED_BITS, redBits);
        glfwWindowHint(GLFW_GREEN_BITS, greenBits);
        glfwWindowHint(GLFW_BLUE_BITS, blueBits);
        glfwWindowHint(GLFW_ALPHA_BITS, alphaBits);
        glfwWindowHint(GLFW_DEPTH_BITS, depthBits);
        glfwWindowHint(GLFW_STENCIL_BITS, stencilBits);
        glfwWindowHint(GLFW_ACCUM_RED_BITS, accumRedBits);
        glfwWindowHint(GLFW_ACCUM_GREEN_BITS, accumGreenBits);
        glfwWindowHint(GLFW_ACCUM_BLUE_BITS, accumBlueBits);
        glfwWindowHint(GLFW_ACCUM_ALPHA_BITS, accumAlphaBits);
        glfwWindowHint(GLFW_AUX_BUFFERS, auxBuffers);
        glfwWindowHint(GLFW_CONTEXT_VERSION_MAJOR, contextVersionMajor);
        glfwWindowHint(GLFW_CONTEXT_VERSION_MINOR, contextVersionMinor);
        glfwWindowHint(GLFW_OPENGL_PROFILE, openglProfile);
        glfwWindowHint(GLFW_OPENGL_FORWARD_COMPAT, openglForwardCompat);
        glfwWindowHint(GLFW_OPENGL_DEBUG_CONTEXT, openglDebugContext);
        glfwWindowHint(GLFW_CONTEXT_ROBUSTNESS, openglRobustness);
        glfwWindowHint(GLFW_CONTEXT_RELEASE_BEHAVIOR, contextReleaseBehavior);
        glfwWindowHint(GLFW_CONTEXT_CREATION_API, contextCreationApi);
        glfwWindowHint(GLFW_CLIENT_API, clientApi);

        if (cocoaFrameName != null) glfwWindowHintString(GLFW_COCOA_FRAME_NAME, cocoaFrameName);
        if (x11ClassName != null) glfwWindowHintString(GLFW_X11_CLASS_NAME, x11ClassName);
        if (x11InstanceName != null) glfwWindowHintString(GLFW_X11_INSTANCE_NAME, x11InstanceName);

        for (Map.Entry<Integer, Integer> entry : extraHints.entrySet()) {
            glfwWindowHint(entry.getKey(), entry.getValue());
        }
    }
}