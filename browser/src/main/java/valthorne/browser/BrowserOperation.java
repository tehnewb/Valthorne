package valthorne.browser;

/**
 * Stable numeric operations shared with the bgfx WebAssembly command bridge.
 * Values are a private runtime protocol and must match operations.h exactly.
 */
public final class BrowserOperation {
    /**
     * Native initialize operation.
     */
    public static final int INITIALIZE = 0;
    /**
     * Native shutdown operation.
     */
    public static final int SHUTDOWN = 1;
    /**
     * Native frame operation.
     */
    public static final int FRAME = 2;
    /**
     * Native reset operation.
     */
    public static final int RESET = 3;
    /**
     * Native capabilities operation.
     */
    public static final int CAPABILITIES = 4;
    /**
     * Native layout create operation.
     */
    public static final int LAYOUT_CREATE = 5;
    /**
     * Native layout free operation.
     */
    public static final int LAYOUT_FREE = 6;
    /**
     * Native layout begin operation.
     */
    public static final int LAYOUT_BEGIN = 7;
    /**
     * Native layout add operation.
     */
    public static final int LAYOUT_ADD = 8;
    /**
     * Native layout skip operation.
     */
    public static final int LAYOUT_SKIP = 9;
    /**
     * Native layout end operation.
     */
    public static final int LAYOUT_END = 10;
    /**
     * Native layout copy operation.
     */
    public static final int LAYOUT_COPY = 11;
    /**
     * Native layout has operation.
     */
    public static final int LAYOUT_HAS = 12;
    /**
     * Native layout decode operation.
     */
    public static final int LAYOUT_DECODE = 13;
    /**
     * Native copy operation.
     */
    public static final int COPY = 14;
    /**
     * Native vertex create operation.
     */
    public static final int VERTEX_CREATE = 15;
    /**
     * Native dynamic vertex create operation.
     */
    public static final int DYNAMIC_VERTEX_CREATE = 16;
    /**
     * Native dynamic vertex update operation.
     */
    public static final int DYNAMIC_VERTEX_UPDATE = 17;
    /**
     * Native vertex destroy operation.
     */
    public static final int VERTEX_DESTROY = 18;
    /**
     * Native dynamic vertex destroy operation.
     */
    public static final int DYNAMIC_VERTEX_DESTROY = 19;
    /**
     * Native shader create operation.
     */
    public static final int SHADER_CREATE = 20;
    /**
     * Native shader destroy operation.
     */
    public static final int SHADER_DESTROY = 21;
    /**
     * Native program create operation.
     */
    public static final int PROGRAM_CREATE = 22;
    /**
     * Native program destroy operation.
     */
    public static final int PROGRAM_DESTROY = 23;
    /**
     * Native uniform create operation.
     */
    public static final int UNIFORM_CREATE = 24;
    /**
     * Native uniform destroy operation.
     */
    public static final int UNIFORM_DESTROY = 25;
    /**
     * Native shader uniforms operation.
     */
    public static final int SHADER_UNIFORMS = 26;
    /**
     * Native shader uniform handle operation.
     */
    public static final int SHADER_UNIFORM_HANDLE = 27;
    /**
     * Native uniform info operation.
     */
    public static final int UNIFORM_INFO = 28;
    /**
     * Native uniform set operation.
     */
    public static final int UNIFORM_SET = 29;
    /**
     * Native texture create operation.
     */
    public static final int TEXTURE_CREATE = 30;
    /**
     * Native texture destroy operation.
     */
    public static final int TEXTURE_DESTROY = 31;
    /**
     * Native texture valid operation.
     */
    public static final int TEXTURE_VALID = 32;
    /**
     * Native framebuffer attachments operation.
     */
    public static final int FRAMEBUFFER_ATTACHMENTS = 33;
    /**
     * Native framebuffer create operation.
     */
    public static final int FRAMEBUFFER_CREATE = 34;
    /**
     * Native framebuffer destroy operation.
     */
    public static final int FRAMEBUFFER_DESTROY = 35;
    /**
     * Native framebuffer texture operation.
     */
    public static final int FRAMEBUFFER_TEXTURE = 36;
    /**
     * Native view rect operation.
     */
    public static final int VIEW_RECT = 37;
    /**
     * Native view scissor operation.
     */
    public static final int VIEW_SCISSOR = 38;
    /**
     * Native view framebuffer operation.
     */
    public static final int VIEW_FRAMEBUFFER = 39;
    /**
     * Native view mode operation.
     */
    public static final int VIEW_MODE = 40;
    /**
     * Native view clear operation.
     */
    public static final int VIEW_CLEAR = 41;
    /**
     * Native palette operation.
     */
    public static final int PALETTE = 42;
    /**
     * Native view clear attachments operation.
     */
    public static final int VIEW_CLEAR_ATTACHMENTS = 43;
    /**
     * Native touch operation.
     */
    public static final int TOUCH = 44;
    /**
     * Native state operation.
     */
    public static final int STATE = 45;
    /**
     * Native texture set operation.
     */
    public static final int TEXTURE_SET = 46;
    /**
     * Native vertex set operation.
     */
    public static final int VERTEX_SET = 47;
    /**
     * Native dynamic vertex set operation.
     */
    public static final int DYNAMIC_VERTEX_SET = 48;
    /**
     * Native instance set operation.
     */
    public static final int INSTANCE_SET = 49;
    /**
     * Native submit operation.
     */
    public static final int SUBMIT = 50;
    /**
     * Native view transform operation.
     */
    public static final int VIEW_TRANSFORM = 51;
    /**
     * Native transient available operation.
     */
    public static final int TRANSIENT_AVAILABLE = 52;
    /**
     * Native transient set operation.
     */
    public static final int TRANSIENT_SET = 53;
    /**
     * Native texture update operation.
     */
    public static final int TEXTURE_UPDATE = 54;

    /**
     * Native image decode operation.
     */
    public static final int IMAGE_DECODE = 55;

    /**
     * Native image free operation.
     */
    public static final int IMAGE_FREE = 56;

    /**
     * Native image failure operation.
     */
    public static final int IMAGE_FAILURE = 57;

    /**
     * Native image flip operation.
     */
    public static final int IMAGE_FLIP = 58;

    /**
     * Native font create operation.
     */
    public static final int FONT_CREATE = 59;

    /**
     * Native font free operation.
     */
    public static final int FONT_FREE = 60;

    /**
     * Native font metrics operation.
     */
    public static final int FONT_METRICS = 61;

    /**
     * Native font horizontal operation.
     */
    public static final int FONT_HORIZONTAL = 62;

    /**
     * Native font box operation.
     */
    public static final int FONT_BOX = 63;

    /**
     * Native font kerning operation.
     */
    public static final int FONT_KERNING = 64;

    /**
     * Native font shape operation.
     */
    public static final int FONT_SHAPE = 65;

    /**
     * Native font vertex operation.
     */
    public static final int FONT_VERTEX = 66;

    /**
     * Native font shape free operation.
     */
    public static final int FONT_SHAPE_FREE = 67;

    /**
     * Native nvg begin path operation.
     */
    public static final int NVG_BEGIN_PATH = 68;

    /**
     * Native nvg close path operation.
     */
    public static final int NVG_CLOSE_PATH = 69;

    /**
     * Native nvg fill operation.
     */
    public static final int NVG_FILL = 70;

    /**
     * Native nvg stroke operation.
     */
    public static final int NVG_STROKE = 71;

    /**
     * Native nvg save operation.
     */
    public static final int NVG_SAVE = 72;

    /**
     * Native nvg restore operation.
     */
    public static final int NVG_RESTORE = 73;

    /**
     * Native nvg reset transform operation.
     */
    public static final int NVG_RESET_TRANSFORM = 74;

    /**
     * Native nvg reset scissor operation.
     */
    public static final int NVG_RESET_SCISSOR = 75;

    /**
     * Native nvg end frame operation.
     */
    public static final int NVG_END_FRAME = 76;

    /**
     * Native nvg translate operation.
     */
    public static final int NVG_TRANSLATE = 77;

    /**
     * Native nvg scale operation.
     */
    public static final int NVG_SCALE = 78;

    /**
     * Native nvg transform operation.
     */
    public static final int NVG_TRANSFORM = 79;

    /**
     * Native nvg rect operation.
     */
    public static final int NVG_RECT = 80;

    /**
     * Native nvg rounded rect operation.
     */
    public static final int NVG_ROUNDED_RECT = 81;

    /**
     * Native nvg circle operation.
     */
    public static final int NVG_CIRCLE = 82;

    /**
     * Native nvg scissor operation.
     */
    public static final int NVG_SCISSOR = 83;

    /**
     * Native nvg intersect scissor operation.
     */
    public static final int NVG_INTERSECT_SCISSOR = 84;

    /**
     * Native nvg stroke width operation.
     */
    public static final int NVG_STROKE_WIDTH = 85;

    /**
     * Native nvg global alpha operation.
     */
    public static final int NVG_GLOBAL_ALPHA = 86;

    /**
     * Native nvg font size operation.
     */
    public static final int NVG_FONT_SIZE = 87;

    /**
     * Native nvg begin frame operation.
     */
    public static final int NVG_BEGIN_FRAME = 88;

    /**
     * Native nvg line cap operation.
     */
    public static final int NVG_LINE_CAP = 89;

    /**
     * Native nvg line join operation.
     */
    public static final int NVG_LINE_JOIN = 90;

    /**
     * Native nvg text align operation.
     */
    public static final int NVG_TEXT_ALIGN = 91;

    /**
     * Native nvg arc operation.
     */
    public static final int NVG_ARC = 92;

    /**
     * Native nvg fill color operation.
     */
    public static final int NVG_FILL_COLOR = 93;

    /**
     * Native nvg stroke color operation.
     */
    public static final int NVG_STROKE_COLOR = 94;

    /**
     * Native nvg fill paint operation.
     */
    public static final int NVG_FILL_PAINT = 95;

    /**
     * Native nvg stroke paint operation.
     */
    public static final int NVG_STROKE_PAINT = 96;

    /**
     * Native nvg linear gradient operation.
     */
    public static final int NVG_LINEAR_GRADIENT = 97;

    /**
     * Native nvg box gradient operation.
     */
    public static final int NVG_BOX_GRADIENT = 98;

    /**
     * Native nvg image pattern operation.
     */
    public static final int NVG_IMAGE_PATTERN = 99;

    /**
     * Native nvg image create operation.
     */
    public static final int NVG_IMAGE_CREATE = 100;

    /**
     * Native nvg image delete operation.
     */
    public static final int NVG_IMAGE_DELETE = 101;

    /**
     * Native nvg image update operation.
     */
    public static final int NVG_IMAGE_UPDATE = 102;

    /**
     * Native nvg font create operation.
     */
    public static final int NVG_FONT_CREATE = 103;

    /**
     * Native nvg font face operation.
     */
    public static final int NVG_FONT_FACE = 104;

    /**
     * Native nvg text operation.
     */
    public static final int NVG_TEXT = 105;

    /**
     * Native nvg text bounds operation.
     */
    public static final int NVG_TEXT_BOUNDS = 106;

    /**
     * Native nvg text metrics operation.
     */
    public static final int NVG_TEXT_METRICS = 107;

    /**
     * Native nvg create operation.
     */
    public static final int NVG_CREATE = 108;

    /**
     * Native nvg delete operation.
     */
    public static final int NVG_DELETE = 109;

    /**
     * Native nvg view operation.
     */
    public static final int NVG_VIEW = 110;

    /**
     * Native nvg move to operation.
     */
    public static final int NVG_MOVE_TO = 111;

    /**
     * Native nvg line to operation.
     */
    public static final int NVG_LINE_TO = 112;
    /**
     * Bakes one checked font atlas and packed glyph range into temporary CPU storage.
     */
    public static final int FONT_BAKE = 113;

    /**
     * Prevents constructing protocol constant holders.
     */
    private BrowserOperation() {
    }
}
