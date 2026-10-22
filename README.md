# ARKAVA VPN — Android core foundation

Native Android application foundation written in Kotlin. Product UI work is deferred while the connection lifecycle is being made testable.

## Implemented foundation

- Android Gradle application skeleton (JDK 17, Android API 26+).
- Explicit user-triggered Android VPN consent flow.
- Foreground VPN service lifecycle shell and persistent status notification.
- Serialized engine adapter/orchestrator with failure cleanup and bounded error messages.
- Bounded validation for Xray JSON outbounds, supported share-link URI schemes, and subscription text.
- Profile persistence encrypted with AES-GCM using a key held by Android Keystore.
- Unit tests for profile validation and connection lifecycle failure handling.
- GitHub Actions workflow for unit tests and debug APK assembly.
- Pinned `AMINOPY/pg-core` source reference as a Git submodule at `third_party/pg-core`, with Android adaptation documented in [docs/PG_CORE_INTEGRATION.md](docs/PG_CORE_INTEGRATION.md).
- No false-positive connection state: the service reports that the engine is pending and does not create a TUN interface.

## Important status

**This is not a working VPN yet.** Xray-core and sing-box are not bundled or wired to packet forwarding. The foreground service is only a lifecycle shell. pg-core is a Go reference project and is not compiled into the Android APK. Do not assume traffic is protected by this build.

The profile store encrypts profile content at rest, but it is not a substitute for engine integration or a security review. If the Android Keystore key is invalidated or lost, previously encrypted profile data may no longer be readable.

## Build and test

Requires JDK 17, Gradle 8.11.1, and Android SDK platform/build-tools 35.

    gradle --no-daemon :app:testDebugUnitTest :app:assembleDebug

The GitHub Actions workflow runs the same checks. A green workflow is a compile/unit-test/package check only; it does not prove that a VPN tunnel works on a device.

## Remaining engineering milestones

1. Pin a reviewed Android-compatible Xray/libXray release and build reproducible native artifacts with documented ABI support, checksums, and licenses.
2. Implement and test the JNI/AAR bridge for the exact pinned API.
3. Implement the real TUN-to-engine packet path, socket protection, DNS and routing behavior; never mark CONNECTED before a proxied connectivity probe succeeds.
4. Add device/instrumentation tests for VPN consent, service lifecycle, connect/disconnect, and failure recovery.
5. Add safe subscription import, profile migration, and bounded redacted logs.
6. Consider sing-box only after the first engine path passes device-level tests.

Never commit server credentials or log raw configuration URLs/tokens.
