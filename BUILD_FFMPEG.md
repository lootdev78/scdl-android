# FFmpeg runtime for arm64-v8a

This project supports only `arm64-v8a`. Build the Termux FFmpeg package with the `aarch64` target outside this project:

```bash
export TERMUX_ARCH=aarch64
export TERMUX_PREFIX=/data/scdl-android/usr
export TERMUX_ANDROID_HOME=/data/scdl-android/home
./build-package.sh ffmpeg
```

Extract FFmpeg and its runtime dependency packages into one staging directory. Development and static-only packages are not required at runtime.

```bash
mkdir -p /tmp/scdl-ffmpeg-arm64
for package in debs/*.deb; do
    dpkg-deb -x "$package" /tmp/scdl-ffmpeg-arm64
done

cd /tmp/scdl-ffmpeg-arm64/data/scdl-android
zip --symlinks -r /tmp/libffmpeg.zip.so usr/lib
```

Restore the executable and runtime archive here:

```text
ffmpeg/src/main/jniLibs/arm64-v8a/libffmpeg.so
ffmpeg/src/main/jniLibs/arm64-v8a/libffmpeg.zip.so
```

Place any loader-level shared libraries needed before extraction beside them in the same `arm64-v8a` directory. SCDL passes the extracted FFmpeg executable path to yt-dlp automatically.
