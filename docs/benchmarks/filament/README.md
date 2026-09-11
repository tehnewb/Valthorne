# Filament integration measurements

Windows x64, RTX 5070, NVIDIA 610.88, Java 25.0.2. The studio runs at 1440×900 with an 872×752 3D viewport, a fixed camera-orbit increment, three editable lights and the UI enabled. Each run warms up 30 frames and measures 120 frames with GPU completion and VSync disabled. Assets/shaders were warmed by earlier validation runs. Audio used the null device because the host output device was unavailable.

| Preset | Mean ms | Median ms | p95 ms |
|---|---:|---:|---:|
| Interactive | 3.024 | 2.956 | 3.601 |
| High | 3.343 | 3.283 | 4.010 |
| Ultra | 3.122 | 3.093 | 3.466 |

These are single-run observations. Small preset differences overlap normal variation; Ultra is not claimed to outperform High. The earlier path-traced scene measured 13.491 ms during motion, but it computes different light transport. This is an algorithm comparison, not an equivalent-image speedup claim. Filament avoids Monte Carlo path noise but uses static environment lighting and screen-space refraction with their documented limitations.

Commands and scope are in [Filament integration](../../filament.md). Raw logs: [Interactive](interactive.txt), [High](high.txt), [Ultra](ultra.txt). Images: [moving studio](studio.png), [overhead view](top-view.png). Final build/test output is in [verification](verification.txt).

Release documentation: this is a historical measurement record. Use the [current release checks](../../releasing.md) for building and validating the development snapshot; preserve the recorded source hashes and results.
