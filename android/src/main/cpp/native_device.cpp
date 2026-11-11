#include <jni.h>
#include <android/native_window_jni.h>
#include <bgfx/bgfx.h>
#include <atomic>
#include <cstdint>
#include <new>

namespace {
    struct AndroidDevice {
        ANativeWindow* window;
    };
    std::atomic_flag occupied = ATOMIC_FLAG_INIT;
}

extern "C" JNIEXPORT jlong JNICALL Java_valthorne_android_internal_AndroidNativeDevice_create(JNIEnv* env, jclass, jobject surface, jboolean vulkan) {
    if (occupied.test_and_set()) return 0;
    ANativeWindow* window = ANativeWindow_fromSurface(env, surface);
    if (!window) { occupied.clear(); return 0; }
    auto* device = new (std::nothrow) AndroidDevice{window};
    if (!device) { ANativeWindow_release(window); occupied.clear(); return 0; }
    bgfx::Init init;
    init.type = vulkan ? bgfx::RendererType::Vulkan : bgfx::RendererType::OpenGLES;
    init.fallback = false;
    init.platformData.nwh = window;
    init.resolution.width = uint32_t(ANativeWindow_getWidth(window));
    init.resolution.height = uint32_t(ANativeWindow_getHeight(window));
    init.resolution.formatColor = bgfx::TextureFormat::RGBA8;
    init.resolution.reset = BGFX_RESET_VSYNC;
    if (!bgfx::init(init)) {
        ANativeWindow_release(window);
        delete device;
        occupied.clear();
        return 0;
    }
    return reinterpret_cast<jlong>(device);
}

extern "C" JNIEXPORT void JNICALL Java_valthorne_android_internal_AndroidNativeDevice_quiesceNative(JNIEnv*, jclass) {
    // The second flush waits for the worker to complete the last presenting frame.
    bgfx::frame(BGFX_FRAME_FLUSH);
    bgfx::frame(BGFX_FRAME_FLUSH);
}

extern "C" JNIEXPORT void JNICALL Java_valthorne_android_internal_AndroidNativeDevice_destroy(JNIEnv*, jclass, jlong handle) {
    auto* device = reinterpret_cast<AndroidDevice*>(handle);
    bgfx::shutdown();
    ANativeWindow_release(device->window);
    delete device;
    occupied.clear();
}
