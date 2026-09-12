# Website design notes

Reviewed September 12, 2026. The goal is to make Valthorne's capabilities easy to
understand, its examples easy to try, and its visual identity consistent with the
original logo and banner.

## Reference review

The following official websites informed the design. These are observations and
design recommendations, not claims that Valthorne has the same capabilities or
production history as another engine.

| Reference | Useful pattern | Application to Valthorne |
| --- | --- | --- |
| [Godot](https://godotengine.org/) | A short product explanation, prominent download action, and real game imagery establish purpose immediately. Navigation separates features, documentation, and community. | Keep “Start building” and “Explore demos” prominent. Show real Valthorne captures with links to working downloads. |
| [Unreal Engine](https://www.unrealengine.com/en-US) | Showcase content leads into explicit installation steps, tutorials, documentation, and sample projects. | Connect the visual showcase to a practical next step: download a demo, read its guide, or copy the Java dependency. |
| [Unity](https://unity.com/) | Large visual examples are organized around a small number of development goals. Learning resources remain a clear part of the journey. | Group the engine's many systems around rendering, interaction, and building the complete experience, with deeper detail on the engine and documentation pages. |
| [Defold](https://defold.com/) | The hero combines clear product positioning with a compact gallery. Small capability cards provide a quick overview before longer explanations. | Retain a compact set of engine facts and a curated showcase. Avoid a long, undifferentiated feature list on the home page. |
| [Bevy](https://bevy.org/) | A centered logo, concise language and licensing information, and a direct getting-started action suit a code-oriented engine. Feature sections pair an explanation with a relevant visual or code example. | Preserve Valthorne's centered composition and Java identity. Code stays readable and left aligned within a centered container. |
| [Stride](https://www.stride3d.net/) | A large engine showcase includes visible playback controls. Product information, release access, and documentation are easy to identify. | Put pause controls next to moving content. Keep the viewer in control while making the current release and documentation discoverable. |
| [PlayCanvas](https://playcanvas.com/) | A contained interactive 3D presentation includes a visible drag-to-rotate instruction. A compact quick-start code block follows the hero. | Give the lab a purposeful 3D object with discoverable interaction, a reset action, and keyboard controls. Keep it close to explanatory content and a path to building something. |

Official page content was reviewed for all seven sites. Desktop browser captures
were also inspected for Godot, Defold, Bevy, Stride, and PlayCanvas. Automated
browser access to Unreal Engine and Unity was blocked, so their observations are
limited to the accessible official page content. No reference-site artwork,
logos, code, customer claims, or testimonials are reused.

## Visual direction

The user-supplied visual reference adds a dark cosmic direction: a centered
composition, near-black backdrop, luminous teal accents, bold bright headings,
pill-shaped controls, and restrained grid, star, and curved-ribbon details.
The reference informs the treatment of the page; its brand, artwork, copy, and
customer claims are not used. Valthorne's original banner and logo retain their
colors, proportions, and transparency without cropping or recoloring.

The background is `#030a12`, with `#0d202b` panels and `#497487` opaque control
borders. Primary text is `#f7fcff`; supporting text is `#c5d7e2`. Cyan `#58eee0`
identifies primary actions, blue `#70cbff` identifies links, and gold `#efca88`
connects the interface to the original artwork. Strong separation between text,
controls, and surfaces takes precedence over the reference's dimmer typography.

System-ui headings use weight 700 through the Java `display` alias. Card titles
and controls use the `ui-medium` alias at weight 600; card titles are 21 pixels
and body copy is 16-pixel Atkinson Hyperlegible. Georgia is reserved for the
navigation wordmark. Rounded
16-pixel panels and pill-shaped buttons and inputs replace the earlier square
surface treatment. Keyboard focus uses an external white outline that remains
distinct from a cyan button, and placeholders use the full secondary-text color.

Measured palette contrast against the panel is 16.15:1 for primary text,
11.28:1 for secondary text, 9.28:1 for links, and 3.28:1 for the opaque control
border. Dark primary-button text against cyan measures 13.94:1. These are checks
of specific color pairs, not a claim of complete WCAG conformance.

`Atmosphere` paints the static grid, stars, teal glow, and curved ribbons through
the Java UI. Decorative detail stays near the page edges so headings, body copy,
and artwork remain legible. The text-mode companion uses CSS for a matching
static atmosphere. Neither treatment adds an animation loop.

Use a common center line for the hero, section headings, calls to action, and
partially filled card rows. Centering is a composition rule; source code and
long-form technical instructions still need conventional reading alignment.
Limit text width, keep consistent vertical spacing, and preserve clear space
around the logo, engine captures, and reading areas.

## Motion and 3D direction

- Use a 600-millisecond translation-only entrance and section/card reveals.
  Keep text at full opacity throughout to preserve contrast; native links follow
  the same movement while the underlying layout remains fixed.
  Hover and focus feedback should make actions easier to recognize. Navigation,
  reading, and downloads must never wait for an animation to finish.
- Keep 3D inside a clearly bounded showcase. A faceted blue crystal and gold
  geometry suit the existing art without imitating or replacing the logo.
  A scene should communicate depth through perspective, lighting, and overlap.
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

Review every page at desktop and phone widths, including its middle and footer.
Check that the fixed header and utility controls never obscure focused actions,
search results, or copy buttons. Search and category filters should show useful
feedback without forcing visitors to scroll past the entire catalog.

Verify pointer and keyboard navigation, direct example downloads, code copying,
scene controls, reduced motion, hidden/offscreen suspension, and browser back
navigation. The semantic HTML companion must remain complete and usable when
JavaScript or WebGL is unavailable. Store local screenshots and verification
output in the ignored `website/build/` directory.
