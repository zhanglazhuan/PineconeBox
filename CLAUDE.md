# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

PineCone OS ("松果智学") is a custom Android TV operating system for Raspberry Pi 5, consisting of a prebuilt LineageOS 23.2 ROM and a custom Kotlin launcher app that serves as the system Home screen. The launcher targets Chinese educational content (English learning, reading, documentaries, learning apps).

## Repository Structure

```
baseOS/                         # Prebuilt LineageOS 23.2 ROMs for RPi5
├── lineage-23.2-...-rpi5.zip       # Standard AOSP variant
└── lineage-23.2-...-rpi5-atv.zip   # Android TV (Google TV) variant
launcher/                       # Android TV launcher app (Gradle/AGP)
├── app/src/main/java/com/pinecone/launcher/
│   ├── MainActivity.kt             # Production launcher: category rows + app launching
│   ├── MainFragment.kt             # Leanback sample template: movie grid + backgrounds
│   ├── DetailsActivity.kt          # Detail screen host activity
│   ├── VideoDetailsFragment.kt     # Detail view: overview, actions, related videos
│   ├── PlaybackActivity.kt         # Video playback host activity
│   ├── PlaybackVideoFragment.kt    # Leanback VideoSupportFragment + MediaPlayerAdapter
│   ├── Movie.kt                    # Serializable data class for video entities
│   ├── MovieList.kt                # Hardcoded sample video catalog (Google sample videos)
│   ├── CardPresenter.kt            # ImageCardView renderer using Glide
│   ├── DetailsDescriptionPresenter.kt  # Binds Movie data to detail description view
│   ├── BrowseErrorActivity.kt      # Error screen host with spinner
│   └── ErrorFragment.kt            # Leanback ErrorSupportFragment
│   ├── res/
│   │   ├── layout/activity_main.xml      # FragmentContainerView hosting BrowseSupportFragment
│   │   └── layout/activity_details.xml   # FrameLayout host for detail fragment
│   └── keepRules/rules.keep              # R8 ProGuard rules (currently empty)
├── build.gradle.kts              # Root: applies android-application plugin (false)
├── app/build.gradle.kts          # App module: SDK 36, AGP 9.3.0, Leanback + Glide
├── gradle/libs.versions.toml     # Version catalog
└── gradle.properties             # config-cache=true, kotlin.code.style=official
```

## Build Commands

```bash
cd launcher

# Build debug APK
./gradlew assembleDebug

# Build release APK (optimization disabled by default)
./gradlew assembleRelease

# Clean build
./gradlew clean

# Install on connected device/emulator
./gradlew installDebug

# The project uses Aliyun Maven mirrors defined in settings.gradle.kts.
# No special flags needed — Gradle resolves from those mirrors automatically.
```

## Architecture Notes

**Two launcher implementations coexist:**

- `MainActivity.kt` is the actual PineCone launcher. It finds a `BrowseSupportFragment` declared in `activity_main.xml` and populates it with hardcoded `CourseItem` rows (English, Reading, Documentary, Learning Apps). Items with `pkg:` prefixed action URLs launch other installed apps via `getLaunchIntentForPackage()`.

- `MainFragment.kt` is the upstream Leanback template code. It programmatically creates the `BrowseSupportFragment`, shuffles `MovieList` entries into category rows, manages background images with a 300ms debounce timer, and navigates to `DetailsActivity`. `BrowseErrorActivity` references this fragment as the base content before showing an error overlay.

**Navigation flow:** `MainActivity` → item click either launches an external app or shows a Toast. In the `MainFragment` path: `MainFragment` → `DetailsActivity`/`VideoDetailsFragment` → `PlaybackActivity`/`PlaybackVideoFragment`.

**Data model:** `Movie` is a `Serializable` data class passed via Intent extras. `MovieList` is a singleton with 5 hardcoded sample videos hosted on Google's CDN (commondatastorage.googleapis.com/android-tv/Sample%20videos/). `CourseItem` is a simple data class local to `MainActivity.kt`.

**Image loading:** Glide 4.16.0 via `@com.github.bumptech.glide:glide`. Backgrounds use `CustomTarget<Drawable>` or `CustomTarget<Bitmap>`; card images use the standard `into(imageView)` pattern.

**Video playback:** Uses Leanback's `VideoSupportFragment` + `MediaPlayerAdapter` + `PlaybackTransportControlGlue`. The adapter plays from a URL via `setDataSource(Uri.parse(videoUrl))`.

## Key Configuration

- **SDK:** compileSdk 36 (minorApiLevel 1), minSdk 36, targetSdk 36 — targeting very recent Android
- **AGP:** 9.3.0 with version catalog (`gradle/libs.versions.toml`)
- **JVM target:** Java 11
- **Gradle:** Configuration cache enabled, `kotlin.code.style=official`
- **ProGuard/R8:** keep rules file exists but is empty (disabled optimization in release builds)
- **Manifest:** Requires `android.software.leanback`, declares touchscreen NOT required, registers as `HOME` + `LEANBACK_LAUNCHER` category (system launcher)
- **Theme:** `Theme.Leanback` (Android TV material theme)

## baseOS ROMs

The two zip files in `baseOS/` are prebuilt LineageOS 23.2 images for Raspberry Pi 5 from KonstaKANG. The `-atv` variant includes Google TV (Android TV) components; the standard variant is AOSP without Google services. These are flashed to an SD card for the RPi5. The launcher APK is installed on top of the ATV variant to replace the default launcher.

These .zip files each contain a single 15GB raw MBR disk image (NOT sparse Android images). The partition layout is:

| Partition | Type | LBA | Offset (bytes) | Size | Purpose |
|-----------|------|-----|-----------------|------|---------|
| P1 (primary) | FAT32 | 2048 | 1,048,576 | 128 MB | boot |
| V1 (logical) | ext4 | 266,240 | 136,314,880 | 3 GB | **system** |
| V2 (logical) | ext4 | 6,559,744 | 3,358,588,928 | 384 MB | vendor |
| V3 (logical) | ext4 | 7,348,224 | 3,762,290,688 | 16 MB | misc/oem |
| P3 (primary) | ext4 | 7,383,040 | 3,780,116,480 | 11 GB | userdata |

## Build & Inject Workflow (`scripts/`)

```
scripts/
├── inject.ps1              # Windows PowerShell: build APK + inject into ROM via WSL
├── inject_apk.sh           # Linux injection engine: mount → copy → remove launchers → repack
├── detect_partitions.py    # Parse MBR/EBR to find system partition offset
├── inject_apk.py           # Python fallback (prints WSL command if no TTY)
└── build.sh                # Bash build script (Git Bash / Linux)
```

### All-in-one (PowerShell — recommended for Windows)

```powershell
cd D:\Proj\PineConeOS
.\scripts\inject.ps1                    # Build APK + inject into ROM
.\scripts\inject.ps1 -BuildOnly         # Just build the APK
.\scripts\inject.ps1 -InjectOnly        # Just inject (skip build)
.\scripts\inject.ps1 -Apk <path> -Rom <path>  # Custom paths
```

The PowerShell script:
1. Builds the APK via `.\gradlew.bat assembleDebug` (auto-detects JAVA_HOME)
2. Converts Windows paths to WSL `/mnt/` paths
3. Runs `wsl -d Ubuntu -- sudo bash inject_apk.sh ...` with console passthrough
4. sudo password prompt works because PowerShell passes the real console to WSL

### Injection details (what `inject_apk.sh` does)

1. Extracts the .img from ROM zip
2. Finds system partition offset (LBA 266240 = byte offset 136,314,880)
3. Mounts via `mount -o loop,offset=136314880`
4. Copies APK to `/system/app/PineConeLauncher/`
5. Removes competing launchers (Trebuchet, Launcher3, LeanbackLauncher, TvLauncher)
6. Sets permissions (755 dir, 644 apk) and SELinux context
7. Unmounts and repacks into `<rom>-pinecone.zip`
