# Native runtime placeholders

This source package intentionally omits native binaries. Only `arm64-v8a` is enabled.

Restore these files before running SCDL:

```text
library/src/main/jniLibs/arm64-v8a/libpython.so
library/src/main/jniLibs/arm64-v8a/libpython.zip.so
library/src/main/jniLibs/arm64-v8a/libqjs.so

ffmpeg/src/main/jniLibs/arm64-v8a/libffmpeg.so
ffmpeg/src/main/jniLibs/arm64-v8a/libffmpeg.zip.so

aria2c/src/main/jniLibs/arm64-v8a/libaria2c.so
aria2c/src/main/jniLibs/arm64-v8a/libaria2c.zip.so
```

`libpython.so`, `libffmpeg.so`, `libqjs.so` and `libaria2c.so` are executable native files used through `ProcessBuilder` or yt-dlp. The `*.zip.so` files contain the corresponding Termux runtime libraries that the wrapper extracts into private app storage.

The Python runtime must be Python 3.10 or newer for SCDL v3. FFmpeg is required for metadata, artwork and conversion operations. aria2c is optional.
