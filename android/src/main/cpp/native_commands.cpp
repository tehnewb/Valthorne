#include <jni.h>
#include <cstdint>
#include <cstring>
#include <vector>
#include "native_handles.h"

extern "C" uint32_t valthorne_command(uint32_t, uint32_t*, float*, const uint8_t*, uint32_t);

namespace {
    void report(JNIEnv* env, const std::exception& failure) {
        env->ThrowNew(env->FindClass("java/lang/IllegalStateException"), failure.what());
    }
    void* direct(JNIEnv* env, jobject buffer) {
        void* result = env->GetDirectBufferAddress(buffer);
        if (!result && env->GetDirectBufferCapacity(buffer) != 0) throw std::invalid_argument("A direct native staging buffer is required");
        return result;
    }
    uint32_t execute(JNIEnv* env, jint operation, jobject words, jobject values, const uint8_t* bytes, uint32_t length) {
        if (env->GetDirectBufferCapacity(words) < 32 || env->GetDirectBufferCapacity(values) < 16384)
            throw std::invalid_argument("Native command staging is too small");
        return valthorne_command(uint32_t(operation), static_cast<uint32_t*>(direct(env, words)), static_cast<float*>(direct(env, values)), bytes, length);
    }
    thread_local std::vector<uint8_t> text;
    void utf8(JNIEnv* env, jstring source) {
        const jsize count = env->GetStringLength(source);
        text.clear();
        text.reserve(size_t(count) * 3 + 1);
        const jchar* chars = env->GetStringChars(source, nullptr);
        if (!chars) throw std::bad_alloc();
        for (jsize i = 0; i < count; ++i) {
            uint32_t code = chars[i];
            if (code >= 0xd800 && code <= 0xdbff && i + 1 < count && chars[i + 1] >= 0xdc00 && chars[i + 1] <= 0xdfff)
                code = 0x10000 + ((code - 0xd800) << 10) + chars[++i] - 0xdc00;
            else if (code >= 0xd800 && code <= 0xdfff) code = 0xfffd;
            if (code < 0x80) text.push_back(uint8_t(code));
            else if (code < 0x800) { text.push_back(uint8_t(0xc0 | (code >> 6))); text.push_back(uint8_t(0x80 | (code & 63))); }
            else if (code < 0x10000) { text.push_back(uint8_t(0xe0 | (code >> 12))); text.push_back(uint8_t(0x80 | ((code >> 6) & 63))); text.push_back(uint8_t(0x80 | (code & 63))); }
            else { text.push_back(uint8_t(0xf0 | (code >> 18))); text.push_back(uint8_t(0x80 | ((code >> 12) & 63))); text.push_back(uint8_t(0x80 | ((code >> 6) & 63))); text.push_back(uint8_t(0x80 | (code & 63))); }
        }
        env->ReleaseStringChars(source, chars);
        text.push_back(0);
    }
}

extern "C" JNIEXPORT jlong JNICALL Java_valthorne_android_internal_AndroidNative_address(JNIEnv* env, jclass, jobject buffer) {
    try { return reinterpret_cast<jlong>(direct(env, buffer)); }
    catch (const std::exception& failure) { report(env, failure); return 0; }
}

extern "C" JNIEXPORT jint JNICALL Java_valthorne_android_internal_AndroidNative_executeSimple(JNIEnv* env, jclass, jint operation, jlong words, jlong values) {
    try {
        return valthorne_command(uint32_t(operation), reinterpret_cast<uint32_t*>(words), reinterpret_cast<float*>(values), nullptr, 0);
    } catch (const std::exception& failure) { report(env, failure); return 0; }
}

extern "C" JNIEXPORT jint JNICALL Java_valthorne_android_internal_AndroidNative_execute(JNIEnv* env, jclass, jint operation, jobject words, jobject values, jobject source, jint offset, jint length) {
    try {
        if (offset < 0 || length < 0 || int64_t(offset) + length > env->GetDirectBufferCapacity(source)) throw std::invalid_argument("Native upload range is invalid");
        auto* bytes = static_cast<const uint8_t*>(direct(env, source));
        return execute(env, operation, words, values, length ? bytes + offset : nullptr, uint32_t(length));
    } catch (const std::exception& failure) { report(env, failure); return 0; }
}

extern "C" JNIEXPORT jint JNICALL Java_valthorne_android_internal_AndroidNative_executeFloats(JNIEnv* env, jclass, jint operation, jobject words, jobject values, jobject source, jint offset, jint length) {
    try {
        if (offset < 0 || length < 0 || int64_t(offset) + length > env->GetDirectBufferCapacity(source)) throw std::invalid_argument("Native float upload range is invalid");
        auto* bytes = static_cast<const float*>(direct(env, source));
        return execute(env, operation, words, values, reinterpret_cast<const uint8_t*>(bytes + offset), uint32_t(length) * 4);
    } catch (const std::exception& failure) { report(env, failure); return 0; }
}

extern "C" JNIEXPORT jint JNICALL Java_valthorne_android_internal_AndroidNative_executeString(JNIEnv* env, jclass, jint operation, jobject words, jobject values, jstring source) {
    try { utf8(env, source); return execute(env, operation, words, values, text.data(), uint32_t(text.size())); }
    catch (const std::exception& failure) { report(env, failure); return 0; }
}

extern "C" JNIEXPORT void JNICALL Java_valthorne_android_internal_AndroidNative_readBytes(JNIEnv* env, jclass, jint token, jobject destination, jint size) {
    try {
        if (size < 0 || size > env->GetDirectBufferCapacity(destination)) throw std::invalid_argument("Native read exceeds destination");
        if (size) std::memcpy(direct(env, destination), valthorne::pointer(uint32_t(token)), size_t(size));
        valthorne::releaseBorrowed(uint32_t(token));
    } catch (const std::exception& failure) { report(env, failure); }
}

extern "C" JNIEXPORT void JNICALL Java_valthorne_android_internal_AndroidNative_readAtlasGlyphs(JNIEnv* env, jclass, jint token, jint pixels, jobject destination, jint size) {
    try {
        if (pixels < 0 || size < 0 || size > env->GetDirectBufferCapacity(destination)) throw std::invalid_argument("Invalid packed atlas read");
        auto* source = static_cast<const uint8_t*>(valthorne::pointer(uint32_t(token)));
        if (size) std::memcpy(direct(env, destination), source + size_t(pixels), size_t(size));
    } catch (const std::exception& failure) { report(env, failure); }
}

extern "C" JNIEXPORT void JNICALL Java_valthorne_android_internal_AndroidNative_readPaddedAlpha(JNIEnv* env, jclass, jint token, jobject destination, jint width, jint height) {
    try {
        const int64_t stride = int64_t(width) + 2;
        if (width < 0 || height < 0 || stride * (int64_t(height) + 2) > env->GetDirectBufferCapacity(destination)) throw std::invalid_argument("Native coverage exceeds destination");
        auto* pixels = static_cast<uint8_t*>(valthorne::pointer(uint32_t(token)));
        auto* target = static_cast<uint8_t*>(direct(env, destination));
        for (int y = 0; y < height; ++y) std::memcpy(target + (y + 1) * stride + 1, pixels + y * width, size_t(width));
    } catch (const std::exception& failure) { report(env, failure); }
}

extern "C" JNIEXPORT void JNICALL Java_valthorne_android_internal_AndroidNative_readAlphaRgba(JNIEnv* env, jclass, jint token, jobject destination, jint count) {
    try {
        if (count < 0 || int64_t(count) * 4 > env->GetDirectBufferCapacity(destination)) throw std::invalid_argument("Native alpha expansion exceeds destination");
        auto* pixels = static_cast<uint8_t*>(valthorne::pointer(uint32_t(token)));
        auto* target = static_cast<uint8_t*>(direct(env, destination));
        for (int i = 0; i < count; ++i) { target[i * 4] = target[i * 4 + 1] = target[i * 4 + 2] = 255; target[i * 4 + 3] = pixels[i]; }
    } catch (const std::exception& failure) { report(env, failure); }
}

extern "C" JNIEXPORT jstring JNICALL Java_valthorne_android_internal_AndroidNative_string(JNIEnv* env, jclass, jint token) {
    try {
        auto* value = static_cast<const char*>(valthorne::pointer(uint32_t(token)));
        jstring result = env->NewStringUTF(value ? value : "");
        valthorne::releaseBorrowed(uint32_t(token));
        return result;
    } catch (const std::exception& failure) { report(env, failure); return nullptr; }
}
