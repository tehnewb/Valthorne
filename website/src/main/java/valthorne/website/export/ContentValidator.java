package valthorne.website.export;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;
import valthorne.website.content.Card;
import valthorne.website.content.Page;
import valthorne.website.content.Section;
import valthorne.website.content.WebsiteContent;

/** Validates Java-authored routes, content links, and referenced media before packaging. */
public final class ContentValidator {
    private ContentValidator() { }

    /** Run with the website asset directory as its sole argument. */
    public static void main(String[] args) throws IOException {
        if (args.length != 1) throw new IllegalArgumentException("Usage: ContentValidator asset-directory");
        validate(Path.of(args[0]));
    }

    /** Fails the build on incomplete pages, duplicate routes, broken local links, or missing assets. */
    public static void validate(Path assets) throws IOException {
        Set<String> routes = new HashSet<>();
        for (Page page : WebsiteContent.pages()) {
            if (!page.id().matches("[a-z][a-z0-9-]*") || !routes.add(page.id() + ".html"))
                throw new IllegalStateException("Invalid or duplicate website route: " + page.id());
        }
        int links = 0;
        for (Page page : WebsiteContent.pages()) {
            if (page.title().isBlank() || page.description().isBlank() || page.sections().isEmpty())
                throw new IllegalStateException("Incomplete website page: " + page.id());
            image(assets, page.heroImage());
            for (Section section : page.sections()) {
                if (section.title().isBlank()) throw new IllegalStateException("Untitled section: " + page.id());
                image(assets, section.image());
                for (Card card : section.cards()) {
                    if (card.title().isBlank() || card.text().isBlank() || card.label().isBlank() || card.href().isBlank())
                        throw new IllegalStateException("Incomplete card: " + page.id() + "/" + card.title());
                    link(routes, card.href()); links++;
                    if (!card.guide().isEmpty()) { link(routes, card.guide()); links++; }
                    image(assets, card.image());
                }
            }
        }
        System.out.println("Validated " + WebsiteContent.pages().size() + " Java-authored pages, " + links + " links, and referenced media.");
    }

    private static void image(Path assets, String name) throws IOException {
        if (name.isEmpty()) return;
        Path root = assets.toAbsolutePath().normalize();
        Path file = root.resolve(name).normalize();
        if (!file.startsWith(root) || !Files.isRegularFile(file)) throw new IOException("Missing or invalid website image: " + name);
    }

    private static void link(Set<String> routes, String href) {
        URI uri = URI.create(href);
        if (uri.isAbsolute()) {
            if (!uri.getScheme().equals("https") || uri.getHost() == null)
                throw new IllegalStateException("Invalid external website link: " + href);
        } else if (!href.startsWith("?") && !routes.contains(uri.getPath())) {
            throw new IllegalStateException("Unknown local website link: " + href);
        }
    }
}
