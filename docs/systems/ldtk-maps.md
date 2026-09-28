# LDtk projects and maps

[System manual](README.md)

Use `LdtkProject` for parsed LDtk levels, layers, entities, tiles, definitions,
and decoded image data. `LdtkMap` uploads the project images and draws level
backgrounds and visible layers through an active `TextureBatch`.

## Load and render

`LdtkParameters.fromPath(path)` selects a project on disk. Use
`LdtkParameters.fromBytes(bytes, virtualPath, files)` when dependencies are
provided in memory. `LdtkLoader` performs file reading, JSON parsing, and image
decoding without OpenGL access, so it can run on a loading worker. The returned
`LdtkProject` owns the decoded `TextureData` and must be disposed after its GPU
map is no longer needed.

On the graphics thread, call `project.asMap()` or construct `LdtkMap` directly.
Call `render(batch)` for every level, `render(batch, levelIdentifier)` for one
named level, or `render(batch, level)` for a selected object. Rendering translates
LDtk's top-left coordinates to Valthorne world coordinates and draws the
background before layers. Dispose the map on the graphics thread to release its
uploaded textures. Keep project disposal separate from map disposal because the
project owns CPU image data while the map owns GPU textures.

## Related types

`LdtkLevel`, `LdtkLayer`, `LdtkTile`, `LdtkEntity`, `LdtkField`, and
`LdtkDefinition` expose parsed project data. `LdtkTileset` and
`LdtkBackground` describe images. `LdtkDependencySource`,
`LdtkDependencyResolver`, and `LdtkResolvers` define how external LDtk files
and images are resolved. Consult their linked source in the [source index](README.md#source-coverage)
for exact fields and failure behavior.
