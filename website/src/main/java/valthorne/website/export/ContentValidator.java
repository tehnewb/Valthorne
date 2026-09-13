package valthorne.website.export;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import valthorne.website.content.LandingContent;

/** Checks the single Java landing page's content, external destinations, and required branding. */
public final class ContentValidator {
    private ContentValidator() { }

    /** Run with the website asset directory as its sole argument. */
    public static void main(String[] args) throws IOException {
        if (args.length != 1) throw new IllegalArgumentException("Usage: ContentValidator asset-directory");
        validate(Path.of(args[0]));
    }

    /** Fails packaging if the compact landing content or its dependencies are incomplete. */
    public static void validate(Path assets) throws IOException {
        for (String text : List.of(LandingContent.TITLE, LandingContent.DESCRIPTION, LandingContent.INTRO_TITLE,
                LandingContent.INTRO_TEXT, LandingContent.RESOURCES_TITLE, LandingContent.RESOURCES_TEXT,
                LandingContent.CTA_TITLE, LandingContent.CTA_TEXT)) requireText(text);
        if (LandingContent.FEATURES.size() != 3 || LandingContent.RESOURCES.size() != 3)
            throw new IllegalStateException("The landing page requires three capabilities and three resources.");
        for (LandingContent.Feature feature : LandingContent.FEATURES) {
            requireText(feature.number()); requireText(feature.title()); requireText(feature.text()); link(feature.href());
        }
        for (LandingContent.Resource resource : LandingContent.RESOURCES) {
            requireText(resource.title()); requireText(resource.text()); requireText(resource.label()); link(resource.href());
        }
        link(LandingContent.REPOSITORY); link(LandingContent.DOCUMENTATION); link(LandingContent.GET_STARTED);
        link(LandingContent.COMMUNITY); link(LandingContent.LICENSE);
        image(assets, "valthorne.png"); image(assets, "banner.png"); image(assets, "fonts/Urbanist-Variable.ttf");
        // Hero artwork is generated during export, so it is deliberately not a checked-in asset requirement.
        if (!WorldArtwork.svg().contains("<svg")) throw new IllegalStateException("Missing generated world artwork");
        System.out.println("Validated one landing page, three capabilities, three resources, and required branding.");
    }

    private static void requireText(String value) {
        if (value == null || value.isBlank()) throw new IllegalStateException("Incomplete landing-page text");
    }

    private static void image(Path assets, String name) throws IOException {
        Path root = assets.toAbsolutePath().normalize(), file = root.resolve(name).normalize();
        if (!file.startsWith(root) || !Files.isRegularFile(file)) throw new IOException("Missing website asset: " + name);
    }

    private static void link(String href) {
        URI uri = URI.create(href);
        if (!"https".equals(uri.getScheme()) || uri.getHost() == null)
            throw new IllegalStateException("Invalid external website link: " + href);
    }
}
