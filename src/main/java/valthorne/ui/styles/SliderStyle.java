package valthorne.ui.styles;

import valthorne.graphics.Drawable;

/**
 * <h1>SliderStyle</h1>
 *
 * <p>
 * {@code SliderStyle} stores the drawable configuration for a {@link valthorne.ui.elements.Slider}.
 * It separates the slider into three visual parts:
 * </p>
 * <ul>
 *     <li>The track</li>
 *     <li>The fill</li>
 *     <li>The thumb</li>
 * </ul>
 *
 * <p>
 * Each part can define a normal drawable plus optional state-specific overrides for hovered, pressed,
 * focused, and disabled states. The slider chooses the most appropriate drawable at render time based
 * on its current interaction state.
 * </p>
 *
 * <h2>Why this class exists</h2>
 * <p>
 * A slider often needs different visuals for interaction feedback without changing its logic. By
 * keeping these drawables in a style object, the same slider code can be reused with many different
 * themes and skin variations.
 * </p>
 *
 * <h2>Resolution model</h2>
 * <p>
 * The slider typically resolves style drawables in this priority order:
 * </p>
 * <ol>
 *     <li>Disabled</li>
 *     <li>Pressed</li>
 *     <li>Hovered</li>
 *     <li>Focused</li>
 *     <li>Default</li>
 * </ol>
 *
 * <h2>Builder-style usage</h2>
 * <p>
 * This class is designed for fluent chaining so styles can be configured compactly and readably.
 * </p>
 *
 * <h2>Example</h2>
 * <pre>{@code
 * SliderStyle style = SliderStyle.of()
 *     .track(trackDrawable)
 *     .fill(fillDrawable)
 *     .thumb(thumbDrawable)
 *     .hoveredThumb(hoveredThumbDrawable)
 *     .pressedThumb(pressedThumbDrawable)
 *     .disabledTrack(disabledTrackDrawable)
 *     .disabledFill(disabledFillDrawable)
 *     .disabledThumb(disabledThumbDrawable);
 * }</pre>
 *
 * @author Albert Beaupre
 * @since March 7th, 2026
 */
public class SliderStyle {

    private Drawable track; // The default drawable used for the slider track.
    private Drawable hoveredTrack; // The track drawable used while the slider is hovered.
    private Drawable pressedTrack; // The track drawable used while the slider is pressed.
    private Drawable focusedTrack; // The track drawable used while the slider is focused.
    private Drawable disabledTrack; // The track drawable used while the slider is disabled.

    private Drawable fill; // The default drawable used for the slider fill.
    private Drawable hoveredFill; // The fill drawable used while the slider is hovered.
    private Drawable pressedFill; // The fill drawable used while the slider is pressed.
    private Drawable focusedFill; // The fill drawable used while the slider is focused.
    private Drawable disabledFill; // The fill drawable used while the slider is disabled.

    private Drawable thumb; // The default drawable used for the slider thumb.
    private Drawable hoveredThumb; // The thumb drawable used while the slider is hovered.
    private Drawable pressedThumb; // The thumb drawable used while the slider is pressed.
    private Drawable focusedThumb; // The thumb drawable used while the slider is focused.
    private Drawable disabledThumb; // The thumb drawable used while the slider is disabled.

    /**
     * Creates a new empty slider style.
     *
     * <p>
     * This static factory exists for fluent style building and mirrors the usage style commonly seen in
     * UI skin APIs.
     * </p>
     *
     * @return a new slider style instance
     */
    public static SliderStyle of() {
        return new SliderStyle();
    }

    /**
     * Returns the default track drawable.
     *
     * @return the default track drawable, or null if none is set
     */
    public Drawable getTrack() {
        return track;
    }

    /**
     * Sets the default track drawable.
     *
     * @param track the drawable to use for the normal track state
     * @return this style for chaining
     */
    public SliderStyle track(Drawable track) {
        this.track = track;
        return this;
    }

    /**
     * Returns the hovered track drawable.
     *
     * @return the hovered track drawable, or null if none is set
     */
    public Drawable getHoveredTrack() {
        return hoveredTrack;
    }

    /**
     * Sets the hovered track drawable.
     *
     * @param hoveredTrack the drawable to use for the hovered track state
     * @return this style for chaining
     */
    public SliderStyle hoveredTrack(Drawable hoveredTrack) {
        this.hoveredTrack = hoveredTrack;
        return this;
    }

    /**
     * Returns the pressed track drawable.
     *
     * @return the pressed track drawable, or null if none is set
     */
    public Drawable getPressedTrack() {
        return pressedTrack;
    }

    /**
     * Sets the pressed track drawable.
     *
     * @param pressedTrack the drawable to use for the pressed track state
     * @return this style for chaining
     */
    public SliderStyle pressedTrack(Drawable pressedTrack) {
        this.pressedTrack = pressedTrack;
        return this;
    }

    /**
     * Returns the focused track drawable.
     *
     * @return the focused track drawable, or null if none is set
     */
    public Drawable getFocusedTrack() {
        return focusedTrack;
    }

    /**
     * Sets the focused track drawable.
     *
     * @param focusedTrack the drawable to use for the focused track state
     * @return this style for chaining
     */
    public SliderStyle focusedTrack(Drawable focusedTrack) {
        this.focusedTrack = focusedTrack;
        return this;
    }

    /**
     * Returns the disabled track drawable.
     *
     * @return the disabled track drawable, or null if none is set
     */
    public Drawable getDisabledTrack() {
        return disabledTrack;
    }

    /**
     * Sets the disabled track drawable.
     *
     * @param disabledTrack the drawable to use for the disabled track state
     * @return this style for chaining
     */
    public SliderStyle disabledTrack(Drawable disabledTrack) {
        this.disabledTrack = disabledTrack;
        return this;
    }

    /**
     * Returns the default fill drawable.
     *
     * @return the default fill drawable, or null if none is set
     */
    public Drawable getFill() {
        return fill;
    }

    /**
     * Sets the default fill drawable.
     *
     * @param fill the drawable to use for the normal fill state
     * @return this style for chaining
     */
    public SliderStyle fill(Drawable fill) {
        this.fill = fill;
        return this;
    }

    /**
     * Returns the hovered fill drawable.
     *
     * @return the hovered fill drawable, or null if none is set
     */
    public Drawable getHoveredFill() {
        return hoveredFill;
    }

    /**
     * Sets the hovered fill drawable.
     *
     * @param hoveredFill the drawable to use for the hovered fill state
     * @return this style for chaining
     */
    public SliderStyle hoveredFill(Drawable hoveredFill) {
        this.hoveredFill = hoveredFill;
        return this;
    }

    /**
     * Returns the pressed fill drawable.
     *
     * @return the pressed fill drawable, or null if none is set
     */
    public Drawable getPressedFill() {
        return pressedFill;
    }

    /**
     * Sets the pressed fill drawable.
     *
     * @param pressedFill the drawable to use for the pressed fill state
     * @return this style for chaining
     */
    public SliderStyle pressedFill(Drawable pressedFill) {
        this.pressedFill = pressedFill;
        return this;
    }

    /**
     * Returns the focused fill drawable.
     *
     * @return the focused fill drawable, or null if none is set
     */
    public Drawable getFocusedFill() {
        return focusedFill;
    }

    /**
     * Sets the focused fill drawable.
     *
     * @param focusedFill the drawable to use for the focused fill state
     * @return this style for chaining
     */
    public SliderStyle focusedFill(Drawable focusedFill) {
        this.focusedFill = focusedFill;
        return this;
    }

    /**
     * Returns the disabled fill drawable.
     *
     * @return the disabled fill drawable, or null if none is set
     */
    public Drawable getDisabledFill() {
        return disabledFill;
    }

    /**
     * Sets the disabled fill drawable.
     *
     * @param disabledFill the drawable to use for the disabled fill state
     * @return this style for chaining
     */
    public SliderStyle disabledFill(Drawable disabledFill) {
        this.disabledFill = disabledFill;
        return this;
    }

    /**
     * Returns the default thumb drawable.
     *
     * @return the default thumb drawable, or null if none is set
     */
    public Drawable getThumb() {
        return thumb;
    }

    /**
     * Sets the default thumb drawable.
     *
     * @param thumb the drawable to use for the normal thumb state
     * @return this style for chaining
     */
    public SliderStyle thumb(Drawable thumb) {
        this.thumb = thumb;
        return this;
    }

    /**
     * Returns the hovered thumb drawable.
     *
     * @return the hovered thumb drawable, or null if none is set
     */
    public Drawable getHoveredThumb() {
        return hoveredThumb;
    }

    /**
     * Sets the hovered thumb drawable.
     *
     * @param hoveredThumb the drawable to use for the hovered thumb state
     * @return this style for chaining
     */
    public SliderStyle hoveredThumb(Drawable hoveredThumb) {
        this.hoveredThumb = hoveredThumb;
        return this;
    }

    /**
     * Returns the pressed thumb drawable.
     *
     * @return the pressed thumb drawable, or null if none is set
     */
    public Drawable getPressedThumb() {
        return pressedThumb;
    }

    /**
     * Sets the pressed thumb drawable.
     *
     * @param pressedThumb the drawable to use for the pressed thumb state
     * @return this style for chaining
     */
    public SliderStyle pressedThumb(Drawable pressedThumb) {
        this.pressedThumb = pressedThumb;
        return this;
    }

    /**
     * Returns the focused thumb drawable.
     *
     * @return the focused thumb drawable, or null if none is set
     */
    public Drawable getFocusedThumb() {
        return focusedThumb;
    }

    /**
     * Sets the focused thumb drawable.
     *
     * @param focusedThumb the drawable to use for the focused thumb state
     * @return this style for chaining
     */
    public SliderStyle focusedThumb(Drawable focusedThumb) {
        this.focusedThumb = focusedThumb;
        return this;
    }

    /**
     * Returns the disabled thumb drawable.
     *
     * @return the disabled thumb drawable, or null if none is set
     */
    public Drawable getDisabledThumb() {
        return disabledThumb;
    }

    /**
     * Sets the disabled thumb drawable.
     *
     * @param disabledThumb the drawable to use for the disabled thumb state
     * @return this style for chaining
     */
    public SliderStyle disabledThumb(Drawable disabledThumb) {
        this.disabledThumb = disabledThumb;
        return this;
    }
}