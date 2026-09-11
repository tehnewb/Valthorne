# Mixed UI rendering

See [UI system guide](ui-system.md) for the showcase, shared behaviors, theme,
input routing, text editing, inspector, virtualization, and migration notes.

Regular and NanoVG controls share one UI tree. Either container family can contain
the other. Use the existing `add`, `setContent`, modal-content, and overlay APIs,
then call `UIRoot.draw()` once per frame.

```java
Panel panel = new Panel();
NanoPanel vectorPanel = new NanoPanel();
vectorPanel.getLayout().width(300).height(160);
vectorPanel.add(new Button("Regular button"));
panel.add(vectorPanel);
panel.add(new NanoButton("Nano button"));
root.add(panel);
```

## Drawing contract

`UIRenderContext` selects each node's backend. Containers own child traversal.
Adjacent same-backend siblings are grouped without reordering. Parent backgrounds,
children, parent foregrounds, and later siblings retain
painter order. Overlays render once, after the normal tree. Backend transitions
flush pending work and restore the batch's GPU bindings while preserving its
clip and translation stacks. The engine-managed projection and bundled Nano font
registration remain in use.

Regular custom containers call `super.draw(batch)`; Nano custom containers call
`super.draw(vg)`. Custom child loops call `child.render(batch)` using the active
UI batch. A mixed tree must be rendered through the root, rather than an external
batch or an independently managed NanoVG frame.

Use the shared batch's translation and clipping scopes for transformed children.
Clip rectangles are in bottom-left world space, including the current translation;
balance scopes with `try/finally`. Both built-in scroll panels demonstrate this
contract. Raw Nano transforms/scissors can paint a node's own geometry, but do not
replace the shared scopes for children using different backends.

The context initializes one Nano frame per root draw. Backend transitions flush
with `nvgEndFrame`, which preserves NanoVG's logical state stack. Widgets must not
call `nvgBeginFrame`, which resets that stack. Native regression tests cover nested
state preservation with the repository's LWJGL version.

## Coordinates and interaction

- Layout and Nano painting use top-left positions; texture painting and hit
  testing use bottom-left world positions. Conversion occurs at the shared
  rendering boundary.
- `screenToWorld` converts a pointer to world space. `screenToContent` also
  accounts for ancestor scrolling. `screenToLayout` returns the corresponding
  top-left position for Nano controls. Captured pointer movement can extend
  outside a viewport without falling back to screen pixels.
- NanoImage and Image interpret the same TextureData row order. Explicit Nano
  image sizes survive attachment.
- Hidden and disabled ancestors block focus and input. Button activation requires
  a primary release over the control. An unrelated button release does not end
  pointer capture.

Nodes retain one parent. Lifecycle callbacks run once per native attachment and
detachment. Local style overrides work without a theme and invalidate layout.
Existing widget APIs and visual skins remain separate; no asset conversion is
required to combine them.

## Verification

`./gradlew test --tests valthorne.ui.MixedUIIntegrationTest` runs the JUnit suite.
It creates an invisible GLFW window and checks actual OpenGL pixels and input
targets. A working graphics context is required.

The suite covers nesting in both directions, sibling and foreground ordering,
translucent overlays, both directions of mixed nested scrolling, clipping,
OrthographicCamera scaling, image orientation, pointer capture, lifecycle,
local styles, and recovery after a draw exception. The normal `test` task includes
these regressions. Arbitrary custom OpenGL state and every camera subclass are
not covered by this suite.
