package java.io;
/** Standard checked exception used by XML character decoders. */
public class CharConversionException extends IOException {
    private static final long serialVersionUID = -8680016352018427031L;
    public CharConversionException() { super(); }
    public CharConversionException(String message) { super(message); }
}
