# Audio performance and area sounds

Runnable demos and assets are maintained in the public
[examples project](https://github.com/tehnewb/Valthorne-examples). Run demo launch tasks there; the engine's local
`src/examples/` files remain ignored and excluded from library artifacts.
See the [example catalog](examples.md). Historical measurements retain their
original commands and source revisions.

## Ambient coverage

### Interactive 3D test scene

```powershell
.\gradlew.bat runAudioStudio
```

The self-contained Audio Studio generates two quiet, distinct mono tones and starts
paused. Select A or B in the inspector (or click its colored source marker), then
press **Play / pause**. Adjust volume, pitch, mute, looping, sphere/box shape,
position, dimensions and fade distance. Scroll the inspector for additional controls
on smaller windows. No external audio assets are required.

Sources stay stationary. Expanding, fading rings visualize sound waves traveling
outward while a sound plays. Pause, stop or mute hides its waves; volume changes
brightness and pitch changes their animation rate. **Silent wave preview** in the
toolbar lets you see the animation without playing audio. Scroll the selected
source's inspector to **WAVE VISUALIZATION** to adjust wave speed.

The rings are a slowed-down illustration, not sampled PCM, measured pressure,
physical propagation speed, reflections or acoustic simulation. Coverage contours
remain static and distinct from the animated waves. Wave geometry is allocated
once and reused; animation changes transforms/colors without moving the source,
rebuilding its geometry or publishing new audio areas.

The yellow marker is the listener, separate from the viewing camera. Drag empty
world space to move it on its current horizontal plane. With the pointer over the
world, WASD move X/Y, Q/E change height, left/right arrows orbit, and R/F zoom.
Escape closes the scene. A button moves the listener directly to the selected
zone center. Bright guides show the full-volume region; dim orthogonal contours
show cross-sections of the silence boundary, including rounded box falloff.
Gain meters report attenuation even while playback is paused. FPS and frame time
are displayed with VSync off. This remains ambient coverage, not directional panning.

`./gradlew runAudioStudio --args=--smoke` runs silently, tests native slider dragging,
source picking, listener dragging and zone editing, verifies expanding waves,
stationary source/area state, pause/mute gating and geometry reuse, checks OpenAL gains/playback
and OpenGL errors, then saves `build/audio-studio/studio.png` and exits.
The public example uses the companion project's shared launcher and published engine dependency.

### Library API

Attach an immutable `SoundArea` to any managed `SoundPlayer`. It works with buffered
or streaming playback, mono or stereo. Sound is full-volume inside the zone and
fades with smoothstep over a world-space distance outside its boundary.

```java
SoundPlayer rain = Audio.load("audio/rain.ogg");
rain.setArea(SoundArea.rectangle(100, 200, 40, 25, 12));
rain.setVolume(0.6f);
rain.setLooping(true);
rain.play();

// In the game update: 2D zones ignore Z.
Audio.setListenerPosition(playerX, playerY, 0);

// Other shapes:
rain.setArea(SoundArea.circle(100, 200, 40, 12));
rain.setArea(SoundArea.sphere(100, 200, 30, 40, 12));
rain.setArea(SoundArea.box(100, 200, 30, 40, 25, 10, 12));

// Remove attenuation, or release the player when no longer needed.
rain.setArea(null);
Audio.destroy(rain);
```

Rectangle/box dimensions are **half extents**, measured from the center. Fade is
measured from the nearest boundary, including Euclidean distance around corners.
Zero fade creates a hard boundary; the boundary itself is inside. Non-finite
coordinates and negative dimensions are rejected. Multiple zones use separate
players and mix independently; their sum can clip if gains are too high.

`getVolume()` reports the player's volume before area attenuation;
`getEffectiveVolume()` reports the applied gain. Existing mute/unmute behavior
preserves the saved player volume, not the attenuated value. Changing areas does
not seek, restart or steal playback controls.

The listener is a logical ambient-zone position, **not** an OpenAL directional
listener transform. This feature does not provide point-source panning, occlusion,
reverb, portals or a spatial index. Unregistered players do not update automatically;
use `Audio.create`, `Audio.load` or `SoundData.asSoundPlayer` for managed playback.

## Work avoided

- Paused/inactive streams return before querying native playback state or decoding.
- MP3 PCM transfer storage grows only for larger reads, instead of allocating a new
  byte array every refill. Decoder-internal allocations still exist.
- OGG reuses the sample view for repeated reads into the same destination.
- Stream uploads reuse the chunk buffer rather than creating a duplicate view.
- WAV/OGG loops seek their existing decoder. MP3 seeking still reopens and decodes
  from the start; it is not a constant-time seek implementation.
- In-memory WAV streams no longer allocate an unused 64 KiB file scratch buffer.
- Player iteration uses an array snapshot rebuilt only on registry changes.
- With no registered players or pending commands, the audio thread parks until
  signaled instead of waking every 5 ms.
- Unchanged listener coordinates reuse their snapshot. Movement publishes one
  immutable position (one allocation per changed call), without queueing or blocking.
  All zones see the latest snapshot; intermediary positions may be coalesced.
- Each area caches the last listener snapshot, and identical gains avoid native
  gain writes. Evaluating geometry itself allocates no objects.

Area changes apply on the audio update cadence (normally about 5 ms, not a hard
real-time guarantee). There is no temporal ramp for teleports across a boundary.
Silent out-of-range sounds continue advancing/decoding so ambience stays in phase.
They still consume an OpenAL voice. This is not voice virtualization or a claim of
minimum possible total CPU usage. Many moving-listener zones still require O(n)
evaluation; decoder I/O remains on the audio thread.

Batch related playback commands inside one `Audio.run(() -> { ... })` to avoid
multiple synchronous cross-thread round trips. Do not perform game logic, blocking
I/O or long-running work in that callback. Ordinary asynchronous runtime exceptions
are reported without terminating the worker; fatal JVM errors are not recovered.

## Correctness fixes

Unstarted stream queues detach safely during reset/disposal, buffered sources detach
before their buffer is deleted, player disposal is idempotent, and disposed-player
commands fail instead of touching deleted handles. OGG stream close is idempotent.
Buffered OGG decoding frees the original PCM pointer without advancing it during
the copy: the old path could free an interior pointer and crash the native heap.
Loop refills no longer reset the consumed-time counter ahead of audible playback.

## Verification and measurements

```powershell
.\gradlew.bat test --tests valthorne.AudioPlaybackTest --tests "valthorne.audio.*"
.\gradlew.bat benchmarkAudio
```

Geometry tests cover shapes, invalid input, hard boundaries and 10,000 randomized
reference comparisons. Native OpenAL tests cover actual gain, pause/resume, mute,
8/16-bit PCM playback setup, disposal and worker exception isolation. WAV fixtures
are generated in memory. MP3/OGG regression checks additionally use the optional,
unversioned local files `src/test/resources/test-sound-3.mp3` and
`src/test/resources/test-sound-2.ogg`; those two tests skip if files are absent.

JMH 1.37, JDK 25.0.2, two forks, three one-second warmups and five one-second
measurements per fork, 256 MiB heap, GC profiler, measured 2026-09-09:

| Geometry | Mean ns/evaluation | 99.9% error |
| --- | ---: | ---: |
| Circle | 1.612 | +/- 0.039 |
| Sphere | 1.698 | +/- 0.052 |
| Rectangle | 3.228 | +/- 0.305 |
| Box | 3.177 | +/- 0.140 |

All four measured below one Java byte/evaluation (profiler noise near 0.00001).
The workload moves a listener through inside/fade/outside positions on one fixed
shape per fork. These are initial geometry baselines, not whole-engine speedups,
native-memory measurements, instruction counts or end-to-end audio latency.
See [raw results](benchmarks/audio-2026-09-09/areas.json) for JVM flags and samples.
