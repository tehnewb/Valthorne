# Portable application services

[System manual](README.md)

The `valthorne.portable` interfaces separate application logic from desktop
and browser implementations. They provide a small contract for input, audio,
local settings, immediate 2D drawing, asset lookup, scene presentation, and
physics integration. The browser implementation and build workflow are covered
in the [portable target guide](../../portable/README.md).

`FrameLoop` drives an `Application` with bounded fixed-step updates and a render
call for each frame; it also manages initialization and disposal. `SceneBackend`
provides scene presentation, `AssetService` and `ModelAsset` describe model
loading, and `PhysicsBody` exposes the physics object used by a scene.
`MediaService` supplies media behavior. Read the interface contracts before
implementing a backend; an implementation must preserve lifecycle and resource
ownership across its desktop or browser boundary.

`PlatformServices` exposes physical key state, consumed key edges, pointer
capture and look deltas, local settings, synthesized audio, viewport dimensions,
and immediate overlay drawing. Key presses and look deltas are consumed by
their `take...` methods. Browser pointer capture requires a user gesture, and
browser audio must be unlocked by interaction. Call `close()` when the owner
ends the service lifetime.
