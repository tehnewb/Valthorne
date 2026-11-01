#include <jni.h>
#include <bx/allocator.h>
#include <bx/readerwriter.h>
#include <shaderc.h>
#include <algorithm>
#include <exception>
#include <limits>
#include <string>
#include <stdexcept>
#include <vector>

namespace bgfx {
    bool compileShader(const char*, const char*, char*, uint32_t, const Options&, bx::WriterI*, bx::WriterI*);
}

namespace {
    struct Diagnostics final : bx::WriterI {
        std::string text;
        int32_t write(const void* data, int32_t size, bx::Error*) override {
            if (size > 0 && text.size() < 65536)
                text.append(static_cast<const char*>(data), std::min<size_t>(size_t(size), 65536 - text.size()));
            return size;
        }
    };
    std::vector<char> bytes(JNIEnv* env, jbyteArray input, size_t padding) {
        jsize length = env->GetArrayLength(input);
        std::vector<char> result(size_t(length) + padding, 0);
        env->GetByteArrayRegion(input, 0, length, reinterpret_cast<jbyte*>(result.data()));
        return result;
    }
}

extern "C" JNIEXPORT jbyteArray JNICALL Java_valthorne_graphics_bgfx_BgfxShaderCompiler_compileNative(JNIEnv* env, jclass, jbyteArray input, jbyteArray varyingInput, jchar type, jstring profile, jstring directory) {
    try {
        auto source = bytes(env, input, 16384);
        auto varyings = bytes(env, varyingInput, 1);
        if (env->ExceptionCheck()) return nullptr;
        const auto length = uint32_t(env->GetArrayLength(input));
        source[length] = '\n';
        bgfx::Options options;
        options.shaderType = char(type);
        const char* profileText = env->GetStringUTFChars(profile, nullptr);
        if (!profileText) return nullptr;
        options.profile = profileText;
        env->ReleaseStringUTFChars(profile, profileText);
        const char* includePath = env->GetStringUTFChars(directory, nullptr);
        if (!includePath) return nullptr;
        options.includeDirs.emplace_back(includePath);
        env->ReleaseStringUTFChars(directory, includePath);
        options.platform = "android";
        options.inputFilePath = "inline.sc";
        bx::DefaultAllocator allocator;
        bx::MemoryBlock block(&allocator);
        bx::MemoryWriter output(&block);
        Diagnostics diagnostics;
        if (!bgfx::compileShader(varyings.data(), "", source.data(), length, options, &output, &diagnostics)) {
            env->ThrowNew(env->FindClass("java/lang/IllegalArgumentException"), diagnostics.text.empty() ? "Could not compile inline shader" : diagnostics.text.c_str());
            return nullptr;
        }
        int64_t size = bx::getSize(&output);
        if (size < 1 || size > std::numeric_limits<jsize>::max()) throw std::runtime_error("Invalid compiled shader size");
        jbyteArray result = env->NewByteArray(jsize(size));
        if (result) env->SetByteArrayRegion(result, 0, jsize(size), static_cast<const jbyte*>(block.more()));
        return result;
    } catch (const std::exception& failure) {
        if (!env->ExceptionCheck()) env->ThrowNew(env->FindClass("java/lang/IllegalArgumentException"), failure.what());
        return nullptr;
    }
}
