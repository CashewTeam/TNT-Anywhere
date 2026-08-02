#include <cstdint>
#include <cstdio>
#include <dlfcn.h>
#include <unistd.h>

namespace {
using Constructor = void (*)(void*, std::uint32_t, std::int16_t, std::int16_t, std::size_t);
using Destructor = void (*)(void*);
using Start = int (*)(void*);
using Stop = void (*)(void*);
using Read = int (*)(void*, std::int8_t*, int);

bool writeAll(const void* data, std::size_t size) {
    const auto* bytes = static_cast<const std::uint8_t*>(data);
    while (size > 0) {
        const ssize_t written = write(STDOUT_FILENO, bytes, size);
        if (written <= 0) return false;
        bytes += written;
        size -= static_cast<std::size_t>(written);
    }
    return true;
}
}

int main() {
    constexpr std::size_t kBufferSize = 4096;
    void* library = dlopen("/system/lib64/libaudioloopback.so", RTLD_NOW | RTLD_LOCAL);
    if (library == nullptr) {
        std::fprintf(stderr, "dlopen failed: %s\n", dlerror());
        return 1;
    }

    auto constructor = reinterpret_cast<Constructor>(
            dlsym(library, "_ZN7android13AudioLoopbackC1Ejssm"));
    auto destructor = reinterpret_cast<Destructor>(
            dlsym(library, "_ZN7android13AudioLoopbackD1Ev"));
    auto start = reinterpret_cast<Start>(
            dlsym(library, "_ZN7android13AudioLoopback5startEv"));
    auto stop = reinterpret_cast<Stop>(
            dlsym(library, "_ZN7android13AudioLoopback4stopEv"));
    auto readLoopback = reinterpret_cast<Read>(
            dlsym(library, "_ZN7android13AudioLoopback4readEPai"));
    if (constructor == nullptr || destructor == nullptr || start == nullptr
            || stop == nullptr || readLoopback == nullptr) {
        std::fprintf(stderr, "required audio_loopback symbol missing\n");
        dlclose(library);
        return 2;
    }

    alignas(16) std::uint8_t instance[128] = {};
    std::int8_t pcm[kBufferSize] = {};
    constructor(instance, 48000, 16, 2, kBufferSize);
    if (start(instance) != 0) {
        std::fprintf(stderr, "audio_loopback start failed\n");
        destructor(instance);
        dlclose(library);
        return 3;
    }

    constexpr char kReady[] = {'T', 'N', 'T', '1'};
    if (!writeAll(kReady, sizeof(kReady))) {
        stop(instance);
        destructor(instance);
        dlclose(library);
        return 4;
    }

    int result = 0;
    while (true) {
        const int bytesRead = readLoopback(instance, pcm, sizeof(pcm));
        if (bytesRead <= 0 || !writeAll(pcm, static_cast<std::size_t>(bytesRead))) {
            result = bytesRead < 0 ? 5 : 0;
            break;
        }
    }

    stop(instance);
    destructor(instance);
    dlclose(library);
    return result;
}
