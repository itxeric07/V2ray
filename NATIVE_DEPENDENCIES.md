# Native VPN dependencies

The Android VPN data path uses these externally maintained native components:

- `app/libs/libv2ray.aar` from [AndroidLibXrayLite](https://github.com/2dust/AndroidLibXrayLite), licensed LGPL-3.0.
- `app/src/main/jniLibs/*/libtun2socks.so` from [LibreXrayVPN](https://github.com/Ko4Learner/LibreXrayVPN), licensed GPL-3.0-or-later.

The binaries are pinned to AndroidLibXrayLite `v26.10.31` and LibreXrayVPN `v1.0.0`. This checkout intentionally ships only `arm64-v8a` for modern physical ARM phones; x86 and 32-bit ARM support are not included. The bundled Xray AAR also omits geoip/geosite databases because the app-generated configs do not use geo-routing rules. Review and preserve the upstream license obligations when redistributing the APK.
