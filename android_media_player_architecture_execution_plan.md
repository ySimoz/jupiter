# PLAN.md — Native Android Media Player (C / libmpv + Kotlin / Jetpack Compose)

> **Purpose:** This document is the single source of truth for AI coding agents building a high-performance, native Android media player powered by **C (`libmpv` via JNI)** and **Kotlin / Jetpack Compose**, featuring a **Glassmorphism UI**, full mpv-grade playback capabilities, and adaptive layouts for **phones and tablets**.
>
> **Execution Rule:** Work strictly **one milestone at a time**, in order. Do not begin Milestone $N+1$ until all verification gates in Milestone $N$ pass.

---

## 1. Product Vision & Core Capabilities

- **Engine:** Native C bridge wrapping `libmpv` (FFmpeg + `libass` + OpenGL ES / Vulkan GPU renderer + Android `MediaCodec` hardware decoding).
- **Aesthetic:** Modern **Glassmorphism** — layered translucent surfaces, live blur where hardware-supported, luminous specular hairline borders, spring-driven micro-animations, and dynamic media-tinted accents.
- **Form Factors:** First-class responsive UX for **Phones** (portrait/landscape, thumb-friendly bottom glass bar & sheets) and **Tablets / Foldables** (adaptive grids, glass navigation rail, side-anchored player drawers that do not obscure video).
- **Feature Parity:**
  - Local file discovery (MediaStore folders & videos), Storage Access Framework (SAF) file picker, and external `ACTION_VIEW` intent handling.
  - Network streaming (HTTP/HTTPS, HLS `.m3u8`, DASH `.mpd`, RTSP, custom HTTP headers) with saved bookmarks and recent history.
  - Full subtitle support (embedded + external `.srt`, `.ass`, `.ssa`, `.vtt`, full `libass` styling, delay, scale, secondary track).
  - Rich gesture controls (brightness, volume, horizontal seek, double-tap seek, long-press 2x speed, pinch-to-zoom, screen lock).
  - Picture-in-Picture (PiP) with custom playback actions and Background Audio playback with system `MediaSession` controls.
  - Power-user controls (hardware decoder toggle, playback speed, audio delay, chapter markers, A-B loop, custom `mpv.conf` overrides).

---

## 2. Tech Stack & Architecture

### 2.1 Pinned Platform & Tooling Targets
- **Language:** Kotlin `2.0.21+` (KSP enabled) & C11 (`-std=c11 -Wall -Wextra`)
- **Android SDK:** `minSdk = 29` (Android 10), `compileSdk = 35`, `targetSdk = 35`
- **NDK & Native Build:** Android NDK `r27+`, CMake `3.22.1+`, ABIs: `arm64-v8a`, `armeabi-v7a`, `x86_64`
- **UI Framework:** Jetpack Compose (Material 3 + `material3-window-size-class` + Compose Animation)
- **Glassmorphism Blur:** `dev.chrisbanes.haze:haze` (hardware `RenderEffect` blur on API 31+, automatic scrim/gradient fallback on API 29–30)
- **Navigation:** Navigation Compose (Type-Safe Kotlin Serialization routes)
- **Dependency Injection:** Hilt (`dagger.hilt.android`)
- **Async & State:** Kotlin Coroutines + `StateFlow` / `SharedFlow` (Unidirectional Data Flow)
- **Persistence:** Room (Watch History, Resume Positions, Network Bookmarks) + Jetpack DataStore Preferences (User Settings, `mpv.conf`)
- **Thumbnails & Media Art:** Coil 3 (`coil-compose` + `coil-video`)
- **System Media Controls:** AndroidX `androidx.media:media` (`MediaSessionCompat`) + Foreground Service (`mediaPlayback`) — avoids pulling in ExoPlayer bloat while delivering full lockscreen/Bluetooth/notification controls.

### 2.2 Repository & Module Structure (2-Module Clean Architecture)
Keep Gradle fast and agent-navigable by using **two focused modules**:
- `:engine` (Android Library) — Owns all C/CMake code, prebuilt `libmpv` `.so` libraries, JNI bindings, lifecycle-safe `MpvEngine` Kotlin wrapper, and immutable `PlaybackState` models.
- `:app` (Android Application) — Owns Compose UI, Glassmorphism Design System, ViewModels, Room DB, DataStore, MediaStore repository, PiP, and Foreground Service.

```text
root/
├── gradle/libs.versions.toml         # Centralized version catalog
├── engine/
│   ├── build.gradle.kts              # NDK, CMake, ABI splits config
│   └── src/main/
│       ├── cpp/
│       │   ├── CMakeLists.txt
│       │   ├── mpv_bridge.h          # Internal C structs & declarations
│       │   ├── mpv_bridge.c          # JNI implementation + event pthread
│       │   ├── log_util.h            # __android_log_print macros
│       │   └── include/mpv/          # libmpv C headers (client.h, render.h, etc.)
│       ├── jniLibs/
│       │   ├── arm64-v8a/libmpv.so   # Prebuilt native binaries
│       │   ├── armeabi-v7a/libmpv.so
│       │   └── x86_64/libmpv.so
│       └── java/com/glassplayer/engine/
│           ├── MpvNative.kt          # Raw external JNI declarations
│           ├── MpvEngine.kt          # Thread-safe Coroutine/Flow wrapper
│           ├── MpvEvent.kt           # Sealed event hierarchy
│           ├── PlaybackState.kt      # Immutable UI-ready state snapshot
│           └── TrackModels.kt        # Video, Audio, Subtitle, Chapter models
├── app/
│   ├── build.gradle.kts
│   └── src/main/
│       ├── AndroidManifest.xml
│       └── java/com/glassplayer/app/
│           ├── GlassPlayerApp.kt     # @HiltAndroidApp + Coil ImageLoader
│           ├── MainActivity.kt       # Single-activity host, PiP, intents
│           ├── core/
│           │   ├── designsystem/     # Glass tokens, GlassSurface, GlassPill, GlassSheet
│           │   ├── adaptive/         # WindowSizeClass helpers (Phone vs Tablet)
│           │   └── di/               # Hilt modules (DB, Engine, Repos)
│           ├── data/
│           │   ├── local/            # Room DB, DAOs, Entities, DataStore
│           │   ├── media/            # MediaStore scanner, SAF URI -> fd:// helper
│           │   └── repository/       # MediaRepository, StreamRepository, SettingsRepository
│           ├── service/              # Background MediaPlaybackService + MediaSession
│           └── feature/
│               ├── library/          # Folders, Videos, Recent Resume screen
│               ├── streams/          # Network URL input, Headers, Bookmarks screen
│               ├── player/           # Video Surface, Glass HUD, Gestures, Track Sheets
│               └── settings/         # Preferences, Hardware Decode, mpv.conf editor
└── scripts/
    └── fetch_libmpv.sh               # Reproducible script to fetch/verify prebuilt libmpv
```

---

## 3. Critical Engineering Guardrails (Read Before Coding)

AI agents **must** adhere to these five Android NDK / `libmpv` architectural rules to avoid fatal runtime bugs:

1. **Prebuilt `libmpv` Strategy (Never Cross-Compile From Scratch During App Dev):**
   - Cross-compiling FFmpeg + `dav1d` + `libass` + `freetype` + `harfbuzz` + `libmpv` inside Gradle breaks builds and wastes hours.
   - Use **prebuilt `libmpv` Android binaries** (from official `mpv-android` / Maven Central / JitPack release artifacts such as `dev.jdtech.mpv:libmpv` or `is.xyz.mpv` prebuilt `.aar`/`.so` + headers) via `scripts/fetch_libmpv.sh` or Gradle prefab/extraction into `engine/src/main/jniLibs/` and `engine/src/main/cpp/include/mpv/`.
   - Our own C library (`libglassmpv.so`) links dynamically against `libmpv.so` (`IMPORTED SHARED` in CMake) and `android` + `log`.

2. **Mandatory `JNI_OnLoad` JavaVM Registration for Hardware Decoding:**
   - FFmpeg's Android `MediaCodec` decoder (`hwdec=mediacodec-copy` / `mediacodec`) requires access to `JavaVM*`.
   - In `mpv_bridge.c`, implement `JNI_OnLoad(JavaVM *vm, void *reserved)`:
     - Cache the global `JavaVM *g_vm`.
     - Dynamically resolve `av_jni_set_java_vm` via `dlsym(RTLD_DEFAULT, "av_jni_set_java_vm")` (or link `libavcodec` symbol if exported by `libmpv.so`) and call `av_jni_set_java_vm(vm, NULL)`. Log a clear warning if unavailable and fall back cleanly to software decoding.

3. **Android `content://` URIs Require `fd://` or `fdclose://`:**
   - `libmpv` cannot open Android Scoped Storage `content://` URIs directly by string path.
   - For any `content://` URI (MediaStore, SAF file picker, external intents, external `.srt`/`.ass` subtitles):
     - Open via `contentResolver.openFileDescriptor(uri, "r")`.
     - Call `pfd.detachFd()` and pass `"fdclose://$fd"` (preferred, lets `libmpv` close the FD when done) or manage FD lifetime carefully with `"fd://$fd"`.
     - Never pass raw `content://` strings directly to `mpv_command("loadfile", ...)`.

4. **Glassmorphism Over Video (`SurfaceView` vs `TextureView`) — Zero-Jank Hybrid Rendering:**
   - Android `SurfaceView` renders in a separate hardware composer plane; Compose `RenderEffect` blur **cannot** directly blur `SurfaceView` pixels (attempting to do so causes black boxes or silent no-ops).
   - **Solution (Safest & Fastest):**
     - **Video Playback Surface:** Use `SurfaceView` bound to `libmpv` (`vo=gpu`, `gpu-context=android`, `opengl-es=yes`, `wid = (int64_t)(intptr_t)surface_global_ref`) for zero-copy 4K/60fps/HDR performance and glitch-free PiP.
     - **Library, Streams & Settings Screens:** Use **true live frosted blur** via `Haze` (`Modifier.hazeSource` on content + `Modifier.hazeEffect` on floating Glass Nav Bar / Rail / Dialogs / Cards).
     - **Player HUD Overlays & Track Sheets:** Use **Tinted Specular Frosted Glass** (semi-transparent dark adaptive backdrop `rgba(18, 22, 30, 0.72)`, inner vertical sheen gradient, `1.dp` luminous top-left-to-bottom-right specular border, soft ambient shadow, and smooth spring animations). This delivers an authentic glass aesthetic with 0% frame drop over 4K video.

5. **Surviving Rotation & PiP Without Killing `libmpv`:**
   - Declare `android:supportsPictureInPicture="true"` and `android:configChanges="screenSize|smallestScreenSize|screenLayout|orientation|keyboard|keyboardHidden|uiMode"` on `MainActivity` in `AndroidManifest.xml`.
   - Always clamp PiP aspect ratios strictly between `Rational(100, 239)` (`0.418410`) and `Rational(239, 100)` (`2.390000`) before calling `setAspectRatio(...)` — Android throws `IllegalArgumentException` outside these bounds.
   - When the `SurfaceView` is destroyed (e.g., background audio mode), detach the surface cleanly (`mpv_set_option_string(ctx, "vid", "no")` or clear `wid` + `vo=null`) and re-attach when the surface returns, without interrupting audio playback.

---

## 4. Glassmorphism Design System & Adaptive UX Specification

### 4.1 Visual Tokens (`core/designsystem`)
- **Theme:** Deep obsidian-slate dark canvas (`#090C12`) by default (best for video contrast & glass depth), with subtle dynamic ambient mesh gradient blobs behind the Library/Streams/Settings screens so live glass blur has rich depth to refract.
- **Glass Surfaces (`GlassSurface` composable):**
  - *Primary Glass (Nav bars, HUD pills, Sheets):* `Color(0xFF141923)` at `68–76%` alpha (higher opacity fallback on API 29–30), `20.dp` blur radius (when backed by Compose Haze), `24.dp` corner radius.
  - *Elevated Glass (Active buttons, Gesture indicator pills):* `Color(0xFF1E2636)` at `78%` alpha, `1.dp` linear gradient border (`Color.White.copy(0.28f)` top-left $\rightarrow$ `Color.White.copy(0.05f)` bottom-right).
  - *Accent Glow:* Electric Cyan / Ice Blue (`#5CE1E6`) or Material You Dynamic Accent (`dynamicDarkColorScheme` on API 31+ as an optional theme setting).
- **Motion System:**
  - HUD show/hide: `fadeIn(tween(220)) + slideInVertically` / `fadeOut(tween(260))`.
  - Interactive glass pills & gesture overlays: `spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMediumLow)`.
  - Sheet transitions: smooth spring slide + scrim fade.

### 4.2 Adaptive Phone & Tablet Layout Matrix
Using `WindowWidthSizeClass`:
| Screen / Component | Compact (Phone Portrait) | Medium / Expanded (Tablet, Foldable, Landscape) |
| :--- | :--- | :--- |
| **App Navigation** | Floating bottom Glass Pill Navigation Bar (`Library`, `Streams`, `Settings`) | Persistent left Glass `NavigationRail` with expanded labels on Expanded width |
| **Media Library Grid** | 1-column list or 2-column thumbnail grid | Adaptive multi-column grid (`GridCells.Adaptive(minSize = 220.dp)`) with larger preview cards |
| **Player Track / Settings Sheets** | Rounded Glass `ModalBottomSheet` (max height 65%) | Right-anchored Glass Side Drawer (`360.dp` width) so video playback stays unobstructed |
| **Player HUD Controls** | Compact top bar + centered play/seek controls + bottom scrubber | Spacious floating glass dock with direct quick-access chips (Speed, Audio, Subs, PiP, Aspect) |

---

## 5. Data & Persistence Schema

### 5.1 Room Database (`GlassPlayerDatabase`)
1. `MediaHistoryEntity`:
   - `uriHash: String` (PK), `uri: String`, `displayName: String`, `durationMs: Long`, `positionMs: Long`, `lastPlayedEpochMs: Long`, `selectedAudioTrackId: Int?`, `selectedSubTrackId: Int?`, `playbackSpeed: Float`, `isNetworkStream: Boolean`
2. `NetworkStreamEntity`:
   - `id: Long` (PK auto), `title: String`, `url: String`, `userAgent: String?`, `referer: String?`, `customHeadersJson: String?`, `isPinned: Boolean`, `createdAtEpochMs: Long`, `lastPlayedEpochMs: Long?`

### 5.2 DataStore Preferences (`UserPreferences`)
- `hwDecMode`: Enum (`AUTO`, `MEDIACODEC_COPY`, `MEDIACODEC`, `SOFTWARE`) — default `AUTO` (`mediacodec-copy`)
- `doubleTapSeekSec`: Int (default `10`)
- `defaultPlaybackSpeed`: Float (default `1.0f`)
- `rememberResumePosition`: Boolean (default `true`)
- `backgroundPlayEnabled`: Boolean (default `true`)
- `autoEnterPip`: Boolean (default `true`)
- `subtitleFontScale`: Float (default `1.0f`)
- `gestureBrightnessEnabled` / `gestureVolumeEnabled` / `gestureSeekEnabled`: Boolean (default `true`)
- `customMpvConf`: String (raw multi-line `mpv.conf` directives applied safely on init)

---

## 6. Milestone Execution Plan

Each milestone builds directly on the previous one. Complete all checkboxes and run every verification command before advancing.

---

### Milestone 0: Project Scaffold, Gradle Catalog & NDK Toolchain
**Goal:** Establish a clean, compiling 2-module Android project (`:app` and `:engine`) with CMake, Hilt, Compose, and prebuilt `libmpv` wired up.

**Tasks:**
- [ ] Initialize Gradle Kotlin DSL project with `gradle/libs.versions.toml` defining Compose BOM, Material 3, WindowSizeClass, Navigation Compose, Hilt, Room, DataStore, Coil 3, Haze, Coroutines, and Testing deps.
- [ ] Configure `:app` (`minSdk = 29`, `compileSdk = 35`, `targetSdk = 35`, Compose enabled, KSP + Hilt).
- [ ] Configure `:engine` Android Library with `externalNativeBuild { cmake { path("src/main/cpp/CMakeLists.txt") } }` and `ndk { abiFilters += listOf("arm64-v8a", "armeabi-v7a", "x86_64") }`.
- [ ] Create `scripts/fetch_libmpv.sh` to download/extract prebuilt Android `libmpv.so` (and any required shared deps) + C headers (`mpv/client.h`, `mpv/render.h`, etc.) into `engine/src/main/jniLibs/` and `engine/src/main/cpp/include/`. Verify `.so` architecture matches each ABI folder via `file`.
- [ ] Create minimal `engine/src/main/cpp/CMakeLists.txt` and stub `mpv_bridge.c` that links against `libmpv.so` and exposes a single JNI test call `nativeGetMpvVersion(): String` calling `mpv_client_api_version()`.
- [ ] Wire `MainActivity` displaying a placeholder Compose screen reading `nativeGetMpvVersion()`.

**Verification Gate (M0):**
```bash
./gradlew :engine:assembleDebug :app:assembleDebug
```
- Build succeeds with zero CMake/linker errors.
- App launches on emulator (`x86_64`) or device (`arm64-v8a`) and displays a valid `libmpv` API version string.

---

### Milestone 1: Native C Engine & Thread-Safe JNI Bridge (`:engine`)
**Goal:** Build a rock-solid, leak-free C JNI bridge to `libmpv` and wrap it in an ergonomic, lifecycle-safe Kotlin `MpvEngine` exposing `StateFlow<PlaybackState>`.

**Tasks:**
- [ ] **C Bridge (`mpv_bridge.h`, `mpv_bridge.c`, `log_util.h`):**
  - Implement `JNI_OnLoad` to cache `JavaVM*` and invoke `av_jni_set_java_vm` via `dlsym`.
  - Create context struct `MpvContext` holding `mpv_handle*`, `pthread_t event_thread`, `atomic_bool running`, `jobject callback_global_ref`, and `jobject surface_global_ref`.
  - Implement JNI lifecycle functions: `nativeCreate(callback, configDir)`, `nativeInitialize()`, `nativeDestroy()`.
  - Implement surface attachment: `nativeAttachSurface(surface: Surface)` (creates `NewGlobalRef`, sets `wid` option as `int64_t` + `vo=gpu`, `gpu-context=android`, `opengl-es=yes`, `ao=audiotrack,opensles`) and `nativeDetachSurface()` (clears `wid`, sets `vo=null`, releases global ref).
  - Implement playback commands: `nativeLoadFile(uriOrFd, options)`, `nativeCommand(args: Array<String>)`, `nativeSetOptionString(name, value)`, `nativeSetPropertyString/Int/Double/Boolean`, `nativeGetPropertyString/Int/Double/Boolean`.
  - Implement dedicated `pthread` event loop calling `mpv_wait_event(ctx->mpv, -1)`:
    - Attach thread once via `AttachCurrentThread`, detach cleanly on exit.
    - Observe core properties on startup: `time-pos`, `duration`, `pause`, `paused-for-cache`, `cache-buffering-state`, `speed`, `video-params/aspect`, `video-params/w`, `video-params/h`, `track-list`, `chapter-list`, `chapter`, `eof-reached`, `media-title`.
    - Dispatch property changes and lifecycle events (`START_FILE`, `FILE_LOADED`, `END_FILE`, `SEEK`) back to Kotlin callback.
  - Ensure all JNI local references inside the event loop are deleted (`DeleteLocalRef`) to prevent JNI local reference table overflow!
- [ ] **Kotlin Engine Wrapper (`MpvEngine.kt`, `PlaybackState.kt`, `TrackModels.kt`):**
  - Wrap `MpvNative` in an injectable singleton/scoped `MpvEngine` with mutex-protected lifecycle states (`IDLE`, `INITIALIZED`, `DESTROYED`).
  - Parse `track-list` and `chapter-list` into clean Kotlin data classes (`AudioTrack`, `SubtitleTrack`, `VideoTrack`, `Chapter`).
  - Expose `val state: StateFlow<PlaybackState>` and `val events: SharedFlow<MpvEvent>`.
  - Provide high-level methods: `play()`, `pause()`, `togglePlayPause()`, `seekTo(ms)`, `seekBy(deltaSec)`, `setSpeed(Float)`, `selectAudioTrack(id)`, `selectSubtitleTrack(id)`, `addExternalSubtitle(fdPath)`, `setSubtitleDelay(sec)`, `setAudioDelay(sec)`, `setVideoZoom(Double)`, `setAspectOverride(String)`, `setHwDec(HwDecMode)`.
- [ ] Write unit tests for `PlaybackState` / track list parsing and an instrumented JNI smoke test in `:engine`.

**Verification Gate (M1):**
```bash
./gradlew :engine:testDebugUnitTest :engine:assembleDebugAndroidTest
```
- Engine initializes, observes properties, plays a bundled test clip or sample URL in a smoke test without crashes, JNI ref leaks, or ANRs.

---

### Milestone 2: Glassmorphism Design System & Adaptive App Shell
**Goal:** Build the reusable Glassmorphism component library and the adaptive Phone/Tablet navigation skeleton.

**Tasks:**
- [ ] Create `core/designsystem/Theme.kt` with dark obsidian background, luminous specular border brushes, typography scale, and ambient gradient background canvas (`AmbientGlassBackground`).
- [ ] Build reusable glass components in `core/designsystem/GlassComponents.kt`:
  - `GlassSurface` (accepts optional `HazeState`; renders live blur on API 31+ and frosted translucent gradient on API 29–30, plus specular `1.dp` gradient border).
  - `GlassPill` (floating badges, HUD indicators, nav capsule).
  - `GlassIconButton` (spring scale-on-press feedback + glass halo).
  - `GlassDialog` & `GlassAdaptiveSheet` (renders `ModalBottomSheet` on `Compact` width and right-anchored floating Glass Side Panel on `Medium`/`Expanded` width).
- [ ] Implement `core/adaptive/WindowSizeUtils.kt` using `calculateWindowSizeClass`.
- [ ] Build the Navigation Compose graph with type-safe routes:
  - `LibraryRoute`
  - `StreamsRoute`
  - `SettingsRoute`
  - `PlayerRoute(mediaUri, title, isNetworkStream, headersJson)`
- [ ] Build the top-level `MainShell` with floating bottom Glass Pill Nav Bar on Phones and left Glass `NavigationRail` on Tablets/Foldables.

**Verification Gate (M2):**
```bash
./gradlew :app:assembleDebug :app:testDebugUnitTest
```
- Compose previews verified for Phone Portrait (`412x915`), Phone Landscape, and Tablet (`1280x800`).
- Smooth transitions between Library, Streams, and Settings tabs with live Haze blur over scrollable content.

---

### Milestone 3: Core Video Player, Glass HUD & Full Gesture Controls
**Goal:** Build the flagship full-screen video player experience with `SurfaceView`, animated Glass HUD, and responsive touch gestures.

**Tasks:**
- [ ] **Video Surface Integration (`MpvSurfaceView.kt`):**
  - Embed Android `SurfaceView` via `AndroidView`, forwarding `surfaceCreated`, `surfaceChanged`, and `surfaceDestroyed` cleanly to `MpvEngine`.
  - Respect system edge-to-edge immersive mode (`WindowInsetsControllerCompat`: hide status/nav bars while HUD is hidden, show transiently or cleanly pad HUD with `systemBarsPadding()`).
- [ ] **Glass HUD (`PlayerHudOverlay.kt`):**
  - Top Glass Bar: Back button, marquee media title, PiP button, Rotation Lock button, Screen Lock button.
  - Center Controls: Rewind 10s, Animated Play/Pause Glass Orb, Forward 10s (plus smooth buffering ring when `pausedForCache == true`).
  - Bottom Glass Control Dock: Custom glass scrubber with chapter tick marks, buffered progress track, elapsed/remaining time toggle, quick-access pills (Speed, Subtitles, Audio, Aspect Ratio, More).
  - Auto-hide timer (4 seconds of inactivity while playing; paused while scrubbing or when any sheet is open).
- [ ] **Complete Gesture Engine (`PlayerGestureLayer.kt`):**
  - Single tap: Toggle HUD visibility (when unlocked) or briefly flash Unlock Glass Pill (when locked).
  - Double-tap left / right third: Seek `-N` / `+N` seconds with ripple & animated chevron glass overlay (supports rapid consecutive taps accumulating `-10s`, `-20s`, `-30s`).
  - Vertical drag (left half): Adjust window brightness (`WindowManager.LayoutParams.screenBrightness`) with floating Glass Vertical Meter Pill.
  - Vertical drag (right half): Adjust media volume (`AudioManager.STREAM_MUSIC`) with floating Glass Vertical Meter Pill.
  - Horizontal drag: Precision timeline scrub showing floating Glass Preview Pill (`00:42:15 (+00:18)`), committing seek on release.
  - Long-press anywhere: Smoothly boost playback speed to `2.0x` while held, displaying a pulsing top Glass Pill (`2x Speed >>`), restoring previous speed on release.
  - Pinch-to-zoom (`detectTransformGestures`): Smoothly toggle Fit/Crop or scale `video-zoom` with glass ratio badge.
  - Ignore edge swipes near system gesture zones (`24.dp` margin) so Android back/home gestures never conflict.
- [ ] Implement `PlayerViewModel` bridging `MpvEngine` state, HUD timers, lock states, and orientation lock.

**Verification Gate (M3):**
```bash
./gradlew :app:testDebugUnitTest :app:assembleDebug
```
- All gestures work reliably without fighting each other (e.g., vertical swipe does not accidentally trigger horizontal seek; pinch does not trigger tap).
- HUD animates at 60fps+ with zero flicker during video playback.

---

### Milestone 4: Local Media Library, SAF Pickers & Watch History
**Goal:** Discover local videos automatically, support opening any file via SAF or external apps, and persist resume positions.

**Tasks:**
- [ ] **Room Database & DAOs (`data/local/`):**
  - Implement `GlassPlayerDatabase`, `MediaHistoryDao`, `NetworkStreamDao`, and Hilt DI bindings.
- [ ] **Local Media Scanner (`data/media/LocalMediaScanner.kt`):**
  - Query `MediaStore.Video.Media.EXTERNAL_CONTENT_URI` on `Dispatchers.IO` for ID, display name, duration, size, bucket/folder name, date modified, resolution, and content URI.
  - Handle runtime permissions cleanly (`READ_MEDIA_VIDEO` on API 33+, `READ_EXTERNAL_STORAGE` on API 29–32) with a friendly Glass Permission Prompt and fallback to SAF picker if denied.
- [ ] **SAF & External Intent Support (`data/media/UriResolver.kt`):**
  - Implement `resolveToMpvPath(uri: Uri): MpvSource` converting `content://` URIs to `fdclose://<fd>` safely and extracting human-readable filenames.
  - Register `ACTION_VIEW` intent-filters (`video/*`, `audio/*`, `application/x-mpegURL`, `application/dash+xml`, common extensions) in `AndroidManifest.xml` and handle `onNewIntent` in `MainActivity`.
- [ ] **Adaptive Library UI (`feature/library/`):**
  - "Continue Watching" horizontal glass carousel showing progress bars from Room `MediaHistoryEntity`.
  - Folder view & All Videos view with search bar, sort chips (Name, Date, Duration, Size), and Coil video frame thumbnails.
  - Floating Glass FAB to open system SAF file picker (`ActivityResultContracts.OpenDocument`).
  - Automatically save resume position every 5 seconds during playback and on pause/stop; clear/reset resume if watched > 95%.

**Verification Gate (M4):**
```bash
./gradlew :app:testDebugUnitTest :app:assembleDebug
```
- Local videos load with thumbnails; tapping a folder filters cleanly; opening a file via SAF or external File Manager launches straight into `PlayerScreen` and resumes where left off on reopen.

---

### Milestone 5: Network Streaming & Bookmarks
**Goal:** Enable reliable streaming over HTTP/HTTPS, HLS, DASH, and RTSP with saved bookmarks and custom HTTP headers.

**Tasks:**
- [ ] **Stream Repository (`data/repository/StreamRepository.kt`):**
  - CRUD operations on `NetworkStreamEntity` (add, edit, pin/favorite, delete, sort by recent).
  - Validate URL schemes (`http`, `https`, `rtsp`, `rtmp`, `mms`, `udp`, `rtp`, `ftp`, `smb`).
- [ ] **mpv Network Configuration:**
  - Configure sensible streaming cache defaults in `MpvEngine` (`cache=yes`, `demuxer-max-bytes=128MiB`, `demuxer-max-back-bytes=32MiB`, `ytdl=no`).
  - Pass per-stream `user-agent`, `referrer`, and `http-header-fields` cleanly to `libmpv` before calling `loadfile`.
- [ ] **Adaptive Streams UI (`feature/streams/`):**
  - Quick-play Glass URL bar with clipboard paste chip.
  - "Add / Edit Stream" Glass Dialog supporting Title, URL, optional User-Agent, Referer, and custom header key-value pairs.
  - Saved Bookmarks grid/list (1-col phone, multi-col tablet) with pinned section and quick delete/edit menu.
  - Player error banner/dialog with "Retry" and diagnostic error message if a stream fails to connect or drops.

**Verification Gate (M5):**
```bash
./gradlew :app:testDebugUnitTest :app:assembleDebug
```
- Public HLS (`.m3u8`) and direct MP4 HTTPS streams play smoothly, display buffering percentage when network slows, persist in bookmarks, and preserve custom headers across restarts.

---

### Milestone 6: Full Subtitle Engine, Audio Tracks & mpv Power Controls
**Goal:** Expose the full power of `libmpv` and `libass` through sleek adaptive Glass sheets.

**Tasks:**
- [ ] **Subtitle Support:**
  - Display all embedded subtitle tracks (language, title, codec) with instant switching and "Off" option.
  - Add "Load External Subtitle" button launching SAF (`text/*`, `*/*` filtering `.srt`, `.ass`, `.ssa`, `.vtt`, `.sub`), converting URI to `fdclose://`, and calling `sub-add`.
  - Support Secondary Subtitles (`secondary-sid`), Subtitle Delay (`sub-delay` with `-100ms`/`+100ms` steppers & reset), and Subtitle Scale (`sub-scale` slider).
  - Configure `libass` font fallback (`sub-font-provider=none` or bundled Noto/Roboto TTF copied to app files dir + `config-dir` + `sub-font`) so non-embedded ASS/SRT fonts render crisply on Android!
- [ ] **Audio Track & Sync Controls:**
  - Embedded audio track selector (language, codec, channel count) + Audio Delay (`audio-delay` stepper).
- [ ] **Power-User Player Sheet (Adaptive Bottom Sheet on Phone / Side Drawer on Tablet):**
  - Playback Speed selector (`0.25x` to `4.0x` presets + fine slider).
  - Aspect Ratio modes (Fit, Fill/Crop, Stretch, 16:9, 4:3, 21:9).
  - Hardware Decoding quick toggle (`Auto` / `HW+` / `SW`).
  - Chapter list jump sheet (when media has chapters).
  - A-B Loop toggle (Set A $\rightarrow$ Set B $\rightarrow$ Clear).
  - Video Equalizer sliders (Brightness, Contrast, Saturation, Gamma via `libmpv` properties).

**Verification Gate (M6):**
```bash
./gradlew :app:testDebugUnitTest :app:assembleDebug
```
- Complex styled `.ass` subtitles render accurately with bundled fallback font; external `.srt` loads from storage; audio/subtitle delays apply in real time; tablet side drawer keeps video unobstructed.

---

### Milestone 7: Picture-in-Picture (PiP), Background Playback & MediaSession
**Goal:** Deliver seamless Android system integration for multitasking and background audio.

**Tasks:**
- [ ] **Picture-in-Picture (`MainActivity.kt` + `PipController.kt`):**
  - Build `PictureInPictureParams` with clamped video aspect ratio (`0.418410` to `2.390000`) and `RemoteAction` buttons (Seek -10s, Play/Pause dynamic icon, Seek +10s) handled via a registered `BroadcastReceiver`.
  - Enable `setAutoEnterEnabled(true)` and `setSeamlessResizeEnabled(true)` on API 31+ while actively playing; trigger `enterPictureInPictureMode` in `onUserLeaveHint()` for API 29–30.
  - Observe `isInPictureInPictureMode` in `PlayerScreen` to hide all HUD overlays and disable gesture interceptors while in PiP.
- [ ] **Background Audio & System MediaSession (`service/MediaPlaybackService.kt`):**
  - Create Foreground Service (`android:foregroundServiceType="mediaPlayback"`, request `POST_NOTIFICATIONS` on API 33+ gracefully).
  - Integrate `MediaSessionCompat` publishing metadata (title, duration, thumbnail bitmap) and `PlaybackStateCompat` (play/pause, seek bar, next/prev/seek actions).
  - Handle `AudioManager.OnAudioFocusChangeListener` (pause on transient loss/calls, duck on notification audio, resume cleanly) and `ACTION_AUDIO_BECOMING_NOISY` (auto-pause when headphones disconnect).
  - When user navigates to background with Background Play enabled (and not in PiP), detach video surface cleanly while keeping audio playing smoothly; re-attach video surface instantaneously on return.

**Verification Gate (M7):**
```bash
./gradlew :app:testDebugUnitTest :app:assembleDebug
```
- Swiping home while playing transitions smoothly into PiP with working Play/Pause/Seek buttons and zero video restart.
- Turning screen off with Background Play enabled continues audio playback with working lockscreen & notification controls; unplugging headphones pauses playback.

---

### Milestone 8: Settings, Custom `mpv.conf`, Profiling & Release Hardening
**Goal:** Complete user preferences, optimize performance & memory safety, and harden the release build.

**Tasks:**
- [ ] **Settings Screen (`feature/settings/`):**
  - Categorized adaptive Glass settings cards:
    - *Playback:* Hardware decoder mode, default speed, resume toggle, background play, auto-PiP.
    - *Gestures:* Enable/disable brightness/volume/seek gestures, double-tap seek duration (5/10/15/30s).
    - *Subtitles:* Default font scale, preferred subtitle/audio language codes.
    - *Appearance:* Dynamic accent color (API 31+), glass blur intensity / reduced-transparency accessibility toggle.
    - *Advanced:* Raw `mpv.conf` text editor (saved to `filesDir/mpv/mpv.conf` with a "Reset to Defaults" safety button) and "Clear Watch History / Cache".
- [ ] **Hardening & QA:**
  - Add ProGuard / R8 keep rules (`app/proguard-rules.pro` & `engine/consumer-rules.pro`) protecting all JNI callbacks, `MpvNative`, Kotlin Serialization routes, and Room entities.
  - Audit `mpv_bridge.c` for memory leaks, thread termination on `nativeDestroy()`, and null safety on every JNI call.
  - Verify `release` build (`minifyEnabled = true`, `shrinkResources = true`) runs cleanly without obfuscation crashes.
  - Verify Foldables / Multi-Window split-screen resizing does not drop playback or corrupt glass layout insets.

**Verification Gate (M8 — Final Sign-Off):**
```bash
./gradlew clean testDebugUnitTest lintDebug assembleRelease
```
- All unit tests pass, zero critical lint errors, and signed/unsigned Release APK installs and passes full manual smoke checklist (Local file, SAF file, External Intent, HLS stream, ASS/SRT subtitles, Gestures, PiP, Background Audio, Phone + Tablet layouts).

---

## 7. AI Agent Operational Guidelines

1. **Never break the build across milestones:** Always run the milestone's `./gradlew` verification commands before reporting completion.
2. **Keep Compose Recomposition Lean:** Never pass raw `60fps` `time-pos` updates to the entire `PlayerScreen` tree. Pass lambdas (`() -> Long`) or isolate the scrubber and timestamp composables so video playback stays silky smooth.
3. **Strict JNI Safety:** Every `NewGlobalRef` must have a matching `DeleteGlobalRef`. Every `GetStringUTFChars` must have a matching `ReleaseStringUTFChars`. Always check `(*env)->ExceptionCheck(env)` after calling back into Java/Kotlin from C.
4. **Accessible Glassmorphism:** Always ensure text and icons on Glass surfaces maintain high contrast ($\ge 4.5:1$) via dark translucent backing layers—never place white icons directly over bright un-scrimmed video frames.
5. **Document Deviations:** If a dependency version or prebuilt `libmpv` artifact layout differs from what is listed here, update this `PLAN.md` and `scripts/fetch_libmpv.sh` immediately so subsequent agent sessions stay aligned.