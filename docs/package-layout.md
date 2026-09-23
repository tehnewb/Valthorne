# Graphics and geometry package layout

The former `valthorne.graphics.model` package is split by responsibility:

| Package | Responsibility |
| --- | --- |
| `valthorne.graphics.model` | Model geometry, materials, builders, parameters, and loaders |
| `valthorne.graphics.scene` | Scene trees, instances, renderable contracts, billboards, and picking results |
| `valthorne.graphics.render` | Batches, renderers, render state, shadow maps, path tracing, and culling |
| `valthorne.graphics.lighting3d` | Point lights and the existing lighting implementation |
| `valthorne.math.geometry` | Geometry, including `Sizeable`, `Locatable`, and `Dimensional` |

This is a source and binary breaking package migration. Update explicit imports
and fully qualified class names, then recompile consumers. A wildcard import of
`graphics.model` no longer includes scene or rendering classes. The maintained
sibling examples use the new imports.

Renderers use `Model3D.getTriangleView()` for a cached, unmodifiable triangle view.
Triangle accessors continue to protect mutable attributes. The destination-taking
`getLocalBounds(AABBf)` copies bounds into caller-owned storage. Scene traversal
methods expose indexed access without allocating list snapshots.

## Directory browsers

`FileExplorer` and `NanoFileExplorer` share
`valthorne.ui.behavior.DirectoryBrowserModel`. It owns directory snapshots,
containment, filtering, ordering, selection, double-click tracking, activation,
and application callbacks. The widgets own layout, rows, highlighting, scrolling,
and input translation.

Both `getEntries()` methods now return `List<DirectoryBrowserModel.Entry>`.
Replace explicit uses of `FileExplorer.Entry` or `NanoFileExplorer.Entry` with
the shared entry type. Existing fluent widget operations remain available.

## Verification

The previous test suites and runners were removed. `build` validates compilation,
Javadoc, and documentation links; `verifyRelease` also validates local publication
artifacts. Neither command provides runtime regression coverage. Historical
validation reports remain documentation rather than executable suites.
