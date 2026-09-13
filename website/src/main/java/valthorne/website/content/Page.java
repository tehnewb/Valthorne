package valthorne.website.content;

import java.util.List;

/** An immutable route shared by the engine renderer and the semantic HTML exporter.
 *
 * <p>Optional text is represented by an empty string and child collections are
 * immutable. This model has no dependency on JSON, a browser, or TeaVM.</p>
 */
public record Page(String id, String title, String eyebrow, String description, String heroImage, String heroCaption, List<Section> sections) {
    /** Normalizes optional fields and protects child collections from mutation. */
    public Page {
        id = id == null ? "" : id;
        title = title == null ? "" : title;
        eyebrow = eyebrow == null ? "" : eyebrow;
        description = description == null ? "" : description;
        heroImage = heroImage == null ? "" : heroImage;
        heroCaption = heroCaption == null ? "" : heroCaption;
        sections = sections == null ? List.of() : List.copyOf(sections);
    }

    /** Reads a named text property for the canvas renderer's compact drawing API. */
    public String get(String key) {
        return switch (key) {
            case "id" -> id;
            case "title" -> title;
            case "eyebrow" -> eyebrow;
            case "description" -> description;
            case "heroImage" -> heroImage;
            case "heroCaption" -> heroCaption;
            default -> "";
        };
    }

    /** Starts an explicitly named content entry. */
    public static Builder page(String id) { return new Builder().id(id); }

    /** Fluent authoring helpers produce an immutable value at build time. */
    public static final class Builder {
        private String id = "";
        private String title = "";
        private String eyebrow = "";
        private String description = "";
        private String heroImage = "";
        private String heroCaption = "";
        private List<Section> sections = List.of();

        /** Sets the id value. */
        public Builder id(String value) { id = value; return this; }
        /** Sets the title value. */
        public Builder title(String value) { title = value; return this; }
        /** Sets the eyebrow value. */
        public Builder eyebrow(String value) { eyebrow = value; return this; }
        /** Sets the description value. */
        public Builder description(String value) { description = value; return this; }
        /** Sets the hero image value. */
        public Builder heroImage(String value) { heroImage = value; return this; }
        /** Sets the hero caption value. */
        public Builder heroCaption(String value) { heroCaption = value; return this; }
        /** Sets the sections collection. */
        public Builder sections(List<Section> value) { sections = value; return this; }
        /** Sets the sections in display order. */
        public Builder sections(Section... values) { sections = List.of(values); return this; }
        /** Creates the immutable page. */
        public Page build() { return new Page(id, title, eyebrow, description, heroImage, heroCaption, sections); }
    }
}
