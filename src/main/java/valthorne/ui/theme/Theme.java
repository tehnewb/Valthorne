package valthorne.ui.theme;

/**
 * <p>
 * {@code Theme} is the root abstraction for creating UI theme definitions in Valthorne.
 * It exists as a simple factory-style contract whose responsibility is to build and
 * return a fully configured {@link ThemeData} instance.
 * </p>
 *
 * <p>
 * Implementations of this interface typically define a complete visual language for
 * the UI system, including:
 * </p>
 *
 * <ul>
 *     <li>global style tokens</li>
 *     <li>named resources</li>
 *     <li>rules for specific element types</li>
 *     <li>rules for style names</li>
 *     <li>rules for different interaction states</li>
 * </ul>
 *
 * <p>
 * The purpose of keeping this as a dedicated interface is to make themes easy to
 * package, swap, recreate, and organize. A theme implementation can build a fresh
 * {@link ThemeData} object every time {@link #create()} is called, allowing the UI
 * system to apply new themes cleanly without mutating old theme instances.
 * </p>
 *
 * <p>
 * This interface is intentionally minimal. It does not prescribe how a theme should
 * be stored internally, only that it must be able to produce a {@link ThemeData}
 * object when requested.
 * </p>
 *
 * <h2>Example Usage</h2>
 *
 * <pre>{@code
 * public final class DarkTheme implements Theme {
 *
 *     @Override
 *     public ThemeData create() {
 *         ThemeData data = new ThemeData();
 *
 *         data.setToken(MyStyleKeys.BACKGROUND, new Color(0.12f, 0.12f, 0.14f, 1f));
 *         data.setToken(MyStyleKeys.FOREGROUND, Color.WHITE);
 *
 *         data.rule(Button.class)
 *             .set(MyStyleKeys.PADDING, 8f)
 *             .set(MyStyleKeys.CORNER_RADIUS, 6f);
 *
 *         return data;
 *     }
 * }
 *
 * Theme theme = new DarkTheme();
 * ThemeData themeData = theme.create();
 * }</pre>
 *
 * <p>
 * This example demonstrates the complete intended use of the interface: implement it,
 * build a {@link ThemeData} instance inside {@link #create()}, and then retrieve that
 * theme data for application to the UI.
 * </p>
 *
 * @author Albert Beaupre
 * @since March 9th, 2026
 */
public interface Theme {

    /**
     * <p>
     * Creates and returns a fully configured {@link ThemeData} instance.
     * </p>
     *
     * <p>
     * Implementations should build all desired tokens, resources, and rules into the
     * returned object before handing it back to the caller.
     * </p>
     *
     * @return a newly created theme data instance
     */
    ThemeData create();

}