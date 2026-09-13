# Landing-page design notes

The website is one focused landing page. Its purpose is to introduce Valthorne,
explain the engine's core systems, and give developers a clear route to the
manual, source, and getting-started instructions.

## Composition

[Unity's website](https://unity.com/) informs the hierarchy: compact navigation,
a decisive product headline, substantial artwork, and clearly separated product
and resource sections. The implementation uses original Valthorne content and
branding. Reference-site artwork, customer lists, testimonials, and product claims
are not reused.

The page follows a consistent content column with generous margins. Headings,
paragraphs, and actions share a deliberate left edge. The hero has a large
landscape illustration; feature rows explain the engine without turning the page
into a long inventory. Three resource columns lead to useful destinations. The
final section gives readers a direct next step.

Desktop navigation remains compact. Narrow layouts put supporting navigation
on a second line, and content stacks into one column. All actions must remain
readable and reachable at phone sizes and in short browser windows.

## Artwork and typography

Valthorne's original logo and banner preserve their colors and proportions.
Urbanist is served locally with its SIL OFL license. Java loads the font before
layout and uses matching measurement and drawing definitions. The semantic HTML
companion uses the same family.

`WorldArtwork` generates an original SVG landscape in Java: a stone arch on a
rocky ridge, layered mineral-blue mountains, and an ivory sky. SVG gives the hero
crisp contours across screen sizes without a separate graphics framework or
remote media dependency. This is editorial illustration, not a gameplay capture
or a demonstration of engine performance.

Use strong text contrast, restrained accents, and clear section boundaries.
Maintain visible keyboard focus. Native controls should align with the Java
painting and avoid clipping beneath the fixed header.

## Java source and browser output

`LandingContent` supplies the shared copy and links. `WebsiteApplication` paints
the Valthorne interface, and `WebsiteController` owns input, fragment navigation,
history, motion preferences, and rendering schedules. Small browser bindings
expose native operations.

At packaging time, `HtmlExporter` generates the semantic landing document,
metadata, sitemap, and error page. `BrowserStyles` emits its stylesheet and
`WorldArtwork` emits the SVG. HTML, CSS, JavaScript, and SVG are necessary browser
output; the authored website behavior and presentation remain in Java.

The compiled TeaVM application uses Valthorne's web-port adapter. Node.js
packaging and Playwright checks are development tools. The checked-in runtime
must be recaptured whenever Java source changes; the current development
`portable/` checkout is still required to rebuild it.

## Navigation and motion

The header links to `#engine`, `#resources`, and `#start`. These are sections of
the same document, so navigation retains the running engine and its drawing
surface. Java controls smooth scrolling, history entries, and section tracking.
Direct fragment URLs, Back, Forward, and Back to top should all reach their
expected positions without exposing another layout between frames.

Keep entrance movement finite and preserve text opacity. Section reveals settle
completely; the page does not need a perpetual animation loop. Stop work when
nothing changes or the document is hidden. The footer motion control preserves
the visitor's session preference, and system reduced motion takes precedence.
Navigation and reading must remain useful with movement disabled.

## Review checklist

- Inspect the hero, section transitions, and footer at desktop, phone, and short
  viewport sizes. Check real canvas pixels as well as native overlay bounds.
- Follow every header anchor, use Back and Forward, and reload a direct section
  fragment. Confirm that the engine remains alive during in-page navigation.
- Check keyboard focus, the footer controls, idle rendering, paused motion, and
  the operating system's reduced-motion preference.
- Read the complete semantic page with JavaScript disabled, in text mode, and
  with WebGL unavailable. Resource and getting-started links must still work.
- Check the packaged output for retired pages and assets. Keep local review
  screenshots and reports in the ignored `website/build/` directory.
