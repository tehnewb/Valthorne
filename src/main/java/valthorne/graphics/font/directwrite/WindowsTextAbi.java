package valthorne.graphics.font.directwrite;

/**
 * Fixed COM vtable contract from the Windows SDK interfaces. Overloaded C++
 * declarations do not imply source-order ABI slots; these values follow the actual
 * C COM vtables, including inherited IUnknown entries. No runtime guessing occurs.
 */
final class WindowsTextAbi {
    /**
     * IDWriteFactory CreateFontFileReference slot.
     */
    static final int WRITE_CREATE_FONT_FILE = 7;
    /**
     * IDWriteFontFile Analyze slot for validating external font data before building sets.
     */
    static final int FONT_FILE_ANALYZE = 5;
    /**
     * IDWriteFactory3 interface-based CreateFontFaceReference overload slot.
     */
    static final int WRITE_CREATE_ANALYZED_FACE_REFERENCE = 33;
    /**
     * IUnknown QueryInterface slot.
     */
    static final int UNKNOWN_QUERY = 0;
    /**
     * IUnknown Release slot.
     */
    static final int UNKNOWN_RELEASE = 2;
    /**
     * IDWriteFactory CreateRenderingParams slot.
     */
    static final int WRITE_CREATE_RENDERING_PARAMS = 10;
    /**
     * IDWriteFactory CreateMonitorRenderingParams slot.
     */
    static final int WRITE_CREATE_MONITOR_PARAMS = 11;
    /**
     * IDWriteFactory CreateTextFormat slot.
     */
    static final int WRITE_CREATE_FORMAT = 15;
    /**
     * IDWriteFactory CreateTextLayout slot.
     */
    static final int WRITE_CREATE_LAYOUT = 18;
    /**
     * IDWriteFactory3 path-based CreateFontFaceReference slot; the file-interface overload occupies slot 33.
     */
    static final int WRITE_CREATE_FILE_FACE_REFERENCE = 34;
    /**
     * IDWriteFactory3 CreateFontSetBuilder slot.
     */
    static final int WRITE_CREATE_SET_BUILDER = 36;
    /**
     * IDWriteFactory3 CreateFontCollectionFromFontSet slot.
     */
    static final int WRITE_CREATE_COLLECTION = 37;
    /**
     * IDWriteFontSetBuilder single-reference overload slot; the property-array overload occupies slot 3.
     */
    static final int SET_BUILDER_ADD_FACE = 4;
    /**
     * IDWriteFontSetBuilder CreateFontSet slot.
     */
    static final int SET_BUILDER_CREATE_SET = 6;
    /**
     * IDWriteFontCollection GetFontFamily slot.
     */
    static final int COLLECTION_GET_FAMILY = 4;
    /**
     * IDWriteFontFamily GetFamilyNames slot.
     */
    static final int FAMILY_GET_NAMES = 6;
    /**
     * IDWriteFontList GetFont slot inherited by font families.
     */
    static final int FONT_LIST_GET_FONT = 5;
    /**
     * IDWriteFont GetWeight slot.
     */
    static final int FONT_GET_WEIGHT = 4;
    /**
     * IDWriteFont GetStretch slot.
     */
    static final int FONT_GET_STRETCH = 5;
    /**
     * IDWriteFont GetStyle slot.
     */
    static final int FONT_GET_STYLE = 6;
    /**
     * IDWriteLocalizedStrings GetStringLength slot.
     */
    static final int NAMES_GET_LENGTH = 7;
    /**
     * IDWriteLocalizedStrings GetString slot.
     */
    static final int NAMES_GET_STRING = 8;
    /**
     * IDWriteTextFormat SetWordWrapping slot.
     */
    static final int FORMAT_SET_WRAPPING = 5;
    /**
     * IDWriteTextLayout SetMaxWidth slot.
     */
    static final int LAYOUT_SET_WIDTH = 28;
    /**
     * IDWriteTextLayout SetMaxHeight slot.
     */
    static final int LAYOUT_SET_HEIGHT = 29;
    /**
     * IDWriteTextLayout GetLineMetrics slot.
     */
    static final int LAYOUT_GET_LINES = 59;
    /**
     * IDWriteTextLayout GetMetrics slot.
     */
    static final int LAYOUT_GET_METRICS = 60;
    /**
     * IDWriteTextLayout GetOverhangMetrics slot.
     */
    static final int LAYOUT_GET_OVERHANG = 61;
    /**
     * IDWriteTextLayout HitTestPoint slot.
     */
    static final int LAYOUT_HIT_POINT = 64;
    /**
     * IDWriteTextLayout HitTestTextPosition slot.
     */
    static final int LAYOUT_HIT_POSITION = 65;
    /**
     * IDWriteTextLayout HitTestTextRange slot.
     */
    static final int LAYOUT_HIT_RANGE = 66;
    /**
     * ID2D1Factory CreateWicBitmapRenderTarget slot.
     */
    static final int D2D_CREATE_WIC_TARGET = 13;
    /**
     * ID2D1RenderTarget CreateSolidColorBrush slot.
     */
    static final int TARGET_CREATE_SOLID_BRUSH = 8;
    /**
     * ID2D1RenderTarget DrawTextLayout slot.
     */
    static final int TARGET_DRAW_TEXT_LAYOUT = 28;
    /**
     * ID2D1RenderTarget SetTextAntialiasMode slot.
     */
    static final int TARGET_SET_TEXT_ANTIALIAS = 34;
    /**
     * ID2D1RenderTarget SetTextRenderingParams slot.
     */
    static final int TARGET_SET_TEXT_PARAMS = 36;
    /**
     * ID2D1RenderTarget Clear slot.
     */
    static final int TARGET_CLEAR = 47;
    /**
     * ID2D1RenderTarget BeginDraw slot.
     */
    static final int TARGET_BEGIN = 48;
    /**
     * ID2D1RenderTarget EndDraw slot.
     */
    static final int TARGET_END = 49;
    /**
     * IWICImagingFactory CreateBitmap slot.
     */
    static final int WIC_CREATE_BITMAP = 17;
    /**
     * IWICBitmapSource CopyPixels slot inherited by IWICBitmap.
     */
    static final int WIC_COPY_PIXELS = 7;

    /**
     * Prevents construction of the ABI constant table.
     */
    private WindowsTextAbi() {}
}
