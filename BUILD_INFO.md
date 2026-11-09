# 🧰 Build Information

## Project Information
- **Project Name:** GoogleDriveSync
- **Package Name:** `com.tanvir.googlesync`
- **Primary Language:** Java 17 (app), Go (bundled rclone, built from source)
- **Target Platform:** Android 7.0+ (API 24 to 34)

## Build Environment
- **Java Development Kit (JDK):** OpenJDK 21 (the code targets Java 17; CI builds releases with JDK 21)
- **Gradle Version:** 8.12
- **Android Gradle Plugin (AGP):** 8.7.3
- **Go:** 1.26 or newer (`rclone-android/go.mod` requires 1.26). CI pins 1.26.
- **Android NDK:** r27c (`27.2.12479018`)
- **Android Studio / IntelliJ IDEA:** 2024.1+

## SDK and Tools
- **compileSdkVersion:** 34
- **minSdkVersion:** 24 (Android 7.0 Nougat)
- **targetSdkVersion:** 34 (Android 14)
- **ABIs:** `arm64-v8a`, `armeabi-v7a`, `x86_64` (one APK each, plus a universal APK)

## Core Dependencies
All dependencies come from Maven Central or Google's Maven repository.

- `androidx.appcompat:appcompat:1.7.0`
- `com.google.android.material:material:1.12.0`
- `androidx.activity:activity:1.9.0`
- `androidx.constraintlayout:constraintlayout:2.1.4`
- `androidx.recyclerview:recyclerview:1.3.2`
- `androidx.swiperefreshlayout:swiperefreshlayout:1.1.0`
- `androidx.lifecycle:lifecycle-viewmodel:2.8.3` / `lifecycle-livedata:2.8.3`
- `androidx.work:work-runtime:2.9.1` (background sync)
- `androidx.room:room-runtime:2.6.1` + `room-compiler` (folder pairs database)
- `com.google.code.gson:gson:2.10.1` (parsing rclone's JSON output)
- `junit:junit:4.13.2` (unit tests)

## Bundled Native Binary
- `app/src/main/jniLibs/<abi>/librclone.so`: [rclone](https://github.com/rclone/rclone) **v1.76.0** (MIT), built from source by `scripts/build-rclone.sh`.
  - **Trimmed build**: `rclone-android/main.go` is rclone's own `main`, but it registers only the **Google Drive** and **local** backends and the commands the app runs (`copy`, `sync`, `bisync`, `lsjson`, `mkdir`, `about`, `config`, `authorize`, `version`). All rclone code is unmodified upstream. This shrinks the binary from ~85 MB to ~26 MB per ABI. If the app starts using another rclone command or backend, add its import there.
  - **Pinned dependencies**: the rclone version is set in `rclone-android/go.mod`, and `go.sum` locks every dependency to the exact versions upstream rclone uses.
  - Built with `CGO_ENABLED=1` against the Android NDK, so host-name lookups use Android's resolver. A pure-Go build cannot resolve DNS on Android because there is no `/etc/resolv.conf`.
  - Flags: `-trimpath -buildvcs=false -ldflags "-s -w -buildid="`, so the same inputs produce a byte-identical binary.
  - Not committed to git. Gradle's `buildRclone` task rebuilds it when `rclone-android/` or the script changes or a binary is missing (otherwise it is skipped, and Go/NDK aren't needed); CI caches the result.
  - Packaged with `useLegacyPackaging true`, so the installer extracts it into `nativeLibraryDir`, the only place an app may execute files from on Android 10+.

## Build Status
- **Code Minification & Shrinking:** R8 enabled (`minifyEnabled true`, `shrinkResources true`)
- **Release APK Size:** ~11 MB (`arm64-v8a`), ~29 MB (universal). Most of it is rclone.
- **CI / CD Pipeline:** GitHub Actions Automated Release (`.github/workflows/release.yml`)

---

## 🛠️ Build Instructions

### Common Prerequisites
1. **Git**: Ensure [Git](https://git-scm.com/) is installed.
   - *Arch Linux*: `sudo pacman -S git`
   - *Ubuntu / Debian*: `sudo apt install git`
   - *macOS*: `brew install git`

2. **Java 21 (JDK)**: Ensure OpenJDK 21 is installed and set as `JAVA_HOME`.
   - *Arch Linux*: `sudo pacman -S jdk21-openjdk`
   - *Ubuntu / Debian*: `sudo apt install openjdk-21-jdk`
   - *macOS*: `brew install openjdk@21`

3. **Go**: version 1.26 or newer.
   - *Arch Linux*: `sudo pacman -S go`
   - *Ubuntu / Debian*: install from [go.dev/dl](https://go.dev/dl/) (distribution packages are often too old)
   - *macOS*: `brew install go`

4. **Android SDK + NDK**: Platform API 34 and NDK r27c (installed by Android Studio under *SDK Manager → SDK Tools → NDK*, or with `sdkmanager "platforms;android-34" "ndk;27.2.12479018"`). Point to the SDK with `ANDROID_HOME` or a `local.properties` file (`sdk.dir=/path/to/sdk`). The rclone script finds the NDK there, or uses `ANDROID_NDK_HOME` if set.

5. **Clone the Repository**:
   ```bash
   git clone "https://github.com/tanvirsweb/Google-Drive-Sync"
   cd Google-Drive-Sync
   git config core.hooksPath .githooks   # enables automatic versioning (see below)
   ```

---

### Method 1: Android Studio / IntelliJ IDEA

1. **Build rclone once** from a terminal: `scripts/build-rclone.sh` (Gradle also does this automatically, but the first build takes several minutes).
2. **Launch Android Studio**, select **Open** and choose the cloned repository directory.
3. Wait for Gradle sync to complete and download required dependencies.
4. **Run Unit Tests**: Right-click on `app/src/test` and select **Run 'Tests in 'test''**.
5. **Build APK**: **Build** > **Build Bundle(s) / APK(s)** > **Build APK(s)**. The debug APKs are generated in `app/build/outputs/apk/debug/`.
6. **Run on Device or Emulator**: Click the green **Run (Play)** button in the toolbar.

---

### Method 2: Command Line (Terminal)

1. **Grant execute permission to Gradle Wrapper**:
   ```bash
   chmod +x gradlew
   ```

2. **Build rclone for Android** (optional; Gradle runs it if a binary is missing):
   ```bash
   scripts/build-rclone.sh                 # all ABIs
   scripts/build-rclone.sh arm64-v8a       # just one
   FORCE=1 scripts/build-rclone.sh         # rebuild
   ```
   To upgrade rclone, change the version in `rclone-android/go.mod`:
   ```bash
   cd rclone-android
   go get github.com/rclone/rclone@vX.Y.Z && go mod tidy
   ```

3. **Run Unit Tests**:
   ```bash
   ./gradlew testDebugUnitTest
   ```

4. **Build Debug APKs**:
   ```bash
   ./gradlew assembleDebug
   ```
   Output: `app/build/outputs/apk/debug/app-<abi>-debug.apk` and `app-universal-debug.apk`

5. **Build Minified Release APKs**:
   ```bash
   ./gradlew assembleRelease
   ```
   - With a keystore at `app/release.jks` (passwords from the `KEYSTORE_PASSWORD`, `KEY_ALIAS` and `KEY_PASSWORD` environment variables): `app/build/outputs/apk/release/app-<abi>-release.apk`, signed.
   - Without a keystore: `app-<abi>-release-unsigned.apk`. This is the build F-Droid uses; it signs the APKs itself.

6. **Install on Connected Device via ADB**:
   ```bash
   adb install -r app/build/outputs/apk/debug/app-arm64-v8a-debug.apk
   ```

---

## 🤖 F-Droid

GoogleDriveSync is set up along the lines of F-Droid's inclusion policy:

- **Free software**: the app is MIT licensed (see [LICENSE](LICENSE)), and every dependency is open source (see the license table in [README.md](README.md)).
- **Builds from source** with plain `./gradlew assembleRelease` (Gradle subdir `app`), from Maven Central and Google's Maven repository only. The rclone binary is also built from source (`scripts/build-rclone.sh`); an F-Droid recipe needs Go and the NDK in its build environment and must run that script before Gradle.
- **No proprietary dependencies**: no Google Play Services, Firebase, analytics or ads. Google Drive is reached through rclone's open-source Drive client.
- **Unsigned release when no keystore is present**, so F-Droid can sign it (release signing is only applied if `app/release.jks` exists).
- **No dependency metadata blob**: `dependenciesInfo { includeInApk = false; includeInBundle = false }`.
- **Store listing** lives in `fastlane/metadata/android/en-US/` (title, short and full description, icon, feature graphic, and per-release changelogs named `<versionCode>.txt`).
- **Versioning**: `versionCode = major * 10000 + minor * 100 + patch` (e.g. `1.3.0` → `10300`). Releases are tagged `vX.Y.Z`. All ABI APKs of a release share one `versionCode`.
- **Anti-features to expect**: *NonFreeNet* (the app talks to Google Drive, a proprietary service).

---

## 🏷️ Versioning & Releases

The version comes from `CHANGELOG.md`. A git pre-commit hook (`.githooks/pre-commit`, enable it once with `git config core.hooksPath .githooks`) runs `.github/scripts/update_version.py`, which:

1. Takes the newest `## [X.Y.Z]` heading of `CHANGELOG.md` (an `## [Unreleased]` section is ignored).
2. Writes `versionCode` / `versionName` into `app/build.gradle`.
3. Sets the **release** badge in `README.md` to the same version (only that line is staged, so other README edits you haven't staged stay out of the commit).
4. Writes that release's notes to `fastlane/metadata/android/en-US/changelogs/<versionCode>.txt`.

**Releases are published only from version tags.** On pushes to `main` / `tmp` and on pull requests, CI only checks the version files (`update_version.py --check`), builds rclone (cached between runs), runs the unit tests and does a release build. To publish a release:

1. Add a `## [X.Y.Z] - YYYY-MM-DD` section to `CHANGELOG.md` and commit (the hook bumps the version, README badge and Fastlane changelog).
2. Tag that commit and push the tag:
   ```bash
   git tag vX.Y.Z
   git push origin vX.Y.Z
   ```
3. CI checks that the tag matches `CHANGELOG.md`, builds the signed APKs from exactly the tagged commit and publishes the GitHub release with `GoogleDriveSync-vX.Y.Z-<abi>.apk`, `GoogleDriveSync-vX.Y.Z-universal.apk` and `GoogleDriveSync-latest.apk`.

### Signing secrets

Add these in **GitHub → Settings → Secrets and variables → Actions**:

| Secret | Value |
| --- | --- |
| `RELEASE_KEYSTORE_BASE64` | `base64 -w0 release.jks` |
| `RELEASE_KEYSTORE_PASSWORD` | Keystore password |
| `RELEASE_KEY_ALIAS` | Key alias |
| `RELEASE_KEY_PASSWORD` | Key password |

Create a keystore once with:
```bash
keytool -genkeypair -v -keystore release.jks -alias googlesync -keyalg RSA -keysize 4096 -validity 10000
```
Keep it safe and out of git (`*.jks` is ignored): Android only accepts updates signed with the same key.

### Fastlane

```bash
bundle install
bundle exec fastlane android test      # unit tests
bundle exec fastlane android build     # debug APKs
bundle exec fastlane android release   # release APKs
```

---

## 📥 Releases & Download

- **GitHub Releases**: Download pre-built APKs from the [Releases](https://github.com/tanvirsweb/Google-Drive-Sync/releases) page.
