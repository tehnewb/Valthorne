# 3D particles

Runnable demos and assets are maintained in the public
[examples project](https://github.com/tehnewb/Valthorne-examples). Run demo launch tasks there; the engine's local
`src/examples/` files remain ignored and excluded from library artifacts.
See the [example catalog](examples.md). Historical measurements retain their
original commands and source revisions.

`ParticleEmitter3D` supports pooled billboard and mesh particles, with optional Jolt rigid bodies. Each emitter has a fixed capacity, a spawn initializer, timed emission, explicit bursts, and finite particle lifetimes. Mesh particles use Filament lighting, shadows and frustum culling when attached to a `Scene3D`.

## Mesh particles with Jolt

```java
Model3D mesh = ModelBuilder3D.sphere(.1f, 8, 4);
CollisionShape3D shape = CollisionShape3D.sphere(.1f);
Material3D material = new Material3D().setRoughness(.3f);

ParticleEmitter3D particles = new ParticleEmitter3D(256, particle -> {
    particle.setLifetime(3).setModel(mesh);
    particle.getModelInstance().setMaterial(material);
    particle.getPosition().set(0, 0, 3);
    particle.getVelocity().set(1, 0, 5);
}).setPhysics(world, particle ->
    new BodySettings3D(shape, MotionType3D.DYNAMIC)
        .setMass(.05f).setRestitution(.7f).setContinuousCollision(true))
  .setEmissionRate(40)
  .attach(scene);

// Each frame: the caller advances the world once. Fixed ticks drive particles.
world.update(deltaSeconds);
renderer.render(scene, camera);

// At teardown: releases bodies, scene membership and listener registrations.
particles.close();
// The caller separately owns world, scene, renderer, meshes and materials.
```

Configure collision layers and gravity on the world as usual. A physical particle is a normal Jolt dynamic body: it supports contacts, sleeping, gravity factors, damping, sensors, collision filters, forces, impulses and continuous collision detection. Prefer simple sphere or box shapes. Rendering scale does not resize collision shapes; configure both consistently.

`getPosition()` is the particle's canonical position during initialization, including mesh particles. The emitter synchronizes the mesh from it. The physics factory controls shape, mass, rotation and other body settings; its initial position and velocity are replaced by the initializer's particle values. Once physical particles are alive, move them with `particle.getBody().setTransform(...)`, change velocity with the body, or apply an impulse/force. Jolt updates the exposed particle position, velocity and mesh rotation after each fixed tick.

Particle acceleration adds `mass × acceleration` as force before every Jolt tick, **in addition to world gravity**. Leave it zero for normal gravity. Changing gravity factor affects world gravity, not this additional force. Emit bursts before advancing the world when acceleration must apply from the first tick; before-step callbacks run in registration order. Reaching the lifetime removes the body and mesh. Physical lifetimes and automatic births are quantized to fixed ticks; newly born bodies are never moved backward through colliders to simulate fractional birth times. Dropped world time is also dropped from particle age. Calling `particles.update(delta)` in physics mode does not advance simulation twice.

## Lights attached to particles

Mesh particles already receive scene lighting. A particle can additionally carry an explicit point light:

```java
particle.getLight().setColor(Color.ORANGE).setIntensity(12).setRange(6);
particle.setLightOffset(0, 0, .1f).setLightEnabled(true);
```

`getLight()` lazily creates independently owned parameters; it does not enable illumination by itself. In Filament, intensity 1 maps to 1,000 lumens, matching the existing emissive-mesh convention. The light follows the particle plus a world-space offset, including Jolt motion. It works without a mesh for invisible transient lighting. Attach the emitter to a scene to register its enabled lights. Expiration, clear, detach, transfer and close remove them; pooled reset disables them and restores default light parameters.

Light range limits influence; zero intensity disables contribution. `getLight().setCastsShadows(true)` opts into shadow casting; the default is false. Bound the number/range of lights and enable shadows selectively to control cost. An attached light does not automatically make its mesh glow. Set the material emission separately and use `setEmissionLightEnabled(false)` when the explicit particle light should be its only point light; the default preserves the legacy implicit emissive-mesh light.

`Scene3D.addLight/removeLight/getLights` manages explicit lights for Filament. `renderer.getPointLightCount()` counts active explicit and emissive point lights after the last render. With legacy `ModelBatch3D`, callers continue adding lights to their `MeshRenderState3D`; the scene does not automatically replace that render-state list. The [playable FPS arena](fps-arena.md) uses soft transparent flecks and flares, each with its own moving light, and has no muzzle light.

For real alpha blending in Filament, set `material.setRenderPass(RenderPass3D.TRANSLUCENT)` and an alpha below one on the tint and/or texture. The alpha material fades coverage and visible emission; transmission is a separate glass/refraction mode. The FPS example shares a radial-alpha texture, rotates quad models toward the camera after physics synchronization, and fades each particle's reused material and light over its lifetime.

## Cosmetic motion and billboards

Omit `setPhysics` and call `particles.update(deltaSeconds)` yourself. These particles use constant-acceleration integration and distribute births within the frame. They create no native bodies and do not collide. Set `particle.getAcceleration()` explicitly if they should fall. Mesh particles can still attach to a scene; billboards use the texture, color and size API on `getSprite()` and `emitter.submit(modelBatch)`.

Filament renders mesh particles, including the camera-facing quad meshes used by the FPS example. The separate sprite billboard API renders through `ModelBatch3D`; attaching a sprite-only emitter does not make its sprites visible in Filament. Avoid submitting an attached mesh emitter a second time through the same scene's batch.

## Ownership and performance

- `burst(count)` fills available capacity; automatic overflow is dropped without a later backlog. `setEmitting(false)` stops automatic births while existing particles continue.
- `clear()` releases live bodies and scene membership, retaining reusable particle objects. `close()` also unregisters world callbacks and releases the pool. The world and rendering assets remain caller-owned.
- Change physics modes only while empty: `clear()` followed by `disablePhysics()` or `setPhysics(...)`. Advance each shared world once regardless of how many emitters use it.
- `attach(scene)` transfers current and future meshes. `detach()` removes them without ending their simulation. Close or detach emitters before rebuilding a scene, or explicitly reattach afterward.
- Use the cached, read-only `getParticles()` list for inspection. Its particles are mutable and pooled: references can represent a different birth after expiration. Do not retain them as permanent identities.
- Initializers and factories run synchronously and must not reenter the emitter. All world operations are confined to its owning thread.
- Mesh geometry and materials can be shared. Filament retains compatible native entries when particle counts change. Pool reuse resets existing sprite/model state; stable compaction removes expired particles in one active-list pass.
- Native body creation/destruction and collision solving still cost CPU time. Rendering still has per-frame scene snapshot allocations. This is a bounded mesh/rigid-body particle system, not a GPU particle simulation.
- Visible mesh emission and attached point lights can be controlled independently. Disable implicit material lights when particles already carry explicit lights, and measure the light budget for the intended scene.

## Interactive test

Run `./gradlew runPhysicsStudio --args="--scenario=4"` to open the fountain, angled deflectors and catch tray. The left panel controls Jolt on/off, emission rate, lifetime, bounce, bursts and clearing. Lifetime/bounce changes apply to subsequent births. Changing physics mode clears and restarts the fountain. Pause, single-step, time scale and gravity work with both modes. The footer displays FPS and frame timings; the left panel displays live particle count. The editable light rig remains available on the right.

`--visual-particles` starts the same scenario without physics. `--particle-rate=120` selects a higher load (0–120 births/second, capped at 512 live particles). Add `--smoke` to exercise camera, light and particle controls with native mouse events, or `--benchmark` for a moving-camera, completed-frame timing run.

Engine regression tasks `./gradlew verify3D verifyPhysics3D` require optional local test files in the engine checkout. Use the companion project's `build` and particle-fountain smoke run for public example validation. [Benchmarks and raw measurements](benchmarks/particles-3d/README.md) cover pooled visual updates and the native studio scenarios.
