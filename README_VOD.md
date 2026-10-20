# Vod

Vod is a Geode-based mod loader for Geometry Dash on Android, plus its own launcher app.

- `loader/`, `cmake/`, `entry.cpp` ... : the Geode loader source (Boost Software License 1.0, see `LICENSE.txt`). Only the
  launcher JNI package (`com.vod.launcher`) and the displayed name were changed. The loader id (`geode.loader`) and the
  binary names (`Geode.android64.so`, `Geode.android32.so`) are kept on purpose so existing Geode mods still load.
- `launcher/` : the Vod launcher app, written from scratch (Java + a small C++ helper). Package `com.vod.launcher`.
- `assets/vod_logo.png` : Vod logo.

## Build on GitHub

1. Create a repo and push this folder.
2. Open **Actions > Build Vod > Run workflow** (it also runs on every push).
3. Download the **Vod-Launcher** artifact -> `Vod-Launcher.apk`.

## Use

1. Install Geometry Dash (Play Store, 64-bit) and Vod-Launcher.apk.
2. Open Vod and tap **Launch Geometry Dash**.
3. Put `.geode` mods in the mods folder shown on the main screen.

## Notes

- Targets the GD version the bundled Geode source supports (`GEODE_GD_VERSION` in `CMakeLists.txt`, currently 2.2081).
- If the game fails to start, the launcher shows the error with a **Copy error** button.
