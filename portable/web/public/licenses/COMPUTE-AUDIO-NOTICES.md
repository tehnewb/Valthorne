# Compute and incremental audio runtimes

These unmodified runtimes are copied into `vendor/` by the web build. npm package
versions and integrity values are pinned in `portable/web/package-lock.json`.
They load locally from the built distribution; no runtime CDN is required.

| Runtime | Version/source | License notices |
| --- | --- | --- |
| mpg123-decoder | npm 1.0.3; wasm-audio-decoders commit `8f2428c1cd96b54dab74836c8471ff75fe35cbee` | JavaScript wrapper: MIT (Ethan Halsall); bundled mpg123: LGPL 2.1, see `mpg123-COPYING` |
| @wasm-audio-decoders/ogg-vorbis | npm 0.1.20; wasm-audio-decoders commit `6fe55b9a29543d0a5a2d896338c39199a81d9a58` | JavaScript wrapper: MIT (Ethan Halsall); libvorbis/libogg: BSD, see `vorbis-COPYING` and `ogg-COPYING` |
| @webgpu/glslang | npm 0.0.15 | See `glslang-LICENSE.txt` and embedded runtime notices |
| twgsl / Tint | Babylon.js 9.26.0 browser build | See `babylon-LICENSE.md` and `dawn-tint-LICENSE` |

Complete decoder sources, dependency submodules and build instructions:

- https://github.com/eshaz/wasm-audio-decoders/tree/8f2428c1cd96b54dab74836c8471ff75fe35cbee
- https://github.com/eshaz/wasm-audio-decoders/tree/6fe55b9a29543d0a5a2d896338c39199a81d9a58
- https://github.com/madebr/mpg123
- https://github.com/xiph/vorbis
- https://github.com/xiph/ogg
- https://github.com/kainino0x/glslang.js
- https://github.com/BabylonJS/Babylon.js/tree/9.26.0/packages/tools/babylonServer/public/twgsl
- https://dawn.googlesource.com/dawn/

Tint download integrity is checked before extraction/copying:

```
twgsl.js   b4f1f66263b801210f955f74aa71f1939be647ce0fd80ea5befd12a78499d5ff
twgsl.wasm a434c2decdbb38caadf5f486d806384a1543554828c983c77922b2d92579914c
```

The mpg123 decoder remains a separately replaceable browser script/WASM module.
Its source and build recipe above allow rebuilding the LGPL component; Valthorne
does not modify its decoder binary. Preserve these notices when distributing a
web build, together with the existing Filament, Jolt and other runtime notices.

The wrapper packages declare the MIT license:

Copyright (c) Ethan Halsall

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies
of the Software, and to permit persons to whom the Software is furnished to do
so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.
