package valthorne.website.export;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import valthorne.website.content.LandingContent;
import valthorne.website.content.LandingContent.Feature;
import valthorne.website.content.LandingContent.Resource;

/**
 * Exports Valthorne's single landing page entirely from Java-authored content.
 *
 * <p>The semantic document carries the same product copy and destinations as
 * the engine renderer. It remains readable without scripting and supplies the
 * native browser controls used by the compiled Java application. Java also
 * generates the stylesheet, cinematic artwork, and search-engine files.</p>
 *
 * <p>Run with Java 17 or later: {@code HtmlExporter output-directory revision}.</p>
 */
public final class HtmlExporter {
    public static final String PUBLIC_URL = "https://tehnewb.github.io/Valthorne/";

    private HtmlExporter() { }

    /** Writes documents without deleting output files or copying branding assets. */
    public static void main(String[] args) throws IOException {
        if (args.length != 2) throw new IllegalArgumentException("Usage: HtmlExporter output-directory revision");
        export(Path.of(args[0]), args[1]);
    }

    /** Exports one public route; retired routes deliberately receive the generated 404 page. */
    public static void export(Path output, String revision) throws IOException {
        if (!revision.matches("[a-zA-Z0-9._-]+")) throw new IllegalArgumentException("Invalid build revision");
        Files.createDirectories(output.resolve("assets"));
        write(output, "index.html", document(revision));
        write(output, "shell.css", BrowserStyles.css());
        write(output, "assets/world.svg", WorldArtwork.svg());
        write(output, "404.html", notFound(revision));
        write(output, ".nojekyll", "");
        write(output, "robots.txt", "User-agent: *\nAllow: /\nSitemap: " + PUBLIC_URL + "sitemap.xml\n");
        write(output, "sitemap.xml", "<?xml version=\"1.0\" encoding=\"UTF-8\"?><urlset xmlns=\"http://www.sitemaps.org/schemas/sitemap/0.9\"><url><loc>" + PUBLIC_URL + "</loc></url></urlset>\n");
        System.out.println("Exported one Java-authored landing page and its artwork.");
    }

    /** Builds the accessible landing document and the minimal compiled-application entry point. */
    public static String document(String revision) {
        StringBuilder html = new StringBuilder(14000);
        html.append("<!doctype html>\n<html lang=\"en\" data-page-id=\"index\"><head>");
        html.append("<meta charset=\"utf-8\"><meta name=\"viewport\" content=\"width=device-width,initial-scale=1\">");
        html.append("<title>Valthorne — ").append(escape(LandingContent.TITLE)).append("</title>");
        meta(html, "name", "description", LandingContent.DESCRIPTION);
        meta(html, "name", "theme-color", "#101114");
        meta(html, "name", "valthorne-build", revision);
        meta(html, "property", "og:title", "Valthorne — " + LandingContent.TITLE);
        meta(html, "property", "og:description", LandingContent.DESCRIPTION);
        meta(html, "property", "og:type", "website");
        meta(html, "property", "og:url", PUBLIC_URL);
        meta(html, "property", "og:image", PUBLIC_URL + "assets/banner.png");
        html.append("<link rel=\"canonical\" href=\"").append(PUBLIC_URL).append("\">");
        html.append("<link rel=\"icon\" href=\"assets/valthorne.png\" type=\"image/png\"><link rel=\"stylesheet\" href=\"shell.css?v=")
                .append(escape(revision)).append("\"></head>\n");
        html.append("""
                <body><a class="skip-link" href="?view=text#content">Skip to text content</a>
                <canvas id="scene" aria-hidden="true"></canvas><div id="scroll-space" aria-hidden="true"></div>
                <nav id="engine-links" aria-label="Page navigation and actions"></nav>
                <button id="back-top" type="button" aria-label="Back to top" title="Back to top" hidden>↑</button>
                """);
        html.append(semanticContent());
        html.append("""
                <footer id="access-bar"><span id="status" role="status">Valthorne · Java game engine</span><a id="view-toggle" href="?view=text">Text version</a>
                <button id="motion-toggle" type="button" aria-label="Pause motion" title="Pause motion"><span aria-hidden="true" class="pause-icon">Ⅱ</span><span aria-hidden="true" class="play-icon">▷</span><span>Motion</span></button>
                """);
        html.append("<a href=\"").append(escape(LandingContent.REPOSITORY)).append("\">GitHub ↗</a></footer>\n");
        html.append("<script type=\"module\" src=\"browser-host.js?v=").append(escape(revision)).append("\"></script>\n</body></html>\n");
        return html.toString();
    }

    /** Returns the complete text-mode document, using the renderer's same anchors and destinations. */
    public static String semanticContent() {
        StringBuilder html = new StringBuilder(11000);
        html.append("<main id=\"content\"><header class=\"masthead\"><div class=\"wrap\">");
        brand(html);
        html.append("<nav aria-label=\"Main navigation\"><a href=\"#engine\">Engine</a><a href=\"#resources\">Resources</a><a href=\"")
                .append(escape(LandingContent.REPOSITORY)).append("\">GitHub ↗</a></nav>");
        action(html, "Get started", "#start", true, false);
        html.append("</div></header>\n<header class=\"hero\">");
        html.append("<figure class=\"hero-media\"><img src=\"assets/world.svg\" alt=\"An imagined mountain world beneath a monumental stone arch.\" width=\"1920\" height=\"1080\" fetchpriority=\"high\"></figure>");
        html.append("<div class=\"wrap\"><p class=\"eyebrow\">").append(escape(LandingContent.EYEBROW)).append("</p><h1>")
                .append(escape(LandingContent.TITLE)).append("</h1><p class=\"hero-copy\">").append(escape(LandingContent.DESCRIPTION))
                .append("</p><div class=\"hero-actions\">");
        action(html, "Get started", LandingContent.GET_STARTED, true, true);
        action(html, "Explore engine", "#engine", false, false);
        html.append("</div><p class=\"hero-caption\">JAVA 25 &nbsp;·&nbsp; APACHE-2.0 &nbsp;·&nbsp; VERSION ")
                .append(escape(LandingContent.VERSION)).append("</p></div></header>\n");
        html.append("<section id=\"engine\"><div class=\"wrap\"><header class=\"section-intro\"><h2>")
                .append(escape(LandingContent.INTRO_TITLE)).append("</h2><p>").append(escape(LandingContent.INTRO_TEXT)).append("</p></header>");
        for (Feature feature : LandingContent.FEATURES) {
            html.append("<article class=\"feature\"><p class=\"feature-number\">").append(escape(feature.number()))
                    .append("</p><div><h3>").append(escape(feature.title())).append("</h3><p>").append(escape(feature.text())).append("</p>");
            textLink(html, "Explore " + feature.number().substring(feature.number().indexOf('/') + 1).trim().toLowerCase(java.util.Locale.ROOT), feature.href());
            html.append("</div></article>");
        }
        html.append("</div></section>\n<section id=\"resources\"><div class=\"wrap\"><header class=\"section-intro\"><h2>")
                .append(escape(LandingContent.RESOURCES_TITLE)).append("</h2><p>").append(escape(LandingContent.RESOURCES_TEXT))
                .append("</p></header><div class=\"resources\">");
        for (Resource resource : LandingContent.RESOURCES) {
            html.append("<article class=\"resource\"><h3>").append(escape(resource.title())).append("</h3><p>")
                    .append(escape(resource.text())).append("</p>");
            textLink(html, resource.label(), resource.href());
            html.append("</article>");
        }
        html.append("</div></div></section>\n<section id=\"start\"><div class=\"wrap\"><h2>")
                .append(escape(LandingContent.CTA_TITLE)).append("</h2><p>").append(escape(LandingContent.CTA_TEXT))
                .append("</p><div class=\"hero-actions\">");
        action(html, "Read the quick start", LandingContent.GET_STARTED, true, true);

        html.append("</div></div></section>\n<footer class=\"project-footer\"><div class=\"wrap\">");
        brand(html);
        html.append("<p>Created by Albert Beaupre.<br>Open source under <a href=\"").append(escape(LandingContent.LICENSE))
                .append("\">Apache-2.0</a>.</p></div></footer></main>\n");
        return html.toString();
    }

    private static void brand(StringBuilder html) {
        html.append("<a class=\"brand\" href=\"#content\" aria-label=\"Valthorne home\"><img src=\"assets/valthorne.png\" alt=\"\" width=\"27\" height=\"43\">Valthorne</a>");
    }

    private static void action(StringBuilder html, String label, String href, boolean primary, boolean arrow) {
        html.append("<a class=\"action").append(primary ? " primary" : "").append("\" href=\"")
                .append(escape(href)).append("\">").append(escape(label));
        if (arrow) html.append("<span aria-hidden=\"true\">↗</span>");
        html.append("</a>");
    }

    private static void textLink(StringBuilder html, String label, String href) {
        html.append("<a class=\"text-link\" href=\"").append(escape(href)).append("\">").append(escape(label))
                .append("<span aria-hidden=\"true\">↗</span></a>");
    }

    private static void meta(StringBuilder html, String attribute, String name, String value) {
        html.append("<meta ").append(attribute).append("=\"").append(name).append("\" content=\"").append(escape(value)).append("\">");
    }

    /** Treats all authored copy as text, including values placed in quoted HTML attributes. */
    private static String escape(String value) {
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }

    private static void write(Path output, String name, String value) throws IOException {
        Files.writeString(output.resolve(name), value, StandardCharsets.UTF_8);
    }

    /** Removed multipage routes remain honest missing pages with one clear way home. */
    private static String notFound(String revision) {
        return """
                <!doctype html><html lang="en"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"><meta name="robots" content="noindex"><title>Page not found — Valthorne</title><link rel="stylesheet" href="/Valthorne/shell.css?v=%s"><link rel="icon" href="/Valthorne/assets/valthorne.png"></head><body><main class="not-found wrap"><p class="eyebrow">404 / PAGE NOT FOUND</p><h1>Let's get you<br>back to building.</h1><p>This page is no longer available.</p><a class="action primary" href="/Valthorne/">Explore Valthorne <span aria-hidden="true">→</span></a></main></body></html>
                """.formatted(escape(revision));
    }
}
