package valthorne.website.export;

/**
 * Styles emitted by Java for the semantic document and native browser controls.
 * The palette matches WebsiteApplication; generated shell.css is a deployment
 * artifact, not a second authored stylesheet.
 */
public final class BrowserStyles {
    private BrowserStyles() { }

    /** Complete stylesheet shared by every exported route. */
    public static String css() {
        return """
            /* Browser companion to the Valthorne renderer. Keep color tokens aligned with WebsiteApplication. */
            @font-face {
              font-family: UrbanistWebsite;
              src: url('assets/fonts/Urbanist-Variable.ttf') format('truetype');
              font-style: normal;
              font-weight: 100 900;
              font-display: swap;
            }
            :root {
              color-scheme: dark;
              font-family: UrbanistWebsite, Arial, sans-serif;
              color: #fff;
              background: #141414;
              --ink: #fff;
              --muted: #c7c7cc;
              --link: #b99bff;
              --primary: #703bf7;
              --focus: #c9b4ff;
              --panel: #1a1a1a;
              --panel-alt: #222;
              --border: #686868;
            }
            * { box-sizing: border-box; }
            body { margin: 0; isolation: isolate; }
            .page-transition {
              position: fixed; inset: 0; width: 100vw; height: 100vh; z-index: 6;
              pointer-events: none; clip-path: inset(88px 0 0); overflow-anchor: none; background: #141414;
            }
            .page-transition canvas { display: block; width: 100%; height: 100%; }
            @media (max-width: 899px) { .page-transition { clip-path: inset(120px 0 0); } }
            a { color: var(--link); text-underline-offset: 4px; }
            a:hover { color: var(--ink); }
            a:focus-visible, input:focus-visible, button:focus-visible, #scene-interaction:focus-visible {
              outline: 3px solid var(--focus); outline-offset: 4px;
            }
            ::selection { color: #fff; background: #703bf7; }
            [hidden] { display: none !important; }

            /* Java draws the interface; native elements supply focus, links, selection, and text entry. */
            #scene { display: none; width: 100vw; height: 100vh; touch-action: pan-y; }
            #valthorne-window { visibility: hidden; }
            .engine-ready #scene { display: block; }
            .engine-ready #valthorne-window { visibility: visible; }
            #engine-links { display: none; position: fixed; inset: 0; pointer-events: none; z-index: 3; }
            .engine-ready #engine-links { display: block; }
            .engine-link {
              position: absolute; pointer-events: auto; color: transparent; text-decoration: none;
              overflow: hidden; white-space: nowrap; border-radius: 4px;
              transition: background-color .16s ease, box-shadow .16s ease;
            }
            .engine-link:link, .engine-link:visited, .engine-link:hover, .engine-link:active, .engine-link:focus-visible { color: transparent; }
            .engine-link:hover { background: #ffffff0d; box-shadow: inset 0 -2px 0 var(--link); }
            .engine-link.pill-action, .engine-link.primary-action { border-radius: 4px; }
            .engine-link.pill-action:hover { box-shadow: inset 0 0 0 1px var(--ink); }
            .engine-link.primary-action:hover { background: #ffffff1a; box-shadow: inset 0 0 0 1px #d7c9ff; }
            .engine-link:focus-visible { background: #ffffff0a; outline-offset: 3px; }
            .engine-link[role=button] { cursor: pointer; }
            .engine-link[aria-disabled=true] { cursor: default; }
            #engine-search {
              position: fixed; z-index: 4; height: 44px; padding: 0 16px; display: none;
              border: 1px solid var(--border); border-radius: 4px; background: var(--panel); color: var(--ink);
              font: 500 16px UrbanistWebsite, Arial, sans-serif; transition: border-color .16s ease;
            }
            #engine-search::placeholder { color: var(--muted); opacity: 1; }
            #engine-search:hover, #engine-search:focus { border-color: var(--link); }
            .engine-ready #engine-search { display: block; }
            #scroll-space { display: none; }
            .engine-ready #scroll-space { display: block; }

            /* Browser utilities occupy a dedicated footer, with unobtrusive, accessible motion controls. */
            #access-bar {
              display: flex; justify-content: space-between; flex-wrap: wrap; gap: 16px; align-items: center;
              max-width: 1240px; margin: 0 auto; padding: 18px 0 28px; font-size: 13px;
              border-top: 1px solid var(--border);
            }
            .engine-ready #access-bar { position: absolute; left: 22px; right: 22px; z-index: 4; }
            #status { color: var(--muted); margin-right: auto; }
            #access-bar a { white-space: nowrap; min-height: 32px; display: inline-flex; align-items: center; }
            #motion-toggle, #back-top {
              display: none; position: fixed; z-index: 5; right: 18px; width: 44px; height: 44px;
              border: 1px solid var(--border); border-radius: 4px; background: #1a1a1a; color: var(--ink);
              font: 18px/1 UrbanistWebsite, Arial, sans-serif; cursor: pointer; transition: border-color .16s ease, background .16s ease;
            }
            .engine-ready #motion-toggle, .engine-ready #back-top { display: block; }
            #motion-toggle { top: 22px; }
            #back-top { bottom: 20px; }
            #motion-toggle:hover, #back-top:hover { border-color: var(--link); background: #262626; }
            #motion-toggle:disabled { cursor: default; color: var(--muted); }
            #motion-toggle .play-icon, .motion-paused #motion-toggle .pause-icon { display: none; }
            .motion-paused #motion-toggle .play-icon { display: inline; }
            #scene-interaction { display: none; position: fixed; z-index: 2; cursor: grab; touch-action: pan-y; border-radius: 4px; }
            .engine-ready #scene-interaction { display: block; }
            #scene-interaction.dragging { cursor: grabbing; }
            #scene-interaction:focus-visible { outline-offset: -6px; }
            .skip-link { position: fixed; top: -100px; left: 16px; z-index: 10; padding: 12px 18px; border-radius: 4px; background: var(--primary); color: #fff; font-weight: 700; }
            .skip-link:focus { top: 12px; }

            /* Complete semantic document, also used when graphics are unavailable or text mode is selected. */
            #content { max-width: 1344px; margin: 0 auto; padding: 0 52px 64px; line-height: 1.65; text-align: left; }
            .masthead { min-height: 88px; display: flex; align-items: center; gap: 64px; border-bottom: 1px solid var(--border); }
            .brand { display: inline-flex; flex-shrink: 0; align-items: center; gap: 10px; color: #fff; text-decoration: none; font-size: 24px; font-weight: 750; letter-spacing: -.02em; }
            .brand img { width: 31px; height: 48px; object-fit: contain; }
            .masthead nav { display: flex; align-items: center; gap: 30px; margin-left: auto; }
            .masthead nav a { min-height: 44px; display: inline-flex; align-items: center; color: var(--muted); font-size: 16px; font-weight: 600; text-decoration: none; }
            .masthead nav a:hover, .masthead nav a[aria-current] { color: #fff; }
            .masthead nav a[aria-current] { box-shadow: inset 0 -2px 0 var(--link); }
            #content .page-hero { padding: 72px 0 20px; }
            #content .page-hero.has-media { display: grid; grid-template-columns: 1fr 1.1fr; gap: 52px; align-items: center; padding: 72px 0 32px; }
            #content h1 { font-size: clamp(44px, 5.1vw, 70px); font-weight: 650; line-height: 1.06; letter-spacing: -.035em; max-width: 900px; margin: 18px 0 24px; text-wrap: balance; white-space: pre-line; }
            #content h2 { font-size: clamp(30px, 3.25vw, 44px); font-weight: 600; line-height: 1.16; letter-spacing: -.025em; margin: 0 0 18px; max-width: 940px; text-wrap: balance; }
            #content h3 { font-size: 23px; font-weight: 600; line-height: 1.28; margin: 0 0 12px; letter-spacing: -.015em; }
            #content .eyebrow { margin: 0; color: var(--link); font-size: 12px; line-height: 1.5; font-weight: 700; letter-spacing: .12em; }
            #content .intro { max-width: 730px; margin: 0; font-size: 19px; line-height: 1.6; color: var(--muted); }
            #content .hero-actions { display: flex; flex-wrap: wrap; gap: 12px; margin-top: 28px; }
            .action { display: inline-flex; min-height: 48px; padding: 11px 22px; align-items: center; justify-content: center; background: var(--panel); border: 1px solid var(--border); border-radius: 4px; color: #fff; font-weight: 650; font-size: 16px; text-decoration: none; transition: background-color .16s ease, border-color .16s ease; }
            .action:hover { background: #262626; border-color: var(--ink); }
            .action.primary { background: var(--primary); border-color: #9364ff; }
            .action.primary:hover { background: #8050ff; border-color: #c9b4ff; }
            #content figure { margin: 0; }
            #content figure img { display: block; width: 100%; height: auto; border-radius: 4px; }
            #content .hero-media img { aspect-ratio: 1.25; object-fit: cover; border: 1px solid var(--border); }
            #content figcaption { margin-top: 12px; color: var(--muted); font-size: 13px; line-height: 1.5; }
            .brand-banner { display: block; width: min(100%, 440px); height: auto; margin: 20px 0 28px; }
            #content .project-identity { margin-top: 44px; padding: 24px 32px; background: var(--panel); border: 1px solid var(--border); border-radius: 4px; }
            #content .project-identity .brand-banner { margin: 0; width: min(100%, 400px); }
            #content section { margin-top: 68px; padding-top: 44px; border-top: 1px solid var(--border); }
            #content .section-heading { max-width: 840px; margin-bottom: 30px; }
            #content .section-heading > p { margin: 0; font-size: 17px; }
            #content .cards { display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); gap: 20px; }
            #content article { min-width: 0; padding: 24px; display: flex; flex-direction: column; background: var(--panel); border: 1px solid var(--border); border-radius: 4px; transition: border-color .16s ease; }
            #content article:hover { border-color: #999; }
            #content article p { margin: 0 0 18px; font-size: 16px; line-height: 1.65; }
            #content .card-category { margin: 0 0 10px; color: var(--muted); font-size: 11px; font-weight: 650; text-transform: uppercase; letter-spacing: .08em; }
            #content article img { width: 100%; aspect-ratio: 1.75; object-fit: cover; margin-bottom: 22px; border-radius: 2px; }
            #content .card-links { margin-top: auto; display: flex; flex-wrap: wrap; gap: 4px 18px; }
            #content .card-links a { min-height: 44px; display: inline-flex; align-items: center; font-size: 15px; font-weight: 650; text-decoration: none; }
            #content .card-links a:hover { text-decoration: underline; }
            #content .section-showcase .cards, #content .section-examples .cards { grid-template-columns: repeat(2, minmax(0, 1fr)); }
            #content .section-showcase article, #content .section-examples article:has(img) { padding: 0 0 24px; overflow: hidden; }
            #content .section-showcase article .card-copy, #content .section-showcase article .card-links,
            #content .section-examples article:has(img) .card-copy, #content .section-examples article:has(img) .card-links { padding-inline: 24px; }
            #content .section-showcase article img, #content .section-examples article img { border-radius: 0; }
            #content .section-rows .cards { grid-template-columns: 1fr; gap: 0; border-top: 1px solid var(--border); }
            #content .section-rows article { display: grid; grid-template-columns: minmax(0, 1fr) auto; align-items: center; gap: 32px; padding: 22px 0; border: 0; border-bottom: 1px solid var(--border); border-radius: 0; background: transparent; }
            #content .section-rows article h3 { font-size: 21px; margin-bottom: 6px; }
            #content .section-rows article p { max-width: 850px; margin-bottom: 0; }
            #content .section-rows article .card-category { margin-bottom: 7px; }
            #content .section-rows article .card-links { margin-top: 0; }
            #content .section-feature { display: grid; grid-template-columns: minmax(0, 1fr) minmax(0, 1.08fr); gap: 52px; align-items: start; }
            #content .section-feature .cards { grid-template-columns: 1fr; gap: 0; }
            #content .section-feature article { padding: 20px 0; border: 0; border-top: 1px solid var(--border); border-radius: 0; background: transparent; }
            #content .section-feature .section-media img { aspect-ratio: 1.15; object-fit: cover; border: 1px solid var(--border); }
            #content .section-media { margin-top: 28px; }
            #content .section-feature .section-media { margin-top: 0; }
            #content p { color: var(--muted); }
            #content pre { overflow: auto; text-align: left; background: #101010; border: 1px solid var(--border); border-radius: 4px; padding: 24px; font: 14px/1.75 Consolas, monospace; color: var(--ink); tab-size: 4; }
            #content .code-filename { margin: 0 0 -1px; padding: 12px 24px; background: var(--panel-alt); border: 1px solid var(--border); border-radius: 4px 4px 0 0; font: 13px/1.6 Consolas, monospace; }
            #content .code-filename + pre { margin-top: 0; border-radius: 0 0 4px 4px; }
            #content .code-filename ~ .cards { margin-top: 28px; }
            #content .lab-note { padding: 22px 24px; margin-bottom: 24px; border: 1px solid var(--border); border-radius: 4px; background: var(--panel); }
            #content .project-footer { display: flex; justify-content: space-between; align-items: center; gap: 24px; margin-top: 76px; padding-top: 30px; border-top: 1px solid var(--border); }
            #content .project-footer p { margin: 0; max-width: 760px; }
            .engine-ready #content { position: absolute; left: 0; top: 0; width: 1px; height: 1px; padding: 0; overflow: hidden; clip-path: inset(50%); white-space: nowrap; }
            @media (max-width: 1000px) {
              #content { padding-inline: 32px; }
              .masthead { gap: 28px; }
              .masthead nav { gap: 22px; }
              #content .page-hero.has-media, #content .section-feature { gap: 32px; }
              #content .cards { grid-template-columns: repeat(2, minmax(0, 1fr)); }
              #access-bar { margin-inline: 32px; }
              .engine-ready #access-bar { margin-inline: 0; }
            }
            @media (max-width: 760px) {
              #content { padding: 0 22px 52px; }
              .masthead { min-height: 120px; flex-wrap: wrap; align-content: center; gap: 8px; padding: 10px 0; }
              .brand { font-size: 22px; }
              .brand img { width: 28px; height: 40px; }
              .masthead nav { width: 100%; margin: 0; gap: 0; justify-content: space-between; }
              .masthead nav a { font-size: 14px; }
              #content .page-hero, #content .page-hero.has-media { padding-top: 44px; }
              #content .page-hero.has-media, #content .section-feature { grid-template-columns: 1fr; }
              #content .hero-media img { aspect-ratio: 1.55; }
              #content h1 { font-size: clamp(42px, 9vw, 60px); }
              #content h2 { font-size: 32px; }
              #content .intro { font-size: 18px; }
              #content section { margin-top: 44px; padding-top: 30px; }
              #content .cards, #content .section-showcase .cards, #content .section-examples .cards { grid-template-columns: 1fr; }
              #content .section-rows article { grid-template-columns: 1fr; gap: 12px; }
              #content .section-rows .card-links { gap: 8px; }
              #content .section-feature .section-media img { aspect-ratio: 1.5; }
              #content .project-footer { align-items: start; flex-direction: column; gap: 14px; }
              #content pre { font-size: 13px; padding: 20px 16px; }
              #content .code-filename { padding-inline: 16px; }
              #access-bar { margin-inline: 22px; gap: 8px 18px; }
              .engine-ready #access-bar { padding-right: 58px; }
              #status { flex-basis: 100%; }
              #motion-toggle, #back-top { right: 14px; }
              #motion-toggle { top: 18px; }
            }
            .motion-paused *, .motion-paused *::before, .motion-paused *::after { transition-duration: 0s !important; animation: none !important; scroll-behavior: auto !important; }
            @media (prefers-reduced-motion: reduce) { *, *::before, *::after { transition-duration: 0s !important; animation: none !important; scroll-behavior: auto !important; } }
            """;
    }
}
