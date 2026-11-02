#pragma once

// CPU font data is independent of graphics-device lifetime. Java holds a GC-owned
// JavaScript token whose finalizer releases this native parser and encoded data.
struct BrowserFont {
    stbtt_fontinfo info;
    uint8_t* data;
};
