# Python runtime for arm64-v8a

SCDL v3 requires Python 3.10 or newer. This project builds only `arm64-v8a`, so use the Termux `aarch64` target exclusively.

## Build with Termux packages

Clone the Termux packages repository outside this project and build Python for `aarch64`:

```bash
export TERMUX_ARCH=aarch64
export TERMUX_PREFIX=/data/scdl-android/usr
export TERMUX_ANDROID_HOME=/data/scdl-android/home
./build-package.sh python
```

Extract the generated Python package and its runtime dependency packages into one staging directory. Preserve symlinks when creating the archive.

```bash
mkdir -p /tmp/scdl-python-arm64
for package in debs/*.deb; do
    dpkg-deb -x "$package" /tmp/scdl-python-arm64
done

cd /tmp/scdl-python-arm64/data/scdl-android
zip --symlinks -r /tmp/libpython.zip.so usr/lib usr/etc
```

Restore the resulting arm64 files here:

```text
library/src/main/jniLibs/arm64-v8a/libpython.so
library/src/main/jniLibs/arm64-v8a/libpython.zip.so
```

Place every shared library required by the Python executable beside these files in the same `arm64-v8a` directory.

The runtime wrapper extracts `libpython.zip.so`, sets `PYTHONHOME` to its `usr` directory and uses `usr/etc/tls/cert.pem` for TLS certificates.

SCDL itself is not added to the Python runtime archive. It remains in the checked-in `scdl/src/main/res/raw/scdl_bundle.zip` resource.
