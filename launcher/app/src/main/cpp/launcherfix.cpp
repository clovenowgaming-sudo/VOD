// Vod launcher native helper.
//
// libcocos2dcpp.so (Geometry Dash) calls fopen()/rename() with paths hardcoded to
// "/data/data/com.robtopx.geometryjump/...". This helper rewrites the GOT/PLT entries of those
// two imports inside libcocos2dcpp.so so the paths are redirected to Vod's data directory.
// It also logs files the game fails to open, which makes missing-asset problems visible in logcat.
#include <jni.h>
#include <link.h>
#include <elf.h>
#include <sys/mman.h>
#include <unistd.h>
#include <errno.h>
#include <stdio.h>
#include <string.h>
#include <stdint.h>
#include <android/log.h>
#include <string>
#include <atomic>

#define TAG "VodLauncher"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, TAG, __VA_ARGS__)
#define LOGW(...) __android_log_print(ANDROID_LOG_WARN, TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, TAG, __VA_ARGS__)

#if defined(__LP64__)
using Rel_t = Elf64_Rela;
#define REL_SYM(i) ELF64_R_SYM(i)
#define REL_TYPE(i) ELF64_R_TYPE(i)
#define T_JUMP_SLOT 1026   // R_AARCH64_JUMP_SLOT
#define T_GLOB_DAT 1025    // R_AARCH64_GLOB_DAT
#define T_ABS 257          // R_AARCH64_ABS64
using Sym_t = Elf64_Sym;
#else
using Rel_t = Elf32_Rel;
#define REL_SYM(i) ELF32_R_SYM(i)
#define REL_TYPE(i) ELF32_R_TYPE(i)
#define T_JUMP_SLOT 22     // R_ARM_JUMP_SLOT
#define T_GLOB_DAT 21      // R_ARM_GLOB_DAT
#define T_ABS 2            // R_ARM_ABS32
using Sym_t = Elf32_Sym;
#endif

static std::string g_newPath;
static std::string g_origPath;
static std::atomic<int> g_failLogged{0};
static std::atomic<int> g_apkLogged{0};

static std::string redirect(const char* p) {
    if (p && !g_origPath.empty() && strncmp(p, g_origPath.c_str(), g_origPath.size()) == 0)
        return g_newPath + (p + g_origPath.size());
    return p ? std::string(p) : std::string();
}

// Geode/cocos probe several search folders before falling back to the APK; those misses are normal noise.
static bool expected_miss(const std::string& r) {
    return r.find("/geode/unzipped/") != std::string::npos || r.find("/geode/resources/") != std::string::npos ||
           r.find("/save/") != std::string::npos;
}

static FILE* vod_fopen(const char* path, const char* mode) {
    std::string r = redirect(path);
    FILE* f = fopen(r.c_str(), mode);
    int e = errno;
    if (!f) {
        if (!expected_miss(r) && g_failLogged.fetch_add(1) < 300)
            LOGW("fopen failed: '%s' (mode %s) errno=%d%s", r.c_str(), mode ? mode : "?", e, r != path ? " [redirected]" : "");
    } else if (r.find(".apk") != std::string::npos && g_apkLogged.fetch_add(1) < 10) {
        LOGI("fopen ok: %s (mode %s)", r.c_str(), mode ? mode : "?");
    }
    errno = e;
    return f;
}

static int vod_rename(const char* a, const char* b) {
    std::string ra = redirect(a), rb = redirect(b);
    return rename(ra.c_str(), rb.c_str());
}

struct Ctx { uintptr_t base = 0; const ElfW(Phdr)* phdr = nullptr; int phnum = 0; bool found = false; };

static int find_cb(struct dl_phdr_info* info, size_t, void* data) {
    auto* c = static_cast<Ctx*>(data);
    if (info->dlpi_name && strstr(info->dlpi_name, "libcocos2dcpp.so")) {
        c->base = info->dlpi_addr; c->phdr = info->dlpi_phdr; c->phnum = info->dlpi_phnum; c->found = true;
        return 1;
    }
    return 0;
}

static bool patch_slot(uintptr_t addr, void* value) {
    long ps = getpagesize();
    uintptr_t page = addr & ~(uintptr_t)(ps - 1);
    if (mprotect((void*)page, ps, PROT_READ | PROT_WRITE) != 0) { LOGE("mprotect RW failed for %p (errno %d)", (void*)page, errno); return false; }
    *reinterpret_cast<void**>(addr) = value;
    mprotect((void*)page, ps, PROT_READ);
    return true;
}

static int patch_table(uintptr_t base, uintptr_t tab, size_t size, const Sym_t* symtab, const char* strtab) {
    int patched = 0;
    size_t n = size / sizeof(Rel_t);
    const Rel_t* rels = reinterpret_cast<const Rel_t*>(tab);
    for (size_t i = 0; i < n; i++) {
        unsigned type = REL_TYPE(rels[i].r_info);
        if (type != T_JUMP_SLOT && type != T_GLOB_DAT && type != T_ABS) continue;
        unsigned sym = REL_SYM(rels[i].r_info);
        if (!sym) continue;
        const char* name = strtab + symtab[sym].st_name;
        void* repl = nullptr;
        if (strcmp(name, "fopen") == 0) repl = reinterpret_cast<void*>(&vod_fopen);
        else if (strcmp(name, "rename") == 0) repl = reinterpret_cast<void*>(&vod_rename);
        if (!repl) continue;
        uintptr_t slot = base + rels[i].r_offset;
        if (patch_slot(slot, repl)) { patched++; LOGI("hooked %s at %p", name, (void*)slot); }
    }
    return patched;
}

static void do_patches() {
    if (g_origPath.empty() || g_newPath.empty()) { LOGE("paths not set"); return; }
    Ctx c; dl_iterate_phdr(find_cb, &c);
    if (!c.found) { LOGE("libcocos2dcpp.so not found in process"); return; }
    LOGI("libcocos2dcpp base=%p", (void*)c.base);

    const ElfW(Dyn)* dyn = nullptr;
    for (int i = 0; i < c.phnum; i++) if (c.phdr[i].p_type == PT_DYNAMIC) dyn = reinterpret_cast<const ElfW(Dyn)*>(c.base + c.phdr[i].p_vaddr);
    if (!dyn) { LOGE("no PT_DYNAMIC"); return; }

    auto fix = [&](uintptr_t v) { return v < c.base ? v + c.base : v; };  // bionic keeps link-time addresses
    uintptr_t jmprel = 0, rel = 0, symtab = 0, strtab = 0; size_t pltsz = 0, relsz = 0;
    for (const ElfW(Dyn)* d = dyn; d->d_tag != DT_NULL; d++) {
        switch (d->d_tag) {
#if defined(__LP64__)
            case DT_JMPREL: jmprel = fix(d->d_un.d_ptr); break;
            case DT_PLTRELSZ: pltsz = d->d_un.d_val; break;
            case DT_RELA: rel = fix(d->d_un.d_ptr); break;
            case DT_RELASZ: relsz = d->d_un.d_val; break;
#else
            case DT_JMPREL: jmprel = fix(d->d_un.d_ptr); break;
            case DT_PLTRELSZ: pltsz = d->d_un.d_val; break;
            case DT_REL: rel = fix(d->d_un.d_ptr); break;
            case DT_RELSZ: relsz = d->d_un.d_val; break;
#endif
            case DT_SYMTAB: symtab = fix(d->d_un.d_ptr); break;
            case DT_STRTAB: strtab = fix(d->d_un.d_ptr); break;
            default: break;
        }
    }
    if (!symtab || !strtab) { LOGE("missing symtab/strtab"); return; }
    int total = 0;
    if (jmprel && pltsz) total += patch_table(c.base, jmprel, pltsz, reinterpret_cast<const Sym_t*>(symtab), reinterpret_cast<const char*>(strtab));
    if (rel && relsz) total += patch_table(c.base, rel, relsz, reinterpret_cast<const Sym_t*>(symtab), reinterpret_cast<const char*>(strtab));
    LOGI("hooked %d import slot(s); redirect %s -> %s", total, g_origPath.c_str(), g_newPath.c_str());
}

extern "C" {
JNIEXPORT void JNICALL Java_com_vod_launcher_LauncherFix_setDataPath(JNIEnv* env, jclass, jstring s) {
    const char* c = env->GetStringUTFChars(s, nullptr); g_newPath = c; env->ReleaseStringUTFChars(s, c);
}
JNIEXPORT void JNICALL Java_com_vod_launcher_LauncherFix_setOriginalDataPath(JNIEnv* env, jclass, jstring s) {
    const char* c = env->GetStringUTFChars(s, nullptr); g_origPath = c; env->ReleaseStringUTFChars(s, c);
}
JNIEXPORT void JNICALL Java_com_vod_launcher_LauncherFix_performPatches(JNIEnv*, jclass) { do_patches(); }
}
