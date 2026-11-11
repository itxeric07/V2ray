# Native VPN dependencies

The Android VPN data path uses this externally maintained native component:

- `app/libs/libv2ray.aar` from [AndroidLibXrayLite](https://github.com/2dust/AndroidLibXrayLite), licensed LGPL-3.0.

The binary is pinned to AndroidLibXrayLite `v26.10.31`. Hollow VPN follows v2rayNG's direct-core TUN path: Android's `ParcelFileDescriptor.fd` is passed to `CoreController.startLoop(config, tunFd)`, so no separately launched tun2socks executable is required. This checkout intentionally ships only `arm64-v8a` for modern physical ARM phones; x86 and 32-bit ARM support are not included. The bundled Xray AAR also omits geoip/geosite databases because the app-generated configs do not use geo-routing rules. Review and preserve the upstream LGPL license obligations when redistributing the APK.
