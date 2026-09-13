package valthorne.website.content;

import java.util.List;

/** An immutable documentation entry, download, or linked feature.
 *
 * <p>Optional text is represented by an empty string and child collections are
 * immutable. This model has no dependency on JSON, a browser, or TeaVM.</p>
 */
public record Card(String title, String text, String href, String label, String image, String category, String guide) {
    /** Normalizes optional fields and protects child collections from mutation. */
    public Card {
        title = title == null ? "" : title;
        text = text == null ? "" : text;
        href = href == null ? "" : href;
        label = label == null ? "" : label;
        image = image == null ? "" : image;
        category = category == null ? "" : category;
        guide = guide == null ? "" : guide;
    }

    /** Reads a named text property for the canvas renderer's compact drawing API. */
    public String get(String key) {
        return switch (key) {
            case "title" -> title;
            case "text" -> text;
            case "href" -> href;
            case "label" -> label;
            case "image" -> image;
            case "category" -> category;
            case "guide" -> guide;
            default -> "";
        };
    }

    /** Starts an explicitly named content entry. */
    public static Builder card(String title) { return new Builder().title(title); }

    /** Fluent authoring helpers produce an immutable value at build time. */
    public static final class Builder {
        private String title = "";
        private String text = "";
        private String href = "";
        private String label = "";
        private String image = "";
        private String category = "";
        private String guide = "";

        /** Sets the title value. */
        public Builder title(String value) { title = value; return this; }
        /** Sets the text value. */
        public Builder text(String value) { text = value; return this; }
        /** Sets the href value. */
        public Builder href(String value) { href = value; return this; }
        /** Sets the label value. */
        public Builder label(String value) { label = value; return this; }
        /** Sets the image value. */
        public Builder image(String value) { image = value; return this; }
        /** Sets the category value. */
        public Builder category(String value) { category = value; return this; }
        /** Sets the guide value. */
        public Builder guide(String value) { guide = value; return this; }
        /** Creates the immutable card. */
        public Card build() { return new Card(title, text, href, label, image, category, guide); }
    }
}
