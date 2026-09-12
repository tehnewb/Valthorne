# Website design notes

Reviewed September 12, 2026. The current direction is a professional product
website: clear capabilities, substantial real engine imagery, straightforward
installation and demo access, and an identifiable Valthorne brand. Unity's
website and the supplied Urbanist and charcoal-purple references guide the
hierarchy and visual system.

## Reference review

The following official websites informed the design. These are observations and
design recommendations, not claims that Valthorne has the same capabilities or
production history as another engine.

| Reference | Useful pattern | Application to Valthorne |
| --- | --- | --- |
| [Godot](https://godotengine.org/) | A short product explanation, prominent download action, and real game imagery establish purpose immediately. Navigation separates features, documentation, and community. | Keep “Start building” and “Explore demos” prominent. Show real Valthorne captures with links to working downloads. |
| [Unreal Engine](https://www.unrealengine.com/en-US) | Showcase content leads into explicit installation steps, tutorials, documentation, and sample projects. | Connect the visual showcase to a practical next step: download a demo, read its guide, or copy the Java dependency. |
| [Unity](https://unity.com/) | Compact black navigation precedes a large asymmetric hero. Real product media leads the page; charcoal cards, restrained controls, and generous spacing separate content. Feature sections and learning resources provide clear next steps. | Pair a concise Java product statement with a real engine capture. Use image-led showcases, distinct feature sections, resource rows, and direct integration actions. |
| [Defold](https://defold.com/) | The hero combines clear product positioning with a compact gallery. Small capability cards provide a quick overview before longer explanations. | Retain a compact set of engine facts and a curated showcase. Avoid a long, undifferentiated feature list on the home page. |
| [Bevy](https://bevy.org/) | Concise language and licensing information, a direct getting-started action, and relevant visuals or code suit a code-oriented engine. | Keep Java requirements, licensing, and integration easy to find. Present readable code within the common content column. |
| [Stride](https://www.stride3d.net/) | A large engine showcase includes visible playback controls. Product information, release access, and documentation are easy to identify. | Put pause controls next to moving content. Keep the viewer in control while making the current release and documentation discoverable. |
| [PlayCanvas](https://playcanvas.com/) | A contained interactive 3D presentation includes a visible drag-to-rotate instruction. A compact quick-start code block follows the hero. | Give the lab a purposeful 3D object with discoverable interaction, a reset action, and keyboard controls. Keep it close to explanatory content and a path to building something. |

Official page content was reviewed for all seven sites. Desktop browser views
were also inspected for Godot, Defold, Bevy, Stride, and PlayCanvas. For this
redesign, Unity's hero, navigation, latest-content cards, and engine feature
section were inspected in the browser. Automated browser access to Unreal Engine
was blocked, so that review is limited to accessible official page content.
No reference-site artwork, logos, code, customer claims, or testimonials are
reused.

## Visual direction

The supplied references establish Urbanist typography, neutral charcoal
surfaces, bright text, and purple accents. The visible purple swatches inform
the palette; the contradictory orange color labels in the supplied image do
not define the implementation. Valthorne's original logo remains in the header
and browser icon; its original banner appears in the footer. Their proportions,
transparency, and colors are preserved.

The Java view centers a content column up to 1,240 pixels wide. Copy follows a
consistent left edge within it. The homepage places the product statement and
actions beside an actual lighting-studio capture, then introduces product facts,
capabilities, example captures, and integration. Engine feature sections pair
relevant media with explanatory content. Documentation and project links use
compact rows where scanning is more useful than imagery. Examples use substantial
captures and clear download links. Missing captures receive a labeled typographic
panel, never an invented screenshot. Code retains conventional left alignment and
indentation. Mobile layouts stack these compositions and preserve reading space.

The background is `#141414`, with `#1a1a1a` panels, `#222222` secondary surfaces,
and `#686868` opaque control borders. Primary text is white; supporting text is
`#c7c7cc`. Purple `#703bf7` identifies primary actions, and the lighter `#b99bff`
identifies links. Flat buttons, inputs, and panels use four-pixel corners.
There is no atmospheric background painter or decorative grid, star field,
ribbon, or glow. Product media provides the visual depth.

Urbanist is self-hosted as an unmodified normal variable font, supporting weights
100–900. The Java host loads it before layout and uses matching font definitions
for measurement and drawing: 400 for body text, 600 for card titles and controls,
and 700 for display headings. The HTML companion uses the same family. Code uses
the system monospace family. [Font provenance and license](assets/fonts/README.md)
are included with the source and distributed assets; no font CDN is contacted.

Measured palette contrast against the panel is 17.40:1 for primary text,
10.33:1 for secondary text, 7.62:1 for links, and 3.12:1 for the opaque control
border. White primary-button text against purple measures 5.74:1. Keyboard focus
uses a separate pale-purple outline and input placeholders use the full
secondary-text color. These are checks of specific color pairs, not a claim of
complete WCAG conformance.

## Motion and 3D direction

- Use a 600-millisecond translation-only entrance and section/card reveals.
  Keep text at full opacity throughout to preserve contrast; native links follow
  the same movement while the underlying layout remains fixed.
  Hover and focus feedback should make actions easier to recognize. Navigation,
  reading, and downloads must never wait for an animation to finish.
- Keep the interactive crystal on the standalone lab page. Its faceted blue
  geometry, gold orbital bands, perspective, and shading illustrate the Java
  website runtime. The homepage leads with real product captures.
- Explain interactions beside the scene. Support pointer dragging and keyboard
  rotation, and provide visible animation and reset controls. Touch interaction
  should preserve the ability to scroll the page.
- Respect the operating system's reduced-motion preference. Decorative motion
  should settle into a useful static composition. User-requested scene movement
  needs an obvious pause action.
- Render only while something changes. Stop continuous work when the document
  is hidden or the scene is outside the viewport. Returning to a page should not
  replay a long entrance or jump to a new camera angle.
- Describe the implemented rendering honestly. A Java-projected 3D illustration
  drawn through the UI canvas demonstrates geometry and the website's Java
  runtime; it does not establish desktop renderer or physics performance.

## Review criteria

Page navigation keeps the engine running. Do not show the HTML companion as an
intermediate frame between two engine pages: its different layout produces a
visible jump. Prepare the destination while retaining the current view, paint it
once, and use a short fade through charcoal below the fixed header. Preserve
native link gestures and browser history; the next Tab after navigation must
remain in the visible navigation sequence. Reduced motion changes pages directly.

Review every page at desktop and phone widths, including its middle and footer.
Check that the fixed header and utility controls never obscure focused actions,
search results, or copy buttons. Search and category filters should show useful
feedback without forcing visitors to scroll past the entire catalog.

Verify pointer and keyboard navigation, direct example downloads, code copying,
scene controls, reduced motion, hidden/offscreen suspension, and browser back
navigation. The semantic HTML companion must remain complete and usable when
JavaScript or WebGL is unavailable. Store local screenshots and verification
output in the ignored `website/build/` directory.
