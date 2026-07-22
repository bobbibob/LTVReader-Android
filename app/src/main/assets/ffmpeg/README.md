# FFmpeg binaries

Положите сюда скомпилированные FFmpeg-бинарники:

```
ffmpeg/
├── arm64-v8a/ffmpeg
├── armeabi-v7a/ffmpeg
└── x86_64/ffmpeg
```

## Где взять

1. **Termux** (нативная сборка через Android NDK): https://github.com/termux/termux-packages
2. **ffmpeg-android-maker** (готовые AAR): https://github.com/Javernaut/ffmpeg-android-maker
3. **niccokunzmann/ffmpeg-kit** (форк): https://github.com/niccokunzmann/ffmpeg-kit

Минимальная сборка (без внешних кодеков):
```bash
./configure \
  --enable-cross-compile \
  --target-os=linux \
  --arch=aarch64 \
  --cross-prefix=$NDK/toolchains/llvm/prebuilt/linux-x86_64/bin/llvm- \
  --sysroot=$NDK/toolchains/llvm/prebuilt/linux-x86_64/sysroot/ \
  --enable-shared \
  --disable-static \
  --enable-libmp3lame \
  --enable-libvorbis \
  --enable-libfdk-aac
make -j8
```

Размер бинарника: ~10-30 МБ на ABI.

