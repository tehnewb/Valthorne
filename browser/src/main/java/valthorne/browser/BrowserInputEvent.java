package valthorne.browser;

/**
 * Retains copied DOM event values while Java is rendering or awaiting asset I/O.
 * @param kind event category
 * @param a first integer payload
 * @param b second integer payload
 * @param c third integer payload
 * @param d fourth integer payload
 * @param x horizontal or scroll payload
 * @param y vertical or scroll payload
 * @param text optional dropped-file path
 */
public record BrowserInputEvent(int kind, int a, int b, int c, int d, double x, double y, String text) {
}
