package valthorne.browser;

import java.util.Base64;

/**
 * Decodes MIME-style Base64 map payloads on browsers whose Java class library
 * exposes only the basic decoder. Non-alphabet separators are ignored as required
 * by MIME decoding; padding and final-block validity remain the decoder's job.
 */
public final class BrowserBase64 {
    /**
     * Prevents constructing stateless payload decoders.
     */
    private BrowserBase64() {
    }

    /**
     * Decodes Base64 while accepting XML indentation and MIME separators.
     * @param text encoded map payload
     * @return independently owned decoded bytes
     * @throws IllegalArgumentException if padding or final-block syntax is invalid
     */
    public static byte[] decodeMime(String text) {
        /*
         * Filter once without regex; the ordinary decoder still validates padding.
         */
        StringBuilder clean = new StringBuilder(text.length());
        for (int i = 0; i < text.length(); i++) {
            char ch = text.charAt(i);
            if (ch >= 'A' && ch <= 'Z' || ch >= 'a' && ch <= 'z' || ch >= '0' && ch <= '9' || ch == '+' || ch == '/' || ch == '=') clean.append(ch);
        }
        return Base64.getDecoder().decode(clean.toString());
    }
}
