// Vod launcher native helper.
// Redirects the data path that libcocos2dcpp.so hardcodes for the original
// Geometry Dash package to the Vod data directory.
#include <jni.h>
#include <link.h>
#include <sys/mman.h>
#include <unistd.h>
#include <android/log.h>
#include <string>
#include <cstring>
#include <cstdint>

#define TAG "VodLauncher"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, TAG, __VA_ARGS__)

static std::string g_newPath;
static std::string g_origPath;

struct Mod { uintptr_t base = 0; std::string name; };

static int cb(struct dl_phdr_info* info, size_t, void* data) {
    auto* m = static_cast<Mod*>(data);
    if (info->dlpi_name && strstr(info->dlpi_name, "libcocos2dcpp.so")) {
        m->base = info->dlpi_addr;
        m->name = info->dlpi_name;
        // walk phdrs: patch every loaded segment
        for (int i = 0; i < info->dlpi_phnum; i++) {
            const ElfW(Phdr)& ph = info->dlpi_phdr[i];
            if (ph.p_type != PT_LOAD) continue;
            uintptr_t start = info->dlpi_addr + ph.p_vaddr;
            size_t len = ph.p_memsz;
            uintptr_t pageStart = start & ~(uintptr_t)(getpagesize() - 1);
            size_t pageLen = ((start + len + getpagesize() - 1) & ~(uintptr_t)(getpagesize() - 1)) - pageStart;
            bool wasWritable = (ph.p_flags & PF_W) != 0;
            int prot = ((ph.p_flags & PF_R) ? PROT_READ : 0) | ((ph.p_flags & PF_X) ? PROT_EXEC : 0);
            // only data segments (no exec) are scanned
            if (ph.p_flags & PF_X) continue;
            if (!wasWritable && mprotect((void*)pageStart, pageLen, prot | PROT_WRITE) != 0) continue;
            auto* p = reinterpret_cast<char*>(start);
            size_t needle = g_origPath.size();
            int patched = 0;
            for (size_t o = 0; o + needle <= len; o++) {
                if (p[o] == g_origPath[0] && memcmp(p + o, g_origPath.data(), needle) == 0) {
                    if (g_newPath.size() <= needle) {
                        memcpy(p + o, g_newPath.c_str(), g_newPath.size() + 1);
                        // blank leftovers so the string stays NUL terminated
                        for (size_t k = g_newPath.size() + 1; k < needle; k++) p[o + k] = '\0';
                        patched++;
                    }
                }
            }
            if (!wasWritable) mprotect((void*)pageStart, pageLen, prot);
            if (patched) LOGI("patched %d occurrence(s) of data path", patched);
        }
        return 1;
    }
    return 0;
}

extern "C" {
JNIEXPORT void JNICALL Java_com_vod_launcher_LauncherFix_setDataPath(JNIEnv* env, jclass, jstring s) {
    const char* c = env->GetStringUTFChars(s, nullptr); g_newPath = c; env->ReleaseStringUTFChars(s, c);
}
JNIEXPORT void JNICALL Java_com_vod_launcher_LauncherFix_setOriginalDataPath(JNIEnv* env, jclass, jstring s) {
    const char* c = env->GetStringUTFChars(s, nullptr); g_origPath = c; env->ReleaseStringUTFChars(s, c);
}
JNIEXPORT void JNICALL Java_com_vod_launcher_LauncherFix_performPatches(JNIEnv*, jclass) {
    if (g_origPath.empty() || g_newPath.empty()) { LOGE("paths not set"); return; }
    Mod m; dl_iterate_phdr(cb, &m);
    if (!m.base) LOGE("libcocos2dcpp.so not found in process");
    else LOGI("libcocos2dcpp base=%p", (void*)m.base);
}
}
