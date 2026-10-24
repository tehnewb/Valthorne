package org.lwjgl.bgfx;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.IntBuffer;
import java.nio.FloatBuffer;
import java.nio.ShortBuffer;
import java.nio.charset.StandardCharsets;
import valthorne.browser.BrowserNative;

import static valthorne.browser.BrowserOperation.*;

/**
 * Browser bindings for the bgfx API used by the engine. Rendering is performed
 * by the bundled bgfx WebAssembly build, using the same compiled ESSL shaders
 * and packed vertex contracts as the desktop renderers. No desktop JNI library
 * is loaded. Calls are confined to the application's browser coroutine.
 */
public final class BGFX {
    /**
     * bgfx api version constant.
     */
    public static final int BGFX_API_VERSION = 156;
    /**
     * bgfx invalid handle constant.
     */
    public static final short BGFX_INVALID_HANDLE = (short)0xFFFF;
    /**
     * bgfx state write r constant.
     */
    public static final long BGFX_STATE_WRITE_R = 0x1L;
    /**
     * bgfx state write g constant.
     */
    public static final long BGFX_STATE_WRITE_G = 0x2L;
    /**
     * bgfx state write b constant.
     */
    public static final long BGFX_STATE_WRITE_B = 0x4L;
    /**
     * bgfx state write a constant.
     */
    public static final long BGFX_STATE_WRITE_A = 0x8L;
    /**
     * bgfx state write z constant.
     */
    public static final long BGFX_STATE_WRITE_Z = 0x4000000000L;
    /**
     * bgfx state write rgb constant.
     */
    public static final long BGFX_STATE_WRITE_RGB = BGFX_STATE_WRITE_R | BGFX_STATE_WRITE_G | BGFX_STATE_WRITE_B;
    /**
     * bgfx state write mask constant.
     */
    public static final long BGFX_STATE_WRITE_MASK = BGFX_STATE_WRITE_RGB | BGFX_STATE_WRITE_A | BGFX_STATE_WRITE_Z;
    /**
     * bgfx state depth test less constant.
     */
    public static final long BGFX_STATE_DEPTH_TEST_LESS = 0x10L;
    /**
     * bgfx state depth test lequal constant.
     */
    public static final long BGFX_STATE_DEPTH_TEST_LEQUAL = 0x20L;
    /**
     * bgfx state depth test equal constant.
     */
    public static final long BGFX_STATE_DEPTH_TEST_EQUAL = 0x30L;
    /**
     * bgfx state depth test gequal constant.
     */
    public static final long BGFX_STATE_DEPTH_TEST_GEQUAL = 0x40L;
    /**
     * bgfx state depth test greater constant.
     */
    public static final long BGFX_STATE_DEPTH_TEST_GREATER = 0x50L;
    /**
     * bgfx state depth test notequal constant.
     */
    public static final long BGFX_STATE_DEPTH_TEST_NOTEQUAL = 0x60L;
    /**
     * bgfx state depth test never constant.
     */
    public static final long BGFX_STATE_DEPTH_TEST_NEVER = 0x70L;
    /**
     * bgfx state depth test always constant.
     */
    public static final long BGFX_STATE_DEPTH_TEST_ALWAYS = 0x80L;
    /**
     * bgfx state blend zero constant.
     */
    public static final long BGFX_STATE_BLEND_ZERO = 0x1000L;
    /**
     * bgfx state blend one constant.
     */
    public static final long BGFX_STATE_BLEND_ONE = 0x2000L;
    /**
     * bgfx state blend src color constant.
     */
    public static final long BGFX_STATE_BLEND_SRC_COLOR = 0x3000L;
    /**
     * bgfx state blend inv src color constant.
     */
    public static final long BGFX_STATE_BLEND_INV_SRC_COLOR = 0x4000L;
    /**
     * bgfx state blend src alpha constant.
     */
    public static final long BGFX_STATE_BLEND_SRC_ALPHA = 0x5000L;
    /**
     * bgfx state blend inv src alpha constant.
     */
    public static final long BGFX_STATE_BLEND_INV_SRC_ALPHA = 0x6000L;
    /**
     * bgfx state blend dst alpha constant.
     */
    public static final long BGFX_STATE_BLEND_DST_ALPHA = 0x7000L;
    /**
     * bgfx state blend inv dst alpha constant.
     */
    public static final long BGFX_STATE_BLEND_INV_DST_ALPHA = 0x8000L;
    /**
     * bgfx state blend dst color constant.
     */
    public static final long BGFX_STATE_BLEND_DST_COLOR = 0x9000L;
    /**
     * bgfx state blend inv dst color constant.
     */
    public static final long BGFX_STATE_BLEND_INV_DST_COLOR = 0xA000L;
    /**
     * bgfx state blend src alpha sat constant.
     */
    public static final long BGFX_STATE_BLEND_SRC_ALPHA_SAT = 0xB000L;
    /**
     * bgfx state blend factor constant.
     */
    public static final long BGFX_STATE_BLEND_FACTOR = 0xC000L;
    /**
     * bgfx state blend inv factor constant.
     */
    public static final long BGFX_STATE_BLEND_INV_FACTOR = 0xD000L;
    /**
     * bgfx state blend equation add constant.
     */
    public static final long BGFX_STATE_BLEND_EQUATION_ADD = 0x0L;
    /**
     * bgfx state blend equation sub constant.
     */
    public static final long BGFX_STATE_BLEND_EQUATION_SUB = 0x10000000L;
    /**
     * bgfx state blend equation revsub constant.
     */
    public static final long BGFX_STATE_BLEND_EQUATION_REVSUB = 0x20000000L;
    /**
     * bgfx state blend equation min constant.
     */
    public static final long BGFX_STATE_BLEND_EQUATION_MIN = 0x30000000L;
    /**
     * bgfx state blend equation max constant.
     */
    public static final long BGFX_STATE_BLEND_EQUATION_MAX = 0x40000000L;
    /**
     * bgfx state cull cw constant.
     */
    public static final long BGFX_STATE_CULL_CW = 0x1000000000L;
    /**
     * bgfx state cull ccw constant.
     */
    public static final long BGFX_STATE_CULL_CCW = 0x2000000000L;
    /**
     * bgfx state pt tristrip constant.
     */
    public static final long BGFX_STATE_PT_TRISTRIP = 0x1000000000000L;
    /**
     * bgfx state pt lines constant.
     */
    public static final long BGFX_STATE_PT_LINES = 0x2000000000000L;
    /**
     * bgfx state pt linestrip constant.
     */
    public static final long BGFX_STATE_PT_LINESTRIP = 0x3000000000000L;
    /**
     * bgfx state pt points constant.
     */
    public static final long BGFX_STATE_PT_POINTS = 0x4000000000000L;
    /**
     * bgfx state msaa constant.
     */
    public static final long BGFX_STATE_MSAA = 0x100000000000000L;
    /**
     * bgfx state lineaa constant.
     */
    public static final long BGFX_STATE_LINEAA = 0x200000000000000L;
    /**
     * bgfx state conservative raster constant.
     */
    public static final long BGFX_STATE_CONSERVATIVE_RASTER = 0x400000000000000L;
    /**
     * bgfx state none constant.
     */
    public static final long BGFX_STATE_NONE = 0x0L;
    /**
     * bgfx state front ccw constant.
     */
    public static final long BGFX_STATE_FRONT_CCW = 0x8000000000L;
    /**
     * bgfx state blend independent constant.
     */
    public static final long BGFX_STATE_BLEND_INDEPENDENT = 0x400000000L;
    /**
     * bgfx state blend alpha to coverage constant.
     */
    public static final long BGFX_STATE_BLEND_ALPHA_TO_COVERAGE = 0x800000000L;
    /**
     * bgfx state default constant.
     */
    public static final long BGFX_STATE_DEFAULT = (0L
        | BGFX_STATE_WRITE_RGB
        | BGFX_STATE_WRITE_A
        | BGFX_STATE_WRITE_Z
        | BGFX_STATE_DEPTH_TEST_LESS
        | BGFX_STATE_CULL_CW
        | BGFX_STATE_MSAA);
    /**
     * bgfx state depth test mask constant.
     */
    public static final long BGFX_STATE_DEPTH_TEST_MASK = 0xF0L;
    /**
     * bgfx state blend mask constant.
     */
    public static final long BGFX_STATE_BLEND_MASK = 0xFFFF000L;
    /**
     * bgfx state blend equation mask constant.
     */
    public static final long BGFX_STATE_BLEND_EQUATION_MASK = 0x3F0000000L;
    /**
     * bgfx state cull mask constant.
     */
    public static final long BGFX_STATE_CULL_MASK = 0x3000000000L;
    /**
     * bgfx state alpha ref mask constant.
     */
    public static final long BGFX_STATE_ALPHA_REF_MASK = 0xFF0000000000L;
    /**
     * bgfx state pt mask constant.
     */
    public static final long BGFX_STATE_PT_MASK = 0x7000000000000L;
    /**
     * bgfx state point size mask constant.
     */
    public static final long BGFX_STATE_POINT_SIZE_MASK = 0xF0000000000000L;
    /**
     * bgfx state reserved mask constant.
     */
    public static final long BGFX_STATE_RESERVED_MASK = 0xe000000000000000L;
    /**
     * bgfx state mask constant.
     */
    public static final long BGFX_STATE_MASK = 0xffffffffffffffffL;
    /**
     * bgfx state depth test shift constant.
     */
    public static final int BGFX_STATE_DEPTH_TEST_SHIFT = 4;
    /**
     * bgfx state blend shift constant.
     */
    public static final int BGFX_STATE_BLEND_SHIFT = 12;
    /**
     * bgfx state blend equation shift constant.
     */
    public static final int BGFX_STATE_BLEND_EQUATION_SHIFT = 28;
    /**
     * bgfx state cull shift constant.
     */
    public static final int BGFX_STATE_CULL_SHIFT = 36;
    /**
     * bgfx state alpha ref shift constant.
     */
    public static final int BGFX_STATE_ALPHA_REF_SHIFT = 40;
    /**
     * bgfx state pt shift constant.
     */
    public static final int BGFX_STATE_PT_SHIFT = 48;
    /**
     * bgfx state point size shift constant.
     */
    public static final int BGFX_STATE_POINT_SIZE_SHIFT = 52;
    /**
     * bgfx state reserved shift constant.
     */
    public static final int BGFX_STATE_RESERVED_SHIFT = 61;
    /**
     * bgfx stencil test less constant.
     */
    public static final int BGFX_STENCIL_TEST_LESS = 0x10000;
    /**
     * bgfx stencil test lequal constant.
     */
    public static final int BGFX_STENCIL_TEST_LEQUAL = 0x20000;
    /**
     * bgfx stencil test equal constant.
     */
    public static final int BGFX_STENCIL_TEST_EQUAL = 0x30000;
    /**
     * bgfx stencil test gequal constant.
     */
    public static final int BGFX_STENCIL_TEST_GEQUAL = 0x40000;
    /**
     * bgfx stencil test greater constant.
     */
    public static final int BGFX_STENCIL_TEST_GREATER = 0x50000;
    /**
     * bgfx stencil test notequal constant.
     */
    public static final int BGFX_STENCIL_TEST_NOTEQUAL = 0x60000;
    /**
     * bgfx stencil test never constant.
     */
    public static final int BGFX_STENCIL_TEST_NEVER = 0x70000;
    /**
     * bgfx stencil test always constant.
     */
    public static final int BGFX_STENCIL_TEST_ALWAYS = 0x80000;
    /**
     * bgfx stencil op fail s zero constant.
     */
    public static final int BGFX_STENCIL_OP_FAIL_S_ZERO = 0x0;
    /**
     * bgfx stencil op fail s keep constant.
     */
    public static final int BGFX_STENCIL_OP_FAIL_S_KEEP = 0x100000;
    /**
     * bgfx stencil op fail s replace constant.
     */
    public static final int BGFX_STENCIL_OP_FAIL_S_REPLACE = 0x200000;
    /**
     * bgfx stencil op fail s incr constant.
     */
    public static final int BGFX_STENCIL_OP_FAIL_S_INCR = 0x300000;
    /**
     * bgfx stencil op fail s incrsat constant.
     */
    public static final int BGFX_STENCIL_OP_FAIL_S_INCRSAT = 0x400000;
    /**
     * bgfx stencil op fail s decr constant.
     */
    public static final int BGFX_STENCIL_OP_FAIL_S_DECR = 0x500000;
    /**
     * bgfx stencil op fail s decrsat constant.
     */
    public static final int BGFX_STENCIL_OP_FAIL_S_DECRSAT = 0x600000;
    /**
     * bgfx stencil op fail s invert constant.
     */
    public static final int BGFX_STENCIL_OP_FAIL_S_INVERT = 0x700000;
    /**
     * bgfx stencil op fail z zero constant.
     */
    public static final int BGFX_STENCIL_OP_FAIL_Z_ZERO = 0x0;
    /**
     * bgfx stencil op fail z keep constant.
     */
    public static final int BGFX_STENCIL_OP_FAIL_Z_KEEP = 0x1000000;
    /**
     * bgfx stencil op fail z replace constant.
     */
    public static final int BGFX_STENCIL_OP_FAIL_Z_REPLACE = 0x2000000;
    /**
     * bgfx stencil op fail z incr constant.
     */
    public static final int BGFX_STENCIL_OP_FAIL_Z_INCR = 0x3000000;
    /**
     * bgfx stencil op fail z incrsat constant.
     */
    public static final int BGFX_STENCIL_OP_FAIL_Z_INCRSAT = 0x4000000;
    /**
     * bgfx stencil op fail z decr constant.
     */
    public static final int BGFX_STENCIL_OP_FAIL_Z_DECR = 0x5000000;
    /**
     * bgfx stencil op fail z decrsat constant.
     */
    public static final int BGFX_STENCIL_OP_FAIL_Z_DECRSAT = 0x6000000;
    /**
     * bgfx stencil op fail z invert constant.
     */
    public static final int BGFX_STENCIL_OP_FAIL_Z_INVERT = 0x7000000;
    /**
     * bgfx stencil op pass z zero constant.
     */
    public static final int BGFX_STENCIL_OP_PASS_Z_ZERO = 0x0;
    /**
     * bgfx stencil op pass z keep constant.
     */
    public static final int BGFX_STENCIL_OP_PASS_Z_KEEP = 0x10000000;
    /**
     * bgfx stencil op pass z replace constant.
     */
    public static final int BGFX_STENCIL_OP_PASS_Z_REPLACE = 0x20000000;
    /**
     * bgfx stencil op pass z incr constant.
     */
    public static final int BGFX_STENCIL_OP_PASS_Z_INCR = 0x30000000;
    /**
     * bgfx stencil op pass z incrsat constant.
     */
    public static final int BGFX_STENCIL_OP_PASS_Z_INCRSAT = 0x40000000;
    /**
     * bgfx stencil op pass z decr constant.
     */
    public static final int BGFX_STENCIL_OP_PASS_Z_DECR = 0x50000000;
    /**
     * bgfx stencil op pass z decrsat constant.
     */
    public static final int BGFX_STENCIL_OP_PASS_Z_DECRSAT = 0x60000000;
    /**
     * bgfx stencil op pass z invert constant.
     */
    public static final int BGFX_STENCIL_OP_PASS_Z_INVERT = 0x70000000;
    /**
     * bgfx stencil none constant.
     */
    public static final int BGFX_STENCIL_NONE = 0xFF00;
    /**
     * bgfx stencil func ref shift constant.
     */
    public static final int BGFX_STENCIL_FUNC_REF_SHIFT = 0;
    /**
     * bgfx stencil func ref mask constant.
     */
    public static final int BGFX_STENCIL_FUNC_REF_MASK = 0xFF;
    /**
     * bgfx stencil func rmask shift constant.
     */
    public static final int BGFX_STENCIL_FUNC_RMASK_SHIFT = 8;
    /**
     * bgfx stencil func rmask mask constant.
     */
    public static final int BGFX_STENCIL_FUNC_RMASK_MASK = 0xFF00;
    /**
     * bgfx stencil test shift constant.
     */
    public static final int BGFX_STENCIL_TEST_SHIFT = 16;
    /**
     * bgfx stencil test mask constant.
     */
    public static final int BGFX_STENCIL_TEST_MASK = 0xF0000;
    /**
     * bgfx stencil op fail s shift constant.
     */
    public static final int BGFX_STENCIL_OP_FAIL_S_SHIFT = 20;
    /**
     * bgfx stencil op fail s mask constant.
     */
    public static final int BGFX_STENCIL_OP_FAIL_S_MASK = 0xF00000;
    /**
     * bgfx stencil op fail z shift constant.
     */
    public static final int BGFX_STENCIL_OP_FAIL_Z_SHIFT = 24;
    /**
     * bgfx stencil op fail z mask constant.
     */
    public static final int BGFX_STENCIL_OP_FAIL_Z_MASK = 0xF000000;
    /**
     * bgfx stencil op pass z shift constant.
     */
    public static final int BGFX_STENCIL_OP_PASS_Z_SHIFT = 28;
    /**
     * bgfx stencil op pass z mask constant.
     */
    public static final int BGFX_STENCIL_OP_PASS_Z_MASK = 0xf0000000;
    /**
     * bgfx stencil mask constant.
     */
    public static final int BGFX_STENCIL_MASK = 0xffffffff;
    /**
     * bgfx clear none constant.
     */
    public static final int BGFX_CLEAR_NONE = 0x0;
    /**
     * bgfx clear color constant.
     */
    public static final int BGFX_CLEAR_COLOR = 0x1;
    /**
     * bgfx clear depth constant.
     */
    public static final int BGFX_CLEAR_DEPTH = 0x2;
    /**
     * bgfx clear stencil constant.
     */
    public static final int BGFX_CLEAR_STENCIL = 0x4;
    /**
     * bgfx clear discard color 0 constant.
     */
    public static final int BGFX_CLEAR_DISCARD_COLOR_0 = 0x8;
    /**
     * bgfx clear discard color 1 constant.
     */
    public static final int BGFX_CLEAR_DISCARD_COLOR_1 = 0x10;
    /**
     * bgfx clear discard color 2 constant.
     */
    public static final int BGFX_CLEAR_DISCARD_COLOR_2 = 0x20;
    /**
     * bgfx clear discard color 3 constant.
     */
    public static final int BGFX_CLEAR_DISCARD_COLOR_3 = 0x40;
    /**
     * bgfx clear discard color 4 constant.
     */
    public static final int BGFX_CLEAR_DISCARD_COLOR_4 = 0x80;
    /**
     * bgfx clear discard color 5 constant.
     */
    public static final int BGFX_CLEAR_DISCARD_COLOR_5 = 0x100;
    /**
     * bgfx clear discard color 6 constant.
     */
    public static final int BGFX_CLEAR_DISCARD_COLOR_6 = 0x200;
    /**
     * bgfx clear discard color 7 constant.
     */
    public static final int BGFX_CLEAR_DISCARD_COLOR_7 = 0x400;
    /**
     * bgfx clear discard depth constant.
     */
    public static final int BGFX_CLEAR_DISCARD_DEPTH = 0x800;
    /**
     * bgfx clear discard stencil constant.
     */
    public static final int BGFX_CLEAR_DISCARD_STENCIL = 0x1000;
    /**
     * bgfx clear discard color mask constant.
     */
    public static final int BGFX_CLEAR_DISCARD_COLOR_MASK = (0
        | BGFX_CLEAR_DISCARD_COLOR_0
        | BGFX_CLEAR_DISCARD_COLOR_1
        | BGFX_CLEAR_DISCARD_COLOR_2
        | BGFX_CLEAR_DISCARD_COLOR_3
        | BGFX_CLEAR_DISCARD_COLOR_4
        | BGFX_CLEAR_DISCARD_COLOR_5
        | BGFX_CLEAR_DISCARD_COLOR_6
        | BGFX_CLEAR_DISCARD_COLOR_7);
    /**
     * bgfx clear discard mask constant.
     */
    public static final int BGFX_CLEAR_DISCARD_MASK = (0
        | BGFX_CLEAR_DISCARD_COLOR_MASK
        | BGFX_CLEAR_DISCARD_DEPTH
        | BGFX_CLEAR_DISCARD_STENCIL);
    /**
     * bgfx discard none constant.
     */
    public static final byte BGFX_DISCARD_NONE = 0x0;
    /**
     * bgfx discard bindings constant.
     */
    public static final byte BGFX_DISCARD_BINDINGS = 0x1;
    /**
     * bgfx discard index buffer constant.
     */
    public static final byte BGFX_DISCARD_INDEX_BUFFER = 0x2;
    /**
     * bgfx discard instance data constant.
     */
    public static final byte BGFX_DISCARD_INSTANCE_DATA = 0x4;
    /**
     * bgfx discard state constant.
     */
    public static final byte BGFX_DISCARD_STATE = 0x8;
    /**
     * bgfx discard transform constant.
     */
    public static final byte BGFX_DISCARD_TRANSFORM = 0x10;
    /**
     * bgfx discard vertex streams constant.
     */
    public static final byte BGFX_DISCARD_VERTEX_STREAMS = 0x20;
    /**
     * bgfx discard all constant.
     */
    public static final byte BGFX_DISCARD_ALL = (byte)0xFF;
    /**
     * bgfx debug none constant.
     */
    public static final int BGFX_DEBUG_NONE = 0x0;
    /**
     * bgfx debug wireframe constant.
     */
    public static final int BGFX_DEBUG_WIREFRAME = 0x1;
    /**
     * bgfx debug ifh constant.
     */
    public static final int BGFX_DEBUG_IFH = 0x2;
    /**
     * bgfx debug stats constant.
     */
    public static final int BGFX_DEBUG_STATS = 0x4;
    /**
     * bgfx debug text constant.
     */
    public static final int BGFX_DEBUG_TEXT = 0x8;
    /**
     * bgfx debug profiler constant.
     */
    public static final int BGFX_DEBUG_PROFILER = 0x10;
    /**
     * bgfx buffer none constant.
     */
    public static final int BGFX_BUFFER_NONE = 0x0;
    /**
     * bgfx buffer compute read constant.
     */
    public static final int BGFX_BUFFER_COMPUTE_READ = 0x100;
    /**
     * bgfx buffer compute write constant.
     */
    public static final int BGFX_BUFFER_COMPUTE_WRITE = 0x200;
    /**
     * bgfx buffer draw indirect constant.
     */
    public static final int BGFX_BUFFER_DRAW_INDIRECT = 0x400;
    /**
     * bgfx buffer allow resize constant.
     */
    public static final int BGFX_BUFFER_ALLOW_RESIZE = 0x800;
    /**
     * bgfx buffer index32 constant.
     */
    public static final int BGFX_BUFFER_INDEX32 = 0x1000;
    /**
     * bgfx buffer compute read write constant.
     */
    public static final int BGFX_BUFFER_COMPUTE_READ_WRITE = (0
        | BGFX_BUFFER_COMPUTE_READ
        | BGFX_BUFFER_COMPUTE_WRITE);
    /**
     * bgfx texture none constant.
     */
    public static final long BGFX_TEXTURE_NONE = 0x0L;
    /**
     * bgfx texture msaa sample constant.
     */
    public static final long BGFX_TEXTURE_MSAA_SAMPLE = 0x800000000L;
    /**
     * bgfx texture rt constant.
     */
    public static final long BGFX_TEXTURE_RT = 0x1000000000L;
    /**
     * bgfx texture rt msaa x2 constant.
     */
    public static final long BGFX_TEXTURE_RT_MSAA_X2 = 0x2000000000L;
    /**
     * bgfx texture rt msaa x4 constant.
     */
    public static final long BGFX_TEXTURE_RT_MSAA_X4 = 0x3000000000L;
    /**
     * bgfx texture rt msaa x8 constant.
     */
    public static final long BGFX_TEXTURE_RT_MSAA_X8 = 0x4000000000L;
    /**
     * bgfx texture rt msaa x16 constant.
     */
    public static final long BGFX_TEXTURE_RT_MSAA_X16 = 0x5000000000L;
    /**
     * bgfx texture rt write only constant.
     */
    public static final long BGFX_TEXTURE_RT_WRITE_ONLY = 0x8000000000L;
    /**
     * bgfx texture compute write constant.
     */
    public static final long BGFX_TEXTURE_COMPUTE_WRITE = 0x100000000000L;
    /**
     * bgfx texture srgb constant.
     */
    public static final long BGFX_TEXTURE_SRGB = 0x200000000000L;
    /**
     * bgfx texture blit dst constant.
     */
    public static final long BGFX_TEXTURE_BLIT_DST = 0x400000000000L;
    /**
     * bgfx texture read back constant.
     */
    public static final long BGFX_TEXTURE_READ_BACK = 0x800000000000L;
    /**
     * bgfx texture external shared constant.
     */
    public static final long BGFX_TEXTURE_EXTERNAL_SHARED = 0x1000000000000L;
    /**
     * bgfx texture rt msaa shift constant.
     */
    public static final int BGFX_TEXTURE_RT_MSAA_SHIFT = 36;
    /**
     * bgfx texture rt msaa mask constant.
     */
    public static final long BGFX_TEXTURE_RT_MSAA_MASK = 0x7000000000L;
    /**
     * bgfx texture rt mask constant.
     */
    public static final long BGFX_TEXTURE_RT_MASK = 0xF000000000L;
    /**
     * bgfx sampler none constant.
     */
    public static final int BGFX_SAMPLER_NONE = 0x0;
    /**
     * bgfx sampler u mirror constant.
     */
    public static final int BGFX_SAMPLER_U_MIRROR = 0x1;
    /**
     * bgfx sampler u clamp constant.
     */
    public static final int BGFX_SAMPLER_U_CLAMP = 0x2;
    /**
     * bgfx sampler u border constant.
     */
    public static final int BGFX_SAMPLER_U_BORDER = 0x3;
    /**
     * bgfx sampler v mirror constant.
     */
    public static final int BGFX_SAMPLER_V_MIRROR = 0x4;
    /**
     * bgfx sampler v clamp constant.
     */
    public static final int BGFX_SAMPLER_V_CLAMP = 0x8;
    /**
     * bgfx sampler v border constant.
     */
    public static final int BGFX_SAMPLER_V_BORDER = 0xC;
    /**
     * bgfx sampler w mirror constant.
     */
    public static final int BGFX_SAMPLER_W_MIRROR = 0x10;
    /**
     * bgfx sampler w clamp constant.
     */
    public static final int BGFX_SAMPLER_W_CLAMP = 0x20;
    /**
     * bgfx sampler w border constant.
     */
    public static final int BGFX_SAMPLER_W_BORDER = 0x30;
    /**
     * bgfx sampler uvw mirror constant.
     */
    public static final int BGFX_SAMPLER_UVW_MIRROR = (0 | BGFX_SAMPLER_U_MIRROR | BGFX_SAMPLER_V_MIRROR | BGFX_SAMPLER_W_MIRROR);
    /**
     * bgfx sampler uvw clamp constant.
     */
    public static final int BGFX_SAMPLER_UVW_CLAMP = (0 | BGFX_SAMPLER_U_CLAMP | BGFX_SAMPLER_V_CLAMP | BGFX_SAMPLER_W_CLAMP);
    /**
     * bgfx sampler uvw border constant.
     */
    public static final int BGFX_SAMPLER_UVW_BORDER = (0 | BGFX_SAMPLER_U_BORDER | BGFX_SAMPLER_V_BORDER | BGFX_SAMPLER_W_BORDER);
    /**
     * bgfx sampler min point constant.
     */
    public static final int BGFX_SAMPLER_MIN_POINT = 0x40;
    /**
     * bgfx sampler min anisotropic constant.
     */
    public static final int BGFX_SAMPLER_MIN_ANISOTROPIC = 0x80;
    /**
     * bgfx sampler mag point constant.
     */
    public static final int BGFX_SAMPLER_MAG_POINT = 0x100;
    /**
     * bgfx sampler mag anisotropic constant.
     */
    public static final int BGFX_SAMPLER_MAG_ANISOTROPIC = 0x200;
    /**
     * bgfx sampler mip point constant.
     */
    public static final int BGFX_SAMPLER_MIP_POINT = 0x400;
    /**
     * bgfx sampler point constant.
     */
    public static final int BGFX_SAMPLER_POINT = (0 | BGFX_SAMPLER_MIN_POINT | BGFX_SAMPLER_MAG_POINT | BGFX_SAMPLER_MIP_POINT);
    /**
     * bgfx sampler compare less constant.
     */
    public static final int BGFX_SAMPLER_COMPARE_LESS = 0x10000;
    /**
     * bgfx sampler compare lequal constant.
     */
    public static final int BGFX_SAMPLER_COMPARE_LEQUAL = 0x20000;
    /**
     * bgfx sampler compare equal constant.
     */
    public static final int BGFX_SAMPLER_COMPARE_EQUAL = 0x30000;
    /**
     * bgfx sampler compare gequal constant.
     */
    public static final int BGFX_SAMPLER_COMPARE_GEQUAL = 0x40000;
    /**
     * bgfx sampler compare greater constant.
     */
    public static final int BGFX_SAMPLER_COMPARE_GREATER = 0x50000;
    /**
     * bgfx sampler compare notequal constant.
     */
    public static final int BGFX_SAMPLER_COMPARE_NOTEQUAL = 0x60000;
    /**
     * bgfx sampler compare never constant.
     */
    public static final int BGFX_SAMPLER_COMPARE_NEVER = 0x70000;
    /**
     * bgfx sampler compare always constant.
     */
    public static final int BGFX_SAMPLER_COMPARE_ALWAYS = 0x80000;
    /**
     * bgfx sampler sample stencil constant.
     */
    public static final int BGFX_SAMPLER_SAMPLE_STENCIL = 0x100000;
    /**
     * bgfx sampler u shift constant.
     */
    public static final int BGFX_SAMPLER_U_SHIFT = 0;
    /**
     * bgfx sampler u mask constant.
     */
    public static final int BGFX_SAMPLER_U_MASK = 0x3;
    /**
     * bgfx sampler v shift constant.
     */
    public static final int BGFX_SAMPLER_V_SHIFT = 2;
    /**
     * bgfx sampler v mask constant.
     */
    public static final int BGFX_SAMPLER_V_MASK = 0xC;
    /**
     * bgfx sampler w shift constant.
     */
    public static final int BGFX_SAMPLER_W_SHIFT = 4;
    /**
     * bgfx sampler w mask constant.
     */
    public static final int BGFX_SAMPLER_W_MASK = 0x30;
    /**
     * bgfx sampler min shift constant.
     */
    public static final int BGFX_SAMPLER_MIN_SHIFT = 6;
    /**
     * bgfx sampler min mask constant.
     */
    public static final int BGFX_SAMPLER_MIN_MASK = 0xC0;
    /**
     * bgfx sampler mag shift constant.
     */
    public static final int BGFX_SAMPLER_MAG_SHIFT = 8;
    /**
     * bgfx sampler mag mask constant.
     */
    public static final int BGFX_SAMPLER_MAG_MASK = 0x300;
    /**
     * bgfx sampler mip shift constant.
     */
    public static final int BGFX_SAMPLER_MIP_SHIFT = 10;
    /**
     * bgfx sampler mip mask constant.
     */
    public static final int BGFX_SAMPLER_MIP_MASK = 0x400;
    /**
     * bgfx sampler compare shift constant.
     */
    public static final int BGFX_SAMPLER_COMPARE_SHIFT = 16;
    /**
     * bgfx sampler compare mask constant.
     */
    public static final int BGFX_SAMPLER_COMPARE_MASK = 0xF0000;
    /**
     * bgfx sampler border color shift constant.
     */
    public static final int BGFX_SAMPLER_BORDER_COLOR_SHIFT = 24;
    /**
     * bgfx sampler border color mask constant.
     */
    public static final int BGFX_SAMPLER_BORDER_COLOR_MASK = 0xF000000;
    /**
     * bgfx sampler reserved shift constant.
     */
    public static final int BGFX_SAMPLER_RESERVED_SHIFT = 28;
    /**
     * bgfx sampler reserved mask constant.
     */
    public static final int BGFX_SAMPLER_RESERVED_MASK = 0xF0000000;
    /**
     * bgfx sampler sampler bits mask constant.
     */
    public static final int BGFX_SAMPLER_SAMPLER_BITS_MASK = (0
        | BGFX_SAMPLER_U_MASK
        | BGFX_SAMPLER_V_MASK
        | BGFX_SAMPLER_W_MASK
        | BGFX_SAMPLER_MIN_MASK
        | BGFX_SAMPLER_MAG_MASK
        | BGFX_SAMPLER_MIP_MASK
        | BGFX_SAMPLER_COMPARE_MASK);
    /**
     * bgfx reset none constant.
     */
    public static final int BGFX_RESET_NONE = 0x0;
    /**
     * bgfx reset fullscreen constant.
     */
    public static final int BGFX_RESET_FULLSCREEN = 0x1;
    /**
     * bgfx reset fullscreen shift constant.
     */
    public static final int BGFX_RESET_FULLSCREEN_SHIFT = 0;
    /**
     * bgfx reset fullscreen mask constant.
     */
    public static final int BGFX_RESET_FULLSCREEN_MASK = 0x1;
    /**
     * bgfx reset msaa x2 constant.
     */
    public static final int BGFX_RESET_MSAA_X2 = 0x10;
    /**
     * bgfx reset msaa x4 constant.
     */
    public static final int BGFX_RESET_MSAA_X4 = 0x20;
    /**
     * bgfx reset msaa x8 constant.
     */
    public static final int BGFX_RESET_MSAA_X8 = 0x30;
    /**
     * bgfx reset msaa x16 constant.
     */
    public static final int BGFX_RESET_MSAA_X16 = 0x40;
    /**
     * bgfx reset msaa shift constant.
     */
    public static final int BGFX_RESET_MSAA_SHIFT = 4;
    /**
     * bgfx reset msaa mask constant.
     */
    public static final int BGFX_RESET_MSAA_MASK = 0x70;
    /**
     * bgfx reset vsync constant.
     */
    public static final int BGFX_RESET_VSYNC = 0x80;
    /**
     * bgfx reset maxanisotropy constant.
     */
    public static final int BGFX_RESET_MAXANISOTROPY = 0x100;
    /**
     * bgfx reset capture constant.
     */
    public static final int BGFX_RESET_CAPTURE = 0x200;
    /**
     * bgfx reset flush after render constant.
     */
    public static final int BGFX_RESET_FLUSH_AFTER_RENDER = 0x2000;
    /**
     * bgfx reset flip after render constant.
     */
    public static final int BGFX_RESET_FLIP_AFTER_RENDER = 0x4000;
    /**
     * bgfx reset srgb backbuffer constant.
     */
    public static final int BGFX_RESET_SRGB_BACKBUFFER = 0x8000;
    /**
     * bgfx reset hdr10 constant.
     */
    public static final int BGFX_RESET_HDR10 = 0x10000;
    /**
     * bgfx reset hidpi constant.
     */
    public static final int BGFX_RESET_HIDPI = 0x20000;
    /**
     * bgfx reset depth clamp constant.
     */
    public static final int BGFX_RESET_DEPTH_CLAMP = 0x40000;
    /**
     * bgfx reset suspend constant.
     */
    public static final int BGFX_RESET_SUSPEND = 0x80000;
    /**
     * bgfx reset transparent backbuffer constant.
     */
    public static final int BGFX_RESET_TRANSPARENT_BACKBUFFER = 0x100000;
    /**
     * bgfx reset reserved shift constant.
     */
    public static final int BGFX_RESET_RESERVED_SHIFT = 31;
    /**
     * bgfx reset reserved mask constant.
     */
    public static final int BGFX_RESET_RESERVED_MASK = 0x80000000;
    /**
     * bgfx caps alpha to coverage constant.
     */
    public static final long BGFX_CAPS_ALPHA_TO_COVERAGE = 0x1L;
    /**
     * bgfx caps blend independent constant.
     */
    public static final long BGFX_CAPS_BLEND_INDEPENDENT = 0x2L;
    /**
     * bgfx caps compute constant.
     */
    public static final long BGFX_CAPS_COMPUTE = 0x4L;
    /**
     * bgfx caps conservative raster constant.
     */
    public static final long BGFX_CAPS_CONSERVATIVE_RASTER = 0x8L;
    /**
     * bgfx caps draw indirect constant.
     */
    public static final long BGFX_CAPS_DRAW_INDIRECT = 0x10L;
    /**
     * bgfx caps draw indirect count constant.
     */
    public static final long BGFX_CAPS_DRAW_INDIRECT_COUNT = 0x20L;
    /**
     * bgfx caps fragment depth constant.
     */
    public static final long BGFX_CAPS_FRAGMENT_DEPTH = 0x40L;
    /**
     * bgfx caps fragment ordering constant.
     */
    public static final long BGFX_CAPS_FRAGMENT_ORDERING = 0x80L;
    /**
     * bgfx caps graphics debugger constant.
     */
    public static final long BGFX_CAPS_GRAPHICS_DEBUGGER = 0x100L;
    /**
     * bgfx caps hdr10 constant.
     */
    public static final long BGFX_CAPS_HDR10 = 0x200L;
    /**
     * bgfx caps hidpi constant.
     */
    public static final long BGFX_CAPS_HIDPI = 0x400L;
    /**
     * bgfx caps image rw constant.
     */
    public static final long BGFX_CAPS_IMAGE_RW = 0x800L;
    /**
     * bgfx caps index32 constant.
     */
    public static final long BGFX_CAPS_INDEX32 = 0x1000L;
    /**
     * bgfx caps instancing constant.
     */
    public static final long BGFX_CAPS_INSTANCING = 0x2000L;
    /**
     * bgfx caps occlusion query constant.
     */
    public static final long BGFX_CAPS_OCCLUSION_QUERY = 0x4000L;
    /**
     * bgfx caps primitive id constant.
     */
    public static final long BGFX_CAPS_PRIMITIVE_ID = 0x8000L;
    /**
     * bgfx caps renderer multithreaded constant.
     */
    public static final long BGFX_CAPS_RENDERER_MULTITHREADED = 0x10000L;
    /**
     * bgfx caps swap chain constant.
     */
    public static final long BGFX_CAPS_SWAP_CHAIN = 0x20000L;
    /**
     * bgfx caps texture blit constant.
     */
    public static final long BGFX_CAPS_TEXTURE_BLIT = 0x40000L;
    /**
     * bgfx caps texture compare lequal constant.
     */
    public static final long BGFX_CAPS_TEXTURE_COMPARE_LEQUAL = 0x80000L;
    /**
     * bgfx caps texture compare reserved constant.
     */
    public static final long BGFX_CAPS_TEXTURE_COMPARE_RESERVED = 0x100000L;
    /**
     * bgfx caps texture cube array constant.
     */
    public static final long BGFX_CAPS_TEXTURE_CUBE_ARRAY = 0x200000L;
    /**
     * bgfx caps texture direct access constant.
     */
    public static final long BGFX_CAPS_TEXTURE_DIRECT_ACCESS = 0x400000L;
    /**
     * bgfx caps texture external constant.
     */
    public static final long BGFX_CAPS_TEXTURE_EXTERNAL = 0x800000L;
    /**
     * bgfx caps texture external shared constant.
     */
    public static final long BGFX_CAPS_TEXTURE_EXTERNAL_SHARED = 0x1000000L;
    /**
     * bgfx caps texture read back constant.
     */
    public static final long BGFX_CAPS_TEXTURE_READ_BACK = 0x2000000L;
    /**
     * bgfx caps texture 2d array constant.
     */
    public static final long BGFX_CAPS_TEXTURE_2D_ARRAY = 0x4000000L;
    /**
     * bgfx caps texture 3d constant.
     */
    public static final long BGFX_CAPS_TEXTURE_3D = 0x8000000L;
    /**
     * bgfx caps transparent backbuffer constant.
     */
    public static final long BGFX_CAPS_TRANSPARENT_BACKBUFFER = 0x10000000L;
    /**
     * bgfx caps variable rate shading constant.
     */
    public static final long BGFX_CAPS_VARIABLE_RATE_SHADING = 0x20000000L;
    /**
     * bgfx caps vertex attrib half constant.
     */
    public static final long BGFX_CAPS_VERTEX_ATTRIB_HALF = 0x40000000L;
    /**
     * bgfx caps vertex attrib uint10 constant.
     */
    public static final long BGFX_CAPS_VERTEX_ATTRIB_UINT10 = 0x80000000L;
    /**
     * bgfx caps vertex id constant.
     */
    public static final long BGFX_CAPS_VERTEX_ID = 0x100000000L;
    /**
     * bgfx caps video decode constant.
     */
    public static final long BGFX_CAPS_VIDEO_DECODE = 0x200000000L;
    /**
     * bgfx caps viewport layer array constant.
     */
    public static final long BGFX_CAPS_VIEWPORT_LAYER_ARRAY = 0x400000000L;
    /**
     * bgfx caps texture compare all constant.
     */
    public static final long BGFX_CAPS_TEXTURE_COMPARE_ALL = BGFX_CAPS_TEXTURE_COMPARE_RESERVED | BGFX_CAPS_TEXTURE_COMPARE_LEQUAL;
    /**
     * bgfx caps format texture none constant.
     */
    public static final int BGFX_CAPS_FORMAT_TEXTURE_NONE = 0x0;
    /**
     * bgfx caps format texture 2d constant.
     */
    public static final int BGFX_CAPS_FORMAT_TEXTURE_2D = 0x1;
    /**
     * bgfx caps format texture 2d srgb constant.
     */
    public static final int BGFX_CAPS_FORMAT_TEXTURE_2D_SRGB = 0x2;
    /**
     * bgfx caps format texture 2d emulated constant.
     */
    public static final int BGFX_CAPS_FORMAT_TEXTURE_2D_EMULATED = 0x4;
    /**
     * bgfx caps format texture 3d constant.
     */
    public static final int BGFX_CAPS_FORMAT_TEXTURE_3D = 0x8;
    /**
     * bgfx caps format texture 3d srgb constant.
     */
    public static final int BGFX_CAPS_FORMAT_TEXTURE_3D_SRGB = 0x10;
    /**
     * bgfx caps format texture 3d emulated constant.
     */
    public static final int BGFX_CAPS_FORMAT_TEXTURE_3D_EMULATED = 0x20;
    /**
     * bgfx caps format texture cube constant.
     */
    public static final int BGFX_CAPS_FORMAT_TEXTURE_CUBE = 0x40;
    /**
     * bgfx caps format texture cube srgb constant.
     */
    public static final int BGFX_CAPS_FORMAT_TEXTURE_CUBE_SRGB = 0x80;
    /**
     * bgfx caps format texture cube emulated constant.
     */
    public static final int BGFX_CAPS_FORMAT_TEXTURE_CUBE_EMULATED = 0x100;
    /**
     * bgfx caps format texture vertex constant.
     */
    public static final int BGFX_CAPS_FORMAT_TEXTURE_VERTEX = 0x200;
    /**
     * bgfx caps format texture image read constant.
     */
    public static final int BGFX_CAPS_FORMAT_TEXTURE_IMAGE_READ = 0x400;
    /**
     * bgfx caps format texture image write constant.
     */
    public static final int BGFX_CAPS_FORMAT_TEXTURE_IMAGE_WRITE = 0x800;
    /**
     * bgfx caps format texture framebuffer constant.
     */
    public static final int BGFX_CAPS_FORMAT_TEXTURE_FRAMEBUFFER = 0x1000;
    /**
     * bgfx caps format texture framebuffer msaa constant.
     */
    public static final int BGFX_CAPS_FORMAT_TEXTURE_FRAMEBUFFER_MSAA = 0x2000;
    /**
     * bgfx caps format texture msaa constant.
     */
    public static final int BGFX_CAPS_FORMAT_TEXTURE_MSAA = 0x4000;
    /**
     * bgfx caps format texture mip autogen constant.
     */
    public static final int BGFX_CAPS_FORMAT_TEXTURE_MIP_AUTOGEN = 0x8000;
    /**
     * bgfx caps format texture backbuffer constant.
     */
    public static final int BGFX_CAPS_FORMAT_TEXTURE_BACKBUFFER = 0x10000;
    /**
     * bgfx caps format texture video decode dst constant.
     */
    public static final int BGFX_CAPS_FORMAT_TEXTURE_VIDEO_DECODE_DST = 0x20000;
    /**
     * bgfx caps video codec none constant.
     */
    public static final int BGFX_CAPS_VIDEO_CODEC_NONE = 0x0;
    /**
     * bgfx caps video codec bit 8 constant.
     */
    public static final int BGFX_CAPS_VIDEO_CODEC_BIT_8 = 0x1;
    /**
     * bgfx caps video codec bit 10 constant.
     */
    public static final int BGFX_CAPS_VIDEO_CODEC_BIT_10 = 0x2;
    /**
     * bgfx caps video codec bit 12 constant.
     */
    public static final int BGFX_CAPS_VIDEO_CODEC_BIT_12 = 0x4;
    /**
     * bgfx caps video codec chroma 420 constant.
     */
    public static final int BGFX_CAPS_VIDEO_CODEC_CHROMA_420 = 0x8;
    /**
     * bgfx caps video codec chroma 422 constant.
     */
    public static final int BGFX_CAPS_VIDEO_CODEC_CHROMA_422 = 0x10;
    /**
     * bgfx caps video codec chroma 444 constant.
     */
    public static final int BGFX_CAPS_VIDEO_CODEC_CHROMA_444 = 0x20;
    /**
     * bgfx video decoder init none constant.
     */
    public static final byte BGFX_VIDEO_DECODER_INIT_NONE = 0x0;
    /**
     * bgfx video decoder init retain constant.
     */
    public static final byte BGFX_VIDEO_DECODER_INIT_RETAIN = 0x1;
    /**
     * bgfx video decode frame none constant.
     */
    public static final byte BGFX_VIDEO_DECODE_FRAME_NONE = 0x0;
    /**
     * bgfx video decode frame set constant.
     */
    public static final byte BGFX_VIDEO_DECODE_FRAME_SET = 0x1;
    /**
     * bgfx video decode frame no blit constant.
     */
    public static final byte BGFX_VIDEO_DECODE_FRAME_NO_BLIT = 0x2;
    /**
     * bgfx video decode frame final constant.
     */
    public static final byte BGFX_VIDEO_DECODE_FRAME_FINAL = 0x4;
    /**
     * bgfx video decode frame loop constant.
     */
    public static final byte BGFX_VIDEO_DECODE_FRAME_LOOP = 0x8;
    /**
     * bgfx resolve none constant.
     */
    public static final byte BGFX_RESOLVE_NONE = 0x0;
    /**
     * bgfx resolve auto gen mips constant.
     */
    public static final byte BGFX_RESOLVE_AUTO_GEN_MIPS = 0x1;
    /**
     * bgfx pci id none constant.
     */
    public static final short BGFX_PCI_ID_NONE = 0x0;
    /**
     * bgfx pci id software rasterizer constant.
     */
    public static final short BGFX_PCI_ID_SOFTWARE_RASTERIZER = 0x1;
    /**
     * bgfx pci id amd constant.
     */
    public static final short BGFX_PCI_ID_AMD = 0x1002;
    /**
     * bgfx pci id apple constant.
     */
    public static final short BGFX_PCI_ID_APPLE = 0x106B;
    /**
     * bgfx pci id intel constant.
     */
    public static final short BGFX_PCI_ID_INTEL = (short)0x8086;
    /**
     * bgfx pci id nvidia constant.
     */
    public static final short BGFX_PCI_ID_NVIDIA = 0x10DE;
    /**
     * bgfx pci id microsoft constant.
     */
    public static final short BGFX_PCI_ID_MICROSOFT = 0x1414;
    /**
     * bgfx pci id arm constant.
     */
    public static final short BGFX_PCI_ID_ARM = 0x13B5;
    /**
     * bgfx cube map positive x constant.
     */
    public static final byte BGFX_CUBE_MAP_POSITIVE_X = 0x0;
    /**
     * bgfx cube map negative x constant.
     */
    public static final byte BGFX_CUBE_MAP_NEGATIVE_X = 0x1;
    /**
     * bgfx cube map positive y constant.
     */
    public static final byte BGFX_CUBE_MAP_POSITIVE_Y = 0x2;
    /**
     * bgfx cube map negative y constant.
     */
    public static final byte BGFX_CUBE_MAP_NEGATIVE_Y = 0x3;
    /**
     * bgfx cube map positive z constant.
     */
    public static final byte BGFX_CUBE_MAP_POSITIVE_Z = 0x4;
    /**
     * bgfx cube map negative z constant.
     */
    public static final byte BGFX_CUBE_MAP_NEGATIVE_Z = 0x5;
    /**
     * bgfx frame none constant.
     */
    public static final byte BGFX_FRAME_NONE = 0x0;
    /**
     * bgfx frame debug capture constant.
     */
    public static final byte BGFX_FRAME_DEBUG_CAPTURE = 0x1;
    /**
     * bgfx frame discard constant.
     */
    public static final byte BGFX_FRAME_DISCARD = 0x2;
    /**
     * bgfx frame flush constant.
     */
    public static final byte BGFX_FRAME_FLUSH = 0x4;
    /**
     * bgfx fatal debug check constant.
     */
    public static final int BGFX_FATAL_DEBUG_CHECK = 0;
    /**
     * bgfx fatal invalid shader constant.
     */
    public static final int BGFX_FATAL_INVALID_SHADER = 1;
    /**
     * bgfx fatal unable to initialize constant.
     */
    public static final int BGFX_FATAL_UNABLE_TO_INITIALIZE = 2;
    /**
     * bgfx fatal unable to create texture constant.
     */
    public static final int BGFX_FATAL_UNABLE_TO_CREATE_TEXTURE = 3;
    /**
     * bgfx fatal device lost constant.
     */
    public static final int BGFX_FATAL_DEVICE_LOST = 4;
    /**
     * bgfx fatal count constant.
     */
    public static final int BGFX_FATAL_COUNT = 5;
    /**
     * bgfx renderer type noop constant.
     */
    public static final int BGFX_RENDERER_TYPE_NOOP = 0;
    /**
     * bgfx renderer type agc constant.
     */
    public static final int BGFX_RENDERER_TYPE_AGC = 1;
    /**
     * bgfx renderer type direct3d11 constant.
     */
    public static final int BGFX_RENDERER_TYPE_DIRECT3D11 = 2;
    /**
     * bgfx renderer type direct3d12 constant.
     */
    public static final int BGFX_RENDERER_TYPE_DIRECT3D12 = 3;
    /**
     * bgfx renderer type gnm constant.
     */
    public static final int BGFX_RENDERER_TYPE_GNM = 4;
    /**
     * bgfx renderer type metal constant.
     */
    public static final int BGFX_RENDERER_TYPE_METAL = 5;
    /**
     * bgfx renderer type nvn constant.
     */
    public static final int BGFX_RENDERER_TYPE_NVN = 6;
    /**
     * bgfx renderer type opengles constant.
     */
    public static final int BGFX_RENDERER_TYPE_OPENGLES = 7;
    /**
     * bgfx renderer type opengl constant.
     */
    public static final int BGFX_RENDERER_TYPE_OPENGL = 8;
    /**
     * bgfx renderer type vulkan constant.
     */
    public static final int BGFX_RENDERER_TYPE_VULKAN = 9;
    /**
     * bgfx renderer type webgpu constant.
     */
    public static final int BGFX_RENDERER_TYPE_WEBGPU = 10;
    /**
     * bgfx renderer type count constant.
     */
    public static final int BGFX_RENDERER_TYPE_COUNT = 11;
    /**
     * bgfx access read constant.
     */
    public static final int BGFX_ACCESS_READ = 0;
    /**
     * bgfx access write constant.
     */
    public static final int BGFX_ACCESS_WRITE = 1;
    /**
     * bgfx access readwrite constant.
     */
    public static final int BGFX_ACCESS_READWRITE = 2;
    /**
     * bgfx access count constant.
     */
    public static final int BGFX_ACCESS_COUNT = 3;
    /**
     * bgfx attrib position constant.
     */
    public static final int BGFX_ATTRIB_POSITION = 0;
    /**
     * bgfx attrib normal constant.
     */
    public static final int BGFX_ATTRIB_NORMAL = 1;
    /**
     * bgfx attrib tangent constant.
     */
    public static final int BGFX_ATTRIB_TANGENT = 2;
    /**
     * bgfx attrib bitangent constant.
     */
    public static final int BGFX_ATTRIB_BITANGENT = 3;
    /**
     * bgfx attrib color0 constant.
     */
    public static final int BGFX_ATTRIB_COLOR0 = 4;
    /**
     * bgfx attrib color1 constant.
     */
    public static final int BGFX_ATTRIB_COLOR1 = 5;
    /**
     * bgfx attrib color2 constant.
     */
    public static final int BGFX_ATTRIB_COLOR2 = 6;
    /**
     * bgfx attrib color3 constant.
     */
    public static final int BGFX_ATTRIB_COLOR3 = 7;
    /**
     * bgfx attrib indices constant.
     */
    public static final int BGFX_ATTRIB_INDICES = 8;
    /**
     * bgfx attrib weight constant.
     */
    public static final int BGFX_ATTRIB_WEIGHT = 9;
    /**
     * bgfx attrib texcoord0 constant.
     */
    public static final int BGFX_ATTRIB_TEXCOORD0 = 10;
    /**
     * bgfx attrib texcoord1 constant.
     */
    public static final int BGFX_ATTRIB_TEXCOORD1 = 11;
    /**
     * bgfx attrib texcoord2 constant.
     */
    public static final int BGFX_ATTRIB_TEXCOORD2 = 12;
    /**
     * bgfx attrib texcoord3 constant.
     */
    public static final int BGFX_ATTRIB_TEXCOORD3 = 13;
    /**
     * bgfx attrib texcoord4 constant.
     */
    public static final int BGFX_ATTRIB_TEXCOORD4 = 14;
    /**
     * bgfx attrib texcoord5 constant.
     */
    public static final int BGFX_ATTRIB_TEXCOORD5 = 15;
    /**
     * bgfx attrib texcoord6 constant.
     */
    public static final int BGFX_ATTRIB_TEXCOORD6 = 16;
    /**
     * bgfx attrib texcoord7 constant.
     */
    public static final int BGFX_ATTRIB_TEXCOORD7 = 17;
    /**
     * bgfx attrib texcoord8 constant.
     */
    public static final int BGFX_ATTRIB_TEXCOORD8 = 18;
    /**
     * bgfx attrib texcoord9 constant.
     */
    public static final int BGFX_ATTRIB_TEXCOORD9 = 19;
    /**
     * bgfx attrib texcoord10 constant.
     */
    public static final int BGFX_ATTRIB_TEXCOORD10 = 20;
    /**
     * bgfx attrib texcoord11 constant.
     */
    public static final int BGFX_ATTRIB_TEXCOORD11 = 21;
    /**
     * bgfx attrib texcoord12 constant.
     */
    public static final int BGFX_ATTRIB_TEXCOORD12 = 22;
    /**
     * bgfx attrib texcoord13 constant.
     */
    public static final int BGFX_ATTRIB_TEXCOORD13 = 23;
    /**
     * bgfx attrib texcoord14 constant.
     */
    public static final int BGFX_ATTRIB_TEXCOORD14 = 24;
    /**
     * bgfx attrib texcoord15 constant.
     */
    public static final int BGFX_ATTRIB_TEXCOORD15 = 25;
    /**
     * bgfx attrib count constant.
     */
    public static final int BGFX_ATTRIB_COUNT = 26;
    /**
     * bgfx attrib type int8 constant.
     */
    public static final int BGFX_ATTRIB_TYPE_INT8 = 0;
    /**
     * bgfx attrib type uint8 constant.
     */
    public static final int BGFX_ATTRIB_TYPE_UINT8 = 1;
    /**
     * bgfx attrib type uint10 constant.
     */
    public static final int BGFX_ATTRIB_TYPE_UINT10 = 2;
    /**
     * bgfx attrib type int16 constant.
     */
    public static final int BGFX_ATTRIB_TYPE_INT16 = 3;
    /**
     * bgfx attrib type uint16 constant.
     */
    public static final int BGFX_ATTRIB_TYPE_UINT16 = 4;
    /**
     * bgfx attrib type half constant.
     */
    public static final int BGFX_ATTRIB_TYPE_HALF = 5;
    /**
     * bgfx attrib type float constant.
     */
    public static final int BGFX_ATTRIB_TYPE_FLOAT = 6;
    /**
     * bgfx attrib type int32 constant.
     */
    public static final int BGFX_ATTRIB_TYPE_INT32 = 7;
    /**
     * bgfx attrib type uint32 constant.
     */
    public static final int BGFX_ATTRIB_TYPE_UINT32 = 8;
    /**
     * bgfx attrib type count constant.
     */
    public static final int BGFX_ATTRIB_TYPE_COUNT = 9;
    /**
     * bgfx texture format bc1 constant.
     */
    public static final int BGFX_TEXTURE_FORMAT_BC1 = 0;
    /**
     * bgfx texture format bc2 constant.
     */
    public static final int BGFX_TEXTURE_FORMAT_BC2 = 1;
    /**
     * bgfx texture format bc3 constant.
     */
    public static final int BGFX_TEXTURE_FORMAT_BC3 = 2;
    /**
     * bgfx texture format bc4 constant.
     */
    public static final int BGFX_TEXTURE_FORMAT_BC4 = 3;
    /**
     * bgfx texture format bc4s constant.
     */
    public static final int BGFX_TEXTURE_FORMAT_BC4S = 4;
    /**
     * bgfx texture format bc5 constant.
     */
    public static final int BGFX_TEXTURE_FORMAT_BC5 = 5;
    /**
     * bgfx texture format bc5s constant.
     */
    public static final int BGFX_TEXTURE_FORMAT_BC5S = 6;
    /**
     * bgfx texture format bc6h constant.
     */
    public static final int BGFX_TEXTURE_FORMAT_BC6H = 7;
    /**
     * bgfx texture format bc6hu constant.
     */
    public static final int BGFX_TEXTURE_FORMAT_BC6HU = 8;
    /**
     * bgfx texture format bc7 constant.
     */
    public static final int BGFX_TEXTURE_FORMAT_BC7 = 9;
    /**
     * bgfx texture format etc1 constant.
     */
    public static final int BGFX_TEXTURE_FORMAT_ETC1 = 10;
    /**
     * bgfx texture format etc2 constant.
     */
    public static final int BGFX_TEXTURE_FORMAT_ETC2 = 11;
    /**
     * bgfx texture format etc2a constant.
     */
    public static final int BGFX_TEXTURE_FORMAT_ETC2A = 12;
    /**
     * bgfx texture format etc2a1 constant.
     */
    public static final int BGFX_TEXTURE_FORMAT_ETC2A1 = 13;
    /**
     * bgfx texture format eacr11 constant.
     */
    public static final int BGFX_TEXTURE_FORMAT_EACR11 = 14;
    /**
     * bgfx texture format eacr11s constant.
     */
    public static final int BGFX_TEXTURE_FORMAT_EACR11S = 15;
    /**
     * bgfx texture format eacrg11 constant.
     */
    public static final int BGFX_TEXTURE_FORMAT_EACRG11 = 16;
    /**
     * bgfx texture format eacrg11s constant.
     */
    public static final int BGFX_TEXTURE_FORMAT_EACRG11S = 17;
    /**
     * bgfx texture format ptc12 constant.
     */
    public static final int BGFX_TEXTURE_FORMAT_PTC12 = 18;
    /**
     * bgfx texture format ptc14 constant.
     */
    public static final int BGFX_TEXTURE_FORMAT_PTC14 = 19;
    /**
     * bgfx texture format ptc12a constant.
     */
    public static final int BGFX_TEXTURE_FORMAT_PTC12A = 20;
    /**
     * bgfx texture format ptc14a constant.
     */
    public static final int BGFX_TEXTURE_FORMAT_PTC14A = 21;
    /**
     * bgfx texture format ptc22 constant.
     */
    public static final int BGFX_TEXTURE_FORMAT_PTC22 = 22;
    /**
     * bgfx texture format ptc24 constant.
     */
    public static final int BGFX_TEXTURE_FORMAT_PTC24 = 23;
    /**
     * bgfx texture format atc constant.
     */
    public static final int BGFX_TEXTURE_FORMAT_ATC = 24;
    /**
     * bgfx texture format atce constant.
     */
    public static final int BGFX_TEXTURE_FORMAT_ATCE = 25;
    /**
     * bgfx texture format atci constant.
     */
    public static final int BGFX_TEXTURE_FORMAT_ATCI = 26;
    /**
     * bgfx texture format astc4x4 constant.
     */
    public static final int BGFX_TEXTURE_FORMAT_ASTC4x4 = 27;
    /**
     * bgfx texture format astc5x4 constant.
     */
    public static final int BGFX_TEXTURE_FORMAT_ASTC5X4 = 28;
    /**
     * bgfx texture format astc5x5 constant.
     */
    public static final int BGFX_TEXTURE_FORMAT_ASTC5x5 = 29;
    /**
     * bgfx texture format astc6x5 constant.
     */
    public static final int BGFX_TEXTURE_FORMAT_ASTC6X5 = 30;
    /**
     * bgfx texture format astc6x6 constant.
     */
    public static final int BGFX_TEXTURE_FORMAT_ASTC6x6 = 31;
    /**
     * bgfx texture format astc8x5 constant.
     */
    public static final int BGFX_TEXTURE_FORMAT_ASTC8x5 = 32;
    /**
     * bgfx texture format astc8x6 constant.
     */
    public static final int BGFX_TEXTURE_FORMAT_ASTC8x6 = 33;
    /**
     * bgfx texture format astc8x8 constant.
     */
    public static final int BGFX_TEXTURE_FORMAT_ASTC8X8 = 34;
    /**
     * bgfx texture format astc10x5 constant.
     */
    public static final int BGFX_TEXTURE_FORMAT_ASTC10x5 = 35;
    /**
     * bgfx texture format astc10x6 constant.
     */
    public static final int BGFX_TEXTURE_FORMAT_ASTC10X6 = 36;
    /**
     * bgfx texture format astc10x8 constant.
     */
    public static final int BGFX_TEXTURE_FORMAT_ASTC10X8 = 37;
    /**
     * bgfx texture format astc10x10 constant.
     */
    public static final int BGFX_TEXTURE_FORMAT_ASTC10X10 = 38;
    /**
     * bgfx texture format astc12x10 constant.
     */
    public static final int BGFX_TEXTURE_FORMAT_ASTC12X10 = 39;
    /**
     * bgfx texture format astc12x12 constant.
     */
    public static final int BGFX_TEXTURE_FORMAT_ASTC12X12 = 40;
    /**
     * bgfx texture format unknown constant.
     */
    public static final int BGFX_TEXTURE_FORMAT_UNKNOWN = 41;
    /**
     * bgfx texture format r1 constant.
     */
    public static final int BGFX_TEXTURE_FORMAT_R1 = 42;
    /**
     * bgfx texture format a8 constant.
     */
    public static final int BGFX_TEXTURE_FORMAT_A8 = 43;
    /**
     * bgfx texture format r8 constant.
     */
    public static final int BGFX_TEXTURE_FORMAT_R8 = 44;
    /**
     * bgfx texture format r8i constant.
     */
    public static final int BGFX_TEXTURE_FORMAT_R8I = 45;
    /**
     * bgfx texture format r8u constant.
     */
    public static final int BGFX_TEXTURE_FORMAT_R8U = 46;
    /**
     * bgfx texture format r8s constant.
     */
    public static final int BGFX_TEXTURE_FORMAT_R8S = 47;
    /**
     * bgfx texture format r16 constant.
     */
    public static final int BGFX_TEXTURE_FORMAT_R16 = 48;
    /**
     * bgfx texture format r16i constant.
     */
    public static final int BGFX_TEXTURE_FORMAT_R16I = 49;
    /**
     * bgfx texture format r16u constant.
     */
    public static final int BGFX_TEXTURE_FORMAT_R16U = 50;
    /**
     * bgfx texture format r16f constant.
     */
    public static final int BGFX_TEXTURE_FORMAT_R16F = 51;
    /**
     * bgfx texture format r16s constant.
     */
    public static final int BGFX_TEXTURE_FORMAT_R16S = 52;
    /**
     * bgfx texture format r32i constant.
     */
    public static final int BGFX_TEXTURE_FORMAT_R32I = 53;
    /**
     * bgfx texture format r32u constant.
     */
    public static final int BGFX_TEXTURE_FORMAT_R32U = 54;
    /**
     * bgfx texture format r32f constant.
     */
    public static final int BGFX_TEXTURE_FORMAT_R32F = 55;
    /**
     * bgfx texture format rg8 constant.
     */
    public static final int BGFX_TEXTURE_FORMAT_RG8 = 56;
    /**
     * bgfx texture format rg8i constant.
     */
    public static final int BGFX_TEXTURE_FORMAT_RG8I = 57;
    /**
     * bgfx texture format rg8u constant.
     */
    public static final int BGFX_TEXTURE_FORMAT_RG8U = 58;
    /**
     * bgfx texture format rg8s constant.
     */
    public static final int BGFX_TEXTURE_FORMAT_RG8S = 59;
    /**
     * bgfx texture format rg16 constant.
     */
    public static final int BGFX_TEXTURE_FORMAT_RG16 = 60;
    /**
     * bgfx texture format rg16i constant.
     */
    public static final int BGFX_TEXTURE_FORMAT_RG16I = 61;
    /**
     * bgfx texture format rg16u constant.
     */
    public static final int BGFX_TEXTURE_FORMAT_RG16U = 62;
    /**
     * bgfx texture format rg16f constant.
     */
    public static final int BGFX_TEXTURE_FORMAT_RG16F = 63;
    /**
     * bgfx texture format rg16s constant.
     */
    public static final int BGFX_TEXTURE_FORMAT_RG16S = 64;
    /**
     * bgfx texture format rg32i constant.
     */
    public static final int BGFX_TEXTURE_FORMAT_RG32I = 65;
    /**
     * bgfx texture format rg32u constant.
     */
    public static final int BGFX_TEXTURE_FORMAT_RG32U = 66;
    /**
     * bgfx texture format rg32f constant.
     */
    public static final int BGFX_TEXTURE_FORMAT_RG32F = 67;
    /**
     * bgfx texture format rgb8 constant.
     */
    public static final int BGFX_TEXTURE_FORMAT_RGB8 = 68;
    /**
     * bgfx texture format rgb8i constant.
     */
    public static final int BGFX_TEXTURE_FORMAT_RGB8I = 69;
    /**
     * bgfx texture format rgb8u constant.
     */
    public static final int BGFX_TEXTURE_FORMAT_RGB8U = 70;
    /**
     * bgfx texture format rgb8s constant.
     */
    public static final int BGFX_TEXTURE_FORMAT_RGB8S = 71;
    /**
     * bgfx texture format rgb9e5f constant.
     */
    public static final int BGFX_TEXTURE_FORMAT_RGB9E5F = 72;
    /**
     * bgfx texture format bgra8 constant.
     */
    public static final int BGFX_TEXTURE_FORMAT_BGRA8 = 73;
    /**
     * bgfx texture format rgba8 constant.
     */
    public static final int BGFX_TEXTURE_FORMAT_RGBA8 = 74;
    /**
     * bgfx texture format rgba8i constant.
     */
    public static final int BGFX_TEXTURE_FORMAT_RGBA8I = 75;
    /**
     * bgfx texture format rgba8u constant.
     */
    public static final int BGFX_TEXTURE_FORMAT_RGBA8U = 76;
    /**
     * bgfx texture format rgba8s constant.
     */
    public static final int BGFX_TEXTURE_FORMAT_RGBA8S = 77;
    /**
     * bgfx texture format rgba16 constant.
     */
    public static final int BGFX_TEXTURE_FORMAT_RGBA16 = 78;
    /**
     * bgfx texture format rgba16i constant.
     */
    public static final int BGFX_TEXTURE_FORMAT_RGBA16I = 79;
    /**
     * bgfx texture format rgba16u constant.
     */
    public static final int BGFX_TEXTURE_FORMAT_RGBA16U = 80;
    /**
     * bgfx texture format rgba16f constant.
     */
    public static final int BGFX_TEXTURE_FORMAT_RGBA16F = 81;
    /**
     * bgfx texture format rgba16s constant.
     */
    public static final int BGFX_TEXTURE_FORMAT_RGBA16S = 82;
    /**
     * bgfx texture format rgba32i constant.
     */
    public static final int BGFX_TEXTURE_FORMAT_RGBA32I = 83;
    /**
     * bgfx texture format rgba32u constant.
     */
    public static final int BGFX_TEXTURE_FORMAT_RGBA32U = 84;
    /**
     * bgfx texture format rgba32f constant.
     */
    public static final int BGFX_TEXTURE_FORMAT_RGBA32F = 85;
    /**
     * bgfx texture format b5g6r5 constant.
     */
    public static final int BGFX_TEXTURE_FORMAT_B5G6R5 = 86;
    /**
     * bgfx texture format r5g6b5 constant.
     */
    public static final int BGFX_TEXTURE_FORMAT_R5G6B5 = 87;
    /**
     * bgfx texture format bgra4 constant.
     */
    public static final int BGFX_TEXTURE_FORMAT_BGRA4 = 88;
    /**
     * bgfx texture format rgba4 constant.
     */
    public static final int BGFX_TEXTURE_FORMAT_RGBA4 = 89;
    /**
     * bgfx texture format bgr5a1 constant.
     */
    public static final int BGFX_TEXTURE_FORMAT_BGR5A1 = 90;
    /**
     * bgfx texture format rgb5a1 constant.
     */
    public static final int BGFX_TEXTURE_FORMAT_RGB5A1 = 91;
    /**
     * bgfx texture format rgb10a2 constant.
     */
    public static final int BGFX_TEXTURE_FORMAT_RGB10A2 = 92;
    /**
     * bgfx texture format rgb10a2u constant.
     */
    public static final int BGFX_TEXTURE_FORMAT_RGB10A2U = 93;
    /**
     * bgfx texture format rg11b10f constant.
     */
    public static final int BGFX_TEXTURE_FORMAT_RG11B10F = 94;
    /**
     * bgfx texture format unknown depth constant.
     */
    public static final int BGFX_TEXTURE_FORMAT_UNKNOWN_DEPTH = 95;
    /**
     * bgfx texture format d16 constant.
     */
    public static final int BGFX_TEXTURE_FORMAT_D16 = 96;
    /**
     * bgfx texture format d24 constant.
     */
    public static final int BGFX_TEXTURE_FORMAT_D24 = 97;
    /**
     * bgfx texture format d24s8 constant.
     */
    public static final int BGFX_TEXTURE_FORMAT_D24S8 = 98;
    /**
     * bgfx texture format d32 constant.
     */
    public static final int BGFX_TEXTURE_FORMAT_D32 = 99;
    /**
     * bgfx texture format d16f constant.
     */
    public static final int BGFX_TEXTURE_FORMAT_D16F = 100;
    /**
     * bgfx texture format d24f constant.
     */
    public static final int BGFX_TEXTURE_FORMAT_D24F = 101;
    /**
     * bgfx texture format d32f constant.
     */
    public static final int BGFX_TEXTURE_FORMAT_D32F = 102;
    /**
     * bgfx texture format d32fs8 constant.
     */
    public static final int BGFX_TEXTURE_FORMAT_D32FS8 = 103;
    /**
     * bgfx texture format d0s8 constant.
     */
    public static final int BGFX_TEXTURE_FORMAT_D0S8 = 104;
    /**
     * bgfx texture format count constant.
     */
    public static final int BGFX_TEXTURE_FORMAT_COUNT = 105;
    /**
     * bgfx uniform type sampler constant.
     */
    public static final int BGFX_UNIFORM_TYPE_SAMPLER = 0;
    /**
     * bgfx uniform type end constant.
     */
    public static final int BGFX_UNIFORM_TYPE_END = 1;
    /**
     * bgfx uniform type vec4 constant.
     */
    public static final int BGFX_UNIFORM_TYPE_VEC4 = 2;
    /**
     * bgfx uniform type mat3 constant.
     */
    public static final int BGFX_UNIFORM_TYPE_MAT3 = 3;
    /**
     * bgfx uniform type mat4 constant.
     */
    public static final int BGFX_UNIFORM_TYPE_MAT4 = 4;
    /**
     * bgfx uniform type count constant.
     */
    public static final int BGFX_UNIFORM_TYPE_COUNT = 5;
    /**
     * bgfx uniform freq draw constant.
     */
    public static final int BGFX_UNIFORM_FREQ_DRAW = 0;
    /**
     * bgfx uniform freq view constant.
     */
    public static final int BGFX_UNIFORM_FREQ_VIEW = 1;
    /**
     * bgfx uniform freq frame constant.
     */
    public static final int BGFX_UNIFORM_FREQ_FRAME = 2;
    /**
     * bgfx uniform freq count constant.
     */
    public static final int BGFX_UNIFORM_FREQ_COUNT = 3;
    /**
     * bgfx backbuffer ratio equal constant.
     */
    public static final int BGFX_BACKBUFFER_RATIO_EQUAL = 0;
    /**
     * bgfx backbuffer ratio half constant.
     */
    public static final int BGFX_BACKBUFFER_RATIO_HALF = 1;
    /**
     * bgfx backbuffer ratio quarter constant.
     */
    public static final int BGFX_BACKBUFFER_RATIO_QUARTER = 2;
    /**
     * bgfx backbuffer ratio eighth constant.
     */
    public static final int BGFX_BACKBUFFER_RATIO_EIGHTH = 3;
    /**
     * bgfx backbuffer ratio sixteenth constant.
     */
    public static final int BGFX_BACKBUFFER_RATIO_SIXTEENTH = 4;
    /**
     * bgfx backbuffer ratio double constant.
     */
    public static final int BGFX_BACKBUFFER_RATIO_DOUBLE = 5;
    /**
     * bgfx backbuffer ratio count constant.
     */
    public static final int BGFX_BACKBUFFER_RATIO_COUNT = 6;
    /**
     * bgfx occlusion query result invisible constant.
     */
    public static final int BGFX_OCCLUSION_QUERY_RESULT_INVISIBLE = 0;
    /**
     * bgfx occlusion query result visible constant.
     */
    public static final int BGFX_OCCLUSION_QUERY_RESULT_VISIBLE = 1;
    /**
     * bgfx occlusion query result noresult constant.
     */
    public static final int BGFX_OCCLUSION_QUERY_RESULT_NORESULT = 2;
    /**
     * bgfx occlusion query result count constant.
     */
    public static final int BGFX_OCCLUSION_QUERY_RESULT_COUNT = 3;
    /**
     * bgfx video codec h264 constant.
     */
    public static final int BGFX_VIDEO_CODEC_H264 = 0;
    /**
     * bgfx video codec h265 constant.
     */
    public static final int BGFX_VIDEO_CODEC_H265 = 1;
    /**
     * bgfx video codec av1 constant.
     */
    public static final int BGFX_VIDEO_CODEC_AV1 = 2;
    /**
     * bgfx video codec count constant.
     */
    public static final int BGFX_VIDEO_CODEC_COUNT = 3;
    /**
     * bgfx topology tri list constant.
     */
    public static final int BGFX_TOPOLOGY_TRI_LIST = 0;
    /**
     * bgfx topology tri strip constant.
     */
    public static final int BGFX_TOPOLOGY_TRI_STRIP = 1;
    /**
     * bgfx topology line list constant.
     */
    public static final int BGFX_TOPOLOGY_LINE_LIST = 2;
    /**
     * bgfx topology line strip constant.
     */
    public static final int BGFX_TOPOLOGY_LINE_STRIP = 3;
    /**
     * bgfx topology point list constant.
     */
    public static final int BGFX_TOPOLOGY_POINT_LIST = 4;
    /**
     * bgfx topology count constant.
     */
    public static final int BGFX_TOPOLOGY_COUNT = 5;
    /**
     * bgfx topology convert tri list flip winding constant.
     */
    public static final int BGFX_TOPOLOGY_CONVERT_TRI_LIST_FLIP_WINDING = 0;
    /**
     * bgfx topology convert tri strip flip winding constant.
     */
    public static final int BGFX_TOPOLOGY_CONVERT_TRI_STRIP_FLIP_WINDING = 1;
    /**
     * bgfx topology convert tri list to line list constant.
     */
    public static final int BGFX_TOPOLOGY_CONVERT_TRI_LIST_TO_LINE_LIST = 2;
    /**
     * bgfx topology convert tri strip to tri list constant.
     */
    public static final int BGFX_TOPOLOGY_CONVERT_TRI_STRIP_TO_TRI_LIST = 3;
    /**
     * bgfx topology convert line strip to line list constant.
     */
    public static final int BGFX_TOPOLOGY_CONVERT_LINE_STRIP_TO_LINE_LIST = 4;
    /**
     * bgfx topology convert count constant.
     */
    public static final int BGFX_TOPOLOGY_CONVERT_COUNT = 5;
    /**
     * bgfx topology sort direction front to back min constant.
     */
    public static final int BGFX_TOPOLOGY_SORT_DIRECTION_FRONT_TO_BACK_MIN = 0;
    /**
     * bgfx topology sort direction front to back avg constant.
     */
    public static final int BGFX_TOPOLOGY_SORT_DIRECTION_FRONT_TO_BACK_AVG = 1;
    /**
     * bgfx topology sort direction front to back max constant.
     */
    public static final int BGFX_TOPOLOGY_SORT_DIRECTION_FRONT_TO_BACK_MAX = 2;
    /**
     * bgfx topology sort direction back to front min constant.
     */
    public static final int BGFX_TOPOLOGY_SORT_DIRECTION_BACK_TO_FRONT_MIN = 3;
    /**
     * bgfx topology sort direction back to front avg constant.
     */
    public static final int BGFX_TOPOLOGY_SORT_DIRECTION_BACK_TO_FRONT_AVG = 4;
    /**
     * bgfx topology sort direction back to front max constant.
     */
    public static final int BGFX_TOPOLOGY_SORT_DIRECTION_BACK_TO_FRONT_MAX = 5;
    /**
     * bgfx topology sort distance front to back min constant.
     */
    public static final int BGFX_TOPOLOGY_SORT_DISTANCE_FRONT_TO_BACK_MIN = 6;
    /**
     * bgfx topology sort distance front to back avg constant.
     */
    public static final int BGFX_TOPOLOGY_SORT_DISTANCE_FRONT_TO_BACK_AVG = 7;
    /**
     * bgfx topology sort distance front to back max constant.
     */
    public static final int BGFX_TOPOLOGY_SORT_DISTANCE_FRONT_TO_BACK_MAX = 8;
    /**
     * bgfx topology sort distance back to front min constant.
     */
    public static final int BGFX_TOPOLOGY_SORT_DISTANCE_BACK_TO_FRONT_MIN = 9;
    /**
     * bgfx topology sort distance back to front avg constant.
     */
    public static final int BGFX_TOPOLOGY_SORT_DISTANCE_BACK_TO_FRONT_AVG = 10;
    /**
     * bgfx topology sort distance back to front max constant.
     */
    public static final int BGFX_TOPOLOGY_SORT_DISTANCE_BACK_TO_FRONT_MAX = 11;
    /**
     * bgfx topology sort count constant.
     */
    public static final int BGFX_TOPOLOGY_SORT_COUNT = 12;
    /**
     * bgfx view mode default constant.
     */
    public static final int BGFX_VIEW_MODE_DEFAULT = 0;
    /**
     * bgfx view mode sequential constant.
     */
    public static final int BGFX_VIEW_MODE_SEQUENTIAL = 1;
    /**
     * bgfx view mode depth ascending constant.
     */
    public static final int BGFX_VIEW_MODE_DEPTH_ASCENDING = 2;
    /**
     * bgfx view mode depth descending constant.
     */
    public static final int BGFX_VIEW_MODE_DEPTH_DESCENDING = 3;
    /**
     * bgfx view mode count constant.
     */
    public static final int BGFX_VIEW_MODE_COUNT = 4;
    /**
     * bgfx shading rate rate 1x 1 constant.
     */
    public static final int BGFX_SHADING_RATE_RATE_1X_1 = 0;
    /**
     * bgfx shading rate rate 1x 2 constant.
     */
    public static final int BGFX_SHADING_RATE_RATE_1X_2 = 1;
    /**
     * bgfx shading rate rate 2x 1 constant.
     */
    public static final int BGFX_SHADING_RATE_RATE_2X_1 = 2;
    /**
     * bgfx shading rate rate 2x 2 constant.
     */
    public static final int BGFX_SHADING_RATE_RATE_2X_2 = 3;
    /**
     * bgfx shading rate rate 2x 4 constant.
     */
    public static final int BGFX_SHADING_RATE_RATE_2X_4 = 4;
    /**
     * bgfx shading rate rate 4x 2 constant.
     */
    public static final int BGFX_SHADING_RATE_RATE_4X_2 = 5;
    /**
     * bgfx shading rate rate 4x 4 constant.
     */
    public static final int BGFX_SHADING_RATE_RATE_4X_4 = 6;
    /**
     * bgfx shading rate count constant.
     */
    public static final int BGFX_SHADING_RATE_COUNT = 7;
    /**
     * bgfx native window handle type default constant.
     */
    public static final int BGFX_NATIVE_WINDOW_HANDLE_TYPE_DEFAULT = 0;
    /**
     * bgfx native window handle type wayland constant.
     */
    public static final int BGFX_NATIVE_WINDOW_HANDLE_TYPE_WAYLAND = 1;
    /**
     * bgfx native window handle type count constant.
     */
    public static final int BGFX_NATIVE_WINDOW_HANDLE_TYPE_COUNT = 2;
    /**
     * bgfx state blend add constant.
     */
    public static final long BGFX_STATE_BLEND_ADD = BGFX_STATE_BLEND_FUNC(BGFX_STATE_BLEND_ONE, BGFX_STATE_BLEND_ONE);
    /**
     * bgfx state blend alpha constant.
     */
    public static final long BGFX_STATE_BLEND_ALPHA = BGFX_STATE_BLEND_FUNC(BGFX_STATE_BLEND_SRC_ALPHA, BGFX_STATE_BLEND_INV_SRC_ALPHA);
    /**
     * bgfx state blend darken constant.
     */
    public static final long BGFX_STATE_BLEND_DARKEN = BGFX_STATE_BLEND_FUNC(BGFX_STATE_BLEND_ONE, BGFX_STATE_BLEND_ONE) | BGFX_STATE_BLEND_EQUATION(BGFX_STATE_BLEND_EQUATION_MIN);
    /**
     * bgfx state blend lighten constant.
     */
    public static final long BGFX_STATE_BLEND_LIGHTEN = BGFX_STATE_BLEND_FUNC(BGFX_STATE_BLEND_ONE, BGFX_STATE_BLEND_ONE) | BGFX_STATE_BLEND_EQUATION(BGFX_STATE_BLEND_EQUATION_MAX);
    /**
     * bgfx state blend multiply constant.
     */
    public static final long BGFX_STATE_BLEND_MULTIPLY = BGFX_STATE_BLEND_FUNC(BGFX_STATE_BLEND_DST_COLOR, BGFX_STATE_BLEND_ZERO);
    /**
     * bgfx state blend normal constant.
     */
    public static final long BGFX_STATE_BLEND_NORMAL = BGFX_STATE_BLEND_FUNC(BGFX_STATE_BLEND_ONE, BGFX_STATE_BLEND_INV_SRC_ALPHA);
    /**
     * bgfx state blend screen constant.
     */
    public static final long BGFX_STATE_BLEND_SCREEN = BGFX_STATE_BLEND_FUNC(BGFX_STATE_BLEND_ONE, BGFX_STATE_BLEND_INV_SRC_COLOR);
    /**
     * bgfx state blend linear burn constant.
     */
    public static final long BGFX_STATE_BLEND_LINEAR_BURN = BGFX_STATE_BLEND_FUNC(BGFX_STATE_BLEND_DST_COLOR, BGFX_STATE_BLEND_INV_DST_COLOR) | BGFX_STATE_BLEND_EQUATION(BGFX_STATE_BLEND_EQUATION_SUB);

    /**
     * Prevents creating static graphics bindings.
     */
    private BGFX() {
    }

    /**
     * Combines independent RGB and alpha blend factors.
     *
     * @param srcRGB native srcRGB argument
     * @param dstRGB native dstRGB argument
     * @param srcA native srcA argument
     * @param dstA native dstA argument
     * @return native operation result
     */
    public static long BGFX_STATE_BLEND_FUNC_SEPARATE(long srcRGB, long dstRGB, long srcA, long dstA) {
        /*
         * Marshal arguments synchronously; bgfx owns any queued upload copies.
         */
        return (srcRGB | (dstRGB << 4)) | ((srcA | (dstA << 4)) << 8);
    }

    /**
     * Combines matching RGB and alpha blend factors.
     *
     * @param src native src argument
     * @param dst native dst argument
     * @return native operation result
     */
    public static long BGFX_STATE_BLEND_FUNC(long src, long dst) {
        /*
         * Marshal arguments synchronously; bgfx owns any queued upload copies.
         */
        return BGFX_STATE_BLEND_FUNC_SEPARATE(src, dst, src, dst);
    }

    /**
     * Combines independent RGB and alpha blend equations.
     *
     * @param rgb native rgb argument
     * @param alpha native alpha argument
     * @return native operation result
     */
    public static long BGFX_STATE_BLEND_EQUATION_SEPARATE(long rgb, long alpha) {
        /*
         * Marshal arguments synchronously; bgfx owns any queued upload copies.
         */
        return rgb | (alpha << 3);
    }

    /**
     * Combines matching RGB and alpha blend equations.
     *
     * @param equation native equation argument
     * @return native operation result
     */
    public static long BGFX_STATE_BLEND_EQUATION(long equation) {
        /*
         * Marshal arguments synchronously; bgfx owns any queued upload copies.
         */
        return BGFX_STATE_BLEND_EQUATION_SEPARATE(equation, equation);
    }

    /**
     * Encodes the alpha comparison threshold.
     *
     * @param value native value argument
     * @return native operation result
     */
    public static long BGFX_STATE_ALPHA_REF(long value) {
        /*
         * Marshal arguments synchronously; bgfx owns any queued upload copies.
         */
        return (value << BGFX_STATE_ALPHA_REF_SHIFT) & BGFX_STATE_ALPHA_REF_MASK;
    }

    /**
     * Encodes the point raster size.
     *
     * @param value native value argument
     * @return native operation result
     */
    public static long BGFX_STATE_POINT_SIZE(long value) {
        /*
         * Marshal arguments synchronously; bgfx owns any queued upload copies.
         */
        return (value << BGFX_STATE_POINT_SIZE_SHIFT) & BGFX_STATE_POINT_SIZE_MASK;
    }

    /**
     * Performs bgfx frame.
     *
     * @param flags native flags argument
     * @return native operation result
     */
    public static int bgfx_frame(int flags) {
        /*
         * Marshal arguments synchronously; bgfx owns any queued upload copies.
         */
        BrowserNative.word(0, flags);
        return BrowserNative.call(FRAME);
    }

    /**
     * Performs bgfx shutdown.
     */
    public static void bgfx_shutdown() {
        /*
         * Marshal arguments synchronously; bgfx owns any queued upload copies.
         */

        BrowserNative.call(SHUTDOWN);
    }

    /**
     * Performs bgfx reset.
     *
     * @param width native width argument
     * @param height native height argument
     * @param flags native flags argument
     * @param format native format argument
     */
    public static void bgfx_reset(int width, int height, int flags, int format) {
        /*
         * Marshal arguments synchronously; bgfx owns any queued upload copies.
         */
        BrowserNative.word(0, width);
        BrowserNative.word(1, height);
        BrowserNative.word(2, flags);
        BrowserNative.call(RESET);
    }

    /**
     * Returns the active browser device capabilities.
     * @return native operation result
     */
    public static BGFXCaps bgfx_get_caps() {
        /*
         * Marshal arguments synchronously; bgfx owns any queued upload copies.
         */
        return BGFXCaps.current();
    }

    /**
     * Returns the browser renderer identity.
     *
     * @param type native type argument
     * @return native operation result
     */
    public static String bgfx_get_renderer_name(int type) {
        /*
         * Marshal arguments synchronously; bgfx owns any queued upload copies.
         */
        if (type != BGFX_RENDERER_TYPE_OPENGLES) throw new IllegalArgumentException("The browser renderer is WebGL 2");
        return "WebGL 2 (bgfx)";
    }

    /**
     * Allocates writable CPU staging for a shader or texture upload.
     *
     * @param size native size argument
     * @return native operation result
     */
    public static BGFXMemory bgfx_alloc(int size) {
        /*
         * Marshal arguments synchronously; bgfx owns any queued upload copies.
         */
        return new BGFXMemory(ByteBuffer.allocateDirect(size));
    }

    /**
     * Copies the current byte range into bgfx-owned upload storage.
     *
     * @param bytes native bytes argument
     * @return native operation result
     */
    public static BGFXMemory bgfx_copy(ByteBuffer bytes) {
        /*
         * Marshal arguments synchronously; bgfx owns any queued upload copies.
         */
        return new BGFXMemory(BrowserNative.call(COPY, bytes));
    }

    /**
     * Copies floating-point geometry into native upload storage.
     *
     * @param values native values argument
     * @return native operation result
     */
    public static BGFXMemory bgfx_copy(FloatBuffer values) {
        /*
         * Marshal arguments synchronously; bgfx owns any queued upload copies.
         */
        return new BGFXMemory(BrowserNative.callFloats(COPY, values));
    }

    /**
     * Performs bgfx vertex create.
     *
     * @param data native data argument
     * @param layout native layout argument
     * @param flags native flags argument
     * @return native operation result
     */
    public static short bgfx_create_vertex_buffer(BGFXMemory data, BGFXVertexLayout layout, int flags) {
        /*
         * Marshal arguments synchronously; bgfx owns any queued upload copies.
         */
        BrowserNative.word(0, data.pointer());
        BrowserNative.word(1, layout.pointer());
        BrowserNative.word(2, flags);
        return (short) BrowserNative.call(VERTEX_CREATE);
    }

    /**
     * Performs bgfx dynamic vertex create.
     *
     * @param count native count argument
     * @param layout native layout argument
     * @param flags native flags argument
     * @return native operation result
     */
    public static short bgfx_create_dynamic_vertex_buffer(int count, BGFXVertexLayout layout, int flags) {
        /*
         * Marshal arguments synchronously; bgfx owns any queued upload copies.
         */
        BrowserNative.word(0, count);
        BrowserNative.word(1, layout.pointer());
        BrowserNative.word(2, flags);
        return (short) BrowserNative.call(DYNAMIC_VERTEX_CREATE);
    }

    /**
     * Performs bgfx dynamic vertex update.
     *
     * @param handle native handle argument
     * @param offset native offset argument
     * @param data native data argument
     */
    public static void bgfx_update_dynamic_vertex_buffer(short handle, int offset, BGFXMemory data) {
        /*
         * Marshal arguments synchronously; bgfx owns any queued upload copies.
         */
        BrowserNative.word(0, handle);
        BrowserNative.word(1, offset);
        BrowserNative.word(2, data.pointer());
        BrowserNative.call(DYNAMIC_VERTEX_UPDATE);
    }

    /**
     * Performs bgfx vertex destroy.
     *
     * @param handle native handle argument
     */
    public static void bgfx_destroy_vertex_buffer(short handle) {
        /*
         * Marshal arguments synchronously; bgfx owns any queued upload copies.
         */
        BrowserNative.word(0, handle);
        BrowserNative.call(VERTEX_DESTROY);
    }

    /**
     * Performs bgfx dynamic vertex destroy.
     *
     * @param handle native handle argument
     */
    public static void bgfx_destroy_dynamic_vertex_buffer(short handle) {
        /*
         * Marshal arguments synchronously; bgfx owns any queued upload copies.
         */
        BrowserNative.word(0, handle);
        BrowserNative.call(DYNAMIC_VERTEX_DESTROY);
    }

    /**
     * Performs bgfx shader destroy.
     *
     * @param handle native handle argument
     */
    public static void bgfx_destroy_shader(short handle) {
        /*
         * Marshal arguments synchronously; bgfx owns any queued upload copies.
         */
        BrowserNative.word(0, handle);
        BrowserNative.call(SHADER_DESTROY);
    }

    /**
     * Performs bgfx program destroy.
     *
     * @param handle native handle argument
     */
    public static void bgfx_destroy_program(short handle) {
        /*
         * Marshal arguments synchronously; bgfx owns any queued upload copies.
         */
        BrowserNative.word(0, handle);
        BrowserNative.call(PROGRAM_DESTROY);
    }

    /**
     * Performs bgfx uniform destroy.
     *
     * @param handle native handle argument
     */
    public static void bgfx_destroy_uniform(short handle) {
        /*
         * Marshal arguments synchronously; bgfx owns any queued upload copies.
         */
        BrowserNative.word(0, handle);
        BrowserNative.call(UNIFORM_DESTROY);
    }

    /**
     * Performs bgfx texture destroy.
     *
     * @param handle native handle argument
     */
    public static void bgfx_destroy_texture(short handle) {
        /*
         * Marshal arguments synchronously; bgfx owns any queued upload copies.
         */
        BrowserNative.word(0, handle);
        BrowserNative.call(TEXTURE_DESTROY);
    }

    /**
     * Performs bgfx framebuffer destroy.
     *
     * @param handle native handle argument
     */
    public static void bgfx_destroy_frame_buffer(short handle) {
        /*
         * Marshal arguments synchronously; bgfx owns any queued upload copies.
         */
        BrowserNative.word(0, handle);
        BrowserNative.call(FRAMEBUFFER_DESTROY);
    }

    /**
     * Performs bgfx shader create.
     *
     * @param data native data argument
     * @return native operation result
     */
    public static short bgfx_create_shader(BGFXMemory data) {
        /*
         * Marshal arguments synchronously; bgfx owns any queued upload copies.
         */
        BrowserNative.word(0, data.pointer());
        return (short) BrowserNative.call(SHADER_CREATE);
    }

    /**
     * Performs bgfx program create.
     *
     * @param vertex native vertex argument
     * @param fragment native fragment argument
     * @param destroyShaders native destroyShaders argument
     * @return native operation result
     */
    public static short bgfx_create_program(short vertex, short fragment, boolean destroyShaders) {
        /*
         * Marshal arguments synchronously; bgfx owns any queued upload copies.
         */
        BrowserNative.word(0, vertex);
        BrowserNative.word(1, fragment);
        BrowserNative.word(2, destroyShaders ? 1 : 0);
        return (short) BrowserNative.call(PROGRAM_CREATE);
    }

    /**
     * Performs bgfx uniform create.
     *
     * @param name native name argument
     * @param type native type argument
     * @param count native count argument
     * @return native operation result
     */
    public static short bgfx_create_uniform(CharSequence name, int type, int count) {
        /*
         * Marshal arguments synchronously; bgfx owns any queued upload copies.
         */
        BrowserNative.word(0, type);
        BrowserNative.word(1, count);
        ByteBuffer bytes = ByteBuffer.wrap((name.toString() + "\0").getBytes(StandardCharsets.UTF_8));
        return (short) BrowserNative.call(UNIFORM_CREATE, bytes);
    }

    /**
     * Counts reflected uniforms without allocating destination storage.
     *
     * @param shader native shader argument
     * @param unused native unused argument
     * @param maximum native maximum argument
     * @return native operation result
     */
    public static short nbgfx_get_shader_uniforms(short shader, long unused, short maximum) {
        /*
         * Marshal arguments synchronously; bgfx owns any queued upload copies.
         */
        BrowserNative.word(0, shader);
        return (short) BrowserNative.call(SHADER_UNIFORMS);
    }

    /**
     * Copies reflected shader uniform handles into caller storage.
     *
     * @param shader native shader argument
     * @param handles native handles argument
     * @return native operation result
     */
    public static short bgfx_get_shader_uniforms(short shader, ShortBuffer handles) {
        /*
         * Marshal arguments synchronously; bgfx owns any queued upload copies.
         */
        BrowserNative.word(0, shader);
        int count = BrowserNative.call(SHADER_UNIFORMS);
        if (count < 0) throw new IllegalStateException("Shader uniform limit exceeded");
        if (handles != null) {
            for (int i = 0; i < Math.min(handles.remaining(), count); i++) {
                BrowserNative.word(0, i);
                handles.put(handles.position() + i, (short) BrowserNative.call(SHADER_UNIFORM_HANDLE));
            }
        }
        return (short) count;
    }

    /**
     * Copies reflected uniform metadata into caller storage.
     *
     * @param handle native handle argument
     * @param info native info argument
     */
    public static void bgfx_get_uniform_info(short handle, BGFXUniformInfo info) {
        /*
         * Marshal arguments synchronously; bgfx owns any queued upload copies.
         */
        BrowserNative.word(0, handle);
        int name = BrowserNative.call(UNIFORM_INFO);
        info.set(BrowserNative.string(name), BrowserNative.word(0), BrowserNative.word(1));
    }

    /**
     * Uploads retained vector or matrix uniform values.
     *
     * @param handle native handle argument
     * @param values native values argument
     * @param count native count argument
     */
    public static void bgfx_set_uniform(short handle, float[] values, int count) {
        /*
         * Marshal arguments synchronously; bgfx owns any queued upload copies.
         */
        BrowserNative.word(0, handle);
        BrowserNative.word(1, count);
        if (values.length > 16384) throw new IllegalArgumentException("Uniform upload exceeds 64 KiB");
        for (int i = 0; i < values.length; i++) BrowserNative.value(i, values[i]);
        BrowserNative.call(UNIFORM_SET);
    }

    /**
     * Performs bgfx texture create.
     *
     * @param width native width argument
     * @param height native height argument
     * @param mipmaps native mipmaps argument
     * @param layers native layers argument
     * @param format native format argument
     * @param flags native flags argument
     * @param data native data argument
     * @param unused native unused argument
     * @return native operation result
     */
    public static short bgfx_create_texture_2d(int width, int height, boolean mipmaps, int layers, int format, long flags, BGFXMemory data, long unused) {
        /*
         * Marshal arguments synchronously; bgfx owns any queued upload copies.
         */
        BrowserNative.word(0, width);
        BrowserNative.word(1, height);
        BrowserNative.word(2, mipmaps ? 1 : 0);
        BrowserNative.word(3, layers);
        BrowserNative.word(4, format);
        BrowserNative.wide(5, flags);
        BrowserNative.word(7, data == null ? 0 : data.pointer());
        return (short) BrowserNative.call(TEXTURE_CREATE);
    }

    /**
     * Performs bgfx texture valid.
     *
     * @param depth native depth argument
     * @param cube native cube argument
     * @param layers native layers argument
     * @param format native format argument
     * @param flags native flags argument
     * @return native operation result
     */
    public static boolean bgfx_is_texture_valid(int depth, boolean cube, int layers, int format, long flags) {
        /*
         * Marshal arguments synchronously; bgfx owns any queued upload copies.
         */
        BrowserNative.word(0, format);
        if (depth != 0 || cube || layers != 1) throw new UnsupportedOperationException("Only ordinary 2D engine textures are supported");
        BrowserNative.wide(1, flags);
        return BrowserNative.call(TEXTURE_VALID) != 0;
    }

    /**
     * Creates an owned framebuffer attachment set.
     *
     * @param attachments native attachments argument
     * @param destroyTextures native destroyTextures argument
     * @return native operation result
     */
    public static short bgfx_create_frame_buffer_from_handles(short[] attachments, boolean destroyTextures) {
        /*
         * Marshal arguments synchronously; bgfx owns any queued upload copies.
         */
        if (attachments.length > 8) throw new IllegalArgumentException("At most eight framebuffer attachments are supported");
        BrowserNative.word(0, attachments.length);
        BrowserNative.word(1, destroyTextures ? 1 : 0);
        for (int i = 0; i < attachments.length; i++) BrowserNative.word(2 + i, attachments[i]);
        return (short) BrowserNative.call(FRAMEBUFFER_ATTACHMENTS);
    }

    /**
     * Performs bgfx framebuffer create.
     *
     * @param width native width argument
     * @param height native height argument
     * @param format native format argument
     * @param flags native flags argument
     * @return native operation result
     */
    public static short bgfx_create_frame_buffer(int width, int height, int format, long flags) {
        /*
         * Marshal arguments synchronously; bgfx owns any queued upload copies.
         */
        BrowserNative.word(0, width);
        BrowserNative.word(1, height);
        BrowserNative.word(2, format);
        BrowserNative.wide(3, flags);
        return (short) BrowserNative.call(FRAMEBUFFER_CREATE);
    }

    /**
     * Performs bgfx framebuffer texture.
     *
     * @param framebuffer native framebuffer argument
     * @param attachment native attachment argument
     * @return native operation result
     */
    public static short bgfx_get_texture(short framebuffer, int attachment) {
        /*
         * Marshal arguments synchronously; bgfx owns any queued upload copies.
         */
        BrowserNative.word(0, framebuffer);
        BrowserNative.word(1, attachment);
        return (short) BrowserNative.call(FRAMEBUFFER_TEXTURE);
    }

    /**
     * Performs bgfx view rect.
     *
     * @param view native view argument
     * @param x native x argument
     * @param y native y argument
     * @param width native width argument
     * @param height native height argument
     */
    public static void bgfx_set_view_rect(int view, int x, int y, int width, int height) {
        /*
         * Marshal arguments synchronously; bgfx owns any queued upload copies.
         */
        BrowserNative.word(0, view);
        BrowserNative.word(1, x);
        BrowserNative.word(2, y);
        BrowserNative.word(3, width);
        BrowserNative.word(4, height);
        BrowserNative.call(VIEW_RECT);
    }

    /**
     * Performs bgfx view scissor.
     *
     * @param view native view argument
     * @param x native x argument
     * @param y native y argument
     * @param width native width argument
     * @param height native height argument
     */
    public static void bgfx_set_view_scissor(int view, int x, int y, int width, int height) {
        /*
         * Marshal arguments synchronously; bgfx owns any queued upload copies.
         */
        BrowserNative.word(0, view);
        BrowserNative.word(1, x);
        BrowserNative.word(2, y);
        BrowserNative.word(3, width);
        BrowserNative.word(4, height);
        BrowserNative.call(VIEW_SCISSOR);
    }

    /**
     * Performs bgfx view framebuffer.
     *
     * @param view native view argument
     * @param framebuffer native framebuffer argument
     */
    public static void bgfx_set_view_frame_buffer(int view, short framebuffer) {
        /*
         * Marshal arguments synchronously; bgfx owns any queued upload copies.
         */
        BrowserNative.word(0, view);
        BrowserNative.word(1, framebuffer);
        BrowserNative.call(VIEW_FRAMEBUFFER);
    }

    /**
     * Performs bgfx view mode.
     *
     * @param view native view argument
     * @param mode native mode argument
     */
    public static void bgfx_set_view_mode(int view, int mode) {
        /*
         * Marshal arguments synchronously; bgfx owns any queued upload copies.
         */
        BrowserNative.word(0, view);
        BrowserNative.word(1, mode);
        BrowserNative.call(VIEW_MODE);
    }

    /**
     * Performs bgfx view clear.
     *
     * @param view native view argument
     * @param flags native flags argument
     * @param rgba native rgba argument
     * @param depth native depth argument
     * @param stencil native stencil argument
     */
    public static void bgfx_set_view_clear(int view, int flags, int rgba, float depth, int stencil) {
        /*
         * Marshal arguments synchronously; bgfx owns any queued upload copies.
         */
        BrowserNative.word(0, view);
        BrowserNative.word(1, flags);
        BrowserNative.word(2, rgba);
        BrowserNative.word(3, stencil);
        BrowserNative.value(0, depth);
        BrowserNative.call(VIEW_CLEAR);
    }

    /**
     * Performs bgfx palette.
     *
     * @param index native index argument
     * @param r native r argument
     * @param g native g argument
     * @param b native b argument
     * @param a native a argument
     */
    public static void bgfx_set_palette_color_rgba32f(int index, float r, float g, float b, float a) {
        /*
         * Marshal arguments synchronously; bgfx owns any queued upload copies.
         */
        BrowserNative.word(0, index);
        BrowserNative.value(0, r);
        BrowserNative.value(1, g);
        BrowserNative.value(2, b);
        BrowserNative.value(3, a);
        BrowserNative.call(PALETTE);
    }

    /**
     * Performs bgfx view clear attachments.
     *
     * @param view native view argument
     * @param flags native flags argument
     * @param depth native depth argument
     * @param stencil native stencil argument
     * @param c0 native c0 argument
     * @param c1 native c1 argument
     * @param c2 native c2 argument
     * @param c3 native c3 argument
     * @param c4 native c4 argument
     * @param c5 native c5 argument
     * @param c6 native c6 argument
     * @param c7 native c7 argument
     */
    public static void bgfx_set_view_clear_mrt(int view, int flags, float depth, int stencil, byte c0, byte c1, byte c2, byte c3, byte c4, byte c5, byte c6, byte c7) {
        /*
         * Marshal arguments synchronously; bgfx owns any queued upload copies.
         */
        BrowserNative.word(0, view);
        BrowserNative.word(1, flags);
        BrowserNative.word(2, stencil);
        BrowserNative.word(3, c0);
        BrowserNative.word(4, c1);
        BrowserNative.word(5, c2);
        BrowserNative.word(6, c3);
        BrowserNative.word(7, c4);
        BrowserNative.word(8, c5);
        BrowserNative.word(9, c6);
        BrowserNative.word(10, c7);
        BrowserNative.value(0, depth);
        BrowserNative.call(VIEW_CLEAR_ATTACHMENTS);
    }

    /**
     * Performs bgfx touch.
     *
     * @param view native view argument
     */
    public static void bgfx_touch(int view) {
        /*
         * Marshal arguments synchronously; bgfx owns any queued upload copies.
         */
        BrowserNative.word(0, view);
        BrowserNative.call(TOUCH);
    }

    /**
     * Performs bgfx state.
     *
     * @param state native state argument
     * @param rgba native rgba argument
     */
    public static void bgfx_set_state(long state, int rgba) {
        /*
         * Marshal arguments synchronously; bgfx owns any queued upload copies.
         */

        BrowserNative.wide(0, state);
        BrowserNative.word(2, rgba);
        BrowserNative.call(STATE);
    }

    /**
     * Performs bgfx texture set.
     *
     * @param stage native stage argument
     * @param uniform native uniform argument
     * @param texture native texture argument
     * @param samplerFlags native samplerFlags argument
     */
    public static void bgfx_set_texture(int stage, short uniform, short texture, int samplerFlags) {
        /*
         * Marshal arguments synchronously; bgfx owns any queued upload copies.
         */
        BrowserNative.word(0, stage);
        BrowserNative.word(1, uniform);
        BrowserNative.word(2, texture);
        BrowserNative.word(3, samplerFlags);
        BrowserNative.call(TEXTURE_SET);
    }

    /**
     * Performs bgfx vertex set.
     *
     * @param stream native stream argument
     * @param handle native handle argument
     * @param start native start argument
     * @param count native count argument
     */
    public static void bgfx_set_vertex_buffer(int stream, short handle, int start, int count) {
        /*
         * Marshal arguments synchronously; bgfx owns any queued upload copies.
         */
        BrowserNative.word(0, stream);
        BrowserNative.word(1, handle);
        BrowserNative.word(2, start);
        BrowserNative.word(3, count);
        BrowserNative.call(VERTEX_SET);
    }

    /**
     * Performs bgfx dynamic vertex set.
     *
     * @param stream native stream argument
     * @param handle native handle argument
     * @param start native start argument
     * @param count native count argument
     */
    public static void bgfx_set_dynamic_vertex_buffer(int stream, short handle, int start, int count) {
        /*
         * Marshal arguments synchronously; bgfx owns any queued upload copies.
         */
        BrowserNative.word(0, stream);
        BrowserNative.word(1, handle);
        BrowserNative.word(2, start);
        BrowserNative.word(3, count);
        BrowserNative.call(DYNAMIC_VERTEX_SET);
    }

    /**
     * Performs bgfx instance set.
     *
     * @param handle native handle argument
     * @param start native start argument
     * @param count native count argument
     */
    public static void bgfx_set_instance_data_from_dynamic_vertex_buffer(short handle, int start, int count) {
        /*
         * Marshal arguments synchronously; bgfx owns any queued upload copies.
         */
        BrowserNative.word(0, handle);
        BrowserNative.word(1, start);
        BrowserNative.word(2, count);
        BrowserNative.call(INSTANCE_SET);
    }

    /**
     * Performs bgfx submit.
     *
     * @param view native view argument
     * @param program native program argument
     * @param depth native depth argument
     * @param flags native flags argument
     */
    public static void bgfx_submit(int view, short program, int depth, int flags) {
        /*
         * Marshal arguments synchronously; bgfx owns any queued upload copies.
         */
        BrowserNative.word(0, view);
        BrowserNative.word(1, program);
        BrowserNative.word(2, depth);
        BrowserNative.word(3, flags);
        BrowserNative.call(SUBMIT);
    }

    /**
     * Sets view and projection matrices independently.
     *
     * @param view native view argument
     * @param camera native camera argument
     * @param projection native projection argument
     */
    public static void bgfx_set_view_transform(int view, float[] camera, float[] projection) {
        /*
         * Marshal arguments synchronously; bgfx owns any queued upload copies.
         */
        BrowserNative.word(0, view);
        BrowserNative.word(1, camera == null ? 0 : 1);
        BrowserNative.word(2, projection == null ? 0 : 1);
        for (int i = 0; i < 16; i++) {
            if (camera != null) BrowserNative.value(i, camera[i]);
            if (projection != null) BrowserNative.value(16 + i, projection[i]);
        }
        BrowserNative.call(VIEW_TRANSFORM);
    }

    /**
     * Performs bgfx transient available.
     *
     * @param count native count argument
     * @param layout native layout argument
     * @return native operation result
     */
    public static int bgfx_get_avail_transient_vertex_buffer(int count, BGFXVertexLayout layout) {
        /*
         * Marshal arguments synchronously; bgfx owns any queued upload copies.
         */
        BrowserNative.word(0, count);
        BrowserNative.word(1, layout.pointer());
        return BrowserNative.call(TRANSIENT_AVAILABLE);
    }

    /**
     * Performs bgfx texture update.
     *
     * @param handle native handle argument
     * @param layer native layer argument
     * @param mip native mip argument
     * @param x native x argument
     * @param y native y argument
     * @param width native width argument
     * @param height native height argument
     * @param memory native memory argument
     * @param pitch native pitch argument
     */
    public static void bgfx_update_texture_2d(short handle, int layer, int mip, int x, int y, int width, int height, BGFXMemory memory, int pitch) {
        /*
         * Marshal arguments synchronously; bgfx owns any queued upload copies.
         */
        BrowserNative.word(0, handle);
        BrowserNative.word(1, layer);
        BrowserNative.word(2, mip);
        BrowserNative.word(3, x);
        BrowserNative.word(4, y);
        BrowserNative.word(5, width);
        BrowserNative.word(6, height);
        BrowserNative.word(7, memory.pointer());
        BrowserNative.word(8, pitch);
        BrowserNative.call(TEXTURE_UPDATE);
    }

    /**
     * Begins a vertex layout for the browser renderer.
     *
     * @param layout native layout argument
     * @param renderer native renderer argument
     */
    public static BGFXVertexLayout bgfx_vertex_layout_begin(BGFXVertexLayout layout, int renderer) {
        /*
         * Marshal arguments synchronously; bgfx owns any queued upload copies.
         */
        layout.begin();
        return layout;
    }

    /**
     * Appends one packed vertex attribute.
     *
     * @param layout native layout argument
     * @param attribute native attribute argument
     * @param count native count argument
     * @param type native type argument
     * @param normalized native normalized argument
     * @param integer native integer argument
     */
    public static BGFXVertexLayout bgfx_vertex_layout_add(BGFXVertexLayout layout, int attribute, int count, int type, boolean normalized, boolean integer) {
        /*
         * Marshal arguments synchronously; bgfx owns any queued upload copies.
         */
        layout.add(attribute, (byte) count, type, normalized, integer);
        return layout;
    }

    /**
     * Adds explicit packed-record padding.
     *
     * @param layout native layout argument
     * @param bytes native bytes argument
     */
    public static BGFXVertexLayout bgfx_vertex_layout_skip(BGFXVertexLayout layout, int bytes) {
        /*
         * Marshal arguments synchronously; bgfx owns any queued upload copies.
         */
        layout.skip(bytes);
        return layout;
    }

    /**
     * Finalizes the packed vertex declaration.
     *
     * @param layout native layout argument
     */
    public static void bgfx_vertex_layout_end(BGFXVertexLayout layout) {
        /*
         * Marshal arguments synchronously; bgfx owns any queued upload copies.
         */
        layout.end();
    }

    /**
     * Checks whether a vertex attribute is present.
     *
     * @param layout native layout argument
     * @param attribute native attribute argument
     * @return native operation result
     */
    public static boolean bgfx_vertex_layout_has(BGFXVertexLayout layout, int attribute) {
        /*
         * Marshal arguments synchronously; bgfx owns any queued upload copies.
         */
        return layout.has(attribute);
    }

    /**
     * Decodes one retained vertex attribute.
     *
     * @param layout native layout argument
     * @param attribute native attribute argument
     * @param count native count argument
     * @param type native type argument
     * @param normalized native normalized argument
     * @param integer native integer argument
     */
    public static void bgfx_vertex_layout_decode(BGFXVertexLayout layout, int attribute, ByteBuffer count, IntBuffer type, ByteBuffer normalized, ByteBuffer integer) {
        /*
         * Marshal arguments synchronously; bgfx owns any queued upload copies.
         */
        layout.decode(attribute, count, type, normalized, integer);
    }

    /**
     * Rejects compute operations unavailable in the WebGL 2 specification.
     * @param shader requested compute argument
     * @param destroyShader requested compute argument
     * @throws UnsupportedOperationException because WebGL 2 has no compute pipeline
     */
    public static short bgfx_create_compute_program(short shader, boolean destroyShader) {
        /*
         * Capability checks must happen before compute resource creation; never
         * emulate unsupported storage or dispatch with silently discarded work.
         */
        throw new UnsupportedOperationException("WebGL 2 does not expose compute shaders or shader-storage buffers");
    }

    /**
     * Rejects compute operations unavailable in the WebGL 2 specification.
     * @param count requested compute argument
     * @param flags requested compute argument
     * @throws UnsupportedOperationException because WebGL 2 has no compute pipeline
     */
    public static short bgfx_create_dynamic_index_buffer(int count, int flags) {
        /*
         * Capability checks must happen before compute resource creation; never
         * emulate unsupported storage or dispatch with silently discarded work.
         */
        throw new UnsupportedOperationException("WebGL 2 does not expose compute shaders or shader-storage buffers");
    }

    /**
     * Rejects compute operations unavailable in the WebGL 2 specification.
     * @param memory requested compute argument
     * @param flags requested compute argument
     * @throws UnsupportedOperationException because WebGL 2 has no compute pipeline
     */
    public static short bgfx_create_dynamic_index_buffer_mem(BGFXMemory memory, int flags) {
        /*
         * Capability checks must happen before compute resource creation; never
         * emulate unsupported storage or dispatch with silently discarded work.
         */
        throw new UnsupportedOperationException("WebGL 2 does not expose compute shaders or shader-storage buffers");
    }

    /**
     * Rejects compute operations unavailable in the WebGL 2 specification.
     * @param handle requested compute argument
     * @param offset requested compute argument
     * @param memory requested compute argument
     * @throws UnsupportedOperationException because WebGL 2 has no compute pipeline
     */
    public static void bgfx_update_dynamic_index_buffer(short handle, int offset, BGFXMemory memory) {
        /*
         * Capability checks must happen before compute resource creation; never
         * emulate unsupported storage or dispatch with silently discarded work.
         */
        throw new UnsupportedOperationException("WebGL 2 does not expose compute shaders or shader-storage buffers");
    }

    /**
     * Rejects compute operations unavailable in the WebGL 2 specification.
     * @param handle requested compute argument
     * @throws UnsupportedOperationException because WebGL 2 has no compute pipeline
     */
    public static void bgfx_destroy_dynamic_index_buffer(short handle) {
        /*
         * Capability checks must happen before compute resource creation; never
         * emulate unsupported storage or dispatch with silently discarded work.
         */
        throw new UnsupportedOperationException("WebGL 2 does not expose compute shaders or shader-storage buffers");
    }

    /**
     * Rejects compute operations unavailable in the WebGL 2 specification.
     * @param stage requested compute argument
     * @param handle requested compute argument
     * @param access requested compute argument
     * @throws UnsupportedOperationException because WebGL 2 has no compute pipeline
     */
    public static void bgfx_set_compute_dynamic_index_buffer(int stage, short handle, int access) {
        /*
         * Capability checks must happen before compute resource creation; never
         * emulate unsupported storage or dispatch with silently discarded work.
         */
        throw new UnsupportedOperationException("WebGL 2 does not expose compute shaders or shader-storage buffers");
    }

    /**
     * Rejects compute operations unavailable in the WebGL 2 specification.
     * @param stage requested compute argument
     * @param handle requested compute argument
     * @param mip requested compute argument
     * @param access requested compute argument
     * @param format requested compute argument
     * @throws UnsupportedOperationException because WebGL 2 has no compute pipeline
     */
    public static void bgfx_set_image(int stage, short handle, int mip, int access, int format) {
        /*
         * Capability checks must happen before compute resource creation; never
         * emulate unsupported storage or dispatch with silently discarded work.
         */
        throw new UnsupportedOperationException("WebGL 2 does not expose compute shaders or shader-storage buffers");
    }

    /**
     * Rejects compute operations unavailable in the WebGL 2 specification.
     * @param view requested compute argument
     * @param program requested compute argument
     * @param x requested compute argument
     * @param y requested compute argument
     * @param z requested compute argument
     * @param flags requested compute argument
     * @throws UnsupportedOperationException because WebGL 2 has no compute pipeline
     */
    public static void bgfx_dispatch(int view, short program, int x, int y, int z, int flags) {
        /*
         * Capability checks must happen before compute resource creation; never
         * emulate unsupported storage or dispatch with silently discarded work.
         */
        throw new UnsupportedOperationException("WebGL 2 does not expose compute shaders or shader-storage buffers");
    }

}
