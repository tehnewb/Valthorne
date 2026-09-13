package valthorne.website.export;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import valthorne.website.content.Card;
import valthorne.website.content.Page;
import valthorne.website.content.Section;
import valthorne.website.content.WebsiteContent;

/**
 * Exports the Java-authored website as complete, accessible static documents.
 *
 * <p>The generated HTML remains useful without JavaScript and gives search
 * engines, assistive technology, and text-mode visitors the same content as the
 * canvas application. Browser-host JavaScript is limited to starting the Java
 * application; no application logic is embedded in these documents.</p>
 *
 * <p>Run with Java 17 or later: {@code HtmlExporter output-directory revision}.
 * This exporter has no dependency on the engine, TeaVM, or a JSON parser.</p>
 */
public final class HtmlExporter {
    /** Canonical public address used by social metadata and search engines. */
    public static final String PUBLIC_URL = "https://tehnewb.github.io/Valthorne/";
    private static final String[][] NAVIGATION = {
        {"engine", "Engine"}, {"examples", "Demos"}, {"docs", "Docs"},
        {"start", "Get started"}, {"lab", "Lab"}, {"about", "About"}
    };

    private HtmlExporter() { }

    /** Writes deployment documents without copying assets or deleting any files. */
    public static void main(String[] args) throws IOException {
        if (args.length != 2) throw new IllegalArgumentException("Usage: HtmlExporter output-directory revision");
        export(Path.of(args[0]), args[1]);
    }

    /** Writes all public pages, native-control styles, and search-engine support files. */
    public static void export(Path output, String revision) throws IOException {
        if (!revision.matches("[a-zA-Z0-9._-]+")) throw new IllegalArgumentException("Invalid build revision");
        Files.createDirectories(output);
        for (Page page : WebsiteContent.pages()) write(output, page.id() + ".html", document(page, revision));
        write(output, "shell.css", BrowserStyles.css());
        write(output, "404.html", notFound(revision));
        write(output, ".nojekyll", "");
        write(output, "robots.txt", "User-agent: *\nAllow: /\nSitemap: " + PUBLIC_URL + "sitemap.xml\n");
        StringBuilder sitemap = new StringBuilder("<?xml version=\"1.0\" encoding=\"UTF-8\"?><urlset xmlns=\"http://www.sitemaps.org/schemas/sitemap/0.9\">");
        for (Page page : WebsiteContent.pages()) sitemap.append("<url><loc>").append(canonical(page)).append("</loc></url>");
        write(output, "sitemap.xml", sitemap.append("</urlset>\n").toString());
        System.out.println("Exported " + WebsiteContent.pages().size() + " Java-authored website pages.");
    }

    /** Builds one route, including stable page/build markers for Java navigation. */
    public static String document(Page page, String revision) {
        String title = page.id().equals("index") ? "Valthorne — Java game engine" : page.title() + " — Valthorne";
        StringBuilder html = new StringBuilder(16000);
        html.append("<!doctype html>\n<html lang=\"en\" data-page-id=\"").append(escape(page.id())).append("\"><head>");
        html.append("""
                <meta charset="utf-8"><meta name="viewport" content="width=device-width, initial-scale=1">
                """);
        html.append("<title>").append(escape(title)).append("</title>");
        meta(html, "name", "description", page.description());
        meta(html, "name", "theme-color", "#141414");
        meta(html, "name", "valthorne-build", revision);
        html.append("<link rel=\"canonical\" href=\"").append(canonical(page)).append("\">");
        meta(html, "property", "og:title", page.title());
        meta(html, "property", "og:description", page.description());
        meta(html, "property", "og:type", "website");
        meta(html, "property", "og:url", canonical(page));
        meta(html, "property", "og:image", PUBLIC_URL + "assets/banner.png");
        html.append("<link rel=\"icon\" href=\"assets/valthorne.png\" type=\"image/png\"><link rel=\"stylesheet\" href=\"shell.css?v=")
                .append(escape(revision)).append("\"></head>\n");
        html.append("""
                <body><a class="skip-link" href="?view=text#content">Skip to text content</a>
                <canvas id="scene" aria-hidden="true"></canvas><div id="scroll-space" aria-hidden="true"></div>
                <nav id="engine-links" aria-label="Engine view navigation and actions"></nav>
                <div id="scene-interaction" role="group" tabindex="0" aria-label="Interactive geometry lab. Drag to orbit. Use arrow keys to rotate, or Home to reset the view." hidden></div>
                <input id="engine-search" type="search" aria-label="Search this collection" placeholder="Search titles and systems…" hidden>
                <button id="motion-toggle" type="button" aria-label="Pause motion" title="Pause motion"><span aria-hidden="true" class="pause-icon">Ⅱ</span><span aria-hidden="true" class="play-icon">▷</span></button>
                <button id="back-top" type="button" aria-label="Back to top" title="Back to top" hidden>↑</button>
                """);
        html.append(semanticContent(page));
        html.append("""
                <footer id="access-bar"><span id="status" role="status">Loading Valthorne…</span><a id="view-toggle" href="?view=text">Text version</a><a href="https://github.com/tehnewb/Valthorne">GitHub ↗</a></footer>
                """);
        html.append("<script type=\"module\" src=\"browser-host.js?v=").append(escape(revision)).append("\"></script>\n</body></html>\n");
        return html.toString();
    }

    /** Complete semantic page content; IDs and classes match the browser's native-control layer. */
    public static String semanticContent(Page page) {
        StringBuilder html = new StringBuilder(12000);
        html.append("<main id=\"content\"><header class=\"masthead\"><a class=\"brand\" href=\"index.html\"");
        if (page.id().equals("index")) html.append(" aria-current=\"page\"");
        html.append("\"><img src=\"assets/valthorne.png\" alt=\"Valthorne logo\" width=\"31\" height=\"48\">Valthorne</a><nav aria-label=\"Main navigation\">");
        for (String[] link : NAVIGATION) {
            html.append("<a href=\"").append(link[0]).append(".html\"");
            if (page.id().equals(link[0])) html.append(" aria-current=\"page\"");
            html.append('>').append(link[1]).append("</a>");
        }
        html.append("</nav></header>\n<header class=\"page-hero");
        if (!page.heroImage().isEmpty()) html.append(" has-media");
        html.append("\"><div class=\"hero-copy\"><p class=\"eyebrow\">").append(escape(page.eyebrow()))
                .append("</p><h1>").append(escape(page.title())).append("</h1><p class=\"intro\">")
                .append(escape(page.description())).append("</p>");
        if (page.id().equals("index")) html.append("<div class=\"hero-actions\"><a class=\"action primary\" href=\"start.html\">Start building</a><a class=\"action\" href=\"examples.html\">Explore demos</a></div>");
        html.append("</div>");
        if (!page.heroImage().isEmpty()) media(html, page.heroImage(), page.heroCaption(), "hero-media");
        html.append("</header>\n");
        if (page.id().equals("about")) html.append("<div class=\"project-identity\"><img class=\"brand-banner\" src=\"assets/banner.png\" alt=\"Valthorne — original gold lettering and blue flame\" width=\"1511\" height=\"623\"></div>");
        for (int index = 0; index < page.sections().size(); index++) section(html, page, page.sections().get(index), index);
        html.append("<div class=\"project-footer\"><p>Created by Albert Beaupre. Valthorne is open source under Apache-2.0.</p><a href=\"about.html\">About the project →</a></div></main>\n");
        return html.toString();
    }

    private static void section(StringBuilder html, Page page, Section section, int index) {
        String layout = section.layout();
        if (layout.isEmpty()) layout = switch (section.catalog()) { case "guides" -> "rows"; case "examples" -> "examples"; default -> "cards"; };
        html.append("<section class=\"section-").append(escape(layout)).append("\"><div class=\"section-copy\"><header class=\"section-heading\"><h2>")
                .append(escape(section.title())).append("</h2>");
        if (!section.description().isEmpty()) html.append("<p>").append(escape(section.description())).append("</p>");
        html.append("</header>\n");
        if (!section.code().isEmpty()) html.append("<p class=\"code-filename\">").append(escape(section.filename()))
                .append("</p><pre id=\"code-").append(index).append("\" tabindex=\"0\"><code>")
                .append(escape(section.code())).append("</code></pre>");
        if (section.lab()) html.append("<p class=\"lab-note\">The interactive scene is available in the <a href=\"").append(escape(page.id()))
                .append(".html\">engine view</a>. Drag to orbit, use the arrow keys to rotate, or pause playback. System reduced-motion settings are respected.</p>");
        if (!section.cards().isEmpty()) {
            html.append("<div class=\"cards\">");
            for (Card card : section.cards()) card(html, card);
            html.append("</div>");
        }
        html.append("</div>");
        if (!section.image().isEmpty()) media(html, section.image(), section.imageCaption(), "section-media");
        html.append("</section>\n");
    }

    private static void card(StringBuilder html, Card card) {
        html.append("<article>");
        if (!card.image().isEmpty()) html.append("<img src=\"assets/").append(escape(card.image())).append("\" alt=\"")
                .append(escape(card.title())).append(" running in Valthorne\" loading=\"lazy\" width=\"800\" height=\"460\">");
        html.append("<div class=\"card-copy\">");
        if (!card.category().isEmpty()) html.append("<p class=\"card-category\">").append(escape(card.category())).append("</p>");
        html.append("<h3>").append(escape(card.title())).append("</h3><p>").append(escape(card.text()))
                .append("</p></div><div class=\"card-links\"><a href=\"").append(escape(card.href())).append("\">")
                .append(escape(card.label())).append(" <span aria-hidden=\"true\">&nbsp;→</span></a>");
        if (!card.guide().isEmpty()) html.append("<a href=\"").append(escape(card.guide()))
                .append("\">Controls and source <span aria-hidden=\"true\">&nbsp;↗</span></a>");
        html.append("</div></article>");
    }

    private static void media(StringBuilder html, String file, String caption, String className) {
        html.append("<figure class=\"").append(className).append("\"><img src=\"assets/").append(escape(file))
                .append("\" alt=\"").append(escape(caption.isEmpty() ? "A scene rendered with Valthorne" : caption))
                .append("\" width=\"800\" height=\"460\"")
                .append(className.equals("hero-media") ? " fetchpriority=\"high\"" : " loading=\"lazy\"").append('>');
        if (!caption.isEmpty()) html.append("<figcaption>").append(escape(caption)).append("</figcaption>");
        html.append("</figure>");
    }

    private static void meta(StringBuilder html, String attribute, String name, String content) {
        html.append("<meta ").append(attribute).append("=\"").append(name).append("\" content=\"").append(escape(content)).append("\">");
    }

    private static String canonical(Page page) { return PUBLIC_URL + (page.id().equals("index") ? "" : page.id() + ".html"); }

    /** Escapes both HTML text and double-quoted attribute values without interpreting content as markup. */
    private static String escape(String value) {
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }

    private static void write(Path output, String name, String content) throws IOException {
        Files.writeString(output.resolve(name), content, StandardCharsets.UTF_8);
    }

    private static String notFound(String revision) {
        return """
                <!doctype html><html lang="en"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"><title>Page not found — Valthorne</title><link rel="stylesheet" href="/Valthorne/shell.css?v=%s"><link rel="icon" href="/Valthorne/assets/valthorne.png"></head><body><main id="content"><img class="brand-banner" src="/Valthorne/assets/banner.png" alt="Valthorne" width="1511" height="623"><div class="eyebrow">404 / PAGE NOT FOUND</div><h1>The page could not be found.</h1><p>Check the address, or continue exploring the engine.</p><a href="/Valthorne/">Return to Valthorne</a></main></body></html>
                """.formatted(escape(revision));
    }
}
