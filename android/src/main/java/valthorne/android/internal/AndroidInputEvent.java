package valthorne.android.internal;

/**
 * Copies one Android UI event for delivery on the render thread. MotionEvent and
 * KeyEvent instances belong to Android and cannot be retained after a callback.
 * @param kind event category
 * @param a first integer argument
 * @param b second integer argument
 * @param c third integer argument
 * @param d fourth integer argument
 * @param x horizontal position or scroll
 * @param y vertical position or scroll
 * @param text optional committed text
 */
record AndroidInputEvent(int kind, int a, int b, int c, int d, double x, double y, String text) {
}
