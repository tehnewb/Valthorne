package valthorne.website.content;

import java.util.List;

/** An immutable page section, including optional media, code, or a searchable catalog.
 *
 * <p>Optional text is represented by an empty string and child collections are
 * immutable. This model has no dependency on JSON, a browser, or TeaVM.</p>
 */
public record Section(String title, String description, String layout, String catalog, String filename, String code, boolean lab, String image, String imageCaption, List<Card> cards) {
    /** Normalizes optional fields and protects child collections from mutation. */
    public Section {
        title = title == null ? "" : title;
        description = description == null ? "" : description;
        layout = layout == null ? "" : layout;
        catalog = catalog == null ? "" : catalog;
        filename = filename == null ? "" : filename;
        code = code == null ? "" : code;
        image = image == null ? "" : image;
        imageCaption = imageCaption == null ? "" : imageCaption;
        cards = cards == null ? List.of() : List.copyOf(cards);
    }

    /** Reads a named text property for the canvas renderer's compact drawing API. */
    public String get(String key) {
        return switch (key) {
            case "title" -> title;
            case "description" -> description;
            case "layout" -> layout;
            case "catalog" -> catalog;
            case "filename" -> filename;
            case "code" -> code;
            case "lab" -> lab ? "true" : "";
            case "image" -> image;
            case "imageCaption" -> imageCaption;
            default -> "";
        };
    }

    /** Starts an explicitly named content entry. */
    public static Builder section(String title) { return new Builder().title(title); }

    /** Fluent authoring helpers produce an immutable value at build time. */
    public static final class Builder {
        private String title = "";
        private String description = "";
        private String layout = "";
        private String catalog = "";
        private String filename = "";
        private String code = "";
        private boolean lab;
        private String image = "";
        private String imageCaption = "";
        private List<Card> cards = List.of();

        /** Sets the title value. */
        public Builder title(String value) { title = value; return this; }
        /** Sets the description value. */
        public Builder description(String value) { description = value; return this; }
        /** Sets the layout value. */
        public Builder layout(String value) { layout = value; return this; }
        /** Sets the catalog value. */
        public Builder catalog(String value) { catalog = value; return this; }
        /** Sets the filename value. */
        public Builder filename(String value) { filename = value; return this; }
        /** Sets the code value. */
        public Builder code(String value) { code = value; return this; }
        /** Sets the lab value. */
        public Builder lab(boolean value) { lab = value; return this; }
        /** Sets the image value. */
        public Builder image(String value) { image = value; return this; }
        /** Sets the image caption value. */
        public Builder imageCaption(String value) { imageCaption = value; return this; }
        /** Sets the cards collection. */
        public Builder cards(List<Card> value) { cards = value; return this; }
        /** Sets the cards in display order. */
        public Builder cards(Card... values) { cards = List.of(values); return this; }
        /** Creates the immutable section. */
        public Section build() { return new Section(title, description, layout, catalog, filename, code, lab, image, imageCaption, cards); }
    }
}
