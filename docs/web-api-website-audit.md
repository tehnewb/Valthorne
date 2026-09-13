# Web API gaps exposed by the Java website

Source review: 2026-09-12. This reviews the application's actual browser glue,
not only the engine classes listed in its documentation. No runtime behavior
was newly tested for this audit, and no website application files were changed.

## Finding

The website is Java-authored, but it is not yet an example of exporting an
ordinary Valthorne application without application-owned browser integration.
Its entry point calls `WebsiteController.launch` before `JGL.init`. Its
controller imports TeaVM DOM types, its `BrowserPort` facade calls a custom
host, and `browser-host.js` overrides renderer behavior. Compiling Java to
JavaScript does not, by itself, remove those platform dependencies.

## Engine work required

| Priority | Gap | Evidence in website | Existing support and required completion |
| --- | --- | --- | --- |
| 1 | Reusable application bootstrap and embedding | `browser-host.js` constructs its own host; `BrowserPort.initialize/onConnect/onFailure`; `WebsiteController.install` | The normal portable host is coupled to the playground DOM and imports the 3D/physics runtime. Provide a configurable engine-owned host for a canvas or embedded UI, with Java startup/failure callbacks and selectable subsystems. Applications should not create `valthorneHost`. |
| 1 | Public vector drawing surface | `BrowserPort.measure/getFace/drawImage/setEffect`; host accesses Nano context state and patches `prepare` | `Canvas2D` exposes only a small subset. Internal BrowserNano already supports save/restore, bounds and image paints. Expose supported measurement, clipping, transforms, opacity and image drawing with cover/contain through shared Java APIs, using existing implementations where possible. |
| 1 | Portable font registration and styling | `BrowserDom.font/addFont`; `setFontResolver`; host overrides `nano.font` | Font loading exists, but the website installs variable weights and aliases through FontFace and a custom font resolver. Supply an engine-owned font registration/weight/fallback contract with matching measurement and rendering. Do not require application callbacks that return CSS font strings. |
| 1 | Configurable frame scheduling and display scale | Controller requests animation frames, pauses hidden tabs, invalidates on input, caps animation rates; host overrides `graphics.resize` | The engine supplies a frame loop, but the website owns its demand-driven scheduling and pixel-ratio policy. Add a public redraw request, continuous/on-demand scheduling, lifecycle notifications and a pixel-density budget. Define how queued tasks wake an idle app. |
| 1 | Complete source-to-export tooling | `tools/site.mjs` manually selects runtime files and vendors; `capture-runtime` stores fingerprints; README requires the development checkout | Provide a versioned consumer build integration that compiles source, gathers runtime modules/assets/licenses, and emits the deployment folder. Preserve the website's useful fingerprint checks, but remove application-maintained runtime file lists and the required recapture step. HTML generation can remain an optional export task. |
| 2 | Public clipboard, links and platform preferences | `BrowserDom.copy/media`; controller handles clipboard denial and reduced motion | Browser clipboard support exists internally in `valthorne.web.ui.BrowserClipboard`; it is not a shared application API. Expose clipboard results, opening URLs, and preference changes without TeaVM imports. Permission failures must remain explicit; export cannot bypass browser security. |
| 2 | Portable pointer/gesture and focus lifecycle | Controller uses DOM pointer IDs, setPointerCapture, touch behavior, focus and visibility handlers | Mouse/window APIs cover part of this, but not the complete pointer/touch contract used here. Supply Java events and engine-managed capture/lifecycle behavior. Fix and test the outstanding WebKit FPS pointer-lock failure separately. |
| 2 | Semantic controls and accessibility integration | Controller creates real anchors/input controls; HTML exporter mirrors content; navigation maintains focus and metadata | A canvas alone does not provide link behavior, selectable text or semantic navigation. Add an optional Java semantic/control adapter for applications that need these features; the website should supply content and actions rather than raw DOM positioning. |
| 2 | Navigation and persistence services | `browser/WebsiteNavigation.java` manages history, URLs, query parameters, scroll restoration and document loading | These are website features, not missing physics/rendering implementations. Offer optional Java navigation and storage services with browser adapters. Keep the website's route definitions, search policy and page transitions in the application. |

## Do not rewrite features already present

- Texture decoding, fonts, UI layout, ordinary input, storage, and application
  updates already have browser implementations. Audit the missing public
  contract before introducing another implementation.
- `BrowserBridge` contains ordinary application content/state access. It is not
  itself a missing platform API; replace only its platform-dependent delegates.
- `CrystalScene` is a deliberately small custom illustration. Its use of Java
  geometry and Canvas2D is not evidence that the full 3D engine is unavailable.
- Page copy, routes, reveal timing and generated semantic content remain
  application responsibilities. The goal is to remove handwritten browser
  adapters, not application-specific behavior.

## Completion criteria

1. Website drawing and interaction code uses Valthorne Java APIs without
   `@JSBody`, TeaVM DOM types, or direct access to `valthorneHost`.
2. Engine-owned modules replace the custom host construction and renderer
   method overrides. An optional generated HTML shell is export configuration.
3. A clean source checkout exports with one build command, without copying a
   previously compiled runtime or enumerating its JavaScript dependencies.
4. Existing website acceptance and navigation tests pass: narrow layouts,
   fonts, keyboard/touch interaction, reduced motion, idle redraw behavior,
   clipboard denial, scroll restoration, navigation races and startup fallback.
5. Shared drawing/input behavior is checked on desktop as well as the browser;
   browser-only semantics have a documented contract. Firefox, WebKit and actual
   Safari validation must be reported honestly, including setup blockers.

Start with the export/host contract and Canvas2D/font APIs. Migrate those pieces
of the website as the acceptance example, then add lifecycle and optional
semantic/navigation services. Merely moving its JSBody methods into another
application package would not satisfy these criteria.

## Sources inspected

- `website/README.md`
- `website/browser-host.js`
- `website/tools/site.mjs`
- `website/src/main/java/valthorne/website/WebsiteApplication.java`
- `website/src/main/java/valthorne/website/WebsiteController.java`
- `website/src/main/java/valthorne/website/browser/BrowserPort.java`
- `website/src/main/java/valthorne/website/browser/BrowserDom.java`
- `website/src/main/java/valthorne/website/browser/WebsiteNavigation.java`
- `website/tools/verify.mjs` and `website/tools/verify-navigation.mjs`
- `src/main/java/valthorne/ui/Canvas2D.java`
- `portable/web/build.gradle`, `prepare-graphics.mjs`, and public host/backends
- `portable/web/src/main/java/valthorne/JGL.java` and internal UI adapters
