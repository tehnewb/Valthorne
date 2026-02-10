package valthorne.graphics;

/**
 * Represents a color in RGBA format. Colors are immutable and stored internally as 32-bit integers
 * in the format 0xAARRGGBB where AA=alpha, RR=red, GG=green, BB=blue.
 *
 * @author Albert Beaupre
 * @since November 3rd, 2025
 */
public class Color {

    /**
     * Pure white color (0xFFFFFFFF)
     */
    public static final Color WHITE = new Color(0xFFFFFFFF);
    /**
     * Pure black color (0xFF000000)
     */
    public static final Color BLACK = new Color(0xFF000000);
    /**
     * Pure red color (0xFFFF0000)
     */
    public static final Color RED = new Color(0xFFFF0000);
    /**
     * Pure green color (0xFF00FF00)
     */
    public static final Color GREEN = new Color(0xFF00FF00);
    /**
     * Pure blue color (0xFF0000FF)
     */
    public static final Color BLUE = new Color(0xFF0000FF);
    /**
     * Magenta color (0xFFFF00FF)
     */
    public static final Color MAGENTA = new Color(0xFFFF00FF);
    /**
     * Yellow color (0xFFFFFF00)
     */
    public static final Color YELLOW = new Color(0xFFFFFF00);
    /**
     * Cyan color (0xFF00FFFF)
     */
    public static final Color CYAN = new Color(0xFF00FFFF);
    /**
     * Orange color (0xFFFFA500)
     */
    public static final Color ORANGE = new Color(0xFFFFA500);
    /**
     * Purple color (0xFF800080)
     */
    public static final Color PURPLE = new Color(0xFF800080);
    /**
     * Pink color (0xFFFFC0CB)
     */
    public static final Color PINK = new Color(0xFFFFC0CB);
    /**
     * Lime color (0xFF32CD32)
     */
    public static final Color LIME = new Color(0xFF32CD32);
    /**
     * Teal color (0xFF008080)
     */
    public static final Color TEAL = new Color(0xFF008080);
    /**
     * Navy blue color (0xFF000080)
     */
    public static final Color NAVY = new Color(0xFF000080);
    /**
     * Medium gray color (0xFF808080)
     */
    public static final Color GRAY = new Color(0xFF808080);
    /**
     * Light gray color (0xFFD3D3D3)
     */
    public static final Color LIGHT_GRAY = new Color(0xFFD3D3D3);
    /**
     * Dark gray color (0xFF404040)
     */
    public static final Color DARK_GRAY = new Color(0xFF404040);
    /**
     * Brown color (0xFF8B4513)
     */
    public static final Color BROWN = new Color(0xFF8B4513);
    /**
     * Gold color (0xFFFFD700)
     */
    public static final Color GOLD = new Color(0xFFFFD700);

    /**
     * Crimson red color (0xFFDC143C)
     */
    public static final Color CRIMSON = new Color(0xFFDC143C);
    /**
     * Maroon red color (0xFF800000)
     */
    public static final Color MAROON = new Color(0xFF800000);
    /**
     * Salmon pink color (0xFFFA8072)
     */
    public static final Color SALMON = new Color(0xFFFA8072);
    /**
     * Firebrick red color (0xFFB22222)
     */
    public static final Color FIREBRICK = new Color(0xFFB22222);
    /**
     * Dark red color (0xFF8B0000)
     */
    public static final Color DARK_RED = new Color(0xFF8B0000);
    /**
     * Indian red color (0xFFCD5C5C)
     */
    public static final Color INDIAN_RED = new Color(0xFFCD5C5C);
    /**
     * Tomato red color (0xFFFF6347)
     */
    public static final Color TOMATO = new Color(0xFFFF6347);

    /**
     * Coral orange color (0xFFFF7F50)
     */
    public static final Color CORAL = new Color(0xFFFF7F50);
    /**
     * Dark orange color (0xFFFF8C00)
     */
    public static final Color DARK_ORANGE = new Color(0xFFFF8C00);
    /**
     * Chocolate brown color (0xFFD2691E)
     */
    public static final Color CHOCOLATE = new Color(0xFFD2691E);
    /**
     * Tan brown color (0xFFD2B48C)
     */
    public static final Color TAN = new Color(0xFFD2B48C);

    /**
     * Khaki yellow color (0xFFF0E68C)
     */
    public static final Color KHAKI = new Color(0xFFF0E68C);
    /**
     * Beige color (0xFFF5F5DC)
     */
    public static final Color BEIGE = new Color(0xFFF5F5DC);
    /**
     * Olive green color (0xFF808000)
     */
    public static final Color OLIVE = new Color(0xFF808000);
    /**
     * Mustard yellow color (0xFFFFDB58)
     */
    public static final Color MUSTARD = new Color(0xFFFFDB58);
    /**
     * Light yellow color (0xFFFFFFE0)
     */
    public static final Color LIGHT_YELLOW = new Color(0xFFFFFFE0);

    /**
     * Dark green color (0xFF006400)
     */
    public static final Color DARK_GREEN = new Color(0xFF006400);
    /**
     * Forest green color (0xFF228B22)
     */
    public static final Color FOREST_GREEN = new Color(0xFF228B22);
    /**
     * Sea green color (0xFF2E8B57)
     */
    public static final Color SEA_GREEN = new Color(0xFF2E8B57);
    /**
     * Spring green color (0xFF00FF7F)
     */
    public static final Color SPRING_GREEN = new Color(0xFF00FF7F);
    /**
     * Turquoise color (0xFF40E0D0)
     */
    public static final Color TURQUOISE = new Color(0xFF40E0D0);
    /**
     * Mint green color (0xFF98FF98)
     */
    public static final Color MINT_GREEN = new Color(0xFF98FF98);
    /**
     * Olive drab green color (0xFF6B8E23)
     */
    public static final Color OLIVE_DRAB = new Color(0xFF6B8E23);
    /**
     * Honeydew green color (0xFFF0FFF0)
     */
    public static final Color HONEYDEW = new Color(0xFFF0FFF0);

    /**
     * Sky blue color (0xFF87CEEB)
     */
    public static final Color SKY_BLUE = new Color(0xFF87CEEB);
    /**
     * Light blue color (0xFFADD8E6)
     */
    public static final Color LIGHT_BLUE = new Color(0xFFADD8E6);
    /**
     * Steel blue color (0xFF4682B4)
     */
    public static final Color STEEL_BLUE = new Color(0xFF4682B4);
    /**
     * Royal blue color (0xFF4169E1)
     */
    public static final Color ROYAL_BLUE = new Color(0xFF4169E1);
    /**
     * Deep sky blue color (0xFF00BFFF)
     */
    public static final Color DEEP_SKY_BLUE = new Color(0xFF00BFFF);
    /**
     * Midnight blue color (0xFF191970)
     */
    public static final Color MIDNIGHT_BLUE = new Color(0xFF191970);
    /**
     * Dodger blue color (0xFF1E90FF)
     */
    public static final Color DODGER_BLUE = new Color(0xFF1E90FF);

    /**
     * Plum purple color (0xFFDDA0DD)
     */
    public static final Color PLUM = new Color(0xFFDDA0DD);
    /**
     * Orchid purple color (0xFFDA70D6)
     */
    public static final Color ORCHID = new Color(0xFFDA70D6);
    /**
     * Lavender color (0xFFE6E6FA)
     */
    public static final Color LAVENDER = new Color(0xFFE6E6FA);
    /**
     * Indigo color (0xFF4B0082)
     */
    public static final Color INDIGO = new Color(0xFF4B0082);
    /**
     * Violet color (0xFFEE82EE)
     */
    public static final Color VIOLET = new Color(0xFFEE82EE);
    /**
     * Hot pink color (0xFFFF69B4)
     */
    public static final Color HOT_PINK = new Color(0xFFFF69B4);
    /**
     * Deep pink color (0xFFFF1493)
     */
    public static final Color DEEP_PINK = new Color(0xFFFF1493);

    /**
     * Saddle brown color (0xFF8B4513)
     */
    public static final Color SADDLE_BROWN = new Color(0xFF8B4513);
    /**
     * Peru brown color (0xFFCD853F)
     */
    public static final Color PERU = new Color(0xFFCD853F);
    /**
     * Wheat color (0xFFF5DEB3)
     */
    public static final Color WHEAT = new Color(0xFFF5DEB3);
    /**
     * Sienna brown color (0xFFA0522D)
     */
    public static final Color SIENNA = new Color(0xFFA0522D);
    /**
     * Burlywood brown color (0xFFDEB887)
     */
    public static final Color BURLYWOOD = new Color(0xFFDEB887);
    /**
     * Moccasin color (0xFFFFE4B5)
     */
    public static final Color MOCCASIN = new Color(0xFFFFE4B5);

    /**
     * Aquamarine color (0xFF7FFFD4)
     */
    public static final Color AQUA_MARINE = new Color(0xFF7FFFD4);
    /**
     * Pale turquoise color (0xFFAFEEEE)
     */
    public static final Color PALE_TURQUOISE = new Color(0xFFAFEEEE);
    /**
     * Cadet blue color (0xFF5F9EA0)
     */
    public static final Color CADET_BLUE = new Color(0xFF5F9EA0);
    /**
     * Powder blue color (0xFFB0E0E6)
     */
    public static final Color POWDER_BLUE = new Color(0xFFB0E0E6);
    /**
     * Teal blue color (0xFF367588)
     */
    public static final Color TEAL_BLUE = new Color(0xFF367588);

    /**
     * Peach puff color (0xFFFFDAB9)
     */
    public static final Color PEACH_PUFF = new Color(0xFFFFDAB9);
    /**
     * Misty rose color (0xFFFFE4E1)
     */
    public static final Color MISTY_ROSE = new Color(0xFFFFE4E1);
    /**
     * Light coral color (0xFFF08080)
     */
    public static final Color LIGHT_CORAL = new Color(0xFFF08080);
    /**
     * Bisque color (0xFFFFE4C4)
     */
    public static final Color BISQUE = new Color(0xFFFFE4C4);
    /**
     * Light salmon color (0xFFFFA07A)
     */
    public static final Color LIGHT_SALMON = new Color(0xFFFFA07A);
    /**
     * Blush pink color (0xFFDE5D83)
     */
    public static final Color BLUSH = new Color(0xFFDE5D83);

    /**
     * Dark goldenrod color (0xFFB8860B)
     */
    public static final Color DARK_GOLDENROD = new Color(0xFFB8860B);
    /**
     * Rosy brown color (0xFFBC8F8F)
     */
    public static final Color ROSY_BROWN = new Color(0xFFBC8F8F);
    /**
     * Cornsilk color (0xFFFFF8DC)
     */
    public static final Color CORNSILK = new Color(0xFFFFF8DC);
    /**
     * Linen color (0xFFFAF0E6)
     */
    public static final Color LINEN = new Color(0xFFFAF0E6);
    /**
     * Old lace color (0xFFFDF5E6)
     */
    public static final Color OLD_LACE = new Color(0xFFFDF5E6);
    /**
     * Sepia color (0xFF704214)
     */
    public static final Color SEPIA = new Color(0xFF704214);
    /**
     * Papaya whip color (0xFFFFEFD5)
     */
    public static final Color PAPAYA_WHIP = new Color(0xFFFFEFD5);

    /**
     * Pale green color (0xFF98FB98)
     */
    public static final Color PALE_GREEN = new Color(0xFF98FB98);
    /**
     * Pale goldenrod color (0xFFEEE8AA)
     */
    public static final Color PALE_GOLDENROD = new Color(0xFFEEE8AA);
    /**
     * Pale violet red color (0xFFDB7093)
     */
    public static final Color PALE_VIOLET_RED = new Color(0xFFDB7093);
    /**
     * Pale blue color (0xFFAFDCFF)
     */
    public static final Color PALE_BLUE = new Color(0xFFAFDCFF);
    /**
     * Pale pink color (0xFFFFCCFF)
     */
    public static final Color PALE_PINK = new Color(0xFFFFCCFF);

    /**
     * Silver color (0xFFC0C0C0)
     */
    public static final Color SILVER = new Color(0xFFC0C0C0);
    /**
     * Slate gray color (0xFF708090)
     */
    public static final Color SLATE_GRAY = new Color(0xFF708090);
    /**
     * Light slate gray color (0xFF778899)
     */
    public static final Color LIGHT_SLATE_GRAY = new Color(0xFF778899);
    /**
     * Gainsboro color (0xFFDCDCDC)
     */
    public static final Color GAINSBORO = new Color(0xFFDCDCDC);
    /**
     * Ash gray color (0xFFB2BEB5)
     */
    public static final Color ASH_GRAY = new Color(0xFFB2BEB5);
    /**
     * Charcoal color (0xFF36454F)
     */
    public static final Color CHARCOAL = new Color(0xFF36454F);
    /**
     * Jet black color (0xFF343434)
     */
    public static final Color JET = new Color(0xFF343434);

    /**
     * Alice blue color (0xFFF0F8FF)
     */
    public static final Color ALICE_BLUE = new Color(0xFFF0F8FF);
    /**
     * Azure color (0xFFF0FFFF)
     */
    public static final Color AZURE = new Color(0xFFF0FFFF);
    /**
     * Ghost white color (0xFFF8F8FF)
     */
    public static final Color GHOST_WHITE = new Color(0xFFF8F8FF);
    /**
     * Snow white color (0xFFFFFAFA)
     */
    public static final Color SNOW = new Color(0xFFFFFAFA);
    /**
     * Ivory color (0xFFFFFFF0)
     */
    public static final Color IVORY = new Color(0xFFFFFFF0);
    /**
     * Floral white color (0xFFFFFAF0)
     */
    public static final Color FLORAL_WHITE = new Color(0xFFFFFAF0);
    /**
     * Cloud white color (0xFFF7F7FF)
     */
    public static final Color CLOUD_WHITE = new Color(0xFFF7F7FF);

    /**
     * Electric lime color (0xFFCCFF00)
     */
    public static final Color ELECTRIC_LIME = new Color(0xFFCCFF00);
    /**
     * Neon pink color (0xFFFF6EC7)
     */
    public static final Color NEON_PINK = new Color(0xFFFF6EC7);
    /**
     * Neon green color (0xFF39FF14)
     */
    public static final Color NEON_GREEN = new Color(0xFF39FF14);
    /**
     * Neon blue color (0xFF1F51FF)
     */
    public static final Color NEON_BLUE = new Color(0xFF1F51FF);
    /**
     * Neon purple color (0xFFBC13FE)
     */
    public static final Color NEON_PURPLE = new Color(0xFFBC13FE);

    /**
     * Fern green color (0xFF4F7942)
     */
    public static final Color FERN_GREEN = new Color(0xFF4F7942);
    /**
     * Moss green color (0xFF8A9A5B)
     */
    public static final Color MOSS_GREEN = new Color(0xFF8A9A5B);
    /**
     * Army green color (0xFF4B5320)
     */
    public static final Color ARMY_GREEN = new Color(0xFF4B5320);
    /**
     * Sage green color (0xFF9C9F84)
     */
    public static final Color SAGE = new Color(0xFF9C9F84);
    /**
     * Basil green color (0xFF568203)
     */
    public static final Color BASIL = new Color(0xFF568203);

    /**
     * Fully transparent color (0x00000000)
     */
    public static final Color TRANSPARENT = new Color(0x00000000);
    /**
     * Semi-transparent black color (0x80000000)
     */
    public static final Color TRANSLUCENT = new Color(0x80000000);

    /**
     * The color value stored as RGBA integer (0xAARRGGBB)
     */
    private int rgba; // stored as 0xAARRGGBB

    /**
     * Creates a new Color from an RGBA integer value.
     *
     * @param rgba The color value in 0xAARRGGBB format
     */
    public Color(int rgba) { // e.g. 0xFFFF0000 = opaque red
        this.rgba = rgba;
    }

    /**
     * Creates a new Color from individual color components.
     *
     * @param red   Red component (0.0-1.0)
     * @param green Green component (0.0-1.0)
     * @param blue  Blue component (0.0-1.0)
     * @param alpha Alpha component (0.0-1.0)
     */
    public Color(float red, float green, float blue, float alpha) { // e.g. 0xFFFF0000 = opaque red
        this.rgba = (int) (alpha * 255) << 24 | (int) (red * 255) << 16 | (int) (green * 255) << 8 | (int) (blue * 255);
    }

    /**
     * Sets the current color to the given color by copying its RGBA value.
     *
     * @param endColor The color from which to copy the RGBA value.
     */
    public void set(Color endColor) {
        this.rgba = endColor.rgba;
    }

    /**
     * Sets the color components of this object using the provided red, green, blue,
     * and alpha values. Each component should be a normalized float value in the
     * range [0.0, 1.0]. The specified values are internally converted to an
     * integer-based RGBA representation.
     *
     * @param red   The red component as a normalized float in the range [0.0, 1.0].
     * @param green The green component as a normalized float in the range [0.0, 1.0].
     * @param blue  The blue component as a normalized float in the range [0.0, 1.0].
     * @param alpha The alpha (transparency) component as a normalized float
     *              in the range [0.0, 1.0].
     */
    public void set(float red, float green, float blue, float alpha) {
        this.rgba = (int) (alpha * 255) << 24 | (int) (red * 255) << 16 | (int) (green * 255) << 8 | (int) (blue * 255);
    }


    /**
     * Multiplies this color by another color (component-wise), storing the result in this instance.
     *
     * @param other other color
     */
    public void mul(Color other) {
        if (other == null) throw new NullPointerException("other cannot be null");
        set(r() * other.r(), g() * other.g(), b() * other.b(), a() * other.a());
    }

    /**
     * Adds another color to this one (component-wise), clamped to [0..1], storing the result in this instance.
     *
     * @param other other color
     */
    public void add(Color other) {
        if (other == null) throw new NullPointerException("other cannot be null");
        set(clamp01(r() + other.r()), clamp01(g() + other.g()), clamp01(b() + other.b()), clamp01(a() + other.a()));
    }

    /**
     * Linearly interpolates this color towards {@code target} by {@code t} and stores the result in this instance.
     *
     * @param target target color
     * @param t      interpolation factor in [0..1]
     */
    public void lerp(Color target, float t) {
        if (target == null) throw new NullPointerException("target cannot be null");
        t = clamp01(t);
        set(r() + (target.r() - r()) * t,
                g() + (target.g() - g()) * t,
                b() + (target.b() - b()) * t,
                a() + (target.a() - a()) * t);
    }

    /**
     * Sets this color to opaque (alpha = 1).
     */
    public void opaque() {
        rgba = (rgba & 0x00FFFFFF) | 0xFF000000;
    }

    /**
     * Sets this color to fully transparent (alpha = 0).
     */
    public void transparent() {
        rgba = (rgba & 0x00FFFFFF);
    }

    /**
     * @param alpha normalized alpha in [0..1]
     * @return a new Color with the same RGB but a different alpha.
     */
    public Color withAlpha(float alpha) {
        int aa = ((int) (clamp01(alpha) * 255) & 0xFF) << 24;
        return new Color((rgba & 0x00FFFFFF) | aa);
    }

    /**
     * Creates a new {@link Color} from packed components.
     *
     * @param r red 0..255
     * @param g green 0..255
     * @param b blue 0..255
     * @param a alpha 0..255
     * @return packed color
     */
    public static Color fromRGBA(int r, int g, int b, int a) {
        int rr = clamp255(r);
        int gg = clamp255(g);
        int bb = clamp255(b);
        int aa = clamp255(a);
        return new Color((aa << 24) | (rr << 16) | (gg << 8) | bb);
    }

    /**
     * Clamps a given float value to the range [0.0, 1.0].
     * If the value is less than 0.0, it returns 0.0.
     * If the value is greater than 1.0, it returns 1.0.
     * Otherwise, it returns the value unchanged.
     *
     * @param v the value to be clamped
     * @return the clamped value within the range [0.0, 1.0]
     */
    private static float clamp01(float v) {
        if (v < 0f) return 0f;
        return Math.min(v, 1f);
    }

    /**
     * Clamps the given integer value to the range of 0 to 255.
     *
     * @param v the integer value to be clamped
     * @return the clamped value, which will be between 0 and 255 inclusive
     */
    private static int clamp255(int v) {
        if (v < 0) return 0;
        return Math.min(v, 255);
    }

    /**
     * Parses a hex string into a {@link Color}.
     *
     * <p>Accepted formats:</p>
     * <ul>
     *     <li>{@code "RRGGBB"} or {@code "#RRGGBB"} (alpha assumed 255)</li>
     *     <li>{@code "AARRGGBB"} or {@code "#AARRGGBB"}</li>
     * </ul>
     *
     * @param hex hex string
     * @return parsed color
     */
    public static Color fromHex(String hex) {
        if (hex == null) throw new NullPointerException("hex cannot be null");
        String s = hex.startsWith("#") ? hex.substring(1) : hex;
        if (s.length() != 6 && s.length() != 8) {
            throw new IllegalArgumentException("Hex must be RRGGBB or AARRGGBB");
        }
        int value = (int) Long.parseLong(s, 16);
        if (s.length() == 6) value |= 0xFF000000;
        return new Color(value);
    }

    /**
     * @return hex string in {@code #AARRGGBB} format.
     */
    public String toHex() {
        return String.format("#%08X", rgba);
    }

    /**
     * @return true if this color is fully opaque (alpha == 255).
     */
    public boolean isOpaque() {
        return ((rgba >>> 24) & 0xFF) == 255;
    }

    /**
     * @return true if this color is fully transparent (alpha == 0).
     */
    public boolean isTransparent() {
        return ((rgba >>> 24) & 0xFF) == 0;
    }

    /**
     * @return a copy of this color.
     */
    public Color copy() {
        return new Color(rgba);
    }

    /**
     * Sets the alpha (transparency) component of the color.
     * The alpha value determines the transparency level, where 0.0 represents
     * fully transparent and 1.0 represents fully opaque.
     *
     * @param a The alpha component as a normalized float value in the range [0.0, 1.0].
     */
    public void a(float a) {
        this.rgba = (int) (a * 255) << 24 | (this.rgba & 0x00FFFFFF);
    }

    /**
     * Sets the red component of the color.
     * The red value is specified as a normalized float in the range [0.0, 1.0].
     * This method updates the internal RGBA representation based on the provided value.
     *
     * @param r The red component as a normalized float value in the range [0.0, 1.0].
     */
    public void r(float r) {
        this.rgba = (int) (r * 255) << 16 | (this.rgba & 0xFF00FFFF);
    }

    /**
     * Sets the green component of the color.
     * The green value is specified as a normalized float in the range [0.0, 1.0].
     * This method updates the internal RGBA representation based on the provided value.
     *
     * @param g The green component as a normalized float value in the range [0.0, 1.0].
     */
    public void g(float g) {
        this.rgba = (int) (g * 255) << 8 | (this.rgba & 0xFFFF00FF);
    }

    /**
     * Sets the blue component of the color.
     * The blue value is specified as a normalized float in the range [0.0, 1.0].
     * This method updates the internal RGBA representation based on the provided value.
     *
     * @param b The blue component as a normalized float value in the range [0.0, 1.0].
     */
    public void b(float b) {
        this.rgba = (int) (b * 255) | (this.rgba & 0xFFFFFF00);
    }

    /**
     * Gets the red component of this color (0-255).
     *
     * @return The red value
     */
    public int getRed() {
        return (rgba >> 16) & 0xFF;
    }

    /**
     * Gets the green component of this color (0-255).
     *
     * @return The green value
     */
    public int getGreen() {
        return (rgba >> 8) & 0xFF;
    }

    /**
     * Gets the blue component of this color (0-255).
     *
     * @return The blue value
     */
    public int getBlue() {
        return rgba & 0xFF;
    }

    /**
     * Gets the alpha component of this color (0-255).
     *
     * @return The alpha value
     */
    public int getAlpha() {
        return (rgba >> 24) & 0xFF;
    }

    /**
     * Calculates the alpha component of this color as a normalized float value in the range [0.0, 1.0].
     *
     * @return The normalized alpha value
     */
    public float a() {
        return getAlpha() / 255.0f;
    }

    /**
     * Calculates the red component of this color as a normalized float value
     * in the range [0.0, 1.0].
     *
     * @return The normalized red value
     */
    public float r() {
        return getRed() / 255.0f;
    }

    /**
     * Calculates the green component of this color as a normalized float value
     * in the range [0.0, 1.0].
     *
     * @return The normalized green value
     */
    public float g() {
        return getGreen() / 255.0f;
    }

    /**
     * Calculates the blue component of this color as a normalized float value
     * in the range [0.0, 1.0].
     *
     * @return The normalized blue value
     */
    public float b() {
        return getBlue() / 255.0f;
    }
}