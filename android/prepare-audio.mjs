import { mkdirSync, readFileSync, writeFileSync } from 'node:fs';
import path from 'node:path';

// This bridge preserves the engine's existing OpenAL audio-thread ownership.
export function prepareAudio(root, output) {
    const operations = [
        ['int', 'alGenSources', '', 'ALuint value; alGenSources(1, &value); return value;'],
        ['int', 'alGenBuffers', '', 'ALuint value; alGenBuffers(1, &value); return value;'],
        ['void', 'alDeleteSources', 'int source', 'ALuint value = source; alDeleteSources(1, &value);'],
        ['void', 'alDeleteBuffers', 'int buffer', 'ALuint value = buffer; alDeleteBuffers(1, &value);'],
        ['void', 'alSourcePlay', 'int source', 'alSourcePlay(source);'],
        ['void', 'alSourcePause', 'int source', 'alSourcePause(source);'],
        ['void', 'alSourceStop', 'int source', 'alSourceStop(source);'],
        ['void', 'alSourceRewind', 'int source', 'alSourceRewind(source);'],
        ['void', 'alSourcei', 'int source, int parameter, int value', 'alSourcei(source, parameter, value);'],
        ['void', 'alSourcef', 'int source, int parameter, float value', 'alSourcef(source, parameter, value);'],
        ['int', 'alGetSourcei', 'int source, int parameter', 'ALint value; alGetSourcei(source, parameter, &value); return value;'],
        ['float', 'alGetSourcef', 'int source, int parameter', 'ALfloat value; alGetSourcef(source, parameter, &value); return value;'],
        ['void', 'alSourceQueueBuffers', 'int source, int buffer', 'ALuint value = buffer; alSourceQueueBuffers(source, 1, &value);'],
        ['int', 'alSourceUnqueueBuffers', 'int source', 'ALuint value; alSourceUnqueueBuffers(source, 1, &value); return value;'],
        ['long', 'alcOpenDevice', 'ByteBuffer unused', 'return reinterpret_cast<jlong>(alcOpenDevice(nullptr));'],
        ['boolean', 'alcCloseDevice', 'long device', 'return alcCloseDevice(reinterpret_cast<ALCdevice*>(device));'],
        ['long', 'alcCreateContext', 'long device, IntBuffer unused', 'return reinterpret_cast<jlong>(alcCreateContext(reinterpret_cast<ALCdevice*>(device), nullptr));'],
        ['void', 'alcDestroyContext', 'long context', 'alcDestroyContext(reinterpret_cast<ALCcontext*>(context));'],
        ['boolean', 'alcMakeContextCurrent', 'long context', 'return alcMakeContextCurrent(reinterpret_cast<ALCcontext*>(context));'],
        ['boolean', 'alcSetThreadContext', 'long context', 'return alcMakeContextCurrent(reinterpret_cast<ALCcontext*>(context));'],
    ];
    const sources = ['Audio.java', 'AudioRuntime.java', 'audio/SoundPlayer.java'];
    for (const name of sources) {
        let source = readFileSync(path.join(root, 'src/main/java/valthorne', name), 'utf8');
        source = source.replace(/^import (?:static )?org\.lwjgl\.openal\.[^;]+;\s*$/gm, '');
        source = source.replace('import valthorne.', 'import static valthorne.android.internal.AndroidOpenAL.*;\n\nimport valthorne.');
        source = source.replace('ALCCapabilities capabilities = ALC.createCapabilities(device);', '')
            .replace('threadContext = capabilities.ALC_EXT_thread_local_context;', 'threadContext = false;')
            .replace('AL.createCapabilities(capabilities);', '')
            .replace('AL.setCurrentThread(null);', '').replace('if (!threadContext) AL.setCurrentProcess(null);', '')
            .replace('ALUtil.getStringList', 'getStringList');
        const file = path.join(output, 'java/valthorne', name);
        mkdirSync(path.dirname(file), { recursive: true });
        writeFileSync(file, source);
    }
    // Values come from the OpenAL specification, not desktop implementation pointers.
    const constants = {AL_FALSE:0, AL_TRUE:1, AL_PITCH:0x1003, AL_LOOPING:0x1007, AL_BUFFER:0x1009, AL_GAIN:0x100A, AL_SOURCE_STATE:0x1010, AL_INITIAL:0x1011, AL_PLAYING:0x1012, AL_PAUSED:0x1013, AL_STOPPED:0x1014, AL_BUFFERS_QUEUED:0x1015, AL_BUFFERS_PROCESSED:0x1016, AL_SEC_OFFSET:0x1024, AL_FORMAT_MONO8:0x1100, AL_FORMAT_MONO16:0x1101, AL_FORMAT_STEREO8:0x1102, AL_FORMAT_STEREO16:0x1103, ALC_DEFAULT_DEVICE_SPECIFIER:0x1004, ALC_DEVICE_SPECIFIER:0x1005, ALC_DEFAULT_ALL_DEVICES_SPECIFIER:0x1012, ALC_ALL_DEVICES_SPECIFIER:0x1013};
    let java = `package valthorne.android.internal;\n\nimport java.nio.ByteBuffer;\nimport java.nio.IntBuffer;\nimport java.util.Arrays;\nimport java.util.List;\n\n/**\n * Direct Android OpenAL Soft bridge used exclusively by the existing audio owner\n * thread. Sources, queues and streaming remain managed by SoundPlayer; no desktop\n * LWJGL initialization or per-call capability lookup is required.\n */\npublic final class AndroidOpenAL {\n`;
    for (const [name, value] of Object.entries(constants)) java += `    /**\n     * OpenAL ${name.replaceAll('_', ' ')} token.\n     */\n    public static final int ${name} = ${value};\n`;
    java += `    static { System.loadLibrary("valthorne_android"); }\n\n    /**\n     * Prevents construction of the native binding.\n     */\n    private AndroidOpenAL() { }\n`;
    let cpp = '#include <jni.h>\n#include <AL/al.h>\n#include <AL/alc.h>\n#include <AL/alext.h>\n#include <cstring>\n#include <vector>\n';
    const types = {int:'jint', void:'void', float:'jfloat', long:'jlong', boolean:'jboolean', ByteBuffer:'jobject', IntBuffer:'jobject'};
    for (const [result, name, args, implementation] of operations) {
        java += `\n    /**\n     * Executes ${name} on the audio thread against the native Android device.\n     */\n    public static native ${result} ${name}(${args});\n`;
        const params = args ? ', ' + args.split(', ').map(arg => { const [type, name] = arg.split(' '); return types[type] + ' ' + name; }).join(', ') : '';
        cpp += `extern "C" JNIEXPORT ${types[result]} JNICALL Java_valthorne_android_internal_AndroidOpenAL_${name}(JNIEnv*, jclass${params}) { ${implementation.replaceAll('ALuint value;', 'ALuint value = 0;').replaceAll('ALint value;', 'ALint value = 0;').replaceAll('ALfloat value;', 'ALfloat value = 0;')} }\n`;
    }
    java += `
    /**
     * Uploads exactly the direct buffer's remaining PCM bytes.
     */
    public static void alBufferData(int buffer, int format, ByteBuffer pcm, int rate) {
        if (!pcm.isDirect()) throw new IllegalArgumentException("Direct PCM buffer required");
        bufferData(buffer, format, pcm, pcm.position(), pcm.remaining(), rate);
    }
    /**
     * Transfers a validated direct PCM range to native storage.
     */
    private static native void bufferData(int buffer, int format, ByteBuffer pcm, int offset, int size, int rate);
    /**
     * Reads a native device name without retaining its borrowed pointer.
     */
    public static native String alcGetString(long device, int token);
    /**
     * Queries an optional native device extension.
     */
    public static native boolean alcIsExtensionPresent(long device, String extension);
    /**
     * Reopens the existing device when the native extension is available.
     */
    public static native boolean alcReopenDeviceSOFT(long device, String name, IntBuffer unused);
    /**
     * Copies the complete null-delimited device list.
     */
    public static List<String> getStringList(long device, int token) { return Arrays.asList(deviceNames(device, token)); }
    /**
     * Copies borrowed device names before returning to Java.
     */
    private static native String[] deviceNames(long device, int token);
    /**
     * Suspends or resumes native output while retaining every source's playback state.
     */
    public static native void setPaused(boolean paused);
}\n`;
    cpp += `
extern "C" JNIEXPORT void JNICALL Java_valthorne_android_internal_AndroidOpenAL_setPaused(JNIEnv*, jclass, jboolean paused) {
    auto* context = alcGetCurrentContext();
    if (!context) return;
    auto* device = alcGetContextsDevice(context);
    auto function = reinterpret_cast<void (*)(ALCdevice*)>(alcGetProcAddress(device, paused ? "alcDevicePauseSOFT" : "alcDeviceResumeSOFT"));
    if (function) function(device);
}
extern "C" JNIEXPORT void JNICALL Java_valthorne_android_internal_AndroidOpenAL_bufferData(JNIEnv* env, jclass, jint buffer, jint format, jobject pcm, jint offset, jint size, jint rate) {
    auto* bytes = static_cast<char*>(env->GetDirectBufferAddress(pcm));
    if (!bytes || offset < 0 || size < 0 || jlong(offset) + size > env->GetDirectBufferCapacity(pcm)) {
        env->ThrowNew(env->FindClass("java/lang/IllegalArgumentException"), "Invalid direct PCM range"); return;
    }
    alBufferData(buffer, format, bytes + offset, size, rate);
    if (alGetError() != AL_NO_ERROR) env->ThrowNew(env->FindClass("java/lang/IllegalStateException"), "OpenAL rejected PCM upload");
}
extern "C" JNIEXPORT jstring JNICALL Java_valthorne_android_internal_AndroidOpenAL_alcGetString(JNIEnv* env, jclass, jlong device, jint token) {
    const char* text = alcGetString(reinterpret_cast<ALCdevice*>(device), token);
    return text ? env->NewStringUTF(text) : nullptr;
}
extern "C" JNIEXPORT jboolean JNICALL Java_valthorne_android_internal_AndroidOpenAL_alcIsExtensionPresent(JNIEnv* env, jclass, jlong device, jstring extension) {
    const char* text = env->GetStringUTFChars(extension, nullptr);
    bool result = alcIsExtensionPresent(reinterpret_cast<ALCdevice*>(device), text);
    env->ReleaseStringUTFChars(extension, text); return result;
}
extern "C" JNIEXPORT jboolean JNICALL Java_valthorne_android_internal_AndroidOpenAL_alcReopenDeviceSOFT(JNIEnv* env, jclass, jlong device, jstring name, jobject) {
    auto* value = reinterpret_cast<ALCdevice*>(device);
    auto function = reinterpret_cast<LPALCREOPENDEVICESOFT>(alcGetProcAddress(value, "alcReopenDeviceSOFT"));
    if (!function) return false;
    const char* text = name ? env->GetStringUTFChars(name, nullptr) : nullptr;
    bool result = function(value, text, nullptr);
    if (name) env->ReleaseStringUTFChars(name, text); return result;
}
extern "C" JNIEXPORT jobjectArray JNICALL Java_valthorne_android_internal_AndroidOpenAL_deviceNames(JNIEnv* env, jclass, jlong device, jint token) {
    std::vector<const char*> names;
    const char* text = alcGetString(reinterpret_cast<ALCdevice*>(device), token);
    while (text && *text) { names.push_back(text); text += std::strlen(text) + 1; }
    auto result = env->NewObjectArray(names.size(), env->FindClass("java/lang/String"), nullptr);
    for (size_t i = 0; i < names.size(); ++i) { auto name = env->NewStringUTF(names[i]); env->SetObjectArrayElement(result, i, name); env->DeleteLocalRef(name); }
    return result;
}\n`;
    writeFileSync(path.join(output, 'java/valthorne/android/internal/AndroidOpenAL.java'), java);
    writeFileSync(path.join(output, 'cpp/audio.cpp'), cpp);
    for (const [prefix, format] of [['Ogg', 'OGG'], ['Mp3', 'MP3']]) {
        const decoder = `package valthorne.audio;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import valthorne.android.internal.AndroidAudioStream;

/**
 * Android ${format} decoder using the platform's media extractor and codec.
 * Probing retains no native resources; buffered decoding returns independent PCM.
 */
public final class ${prefix}SoundDecoder implements SoundDecoder {
    /**
     * Probes encoded bytes without allocating decoded PCM.
     */
    public static SoundMetadata probe(byte[] bytes) { return AndroidAudioStream.probe(bytes); }
    /**
     * Probes an encoded filesystem resource.
     */
    public static SoundMetadata probe(String path) throws IOException { return probe(Files.readAllBytes(Path.of(path))); }
    @Override
    public SoundData decode(byte[] bytes) {
        SoundMetadata metadata = probe(bytes);
        SoundData source = new SoundData(new SoundSource.BytesSource(bytes), null, 0, bytes.length, metadata.duration(), metadata.channels(), metadata.sampleRate(), 16, true, true, AudioFormat.${format});
        try (AndroidAudioStream stream = new AndroidAudioStream(source)) {
            ByteBuffer chunk = ByteBuffer.allocateDirect(65536);
            byte[] copy = new byte[chunk.capacity()];
            ByteArrayOutputStream pcm = new ByteArrayOutputStream();
            int count;
            while ((count = stream.read(chunk)) > 0) {
                chunk.get(copy, 0, count);
                pcm.write(copy, 0, count);
            }
            ByteBuffer decoded = ByteBuffer.allocateDirect(pcm.size());
            decoded.put(pcm.toByteArray());
            decoded.flip();
            return new SoundData(null, decoded, 0, decoded.remaining(), metadata.duration(), metadata.channels(), metadata.sampleRate(), 16, false, true, AudioFormat.${format});
        }
    }
}
`;
        let stream = `package valthorne.audio;\n\nimport java.nio.ByteBuffer;\nimport valthorne.android.internal.AndroidAudioStream;\n\n/**\n * Incremental Android ${format} decoding with the shared platform codec lifecycle.\n */\npublic final class ${prefix}SoundStream implements SoundStream {\n    private final AndroidAudioStream decoder; // Owned incremental platform decoder.\n\n    /**\n     * Opens the sound's retained encoded source.\n     */\n    public ${prefix}SoundStream(SoundData data) { decoder = new AndroidAudioStream(data); }\n`;
        for (const [result, name, parameters, args] of [['int','channels','',''], ['int','sampleRate','',''], ['int','bitsPerSample','',''], ['float','duration','',''], ['boolean','seek','float seconds','seconds'], ['int','read','ByteBuffer destination','destination'], ['void','close','','']])
            stream += `    @Override\n    public ${result} ${name}(${parameters}) { ${result === 'void' ? '' : 'return '}decoder.${name}(${args}); }\n`;
        writeFileSync(path.join(output, 'java/valthorne/audio', prefix + 'SoundDecoder.java'), decoder);
        writeFileSync(path.join(output, 'java/valthorne/audio', prefix + 'SoundStream.java'), stream + '}\n');
    }
}
