package org.lwjgl.stb;

import valthorne.browser.BrowserNative;

import static valthorne.browser.BrowserOperation.FONT_VERTEX;

/**
 * Reusable glyph-outline vertex copied from STB's native shape array.
 * A shape buffer reuses one vertex view while walking its contour.
 */
public final class STBTTVertex {
    private byte type; // Outline command kind.
    private short x; // Endpoint horizontal font units.
    private short y; // Endpoint vertical font units.
    private short cx; // First control point horizontal font units.
    private short cy; // First control point vertical font units.
    private short cx1; // Second cubic control point horizontal font units.
    private short cy1; // Second cubic control point vertical font units.

    /**
     * Creates an empty reusable vertex view.
     */
    STBTTVertex() {
    }

    /**
     * Copies one native vertex into the reusable Java view.
     * @param pointer first native vertex
     * @param index requested vertex index
     */
    void read(int pointer, int index) {
        BrowserNative.word(0, pointer);
        BrowserNative.word(1, index);
        BrowserNative.call(FONT_VERTEX);
        type = (byte) BrowserNative.word(0);
        x = (short) BrowserNative.word(1);
        y = (short) BrowserNative.word(2);
        cx = (short) BrowserNative.word(3);
        cy = (short) BrowserNative.word(4);
        cx1 = (short) BrowserNative.word(5);
        cy1 = (short) BrowserNative.word(6);
    }

    /**
     * Returns the copied type outline component.
     * @return outline command kind
     */
    public byte type() { return type; }

    /**
     * Returns the copied x outline component.
     * @return signed font-space coordinate
     */
    public short x() { return x; }

    /**
     * Returns the copied y outline component.
     * @return signed font-space coordinate
     */
    public short y() { return y; }

    /**
     * Returns the copied cx outline component.
     * @return signed font-space coordinate
     */
    public short cx() { return cx; }

    /**
     * Returns the copied cy outline component.
     * @return signed font-space coordinate
     */
    public short cy() { return cy; }

    /**
     * Returns the copied cx1 outline component.
     * @return signed font-space coordinate
     */
    public short cx1() { return cx1; }

    /**
     * Returns the copied cy1 outline component.
     * @return signed font-space coordinate
     */
    public short cy1() { return cy1; }

}
