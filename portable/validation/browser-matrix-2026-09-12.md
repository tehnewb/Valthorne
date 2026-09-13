# Full FPS browser compatibility investigation

Test host: Windows, 1600 × 960. This is a partial compatibility assessment;
an inability to launch a test browser is not an engine pass or failure.

| Browser | Result |
| --- | --- |
| Chrome 152 | Full FPS regression rerun after the canvas fallback; see generated `full-fps-chrome.json` |
| Playwright WebKit 26.0 | Initially failed during texture decoding because OffscreenCanvas was absent. With the fallback, the full arena, textures, HUD and Jolt simulation render. Pointer lock is denied, so the combat/input suite does not pass. |
| Playwright Firefox 146.0.1 and 144.0.2 | Both executables fail before browser startup. Windows SideBySide reports that the mozglue assembly cannot be found. Reinstallation did not resolve it. |
| Microsoft Edge | Not installed; automated installation failed. No runtime result. |
| Safari on macOS/iOS | Not tested. Windows Playwright WebKit does not establish Safari compatibility. |

The new `drawingCanvas` helper uses OffscreenCanvas when available and an
unattached HTML canvas otherwise. Image decoding, font rasterization and Nano UI
use it. The WebKit failure screenshot was inspected and shows the complete
textured game and active simulation after this fix. Mouse capture remains an
unresolved failure; the cause may involve browser automation or user activation
requirements and has not been established.

## Repeat

Build the original FPS with `node portable/fps.mjs build web`. In PowerShell:

```powershell
$env:TEST_BROWSER='firefox' # chrome, edge, firefox, or webkit
node portable/web/verify-fps.mjs
```

Install the appropriate Playwright browser first. The test selects the real
Chrome/Edge channel or the bundled Firefox/WebKit engine. It saves browser-named
success JSON, and failure JSON/screenshots, under `portable/web/build/verification`.
It does not bypass pointer lock or silently skip failed gameplay checks.
