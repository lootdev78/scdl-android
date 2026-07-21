# SCDL for Android

A standalone Android GUI wrapper for **SCDL v3**. The launcher app is SCDL, not a yt-dlp example application. yt-dlp is an internal shared runtime dependency used by SCDL.

## Application

- Application ID: `io.github.lootdev.scdl`
- English-only interface
- Terminal-style SCDL console
- Original `scdl.cfg` retained and editable from `SettingsActivity`
- SoundCloud link downloads for tracks, sets/playlists, users, likes, reposts and other SCDL link modes
- SoundCloud search (`-s`) intentionally disabled
- WebView helper for capturing a SoundCloud `client_id` and `auth_token`, with manual fields as fallback
- SCDL and yt-dlp update actions in Settings
- `arm64-v8a` only

## Modules and packages

| Module | Namespace/package |
|---|---|
| app | `io.github.lootdev.scdl` |
| scdl | `io.github.lootdev.scdl.core` |
| library | `io.github.lootdev.scdl.runtime` |
| common | `io.github.lootdev.scdl.common` |
| ffmpeg | `io.github.lootdev.scdl.ffmpeg` |
| aria2c | `io.github.lootdev.scdl.aria2c` |

The `library` module contains the embedded Python/yt-dlp process runtime. It is not exposed as a separate downloader UI.

## Build environment

Java and Kotlin bytecode target Java 17, which is supported while Gradle runs on JDK 21.

The project uses only Google Maven, Maven Central and the Gradle Plugin Portal. It has no `buildSrc`, custom convention plugin, publishing plugin, JitPack, jcenter, test dependencies, instrumentation runner or `projectdir/scripts/*` requirement.

## Native runtime files

Only source wrappers are included. Restore your arm64-v8a Python, FFmpeg, QuickJS and aria2c files at the paths documented in `NATIVE_RUNTIME.md`.

## Terminal examples

```text
-l https://soundcloud.com/artist/track
-l https://soundcloud.com/artist/sets/playlist
-l https://soundcloud.com/artist -t
me -f
--help
```

Built-in GUI terminal commands are `help`, `settings`, `paths`, `version`, `stop` and `clear`.

## Download folders

The default directory is `/storage/emulated/0/SCDL`. The app creates `Tracks`, `Playlists` and `Users` below it. Settings stores the selected base path permanently and writes it to the original `scdl.cfg`.
