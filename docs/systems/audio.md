# Audio playback and ambient areas

Author: Albert Beaupre

[System manual](README.md)

## Purpose

Audio separates encoded sources, decoded or streaming data, and an active player. `Audio` owns managed OpenAL execution; `SoundPlayer` provides playback, volume, looping, seeking, and streaming behavior. Ambient areas add position-based gain without forcing every sound to become a spatially panned source.

## Features and when to use them

| Feature | Purpose |
| --- | --- |
| Buffered playback | Decode short sounds once and play from resident PCM buffers. |
| Streaming | Keep long audio encoded and replenish queued PCM chunks as playback progresses. |
| Ambient coverage | Circle, rectangle, sphere, and box areas define coverage and boundary falloff. |
| Logical listener | Publish ambient listener coordinates to reevaluate area attenuation. |
| Gain and transport | Volume, mute, loop, seek, and play/pause control the source independently of its encoded data. |

## Getting started

1. Load or construct SoundData for a supported format and choose buffered versus streaming behavior.
2. Create a player through its documented constructor or factory after audio initialization.
3. Use playback controls; for ambient sound assign a SoundArea and update `Audio.setListenerPosition`.
4. Dispose players before their backing data and before final audio shutdown.

## Ownership and lifecycle

Managed OpenAL operations run on the audio thread. OGG stream reads reuse a direct PCM destination and produce interleaved 16-bit samples. SoundSource.BytesSource retains its array, so keep encoded bytes stable while a consumer uses them.

## Important behavior

- A recognized AudioFormat signature does not imply a decoder exists for that format.
- Effective volume is source volume multiplied by area gain, before later listener/device mixing.
- The ambient listener snapshot does not change OpenAL's spatial panning transform.

## Components and examples

The sections below explain each component and its declared public or protected operations. Expand an operation reference for signatures, parameters, results, and edge cases. Inherited operations are documented with their base type. Examples embedded in component descriptions are integration fragments: supply the surrounding resources and application variables they name.

- [`Audio`](#type-audio)
- [`Audio.ListenerPosition`](#type-audio-listenerposition)
- [`AudioFormat`](#type-audioformat)
- [`Mp3SoundDecoder`](#type-mp3sounddecoder)
- [`Mp3SoundStream`](#type-mp3soundstream)
- [`OggSoundDecoder`](#type-oggsounddecoder)
- [`OggSoundStream`](#type-oggsoundstream)
- [`SoundArea`](#type-soundarea)
- [`SoundData`](#type-sounddata)
- [`SoundDecoder`](#type-sounddecoder)
- [`SoundLoader`](#type-soundloader)
- [`SoundMetadata`](#type-soundmetadata)
- [`SoundParameters`](#type-soundparameters)
- [`SoundPlayer`](#type-soundplayer)
- [`SoundSource`](#type-soundsource)
- [`SoundSource.PathSource`](#type-soundsource-pathsource)
- [`SoundSource.BytesSource`](#type-soundsource-bytessource)
- [`SoundStream`](#type-soundstream)
- [`WaveSoundDecoder`](#type-wavesounddecoder)
- [`WaveSoundStream`](#type-wavesoundstream)

<a id="type-audio"></a>

### Audio

[Source](../../src/main/java/valthorne/Audio.java#L92)

`Audio` is the central audio runtime manager for Valthorne. It owns the
OpenAL device and context, runs a dedicated audio thread, executes audio-bound
tasks on that thread, tracks active `SoundPlayer` instances, and ensures
that OpenAL operations happen from the correct execution context.

This class is intentionally static and non-instantiable. It behaves like a global
subsystem that is started once, kept alive while the engine is running, and then
disposed when audio is no longer needed. Internally, it creates a daemon thread
dedicated to audio work. That thread:

- opens the OpenAL device

- creates and activates the OpenAL context

- drains queued audio tasks

- updates all registered `SoundPlayer` instances

- disposes registered players during shutdown

The public API is designed around safe thread delegation. Code outside the audio
thread can submit work through `run(Runnable)`, synchronously execute work
through `sync(Runnable)`, or return values from audio-thread operations
through `call(Callable)`. This allows the rest of the engine to create,
destroy, and manipulate audio resources without directly owning the OpenAL thread.

Sound playback objects are represented by `SoundPlayer`. New players are
typically created through `load(String)`, `load(byte[])`, or
`create(SoundData)`. Once created, they are automatically registered so
the audio thread can call `SoundPlayer#update()` each cycle.

This design avoids forcing engine users to manually update every individual sound
player each frame. Instead, the audio subsystem owns that update lifecycle
internally and keeps OpenAL interaction isolated to a single thread.

##### Example Usage

```java
Audio.init();

SoundPlayer player = Audio.load("audio/music/theme.ogg");
player.play();

Audio.run(() -> {
    player.setLooping(true);
    player.setGain(0.6f);
});

boolean onAudioThread = Audio.isAudioThread();

Audio.destroy(player);
Audio.dispose();
```

This example demonstrates the intended full usage of the class: startup,
resource creation, queued audio-thread work, state inspection, cleanup of
players, and subsystem shutdown.

<details>
<summary>Audio operation reference (12 declarations)</summary>

#### setListenerPosition

```java
public static void setListenerPosition(float x, float y, float z)
```

Publishes the listener position used for ambient-zone attenuation without
waiting for the audio thread. Identical coordinates retain the existing snapshot
so sound players can skip redundant calculations. This does not set OpenAL's
spatial listener transform.

- **`x`** — finite horizontal world coordinate
- **`y`** — finite vertical world coordinate
- **`z`** — finite depth world coordinate

**Throws `IllegalArgumentException`:** if any coordinate is infinite or NaN

#### getListenerPosition

```java
public static ListenerPosition getListenerPosition()
```

Returns the latest published ambient-zone listener position. The immutable
snapshot can be retained across threads; a later position change publishes a
new object without modifying this one.

**Returns:** current logical listener snapshot

#### load

```java
public static SoundPlayer load(String path)
```

Loads sound data from a file path and creates a managed `SoundPlayer`
for it.

This is a convenience method that first loads `SoundData` from the given
path and then delegates to `create(SoundData)` so the resulting player
is created on the audio thread and registered for automatic updates.

- **`path`** — the path to the sound resource

**Returns:** a newly created and registered sound player

#### load

```java
public static SoundPlayer load(byte[] data)
```

Loads sound data from raw encoded bytes and creates a managed
`SoundPlayer` for it.

This is a convenience method that first decodes `SoundData` from the
provided byte array and then delegates to `create(SoundData)` so the
resulting player is created on the audio thread and registered for updates.

- **`data`** — encoded sound data bytes

**Returns:** a newly created and registered sound player

#### create

```java
public static SoundPlayer create(SoundData data)
```

Creates a new `SoundPlayer` from already prepared `SoundData`.

The actual player construction happens on the audio thread through
`call(Callable)` so OpenAL resource creation remains thread-safe.
After creation, the player is added to the tracked player list so the audio
subsystem updates it automatically during each loop iteration.

- **`data`** — the sound data used to create the player

**Returns:** the newly created and registered sound player

#### destroy

```java
public static void destroy(SoundPlayer player)
```

Destroys a managed `SoundPlayer` and removes it from the update registry.

If the provided player is `null`, this method returns immediately. When
a valid player is provided, destruction is queued onto the audio thread so
OpenAL cleanup happens safely and consistently.

- **`player`** — the player to destroy

#### run

```java
public static void run(Runnable runnable)
```

Queues an asynchronous task to run on the audio thread.

If the subsystem has not been started yet, it is started automatically through
`ensureStarted()`. If the current thread is already the audio thread,
the runnable executes immediately. Otherwise the runnable is added to the task
queue and the audio thread is unparked so it can process the work promptly.

- **`runnable`** — the task to execute on the audio thread

#### sync

```java
public static void sync(Runnable runnable)
```

Executes a runnable synchronously on the audio thread and blocks until it
completes.

This method is a convenience wrapper around `call(Callable)` for
callers that do not need a return value. The runnable is executed on the
audio thread whether the caller is already on that thread or not.

- **`runnable`** — the task to execute synchronously on the audio thread

#### call

```java
public static <T> T call(Callable<T> callable)
```

Executes a callable on the audio thread and returns its result.

If the current thread is already the audio thread, the callable is invoked
immediately. Otherwise a `FutureTask` is enqueued and the calling thread
blocks until the result is available.

Checked exceptions thrown by the callable are wrapped in
`RuntimeException`. Runtime exceptions are rethrown directly.

- **`callable`** — the task to execute
- **`<T>`** — the result type

**Returns:** the result produced by the callable

**Throws `RuntimeException`:** if execution fails or waiting for the result fails

#### isAudioThread

```java
public static boolean isAudioThread()
```

Returns whether the current thread is the dedicated audio thread.

This check is useful when enforcing thread ownership rules for OpenAL-backed
operations or when deciding whether a task should execute immediately or be
queued for later processing.

**Returns:** `true` if the current thread is the audio thread

#### register

```java
public static void register(SoundPlayer player)
```

Registers a `SoundPlayer` with the audio subsystem so it will be updated
each loop iteration.

If the player is `null`, the method returns immediately. If the current
thread is already the audio thread, registration happens immediately. Otherwise
registration is queued through `run(Runnable)`. Duplicate registration
is avoided by checking whether the player already exists in the tracked list.

- **`player`** — the player to register

#### unregister

```java
public static void unregister(SoundPlayer player)
```

Unregisters a `SoundPlayer` from the audio subsystem.

This removes the player from the automatic update list but does not, by itself,
guarantee full resource destruction unless caller logic also disposes it.

If the current thread is already the audio thread, removal happens immediately.
Otherwise it is queued for execution on the audio thread.

- **`player`** — the player to unregister

</details>

<a id="type-audio-listenerposition"></a>

### Audio.ListenerPosition

[Source](../../src/main/java/valthorne/Audio.java#L146)

Immutable logical listener coordinates used by ambient sound areas.
Publishing a new snapshot triggers area-gain reevaluation without changing
OpenAL's panning transform. Use the same world-coordinate units as the sound areas.

- **`x`** — listener horizontal coordinate
- **`y`** — listener vertical coordinate
- **`z`** — listener depth coordinate

<a id="type-audioformat"></a>

### AudioFormat

[Source](../../src/main/java/valthorne/audio/AudioFormat.java#L39)

`AudioFormat` enumerates the audio container or codec types currently recognized
by Valthorne's sound loading pipeline. Each enum constant stores the signature bytes
used for format detection, whether special MP3 frame-sync fallback logic should be
applied, and the `SoundDecoder` responsible for decoding that format when
decoding is supported.

The main purpose of this enum is to provide a single place where format detection and
decoder selection are defined. Callers typically use `detect(byte[])` to inspect
a file header or raw audio byte array and then obtain the appropriate decoder through
`getDecoder()`.

##### Example Usage

```java
byte[] bytes = Files.readAllBytes(Path.of("music.ogg"));
AudioFormat format = AudioFormat.detect(bytes);
SoundDecoder decoder = format.getDecoder();

if (decoder != null) {
    SoundData sound = decoder.decode(bytes);
}
```

<details>
<summary>AudioFormat operation reference (12 declarations)</summary>

#### WAV

```java
public static final  AudioFormat WAV
```

RIFF/WAVE signature with the built-in WAV decoder.

#### OGG

```java
public static final  AudioFormat OGG
```

Ogg container signature with the built-in Vorbis decoder.

#### MP3

```java
public static final  AudioFormat MP3
```

MP3 identification using ID3 signatures and frame-sync fallback, with a built-in decoder.

#### FLAC

```java
public static final  AudioFormat FLAC
```

FLAC signature recognized for identification; no decoder is registered here.

#### AIFF

```java
public static final  AudioFormat AIFF
```

AIFF container signature recognized for identification; no decoder is registered here.

#### AAC

```java
public static final  AudioFormat AAC
```

AAC ADTS signatures recognized for identification; no decoder is registered here.

#### OPUS

```java
public static final  AudioFormat OPUS
```

Opus header signature recognized for identification; no decoder is registered here.

#### MID

```java
public static final  AudioFormat MID
```

MIDI header signature recognized for identification; no synthesizer or decoder is registered here.

#### AU

```java
public static final  AudioFormat AU
```

Sun/NeXT AU signature recognized for identification; no decoder is registered here.

#### UNKNOWN

```java
public static final  AudioFormat UNKNOWN
```

Fallback when no known signature matches; supplies no decoder.

#### detect

```java
public static AudioFormat detect(byte[] data)
```

Detects the format of the supplied audio bytes.

- **`data`** — the audio data to inspect

**Returns:** the detected audio format, or `UNKNOWN` when no match is found

#### getDecoder

```java
public SoundDecoder getDecoder()
```

Returns the decoder associated with this format.

**Returns:** the decoder, or `null` when decoding is not currently supported

</details>

<a id="type-mp3sounddecoder"></a>

### Mp3SoundDecoder

[Source](../../src/main/java/valthorne/audio/sound/Mp3SoundDecoder.java#L24)

`Mp3SoundDecoder` decodes MP3 audio into 16-bit signed PCM using the Java
sound system. It also exposes probing helpers that extract lightweight metadata
without building a full `SoundData` instance.

<details>
<summary>Mp3SoundDecoder operation reference (3 declarations)</summary>

#### probe

```java
public static SoundMetadata probe(byte[] data) throws Exception
```

Probes MP3 metadata from an in-memory byte array.

- **`data`** — the encoded MP3 bytes

**Returns:** the probed metadata

**Throws `Exception`:** if probing fails

#### probe

```java
public static SoundMetadata probe(String path) throws Exception
```

Probes MP3 metadata from a file path.

- **`path`** — the file path to inspect

**Returns:** the probed metadata

**Throws `Exception`:** if probing fails

#### decode

```java
    public SoundData decode(byte[] data) throws Exception
```

Decodes MP3 bytes into buffered PCM sound data.

- **`data`** — the encoded MP3 bytes

**Returns:** the decoded sound data

**Throws `Exception`:** if decoding fails

</details>

<a id="type-mp3soundstream"></a>

### Mp3SoundStream

[Source](../../src/main/java/valthorne/audio/sound/Mp3SoundStream.java#L19)

`Mp3SoundStream` provides chunked PCM decoding for MP3 data using the Java
sound system. It supports both file-backed and in-memory sources and can reopen
itself to implement seeking.

<details>
<summary>Mp3SoundStream operation reference (8 declarations)</summary>

#### Constructor

```java
public Mp3SoundStream(SoundData data)
```

Creates a new MP3 stream for the supplied sound data.

- **`data`** — the stream-backed sound data

#### channels

```java
    public int channels()
```

Reads the decoded PCM channel count without advancing the stream cursor.

**Returns:** the channel count produced by this stream

#### sampleRate

```java
    public int sampleRate()
```

Reads the decoded PCM sampling frequency in frames per second (Hz), independent of playback position.

**Returns:** the sample rate produced by this stream

#### bitsPerSample

```java
    public int bitsPerSample()
```

Reads the decoded PCM bit depth per channel sample, rather than the compressed file bitrate.

**Returns:** the bits per sample produced by this stream

#### duration

```java
    public float duration()
```

Reads total decoded-content duration in seconds, not remaining time or the current playback position.

**Returns:** the total stream duration in seconds

#### seek

```java
    public boolean seek(float seconds)
```

Seeks by reopening the MP3 stream and discarding decoded PCM until the target time is reached.

- **`seconds`** — the target time in seconds

**Returns:** `true` if seeking succeeded

#### read

```java
    public int read(ByteBuffer pcmBuffer)
```

Reads decoded PCM bytes into the supplied buffer.

- **`pcmBuffer`** — the destination PCM buffer

**Returns:** the number of bytes read

#### close

```java
    public void close()
```

Closes both source and PCM streams.

</details>

<a id="type-oggsounddecoder"></a>

### OggSoundDecoder

[Source](../../src/main/java/valthorne/audio/sound/OggSoundDecoder.java#L27)

Decodes complete OGG Vorbis payloads into interleaved 16-bit PCM and probes
metadata without retaining a streaming decoder. Probing accepts either a file
path or encoded bytes; full decoding accepts bytes and returns independent
buffered SoundData. Temporary native decoder and decoded-sample allocations
are released after use.

For long audio that should not be held fully decoded in memory, use the separate
OggSoundStream implementation. This decoder creates no OpenAL playback source.

<details>
<summary>OggSoundDecoder operation reference (3 declarations)</summary>

#### probe

```java
public static SoundMetadata probe(byte[] data)
```

Opens a temporary native decoder over a direct copy of the encoded bytes and
reads channels, sample rate, and stream length. No PCM buffer is returned; the
decoder is closed even when metadata extraction fails.

- **`data`** — complete encoded OGG Vorbis payload

**Returns:** metadata with 16-bit PCM depth, encoded byte length, and duration in
seconds, or minus one duration when sample count is unavailable

**Throws `RuntimeException`:** if STB cannot open the encoded stream

#### probe

```java
public static SoundMetadata probe(String path)
```

Opens a temporary decoder using an absolute filesystem path, reads metadata,
and closes the decoder. A separate file-size failure records minus one encoded
bytes without discarding successfully read audio metadata.

- **`path`** — filesystem path to an OGG Vorbis file

**Returns:** metadata with 16-bit PCM depth and duration in seconds; unavailable
duration or encoded byte count is represented by minus one

**Throws `RuntimeException`:** if STB cannot open the file

#### decode

```java
    public SoundData decode(byte[] data) throws Exception
```

Decodes the complete payload into interleaved signed 16-bit PCM.
Copies native STB samples into a separate direct buffer before freeing the STB
allocation. Returned SoundData retains this PCM buffer and does not retain the
encoded input or create an OpenAL source.

- **`data`** — complete encoded OGG Vorbis bytes

**Returns:** buffered PCM data with channel count, sample rate, and duration

**Throws `Exception`:** if decoding fails or the decoded byte count cannot be represented

</details>

<a id="type-oggsoundstream"></a>

### OggSoundStream

[Source](../../src/main/java/valthorne/audio/sound/OggSoundStream.java#L22)

Decodes file-backed or memory-backed OGG Vorbis audio into interleaved signed
16-bit PCM through an owned native STB decoder. Reads and seeks share one decoder
position and must be externally serialized; instances are not thread-safe.

Close the stream to release the native decoder. Closing is idempotent and leaves
metadata accessible, but further reads and seeks fail. Memory-backed encoded data
is retained for the decoder's lifetime.

<details>
<summary>OggSoundStream operation reference (8 declarations)</summary>

#### Constructor

```java
public OggSoundStream(SoundData data)
```

Creates a new OGG stream for the supplied sound data.

- **`data`** — the stream-backed sound data

#### channels

```java
    public int channels()
```

Reads the decoded PCM channel count without advancing the stream cursor.

**Returns:** the channel count produced by this stream

#### sampleRate

```java
    public int sampleRate()
```

Reads the decoded PCM sampling frequency in frames per second (Hz), independent of playback position.

**Returns:** the sample rate produced by this stream

#### bitsPerSample

```java
    public int bitsPerSample()
```

Reads the decoded PCM bit depth per channel sample, rather than the compressed file bitrate.

**Returns:** the bits per sample produced by this stream

#### duration

```java
    public float duration()
```

Reads total decoded-content duration in seconds, not remaining time or the current playback position.

**Returns:** the total stream duration in seconds

#### seek

```java
    public boolean seek(float seconds)
```

Seeks to a target playback time.

- **`seconds`** — the target time in seconds

**Returns:** `true` if seeking succeeded

#### read

```java
    public int read(ByteBuffer pcmBuffer)
```

Decodes from the current stream position into a direct PCM destination.
The destination is cleared first, so its incoming position and limit are ignored.
On return its position is zero and its limit is the number of decoded bytes.
Samples are interleaved signed 16-bit values; use native byte order when reading
them. A cached short view is reused while the same destination object is supplied.

- **`pcmBuffer`** — writable direct buffer for complete interleaved PCM frames

**Returns:** bytes produced, or zero when no more frames can be decoded

**Throws `IllegalStateException`:** if this stream has been closed

#### close

```java
    public void close()
```

Closes the native decoder handle.

</details>

<a id="type-soundarea"></a>

### SoundArea

[Source](../../src/main/java/valthorne/audio/sound/SoundArea.java#L11)

Immutable ambient sound zone. Full gain inside, smoothstep attenuation outside,
zero gain at and beyond the fade distance. Two-dimensional zones ignore Z.
Evaluation allocates no objects. Coordinates and dimensions must be finite.
This models ambient coverage, not directional panning, occlusion or reverb.

<details>
<summary>SoundArea operation reference (5 declarations)</summary>

#### circle

```java
public static SoundArea circle(float x, float y, float radius, float fade)
```

Creates a circular full-gain zone in the XY plane with smooth attenuation
beyond its radius. A zero fade makes the boundary abrupt; listener Z is
ignored geometrically but must still be finite during evaluation.

- **`x`** — finite center X
- **`y`** — finite center Y
- **`radius`** — finite nonnegative full-gain radius
- **`fade`** — finite nonnegative outer fade distance

**Returns:** immutable circular zone

**Throws `IllegalArgumentException`:** if inputs violate the stated ranges

#### sphere

```java
public static SoundArea sphere(float x, float y, float z, float radius, float fade)
```

Creates a spherical full-gain zone with smooth attenuation outside its
surface. At a zero fade distance, gain drops immediately outside the sphere.

- **`x`** — finite center X
- **`y`** — finite center Y
- **`z`** — finite center Z
- **`radius`** — finite nonnegative full-gain radius
- **`fade`** — finite nonnegative outer fade distance

**Returns:** immutable spherical zone

**Throws `IllegalArgumentException`:** if inputs violate the stated ranges

#### rectangle

```java
public static SoundArea rectangle(float x, float y, float halfWidth, float halfHeight, float fade)
```

Creates an axis-aligned XY rectangle specified by center and half-extents.
Outside attenuation uses Euclidean distance to the rectangle, producing
rounded fade corners rather than a larger rectangular border.

- **`x`** — finite center X
- **`y`** — finite center Y
- **`halfWidth`** — finite nonnegative X half-extent
- **`halfHeight`** — finite nonnegative Y half-extent
- **`fade`** — finite nonnegative distance outside the rectangle

**Returns:** immutable two-dimensional zone

**Throws `IllegalArgumentException`:** if inputs violate the stated ranges

#### box

```java
public static SoundArea box(float x, float y, float z, float halfWidth,
                                float halfHeight, float halfDepth, float fade)
```

Creates an axis-aligned full-gain box with Euclidean-distance attenuation
outside its surface. Half-extents and fade may be zero; no rotation is stored.

- **`x`** — finite center X
- **`y`** — finite center Y
- **`z`** — finite center Z
- **`halfWidth`** — finite nonnegative X half-extent
- **`halfHeight`** — finite nonnegative Y half-extent
- **`halfDepth`** — finite nonnegative Z half-extent
- **`fade`** — finite nonnegative distance outside the box

**Returns:** immutable three-dimensional zone

**Throws `IllegalArgumentException`:** if inputs violate the stated ranges

#### gainAt

```java
public float gainAt(float listenerX, float listenerY, float listenerZ)
```

Evaluates full gain inside or on the boundary and smoothstep falloff outside.
Returns zero once outside distance reaches fade. With zero fade, boundary
points still receive full gain and every outside point receives zero. Uses
no per-call objects and validates all coordinates, including ignored Z.

- **`listenerX`** — finite listener world X
- **`listenerY`** — finite listener world Y
- **`listenerZ`** — finite listener world Z

**Returns:** gain multiplier between zero and one inclusive

**Throws `IllegalArgumentException`:** if any listener coordinate is non-finite

</details>

<a id="type-sounddata"></a>

### SoundData

[Source](../../src/main/java/valthorne/audio/sound/SoundData.java#L53)

`SoundData` is the central immutable description of loaded sound content in
Valthorne. It can represent either fully buffered PCM data or a stream-backed audio
source depending on the format, metadata, and loader heuristics.

Instances store decoded PCM data when the sound is buffered, or stream metadata and
source information when the sound should be decoded incrementally at playback time.
The factory methods `load(byte[])` and `load(String)` perform format
detection, metadata probing, compression classification, and buffered-versus-streamed
selection automatically.

##### Example Usage

```java
SoundData click = SoundData.load("assets/sfx/click.wav");
SoundData theme = SoundData.load(Files.readAllBytes(Path.of("assets/music/theme.ogg")));

if (theme.streaming()) {
    try (SoundStream stream = theme.openStream()) {
        stream.seek(10f);
    }
}
```

- **`source`** — the original source used for stream reopening, or `null` for fully buffered sounds
- **`data`** — the decoded PCM data for buffered sounds, or `null` for streamed sounds
- **`streamOffset`** — the start offset used when opening a stream from the source
- **`streamLength`** — the readable stream length in bytes
- **`duration`** — the sound duration in seconds
- **`channels`** — the number of audio channels
- **`sampleRate`** — the sample rate in hertz
- **`bitsPerSample`** — the bits per sample
- **`streaming`** — whether this sound should be played through a stream
- **`compressed`** — whether the original format is compressed
- **`format`** — the detected audio format

<details>
<summary>SoundData operation reference (5 declarations)</summary>

#### load

```java
public static SoundData load(byte[] bytes)
```

Loads sound data from an in-memory byte array.

- **`bytes`** — the encoded sound bytes

**Returns:** the loaded sound data

#### load

```java
public static SoundData load(String path)
```

Loads sound data from a filesystem path.

- **`path`** — the file path to load

**Returns:** the loaded sound data

#### openStream

```java
public SoundStream openStream()
```

Opens a new decoded stream for this sound.

**Returns:** a new sound stream

#### estimatedPcmBytes

```java
public long estimatedPcmBytes()
```

Estimates the PCM byte count represented by this sound.

**Returns:** the estimated PCM byte count, `-1` when insufficient data exists,
or `Long#MAX_VALUE` if the estimate would overflow

#### asSoundPlayer

```java
public SoundPlayer asSoundPlayer()
```

Creates a managed player on the audio thread, with automatic stream and area updates.

**Returns:** a new SoundPlayer initialized with the current SoundData

</details>

<a id="type-sounddecoder"></a>

### SoundDecoder

[Source](../../src/main/java/valthorne/audio/sound/SoundDecoder.java#L25)

`SoundDecoder` defines the contract used by Valthorne to convert raw encoded
audio bytes into a fully decoded `SoundData` instance suitable for playback
or buffering.

Implementations are format-specific. For example, WAV, OGG, and MP3 decoders each
interpret the incoming bytes differently but all expose the same decode operation.

##### Example Usage

```java
SoundDecoder decoder = new WaveSoundDecoder();
SoundData data = decoder.decode(bytes);
```

<details>
<summary>SoundDecoder operation reference (1 declarations)</summary>

#### decode

```java
SoundData decode(byte[] data) throws Exception
```

Decodes the supplied encoded audio bytes into `SoundData`.

- **`data`** — the encoded audio bytes to decode

**Returns:** the decoded sound data

**Throws `Exception`:** if decoding fails for any reason

</details>

<a id="type-soundloader"></a>

### SoundLoader

[Source](../../src/main/java/valthorne/audio/sound/SoundLoader.java#L23)

`SoundLoader` is the asset-loader bridge between generic asset loading and the
sound system. It accepts `SoundParameters`, inspects the underlying
`SoundSource`, and delegates loading to the appropriate `SoundData`
factory method.

##### Example Usage

```java
SoundLoader loader = new SoundLoader();
SoundData data = loader.load(SoundParameters.fromPath("music.ogg", "theme"));
```

<details>
<summary>SoundLoader operation reference (1 declarations)</summary>

#### load

```java
    public SoundData load(SoundParameters parameters)
```

Loads sound data from the source defined by the supplied parameters.

- **`parameters`** — the sound loading parameters

**Returns:** the loaded sound data

</details>

<a id="type-soundmetadata"></a>

### SoundMetadata

[Source](../../src/main/java/valthorne/audio/sound/SoundMetadata.java#L26)

`SoundMetadata` stores lightweight information about a piece of audio without
requiring the full decoded PCM data to be retained. It is primarily used during
probing and stream setup so the loader can make buffering and streaming decisions.

##### Example Usage

```java
SoundMetadata metadata = WaveSoundDecoder.probe(bytes);
long estimated = metadata.estimatedPcmBytes();
```

- **`duration`** — the duration in seconds, or a negative value when unknown
- **`channels`** — the number of audio channels
- **`sampleRate`** — the sample rate in hertz
- **`bitsPerSample`** — the number of bits per sample
- **`dataOffset`** — the byte offset where streamable PCM or encoded data begins
- **`dataLength`** — the byte length of the relevant data section

<details>
<summary>SoundMetadata operation reference (1 declarations)</summary>

#### estimatedPcmBytes

```java
public long estimatedPcmBytes()
```

Estimates the decoded PCM byte size represented by this metadata.

**Returns:** the estimated PCM size, `-1` when insufficient information is available,
or `Long#MAX_VALUE` if the estimate would overflow

</details>

<a id="type-soundparameters"></a>

### SoundParameters

[Source](../../src/main/java/valthorne/audio/sound/SoundParameters.java#L24)

Represents the parameters required to initialize or manage a sound resource.
This record combines a `SoundSource`, which specifies the source of the
sound data, and a human-readable name used as an identifier or key for the sound.

Instances of this class enforce immutability and validation of input parameters.

- **`source`** — The source of the sound data, which can be from a path or in-memory bytes. Must not be null.
- **`name`** — The unique name or identifier for the sound. Must not be null or blank.  Only name supplies the shared asset-cache key; source do not distinguish cached requests. Use different names for variants that must coexist. Path factories defer file reads, byte factories defensively copy input, and classpath factories read the resource immediately before wrapping its bytes.

<details>
<summary>SoundParameters operation reference (7 declarations)</summary>

#### Constructor

```java
public SoundParameters
```

Constructs an instance of `SoundParameters` with the specified sound source and name.
Validates that the provided parameters are not null or blank to ensure correctness.

- **`source`** — The source of the sound data, such as a file path or in-memory bytes. Must not be null.
- **`name`** — The unique name or identifier for the sound. Must not be null or blank.

**Throws `IllegalArgumentException`:** If `source` is null or `name` is null/empty/blank.

#### fromPath

```java
public static SoundParameters fromPath(String path)
```

Creates a `SoundParameters` instance using a file path as the sound source.
This method initializes the `SoundSource` as a `SoundSource.PathSource`
with the provided path, and uses the same path as the name for the sound.

- **`path`** — The path to the sound file, representing the location of the sound resource. Must not be null or blank.

**Returns:** A `SoundParameters` instance with the `SoundSource.PathSource`
initialized from the provided path, and the name set to that path.

**Throws `IllegalArgumentException`:** If the provided path is null or blank.

#### fromPath

```java
public static SoundParameters fromPath(String path, String name)
```

Creates a `SoundParameters` instance using a file path as the sound source and a custom name.
This method initializes the `SoundSource` as a `SoundSource.PathSource`
with the provided path and sets the name explicitly.

- **`path`** — The path to the sound file, representing the location of the sound resource. Must not be null or blank.
- **`name`** — A custom name or identifier for the sound. Must not be null or blank.

**Returns:** A `SoundParameters` instance with the `SoundSource.PathSource`
initialized from the provided path and the name explicitly set to the provided value.

**Throws `IllegalArgumentException`:** If the provided path or name is null or blank.

#### fromBytes

```java
public static SoundParameters fromBytes(byte[] bytes, String name)
```

Creates a `SoundParameters` instance using raw sound data bytes and a custom name.
This method initializes the `SoundSource` as a `SoundSource.BytesSource`
with the provided byte array and sets the name explicitly.

- **`bytes`** — A byte array representing the raw sound data. Must not be null or empty.
- **`name`** — A custom name or identifier for the sound. Must not be null or blank.

**Returns:** A `SoundParameters` instance with the `SoundSource.BytesSource`
initialized from the provided byte array and the name explicitly set to the provided value.

**Throws `IllegalArgumentException`:** If the `bytes` array is null or empty,
or if `name` is null or blank.

#### fromClasspath

```java
public static SoundParameters fromClasspath(String resourcePath)
```

Creates a `SoundParameters` instance using a classpath resource as the sound source.
This method loads the resource's bytes and uses the resource path as the name of the sound.

- **`resourcePath`** — The path to the resource file, relative to the classpath root. Must not be null or blank.

**Returns:** A `SoundParameters` instance with the sound source initialized from
the resource bytes and the name set to the resource path.

**Throws `IllegalArgumentException`:** If the `resourcePath` is null or empty,
or if the resource could not be loaded.

**Throws `valthorne.io.file.ValthorneFileException`:** if the classpath resource
is missing or cannot be read; resource bytes are read synchronously

#### fromClasspath

```java
public static SoundParameters fromClasspath(String resourcePath, String name)
```

Creates a `SoundParameters` instance using a classpath resource as the sound source
and a custom name. This method loads the resource's bytes and sets the name explicitly.

- **`resourcePath`** — The path to the resource file, relative to the classpath root. Must not be null or blank.
- **`name`** — A custom name or identifier for the sound. Must not be null or blank.

**Returns:** A `SoundParameters` instance with the sound source initialized from
the resource bytes and the name explicitly set to the provided value.

**Throws `IllegalArgumentException`:** If the `resourcePath` is null or empty,
or if the resource could not be loaded,
or if `name` is null or blank.

**Throws `valthorne.io.file.ValthorneFileException`:** if the classpath resource
is missing or cannot be read; resource bytes are read synchronously

#### key

```java
    public String key()
```

Returns name as the asset-cache identity without including source contents
or loading options. Equal names can therefore reuse an existing cached load
even when other parameters differ.

**Returns:** nonblank cache key supplied at construction

</details>

<a id="type-soundplayer"></a>

### SoundPlayer

[Source](../../src/main/java/valthorne/audio/sound/SoundPlayer.java#L102)

`SoundPlayer` is Valthorne's high-level playback controller for a single
`SoundData` instance. It wraps one OpenAL source and handles all of the
state and operations needed to play, pause, stop, seek, loop, mute, change
playback speed, and dispose audio safely.

This class supports two different playback models:

- **Fully buffered playback**, where the decoded audio is uploaded
once into a single OpenAL buffer and then played directly from that buffer.

- **Streaming playback**, where audio is read incrementally from a
`SoundStream`, filled into a rotating set of OpenAL buffers, and queued
onto the source as playback progresses.

The playback mode is determined by `SoundData#streaming()`. If the sound data
is not streaming, this player creates one normal OpenAL buffer and binds it to the
source. If the sound data is streaming, this player creates a small pool of stream
buffers and continuously refills them as processed buffers are returned by OpenAL.

All OpenAL work must happen on the audio thread owned by `Audio`. To enforce
that safely, public methods route work through `Audio#sync(Runnable)` or
`Audio#call(java.util.concurrent.Callable)` when needed. If the caller is
already on the audio thread, work executes immediately. This means the class is
convenient to use from normal game code while still preserving proper OpenAL
threading rules.

In addition to normal playback controls, this class also provides:

- time seeking in seconds

- progress-based seeking

- relative seek operations

- wording-friendly helpers like `restart()`, `skipToStart()`,
`skipToEnd()`, `stepForward()`, and `stepBackward()`

- volume and mute controls

- pitch-based playback speed changes

- automatic stream rewinding and loop refill logic for streamed audio

For streaming sounds, `update()` must be called from the audio system so
processed buffers can be unqueued, timed correctly, refilled, and requeued. In
Valthorne, that update lifecycle is handled by `Audio`, so engine users
normally do not need to update each player manually.

##### Example Usage

```java
SoundPlayer player = Audio.load("audio/music/theme.ogg");

player.setLooping(true);
player.setVolume(0.65f);
player.play();

float duration = player.duration();
float progress = player.getProgress();

player.fastForward(10f);
player.pause();
player.resume();

player.setPlaybackSpeed(1.25f);
player.toggleMute();
player.stop();

player.dispose();
```

This example demonstrates the full intended use of the class: creation through
the audio system, playback control, seeking, playback-speed changes, mute control,
state inspection, and disposal.

<details>
<summary>SoundPlayer operation reference (54 declarations)</summary>

#### setArea

```java
public void setArea(SoundArea area)
```

Assigns immutable ambient coverage on the audio thread and recalculates gain
at the latest listener position. Playback position and playing state are preserved.
Passing null removes area attenuation; the player's configured volume still applies.

- **`area`** — ambient coverage, or null for unrestricted playback

#### getArea

```java
public SoundArea getArea()
```

Retrieves the current ambient coverage through the audio-thread query mechanism.
The returned area is immutable and may be shared without transferring ownership.

**Returns:** current coverage, or null when no area attenuation is configured

#### getEffectiveVolume

```java
public float getEffectiveVolume()
```

Recalculates area attenuation when the listener snapshot has changed and returns
the source gain applied to OpenAL. This is configured volume multiplied by area
gain; it does not include listener gain or subsequent device mixing.

**Returns:** most recently applied source gain

#### Constructor

```java
public SoundPlayer(SoundData data)
```

Creates a new sound player for the supplied `SoundData`.

This constructor immediately allocates an OpenAL source. From there, one of two
initialization paths is taken:

- If the data is streaming, the player allocates the rotating stream buffer
set, creates the temporary chunk buffer, and primes the stream through
`resetStream(float)`.

- If the data is not streaming, the player allocates a single OpenAL buffer,
copies the PCM data into it, and binds that buffer directly to the source.

After the source and buffers are prepared, the player applies its initial default
state of full volume, normal pitch, and looping disabled.

- **`data`** — the sound data that this player will control

#### update

```java
public void update()
```

Updates streamed playback state.

Updates ambient attenuation for either playback mode. For streamed sounds, it must
run on the audio thread and is responsible for:

- unqueueing processed stream buffers

- advancing `streamedSeconds`

- re-filling buffers with new audio data

- re-queueing buffers onto the source

- resuming playback if the player still wants to be playing and buffers exist

In normal Valthorne usage, this is called automatically by the global audio
subsystem.

#### play

```java
public void play()
```

Starts playback of this sound.

For streamed sounds, this may also re-prime the stream if no playable buffers
are currently queued. For non-streaming sounds, it simply plays the source.

#### pause

```java
public void pause()
```

Pauses playback of this sound.

For streamed sounds, this also clears the logical desire to keep playing until
a future resume or play request happens.

#### resume

```java
public void resume()
```

Resumes playback after a pause.

For streamed sounds, this also ensures that the stream still has playable
buffers available and re-primes them if necessary before playback resumes.

#### stop

```java
public void stop()
```

Stops playback of this sound.

For streamed sounds, stopping also resets the stream back to the beginning.
For fully buffered sounds, the source is stopped and then rewound.

#### rewind

```java
public void rewind()
```

Rewinds the sound back to the beginning without automatically starting playback.

For streamed sounds, this is implemented through seeking to time zero. For
non-streaming sounds, it directly rewinds the OpenAL source.

#### isPlaying

```java
public boolean isPlaying()
```

Returns whether the source is currently in the OpenAL playing state.

**Returns:** `true` if the source is currently playing

#### isPaused

```java
public boolean isPaused()
```

Returns whether the source is currently paused.

**Returns:** `true` if the source is currently paused

#### isStopped

```java
public boolean isStopped()
```

Returns whether the source is currently stopped or still in its initial state.

**Returns:** `true` if the source is not actively playing or paused

#### duration

```java
public float duration()
```

Returns the full duration of the underlying sound data in seconds.

**Returns:** the total sound duration in seconds

#### getCurrentTime

```java
public float getCurrentTime()
```

Returns the current playback position in seconds.

For fully buffered sounds, this is read directly from OpenAL. For streamed
sounds, the value is reconstructed from `streamedSeconds` plus the
source's current buffer offset.

**Returns:** the current playback position in seconds

#### setCurrentTime

```java
public void setCurrentTime(float seconds)
```

Sets the current playback position in seconds.

The requested time is clamped to a valid range before being applied. For streamed
sounds, seeking rebuilds stream state and may resume or re-pause playback
depending on the state before the seek.

- **`seconds`** — the desired playback position in seconds

#### getVolume

```java
public float getVolume()
```

Returns the current effective source gain.

**Returns:** the current source volume in the `[0, 1]` range

#### setVolume

```java
public void setVolume(float volume)
```

Sets the source gain.

The value is clamped into the `[0, 1]` range before being applied to
OpenAL.

- **`volume`** — the desired volume

#### getPitch

```java
public float getPitch()
```

Returns the current source pitch.

**Returns:** the current pitch value

#### setPitch

```java
public void setPitch(float pitch)
```

Sets the source pitch.

Pitch is clamped into a safe playback range before it is applied.

- **`pitch`** — the desired pitch value

#### isLooping

```java
public boolean isLooping()
```

Returns whether looping is enabled for this player.

For streamed sounds, the value is tracked manually. For non-streaming sounds,
it is also mirrored into the OpenAL source state.

**Returns:** `true` if looping is enabled

#### setLooping

```java
public void setLooping(boolean looping)
```

Enables or disables looping for this player.

- **`looping`** — whether looping should be enabled

#### playFromStart

```java
public void playFromStart()
```

Rewinds the player to the beginning and starts playback immediately.

#### playFrom

```java
public void playFrom(float seconds)
```

Seeks to the given playback position and starts playback immediately.

- **`seconds`** — the playback position in seconds to start from

#### restart

```java
public void restart()
```

Restarts playback from the beginning.

This is a convenience alias for `playFromStart()`.

#### fastForward

```java
public void fastForward(float seconds)
```

Moves playback forward by a relative number of seconds.

- **`seconds`** — the amount of time to skip forward

#### rewind

```java
public void rewind(float seconds)
```

Moves playback backward by a relative number of seconds.

- **`seconds`** — the amount of time to move backward

#### seekBy

```java
public void seekBy(float seconds)
```

Moves playback by a signed relative offset in seconds.

Positive values move forward. Negative values move backward.

- **`seconds`** — the relative seek amount

#### skipToStart

```java
public void skipToStart()
```

Jumps playback to the start of the sound.

#### skipToEnd

```java
public void skipToEnd()
```

Jumps playback to the end of the sound.

#### stepForward

```java
public void stepForward()
```

Moves playback forward by the default step amount of five seconds.

#### stepBackward

```java
public void stepBackward()
```

Moves playback backward by the default step amount of five seconds.

#### stepForward

```java
public void stepForward(float seconds)
```

Moves playback forward by a caller-defined step amount.

- **`seconds`** — the step size in seconds

#### stepBackward

```java
public void stepBackward(float seconds)
```

Moves playback backward by a caller-defined step amount.

- **`seconds`** — the step size in seconds

#### togglePlayPause

```java
public void togglePlayPause()
```

Toggles between playing and paused states.

#### toggleLooping

```java
public void toggleLooping()
```

Toggles the looping flag.

#### isFinished

```java
public boolean isFinished()
```

Returns whether playback has fully finished.

A looping sound is never considered finished. For streamed audio, this checks
that playback is no longer active, no playable buffers remain, and the playback
position has reached the duration.

**Returns:** `true` if playback is complete

#### getProgress

```java
public float getProgress()
```

Returns the current playback progress as a normalized value in the
`[0, 1]` range.

**Returns:** normalized playback progress

#### setProgress

```java
public void setProgress(float progress)
```

Sets playback progress using a normalized value in the `[0, 1]` range.

- **`progress`** — the desired normalized progress

#### fastForwardPercent

```java
public void fastForwardPercent(float percent)
```

Moves playback forward by a percentage of the full sound duration.

- **`percent`** — the normalized percentage of the duration to move forward

#### rewindPercent

```java
public void rewindPercent(float percent)
```

Moves playback backward by a percentage of the full sound duration.

- **`percent`** — the normalized percentage of the duration to move backward

#### mute

```java
public void mute()
```

Mutes the player while remembering the current volume so it can be restored later.

#### unmute

```java
public void unmute()
```

Restores the remembered volume and clears muted state.

#### toggleMute

```java
public void toggleMute()
```

Toggles the mute state.

#### isMuted

```java
public boolean isMuted()
```

Returns whether this player is currently muted.

**Returns:** `true` if muted or effectively silent

#### volumeUp

```java
public void volumeUp(float amount)
```

Raises volume by the given amount.

- **`amount`** — the amount to increase volume by

#### volumeDown

```java
public void volumeDown(float amount)
```

Lowers volume by the given amount.

- **`amount`** — the amount to decrease volume by

#### getPlaybackSpeed

```java
public float getPlaybackSpeed()
```

Returns the current playback speed value.

Internally, playback speed is represented through OpenAL pitch.

**Returns:** the current playback speed multiplier

#### setPlaybackSpeed

```java
public void setPlaybackSpeed(float speed)
```

Sets playback speed by mapping the given speed directly to source pitch.

- **`speed`** — the desired playback speed multiplier

#### normalSpeed

```java
public void normalSpeed()
```

Resets playback speed to normal.

#### slower

```java
public void slower()
```

Decreases playback speed by a small fixed step.

#### faster

```java
public void faster()
```

Increases playback speed by a small fixed step.

#### dispose

```java
public void dispose()
```

Disposes all resources owned by this player.

This stops playback, releases OpenAL buffers and sources, closes any active
stream if present, and unregisters the player from `Audio`.

#### getData

```java
public SoundData getData()
```

Returns the underlying sound data object used by this player.

**Returns:** the backing sound data

</details>

<a id="type-soundsource"></a>

### SoundSource

[Source](../../src/main/java/valthorne/audio/sound/SoundSource.java#L20)

`SoundSource` describes where raw sound data originates. It is modeled as a
sealed interface with compact record implementations for file-path sources and
in-memory byte-array sources.

##### Example Usage

```java
SoundSource pathSource = new SoundSource.PathSource("music/theme.ogg");
SoundSource bytesSource = new SoundSource.BytesSource(bytes);
```

<a id="type-soundsource-pathsource"></a>

### SoundSource.PathSource

[Source](../../src/main/java/valthorne/audio/sound/SoundSource.java#L30)

Immutable filesystem-path description for deferred sound loading or streaming.
Construction validates only that the string is nonblank; it does not open the
file or verify its audio format. The original path string is retained unchanged.

- **`path`** — nonblank filesystem path

<details>
<summary>SoundSource.PathSource operation reference (1 declarations)</summary>

#### Constructor

```java
public PathSource
```

Validates the stored path.

- **`path`** — the filesystem path

</details>

<a id="type-soundsource-bytessource"></a>

### SoundSource.BytesSource

[Source](../../src/main/java/valthorne/audio/sound/SoundSource.java#L51)

In-memory encoded audio source that retains the supplied nonempty byte array.
Unlike a defensive-copy value object, the array is shared and the generated
accessor returns that same array. Callers must keep its contents stable while
loaders or streams use it; construction does not validate the audio format.

- **`bytes`** — nonempty encoded audio array retained by reference

<details>
<summary>SoundSource.BytesSource operation reference (1 declarations)</summary>

#### Constructor

```java
public BytesSource
```

Validates the stored bytes.

- **`bytes`** — the encoded sound bytes

</details>

<a id="type-soundstream"></a>

### SoundStream

[Source](../../src/main/java/valthorne/audio/sound/SoundStream.java#L25)

`SoundStream` represents a pull-based decoded PCM stream used for long or
compressed audio playback. Implementations expose stream metadata, seeking, chunked
reading, and resource cleanup.

##### Example Usage

```java
try (SoundStream stream = soundData.openStream()) {
    ByteBuffer chunk = BufferUtils.createByteBuffer(64 * 1024);
    stream.seek(5f);
    int bytesRead = stream.read(chunk);
}
```

<details>
<summary>SoundStream operation reference (7 declarations)</summary>

#### channels

```java
int channels()
```

Returns the number of channels produced by this stream.

**Returns:** the channel count

#### sampleRate

```java
int sampleRate()
```

Returns the sample rate produced by this stream.

**Returns:** the sample rate in hertz

#### bitsPerSample

```java
int bitsPerSample()
```

Returns the number of bits per sample produced by this stream.

**Returns:** the bits per sample

#### duration

```java
float duration()
```

Returns the duration of the underlying audio.

**Returns:** the duration in seconds

#### seek

```java
boolean seek(float seconds)
```

Seeks to a target playback time.

- **`seconds`** — the time in seconds to seek to

**Returns:** `true` when the seek succeeded

#### read

```java
int read(ByteBuffer pcmBuffer)
```

Reads decoded PCM data into the supplied buffer.

- **`pcmBuffer`** — the destination PCM buffer

**Returns:** the number of bytes read

#### close

```java
    void close()
```

Closes this stream and releases any underlying resources.

</details>

<a id="type-wavesounddecoder"></a>

### WaveSoundDecoder

[Source](../../src/main/java/valthorne/audio/sound/WaveSoundDecoder.java#L56)

`WaveSoundDecoder` loads uncompressed PCM WAV audio into Valthorne's
`SoundData` model. It supports probing both in-memory byte arrays and
file paths, extracts the essential stream metadata from the WAV container, and
can decode the PCM payload directly into a buffered `ByteBuffer` when a
fully buffered sound is required.

This decoder currently supports only little-endian PCM WAV files with:

- format code `1` (PCM)

- 16 bits per sample

During probing, the decoder scans RIFF chunks until it finds the `fmt`
chunk and then the audio data chunk, accepting both `data` and
`buffer` as valid audio payload chunk names. Metadata is returned as a
`SoundMetadata` instance so higher-level code can decide whether the file
should be buffered or streamed.

##### Example Usage

```java
byte[] bytes = Files.readAllBytes(Path.of("assets/audio/click.wav"));

SoundMetadata metadata = WaveSoundDecoder.probe(bytes);
SoundData sound = new WaveSoundDecoder().decode(bytes);

SoundMetadata fileMetadata = WaveSoundDecoder.probe("assets/audio/click.wav");
```

This example demonstrates the full expected usage of the class: probing from
memory, decoding from memory, and probing from a filesystem path.

<details>
<summary>WaveSoundDecoder operation reference (3 declarations)</summary>

#### probe

```java
public static SoundMetadata probe(byte[] bytes)
```

Probes WAV metadata from an in-memory byte array.

The byte array is wrapped in a little-endian `ByteBuffer` and delegated to
the internal probing routine.

- **`bytes`** — the encoded WAV bytes to inspect

**Returns:** the extracted sound metadata

#### probe

```java
public static SoundMetadata probe(String path) throws Exception
```

Probes WAV metadata from a file path.

The method validates the RIFF and WAVE container headers, then scans the file's
chunks until it finds a supported `fmt` chunk and a matching audio data
chunk. The returned metadata includes channel count, sample rate, bits per sample,
duration, byte offset, and byte length of the audio payload.

- **`path`** — the filesystem path to the WAV file

**Returns:** the extracted sound metadata

**Throws `Exception`:** if the file cannot be read or the WAV structure is invalid

#### decode

```java
    public SoundData decode(byte[] bytes)
```

Decodes the supplied WAV bytes into a fully buffered `SoundData` instance.

The method first probes the WAV metadata so it knows where the PCM payload starts
and how large it is. It then copies just the audio payload into a direct
`ByteBuffer`, flips that buffer for reading, and wraps the result in a
non-streaming `SoundData` record marked as `AudioFormat#WAV`.

- **`bytes`** — the encoded WAV bytes to decode

**Returns:** a fully buffered sound data object containing the PCM payload

</details>

<a id="type-wavesoundstream"></a>

### WaveSoundStream

[Source](../../src/main/java/valthorne/audio/sound/WaveSoundStream.java#L52)

`WaveSoundStream` provides pull-based chunked PCM reading for WAV audio that
is being played through Valthorne's streaming pipeline. It can stream either from a
filesystem-backed source or directly from an in-memory byte array, depending on the
`SoundSource` stored inside the owning `SoundData`.

Because WAV audio is already stored as PCM in the supported formats, the stream does
not need to transcode sample data on the fly. Instead, it reads byte ranges directly
from the WAV payload described by `SoundData#streamOffset()` and
`SoundData#streamLength()` and copies them into caller-supplied buffers.

Seeking is implemented by moving to a byte position derived from the requested time,
aligned to the current frame size so channel/sample boundaries are preserved.

##### Example Usage

```java
SoundData sound = SoundData.load("assets/music/theme.wav");

try (WaveSoundStream stream = new WaveSoundStream(sound)) {
    ByteBuffer pcm = ByteBuffer.allocateDirect(64 * 1024);

    stream.seek(3.5f);
    int bytesRead = stream.read(pcm);

    int channels = stream.channels();
    int sampleRate = stream.sampleRate();
    int bitsPerSample = stream.bitsPerSample();
    float duration = stream.duration();
}
```

This example demonstrates the complete usage of the class: construction, seeking,
PCM reading, metadata access, and resource cleanup.

<details>
<summary>WaveSoundStream operation reference (8 declarations)</summary>

#### Constructor

```java
public WaveSoundStream(SoundData data)
```

Creates a new WAV stream for the supplied `SoundData`.

The constructor determines whether streaming should pull bytes from a file path or
an in-memory byte array. It also computes the PCM frame size so future seeks and
reads can remain frame aligned.

- **`data`** — the stream-backed sound data describing the WAV payload

#### channels

```java
    public int channels()
```

Returns the channel count produced by this stream.

**Returns:** the PCM channel count

#### sampleRate

```java
    public int sampleRate()
```

Returns the sample rate produced by this stream.

**Returns:** the PCM sample rate in hertz

#### bitsPerSample

```java
    public int bitsPerSample()
```

Returns the bits per sample produced by this stream.

**Returns:** the PCM bit depth

#### duration

```java
    public float duration()
```

Returns the total duration of the underlying sound.

**Returns:** the duration in seconds

#### seek

```java
    public boolean seek(float seconds)
```

Seeks to a target playback time.

The requested time is clamped into the valid duration range, converted into a byte
offset using the stream's sample rate and frame size, then aligned down to the
nearest full frame. The internal byte position is updated, and file-backed streams
also move their random-access pointer to the new payload location.

- **`seconds`** — the desired seek time in seconds

**Returns:** `true` when the seek completed successfully

#### read

```java
    public int read(ByteBuffer pcmBuffer)
```

Reads the next chunk of PCM data into the supplied buffer.

The method respects the remaining stream length, clamps the requested read size to
the destination buffer capacity, and keeps the read aligned to whole PCM frames.
File-backed sources copy through a reusable scratch array, while memory-backed
sources copy directly from the in-memory byte array.

The destination buffer is cleared before writing and flipped before returning.
When no data remains, the buffer is returned empty and `0` is reported.

- **`pcmBuffer`** — the destination PCM buffer

**Returns:** the number of bytes written into the buffer

#### close

```java
    public void close()
```

Closes this stream and releases any file handle it owns.

Memory-backed streams do not own a file handle, so closing them only matters for
consistency. File-backed streams silently ignore secondary close failures.

</details>

## Related guides

- [Asset loading and caching](assets.md)
- [Ticks and frame timing](timing.md)
- [Existing audio system guide](../audio-system.md)
