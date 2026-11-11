# Native VPN dependencies

The Android VPN data path uses this externally maintained native component:

- `app/libs/libv2ray.aar` from [AndroidLibXrayLite](https://github.com/2dust/AndroidLibXrayLite), licensed LGPL-3.0.

The binary is pinned to AndroidLibXrayLite `v26.10.31`. Hollow VPN follows v2rayNG's direct-core TUN path: Android's `ParcelFileDescriptor.fd` is passed to `CoreController.startLoop(config, tunFd)`, so no separately launched tun2socks executable is required. The release build produces three signed minified APKs: `armeabi-v7a`, `arm64-v8a`, and a universal ARM APK. x86 and x86_64 are intentionally excluded to keep phone builds smaller. The bundled Xray AAR also omits geoip/geosite databases because the app-generated configs do not use geo-routing rules. Review and preserve the upstream LGPL license obligations when redistributing the APK.
