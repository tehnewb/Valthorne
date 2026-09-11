# Lighting Studio visual audit

Inspected the actual running Lighting Studio window and the saved `motion-realtime.png` on September 8–9, 2026. The live window used Progressive / Interactive, with a user-edited rig. It was observed at approximately 444, 18 and 3652 accumulated samples. The rig changed between captures; those views are observations, not controlled before/after comparisons. Further input was avoided to leave the user's edits alone.

## Visible problems

| Area | Observation | Interpretation |
|---|---|---|
| Glass | Central sphere reads as a dark polished ball with isolated white patches; moving realtime capture resembles frosted glass | Clear glass is not communicated convincingly. This persists beyond the initial few samples. A reference scene is needed to distinguish transport errors from poor environment/rig design and filtering. |
| Reflections | Gold and blue spheres have broad, smeared highlights and little readable reflected structure in motion | Reduced noise does not equal retained detail. The current diffuse-patch error metric does not assess this failure. |
| Edges | Wall tops, light outlines and small rear objects show stepping/soft reconstruction | Progressive Interactive renders at half resolution on each axis, then enlarges the image. Accumulating samples does not recover missing output resolution. |
| Illumination | Floor and walls show broad gradients; low-sample captures have mottled/patterned illumination | The scene has inadequate visual detail to distinguish plausible illumination from over-filtering, and low-sample reconstruction remains visibly weak. |
| Light presentation | Large visible sources become featureless white shapes, one occupying a prominent part of the shot | Bright emitters can legitimately clip in an exposure. This is a rig/exposure/composition problem to evaluate, not by itself proof of incorrect emission math. |
| Scene composition | Empty foreground occupies much of the viewport; the rear roughness samples are small and partly obscured | The scene is a poor inspection tool: important material behavior is hard to evaluate at normal viewing size. |
| Statistics | High FPS and sub-millisecond submission values appear alongside accumulated stationary images | These values do not demonstrate high-quality moving-light rendering. CPU submission and reuse of a converged frame need to be visibly distinguished from completed rendering time. |

## Implementation limitations at the time of the initial audit

- The denoiser filters combined radiance using primary-surface normal, depth and instance identity. It has no separate diffuse/specular/transmission signals or reflected/refracted hit-distance guides. A reflection boundary inside a smooth sphere is not a primary geometry boundary.
- Realtime reprojection likewise follows the primary surface. View-dependent reflection/refraction features do not necessarily follow that surface's motion. Short history caps reduce lag but leave these materials with fewer usable samples.
- Interactive limits paths to five bounces. Refraction consumes surface crossings; whether this materially darkens this glass needs a same-camera higher-depth reference, rather than an unsupported assumption.
- The scene uses flat material colors and a constant environment radiance, without a detailed environment or surface textures. More samples cannot add the missing visual structure.
- The reported 97.5% error reduction measured a diffuse patch. It did not measure glass clarity, specular detail, silhouette quality, temporal trails, exposure, composition or user-visible overall quality. It cannot support a claim that the scene is visually fixed.

## Work identified by the initial audit

1. Capture a fixed-camera, fixed-rig reference at full resolution with higher path depth and sufficient samples. Compare raw and filtered outputs from the same radiance data. Keep the user's current rig intact.
2. Put a high-contrast reference pattern behind clear glass, make roughness samples large enough to inspect, and add a close view of contact shadows and curved silhouettes. Use a neutral rig before colored-light stress cases.
3. Evaluate separate reconstruction for diffuse versus specular/transmitted lighting. Preserve reflection detail rather than accepting a diffuse-only noise score as the quality target.
4. Compare identical orbit, pan and zoom trajectories, including newly exposed surfaces and light edits. Inspect frame sequences for blur, trails, sparkle and changes after stopping; a single still is insufficient.
5. Report visual results alongside GPU-completed frame time and memory. Higher FPS with a softer or less informative picture is not a successful fix.

## Implemented follow-up

Controlled raw/filtered references confirmed that transport contained readable refraction which the combined filter blurred. Diffuse and specular/transmission now have separate histories and reconstruction. Diffuse albedo demodulation preserves checker contrast; glass receives weighted Fresnel branches and limited spatial filtering. Shared primary/guide jitter removes the large bright trails behind visible emitters. Stationary history no longer undergoes repeated interpolation, which had blurred settled glass. The studio now uses a neutral rig, larger material subjects, a reference pattern and actual GPU timing.

The full-resolution moving reference improved display-space RGB RMSE against the original fixed 1024-sample reference: glass 23.35 → 16.66, metal 11.69 → 10.62, checker wall 17.96 → 14.22 (0–255 channel scale, fixed regions). These narrow metrics do not establish overall photorealism. The new jitter sequence affects edge sampling, and the surface-area BVH changes emitter ordering, so individual noise samples differ. Consecutive moving frames and the settled image were also inspected.

| Evidence | Image |
|---|---|
| Original moving reconstruction | [Before](benchmarks/lighting-visual-quality/before-motion.png) |
| Updated moving reconstruction | [After](benchmarks/lighting-visual-quality/after-motion.png) |
| Raw 1024-sample reference | [Reference](benchmarks/lighting-visual-quality/reference.png) |
| Updated settled view | [Settled](benchmarks/lighting-visual-quality/settled.png) |
| Updated studio during orbit | [Studio](benchmarks/lighting-visual-quality/studio-motion.png) |

Fine glossy grain remains during motion, and the moving glass is softer than the converged reference. Primary-surface reprojection does not model reflected/refracted feature motion. This is an improvement with measured limits, not a claim of noise-free or fully photorealistic rendering. The existing fast raster lighting paths are unchanged by this reconstruction work; PathTracer2D shares the improved tracing implementation.
