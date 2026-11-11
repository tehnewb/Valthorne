#pragma once
#include <cstdint>
#include <mutex>
#include <stdexcept>
#include <vector>

// Compact command tokens preserve the portable 32-bit command format on ARM64.
// The registry is shared by render and asset workers; objects remain thread-owned.
namespace valthorne {
    struct NativeSlot { void* pointer; bool borrowed; };
    inline std::mutex handleMutex;
    inline std::vector<NativeSlot> handles(1, {nullptr, false});
    inline std::vector<uint32_t> freeHandles;

    inline uint32_t storePointer(const void* pointer, bool borrowed = false) {
        if (!pointer) return 0;
        std::lock_guard<std::mutex> lock(handleMutex);
        uint32_t index;
        if (freeHandles.empty()) { index = uint32_t(handles.size()); handles.push_back({nullptr, false}); }
        else { index = freeHandles.back(); freeHandles.pop_back(); }
        handles[index] = {const_cast<void*>(pointer), borrowed};
        return index;
    }

    inline void* pointer(uint32_t index, bool consume = false) {
        if (!index) return nullptr;
        std::lock_guard<std::mutex> lock(handleMutex);
        if (index >= handles.size() || !handles[index].pointer) throw std::invalid_argument("Closed native resource token");
        void* result = handles[index].pointer;
        if (consume) { handles[index] = {nullptr, false}; freeHandles.push_back(index); }
        return result;
    }

    inline void releaseBorrowed(uint32_t index) {
        std::lock_guard<std::mutex> lock(handleMutex);
        if (index && index < handles.size() && handles[index].pointer && handles[index].borrowed) {
            handles[index] = {nullptr, false};
            freeHandles.push_back(index);
        }
    }
}
