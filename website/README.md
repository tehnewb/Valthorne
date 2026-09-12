# Valthorne website

The public website is a **Java Valthorne application**, compiled to JavaScript
with TeaVM and hosted at **https://tehnewb.github.io/Valthorne/**.
It contains the engine overview, platform requirements, ten demo downloads,
47 system guides, integration instructions, an interactive 3D illustration, and
project links.

## Visual identity

The website follows an editorial product layout informed by Unity's hierarchy
and the supplied Urbanist, charcoal, and purple design references. A centered
content column, capped at 1,240 pixels in the Java view, provides a consistent
left edge for headings, paragraphs, actions, and cards. The homepage pairs a
large product statement with an actual lighting-studio capture. Feature sections,
image-led showcases, compact resource rows, and code panels use distinct layouts
suited to their content; narrow screens stack these layouts into one column.

The original `images/logo-transparent.png` identifies the navigation and browser
icon. The original `images/banner.png` appears in the footer. Both retain their
transparency, proportions, and colors. Flat charcoal surfaces, restrained purple
actions, bright text, and four-pixel corners frame the product imagery. The
background is plain; no atmospheric painter, stars, grid, or glow is used.

Urbanist is served locally from `assets/fonts/Urbanist-Variable.ttf`, with its
SIL OFL license and pinned source documented in [assets/fonts/README.md](assets/fonts/README.md).
The host loads it before Java measures or draws text. The Java aliases use
weight 400 for body copy (`default`), 600 for titles and controls (`ui-medium`),
and 700 for display headings (`display`). The HTML companion uses the same
variable family, `UrbanistWebsite`. Code uses the system monospace family.

| Role | Color | Measured contrast |
| --- | --- | --- |
| Page background | `#141414` | — |
| Panel surface | `#1a1a1a` | — |
| Primary text | `#ffffff` | 17.40:1 against the panel |
| Secondary text | `#c7c7cc` | 10.33:1 against the panel |
| Primary action | `#703bf7` | 5.74:1 with white button text |
| Link purple | `#b99bff` | 7.62:1 against the panel |
| Opaque control border | `#686868` | 3.12:1 against the panel |

These measurements describe the listed palette pairs, not a claim of complete
WCAG conformance. Input placeholders retain the full secondary-text color, and
keyboard focus uses a pale purple outline outside controls. Reveals never
reduce text opacity.

Copy and incomplete card rows follow the left edge of the content column. Code
retains its original indentation for readability and copying. The HTML companion
follows the same visual identity. Demo cards without a representative capture
use a labeled typographic panel. These panels are not presented as screenshots;
the FPS demo remains available without the old incorrect-hand screenshot.

The documentation page puts installation, migration, and platform quick links
before the searchable guide catalog. Empty searches offer a single action to
clear the query and filters. Code panels show filenames, use 13-pixel monospace
text on phones, and confirm copying beside the action. The text-version and
GitHub links sit in the footer, with a separate return-to-top button on long
pages.

[DESIGN_NOTES.md](DESIGN_NOTES.md) records the review of seven official engine
websites and the rationale for Valthorne's layout, motion, and scene controls.

## Architecture

| Part | Responsibility |
| --- | --- |
| `src/main/java/valthorne/website/WebsiteApplication.java` | Valthorne application lifecycle, responsive layout, text wrapping, cards, navigation painting, and placement of the interactive scene |
| `src/main/java/valthorne/website/CrystalScene.java` | Procedural meshes, perspective projection, face lighting, and depth-sorted Canvas2D painting for the crystal illustration |
| `src/main/java/valthorne/website/BrowserBridge.java` | Small TeaVM bridge for content, scrolling, image painting, measurements, motion state, scene input, and semantic link placement |
| `content.json`, `guides.json` | Shared content for the engine view and complete semantic HTML companion |
| `site-host.js` | Frame scheduling, finite reveals, motion preferences, scene interaction, native links, search input, clipboard, browser history, and rendering failure recovery |
| `boot.js`, `shell.css` | Engine startup and accessible HTML presentation |
| `runtime/` | Verified, compiled website UI runtime and bundled dependency notices |
| `assets/` | Original branding, real engine screenshots, and the self-hosted Urbanist font; no runnable examples or game asset trees |
| `tools/site.mjs` | Validation, static packaging, preview, guide import, and runtime capture |
| `tools/verify.mjs` | Browser acceptance checks; output is written to ignored `build/` |

`JGL` owns the normal init/update/render/dispose lifecycle. `UIRoot` and
`NanoContainer` provide the engine UI context, and `Canvas2D` paints the visible
site. The host uses the portable vector, WebGL, and Yoga backends. It does not
initialize Filament, Jolt, audio, or the engine's full 3D scene renderer.

The browser retains ordinary scrolling, links, focus, and native text input.
Each page has its own URL; a search query is preserved in `?q=...`. A complete
HTML document is generated from the same content. **Text version** switches to
`?view=text`, which does not load the engine. The HTML is also readable without
JavaScript or when engine startup fails. Code copying uses the original source,
not the visually wrapped canvas text.

## Motion and the interactive scene

The entrance and section/card reveals last 600 milliseconds and play once as
content enters the viewport. The layout stays fixed while the drawing moves a
short distance at full opacity; its native links follow the same offset. This
translation-only reveal preserves text contrast throughout. CSS supplies
brief hover and focus feedback. Content and navigation remain available during
the transitions. The plain background adds no animation work.

The standalone lab contains a blue faceted crystal, orbital bands, and a layered
pedestal generated in Java. The homepage presents real engine captures.
`CrystalScene` rotates
three-dimensional mesh vertices, computes face lighting, applies a perspective
camera, and sorts faces before `Canvas2D` paints them. Its triangle pool is
reused across frames. This is a custom UI illustration, not a demonstration or
benchmark of the engine's full 3D renderer or physics system. It adds no model,
texture, physics, or third-party rendering downloads.

Drag or swipe horizontally to orbit the scene. Focus its interaction region and
use the arrow keys to rotate; **Home** or **Reset view** restores the initial
camera. Vertical touch scrolling remains available. **Pause rotation** stops the
scene's automatic movement, and **Play rotation** resumes it.

The header's motion button pauses decorative motion throughout the site. This
choice persists across pages in the current tab through optional
`sessionStorage`; storage access is not required. An operating-system
reduced-motion preference disables automatic rotation, reveals, and CSS
transitions. Manual orbit and reset controls remain usable in that mode.

Frames are requested for viewport changes, scrolling, image loads, and actions.
Automatic scene animation runs only while the scene is visible and motion is
enabled. Hidden tabs stop scheduling frames. The scheduler caps continuous scene
redraws at 30 frames per second and finite reveals at 60; direct interaction
requests an immediate redraw. Once reveals settle and no animated scene is in
view, the canvas retains its last frame. Device pixel ratio is capped at two to
keep high-density text sharp without unbounded surface allocation.

## Build and preview

Node.js 24 is sufficient to package the checked-in runtime. No npm installation,
Java installation, Gradle run, credentials, or network access is needed for this:

```sh
node website/tools/site.mjs build
node website/tools/site.mjs serve
```

Open **http://127.0.0.1:8097/Valthorne/**. The preview deliberately includes the
repository prefix used by Pages. Set `PORT` to use a different local port.
Opening an HTML file directly is unsupported because JavaScript modules and
font loading require HTTP. Publish only `website/dist/`.

The package contains the original branding, selected engine captures, and the
bundled UI runtime. Procedural scene geometry and motion use that same runtime;
the published website does not load external fonts or a separate 3D framework.

The checker validates content references, script syntax, runtime checksums, and
the Java source fingerprints. `dist/`, `build/`, and `node_modules/` are ignored.

## Editing content

Edit `content.json` for page copy, code snippets, and feature cards. Edit
`guides.json` for the manual directory. The explicit import command below reads
the **committed** manual index so local engine work cannot silently alter the
published website:

```sh
node website/tools/site.mjs update-guides
node website/tools/site.mjs build
```

The demo catalog and release filenames are maintained in `demoCards()` in
`tools/site.mjs`. Update the release tag and filenames together after verifying
the release assets. Screenshots are actual engine captures; captions should not
imply that desktop screenshots are playable browser demos.

## Editing Java and rebuilding the runtime

The browser target is currently development code in `portable/`, separate from
the stable Maven library. This module intentionally checks in its small compiled
UI runtime so a fresh website checkout can be packaged and deployed independently
of that ongoing port. **Recompiling Java currently requires the development
checkout containing `portable/`; the 2.0.0 Maven artifact alone is insufficient.**
The deployment workflow verifies and packages the snapshot; it does not compile
Java. Replace this bootstrap arrangement with source compilation once the web
target is versioned and available to consumers.

With that checkout, Java 25, and the portable web npm dependencies installed:

```sh
./gradlew -p portable :web:webDist '-PapplicationMain=valthorne.website.WebsiteApplication' '-PapplicationSources=website/src/main/java'
node website/tools/site.mjs capture-runtime
node website/tools/site.mjs build
```

On Windows use `./gradlew.bat`, retaining the quotes around `-P` arguments.
This replaces the portable module's generated distribution with the website
application; it does not modify its sources. Run the normal portable build to
select another application afterward.

Commit the Java source and captured runtime together. The manifest makes stale
compiled website code a build error. Browser backend files in `runtime/` are
snapshots: make backend fixes upstream and recapture rather than patching them
only in the website. The manifest records SHA-256 fingerprints of every captured
file; bundled licenses must remain present.

## Browser verification

The site uses no front-end framework. The sole npm development dependency is
Playwright for browser verification:

```sh
npm ci --prefix website
npx --prefix website playwright install chromium
node website/tools/site.mjs serve
# In another terminal:
node website/tools/verify.mjs
```

Use `TEST_BROWSER=chrome` or `TEST_BROWSER=msedge` for installed Chrome or Edge.
`SITE_URL` can target another served deployment. Checks cover all seven pages,
mobile overflow, visible engine pixels, navigation, search and filters, idle
rendering, animation, clipboard, and text/no-JavaScript fallback. Reports and
screenshots are written to `website/build/`, not a test directory. The Chromium
check is also required before Pages deployment. Other browsers are not claimed
as verified by that check.

## GitHub Pages

`.github/workflows/pages.yml` builds and verifies this module on changes to
`website/` or the workflow. Pull requests run verification without deployment.
Successful pushes to `main` deploy `website/dist/` through the GitHub Pages
environment. The repository Pages source must be **GitHub Actions**. There is
no deployment branch or custom domain requirement.

All assets are relative to the document, so the site works under `/Valthorne/`.
Canonical metadata and the sitemap use the public URL. If the repository is
renamed, update `publicURL`, the preview prefix, the 404 home link, and the
workflow preview URL together. No application server, CDN, analytics, cookies,
or cross-origin isolation headers are required.

## Assets and licenses

Valthorne code and website source use the repository's Apache-2.0 license.
Branding comes from `images/logo-transparent.png` and `images/banner.png`.
The physics studio and UI captures come from `Valthorne-examples/docs/images/`; the other screenshots
come from that project's local capture outputs. They show the actual examples,
not invented game scenes. Example model and environment provenance remains in
the [examples notices](https://github.com/tehnewb/Valthorne-examples/blob/main/THIRD_PARTY_NOTICES.md).

Urbanist and the runtime's bundled Atkinson Hyperlegible use SIL OFL 1.1; Yoga,
JOML, and OpenType.js use MIT; TeaVM uses Apache-2.0. Urbanist's license is in
`assets/fonts/`; runtime dependency copies remain in `runtime/` and `licenses/`.
See [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md).
