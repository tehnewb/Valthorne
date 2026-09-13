# Stylized volume preview

Scene: 1100 × 760 forest, animated trees and character, three fire lights, retained UI,
rounded sprite shading and contact/projected shadows. VSync disabled.

Command: `./gradlew.bat runLighting2DExample --args="--sprite-shadows --benchmark" --no-daemon`

GPU: NVIDIA GeForce RTX 5070, OpenGL driver 610.88.
After 120 warm-up frames, 300 render samples measured mean **0.476 ms**,
median **0.465 ms**, p95 **0.642 ms**. The harness includes a GPU completion wait
and UI drawing, but excludes update and window presentation. This is a single
short local rendering measurement, not a throughput guarantee or full-game FPS.
No before/after speedup is claimed. The volume renderer currently draws per sprite;
large-world scaling and allocation profiling remain separate work.

Visual checks: rounded foliage/rocks, finite softened shadows, contact footprints,
raised path edge, settings comparison button. GPU regression coverage checks atlas
alpha preservation, shader/VAO restoration and the contact-shadow draw path.
