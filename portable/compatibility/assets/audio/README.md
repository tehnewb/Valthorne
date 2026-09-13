These deterministic 60-second, 440 Hz stereo test tones were synthesized for
Valthorne with FFmpeg 7.1 at 22050 Hz. They contain no recorded or third-party art.

Encoding: libmp3lame at 32 kbit/s (`tone.mp3`), libvorbis quality 0 (`tone.ogg`).
They exercise incremental decoding, duration, seeking, looping, and cleanup.
The matching `short.mp3` and `short.ogg` fixtures contain the first two seconds,
re-encoded with the same settings to validate the buffered sound path.
