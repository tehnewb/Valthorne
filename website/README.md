# Valthorne website

Valthorne's public site is a single landing page at
[tehnewb.github.io/Valthorne](https://tehnewb.github.io/Valthorne/).
The website is authored in Java: content, responsive layout, drawing, animation,
section navigation, accessible HTML, and the browser stylesheet all come from
Java source. TeaVM compiles the application for the browser. The web-port adapter
connects the compiled program to browser graphics and native platform operations.

The page introduces the engine, points readers to its documentation and source,
and provides a direct path to getting started. Navigation uses the sections
`#engine`, `#resources`, and `#start` within the same page.

## Structure and visual identity

The landing page uses a compact dark navigation bar, a large product statement,
a substantial landscape illustration, and generously spaced feature and resource
sections. Unity's product-page hierarchy informs the composition. The visual
content and copy are Valthorne's own.

The original logo and banner retain their proportions and colors. Urbanist is
self-hosted from `assets/fonts/Urbanist-Variable.ttf`; its license and pinned
source are recorded in [assets/fonts/README.md](assets/fonts/README.md).
The Java controller loads the font before measuring or drawing text, so the
same family and weights are used for layout and painting.

`WorldArtwork` generates `assets/world.svg` during packaging. It is original
procedural artwork: a stone arch, rocky terrain, and layered mountains. It is
an illustration, not a screenshot or a claim about a finished Valthorne game.

## Package and preview

Packaging requires Node.js 24 and JDK 17 or later. It compiles the standalone
Java exporter and uses the checked-in browser runtime; packaging does not
require Gradle, npm installation, credentials, or network access.

```sh
node website/tools/site.mjs build
node website/tools/site.mjs serve
```

Open **http://127.0.0.1:8097/Valthorne/**. Set `PORT` to use another local port.
The repository prefix matches GitHub Pages. Open the site over HTTP; loading
an HTML file directly does not support the browser modules and font loading.

Publish only `website/dist/`. The exporter produces the landing document,
stylesheet, artwork, metadata, sitemap, and 404 page. Packaging clears that output
directory before writing the new distribution, so retired pages and assets do
not remain in later deployments. `dist/`, `build/`, and `node_modules/` are ignored.

## Edit the Java source

| Source | Responsibility |
| --- | --- |
| `content/LandingContent.java` | Landing-page copy, capabilities, resource links, and getting-started links |
| `WebsiteApplication.java` | Responsive page geometry and Valthorne drawing |
| `WebsiteController.java` | Native controls, fragment navigation, browser history, motion preferences, and frame scheduling |
| `BrowserBridge.java` | Java facade used by the painter |
| `export/WorldArtwork.java` | Original SVG landscape generation |
| `export/HtmlExporter.java` | Semantic HTML, metadata, sitemap, and 404 output |
| `export/BrowserStyles.java` | Native-control and text-view stylesheet generation |
| `browser/BrowserDom.java`, `browser/BrowserPort.java` | Thin browser and web-port bindings |

These paths are relative to `src/main/java/valthorne/website/`. Keep the semantic
content and visual painter in agreement when changing the page. Java content is
the source of truth; do not edit files inside `dist/`.

The navigation links are ordinary fragment URLs. Java handles smooth scrolling
and history while preserving the engine and drawing surface. Back and Forward
return to the selected section; direct fragment URLs also work. Native HTML
links provide keyboard focus and normal browser gestures. The complete semantic
page is available through **Text version**, when JavaScript is disabled, or when
graphics initialization fails.

Motion is finite. Entrance movement settles completely, idle pages stop drawing,
and the operating system's reduced-motion setting is respected. A footer control
stores the visitor's motion preference for the browser session.

## Rebuild the browser runtime

The web target remains development code in `portable/`, separate from the stable
Maven library. This module checks in a compiled UI runtime so the website can be
packaged without that development checkout. **Recompiling the browser application
requires the checkout containing `portable/`; the published desktop artifact
alone is insufficient.**

With that checkout, Java 25, and the portable web npm dependencies installed:

```sh
./gradlew -p portable :web:webDist '-PapplicationMain=valthorne.website.WebsiteApplication' '-PapplicationSources=website/src/main/java'
node website/tools/site.mjs capture-runtime
node website/tools/site.mjs build
```

On Windows use `./gradlew.bat` and retain the quotes around the `-P` arguments.
This selects the website application for the portable module's generated output.
Run the usual portable build afterward to select a different application.

Commit changed Java source and the captured runtime together. The manifest
checks source fingerprints and runtime SHA-256 digests; stale compiled Java is
a build error. Update browser backends upstream, then recapture them rather than
patching the website snapshot alone. Keep all bundled license copies.

GitHub Actions compiles the standalone exporter, verifies the runtime snapshot,
and packages the site. It does not rebuild the browser application through
TeaVM. Replace the snapshot arrangement when the web target is versioned and
available to consumers.

## Browser verification

Playwright is the only npm development dependency:

```sh
npm ci --prefix website
npx --prefix website playwright install chromium
node website/tools/site.mjs serve
# In another terminal:
node website/tools/verify.mjs
node website/tools/verify-navigation.mjs
```

Use `TEST_BROWSER=chrome` or `TEST_BROWSER=msedge` for an installed browser.
Set `SITE_URL` to verify another served deployment. PowerShell uses environment
assignments such as `$env:TEST_BROWSER = 'chrome'`.

Checks cover desktop, phone, and short viewports; visible engine content;
keyboard and section navigation; Back/Forward and direct fragment links; finite
idle behavior; motion preferences; text and no-JavaScript views; and graphics
fallback. Removed routes and old assets are checked for 404 responses. Reports
and screenshots go to the ignored `website/build/` directory. There is no test
source folder. The Pages workflow requires the Chromium checks before deployment;
that does not imply every browser has been verified.

## GitHub Pages

[.github/workflows/pages.yml](../.github/workflows/pages.yml) builds and verifies
changes to the website or workflow. Pull requests verify without deploying.
Successful pushes to `main` publish `website/dist/` through the GitHub Pages
environment. Configure the repository's Pages source as **GitHub Actions**.

Assets use relative URLs and work under `/Valthorne/`. Canonical metadata and
the sitemap use the public URL. If the repository is renamed, update
`HtmlExporter.PUBLIC_URL`, the preview prefix, the 404 home link, and the workflow
preview URL together. No application server, external font service, analytics,
cookies, or cross-origin isolation headers are required.

## Licenses

Website source and generated artwork use the repository's Apache-2.0 license.
Branding comes from `images/logo-transparent.png` and `images/banner.png`.
Third-party fonts and browser-runtime dependencies retain their individual
licenses. See [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md).
