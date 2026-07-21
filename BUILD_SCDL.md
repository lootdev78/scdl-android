# SCDL Android integration

The launcher application is an SCDL terminal GUI. The `:library` module is only the internal embedded Python/yt-dlp process runtime used by SCDL.

## Module graph

```text
app -> scdl -> library -> common
           -> ffmpeg -> common
           -> aria2c -> common
```

All namespaces use `io.github.lootdev.scdl.*`. No custom Gradle convention plugin, `buildSrc`, publishing configuration, tests, JitPack, jcenter or project-local build scripts are required.

## Embedded resources

- `library/src/main/res/raw/ytdlp` contains the single yt-dlp zip application used by SCDL.
- `scdl/src/main/res/raw/scdl_bundle.zip` contains SCDL and its pure-Python dependencies, without a second `yt_dlp` package.
- Settings can update yt-dlp and SCDL independently.

The checked-in SCDL bundle is ready to package. Replacing it is a manual resource update: create a ZIP whose root contains `scdl/`, its `.dist-info` directory, dependencies and `scdl_android_entry.py`, then replace `scdl_bundle.zip` and increment `BUNDLE_VERSION` in `Scdl.kt`.

## Required native runtime

SCDL v3 requires Python 3.10 or newer. Only `arm64-v8a` is enabled. Restore the files listed in `NATIVE_RUNTIME.md` before running downloads.

## Runtime invocation

The terminal forwards normal SCDL arguments to:

```text
libpython.so -m scdl_android_entry <arguments>
```

The wrapper automatically adds the installed FFmpeg and QuickJS paths to `--yt-dlp-args`. aria2c is added only when enabled in Settings and its library is present.

## Kotlin API

```kotlin
Scdl.init(applicationContext)

val request = ScdlRequest.link("https://soundcloud.com/artist/track")
    .addOption("--path", outputDirectory.absolutePath)
    .addOption("-c")

val response = Scdl.execute(
    request = request,
    processId = "soundcloud-download"
) { progress, etaSeconds, line ->
    // Update your terminal UI.
}
```

`execute` is blocking and must run on a worker thread. `Scdl.destroyProcessById(processId)` stops a running command.

The GUI intentionally blocks SoundCloud search (`-s`). Link, playlist/set, user, likes, reposts and other link-based SCDL modes remain available.
