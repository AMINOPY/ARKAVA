# ARKAVA VPN — Android core foundation

Native Android application foundation written in Kotlin. Product UI work is deferred while the connection lifecycle is being made testable.

## Current scope

- Android Gradle application skeleton (JDK 17, Android API 26+).
- Explicit user-triggered Android VPN consent flow.
- Foreground VPN service lifecycle shell and persistent status notification.
- Domain connection states and a serialized engine adapter/orchestrator contract.
- Basic bounded JSON and share-link validation.
- GitHub Actions workflow that attempts to assemble a debug APK on pushes and pull requests.
- No false-positive connection state: the service reports that the engine is pending and does not create a TUN interface.

## Important status

**This is not a working VPN yet.** Xray-core and sing-box are not bundled or wired to packet forwarding. The foreground service is only a lifecycle shell. Do not assume traffic is protected by this build.

## Build

Requires JDK 17, Gradle 8.11.1, and Android SDK platform/build-tools 35.

    gradle --no-daemon :app:assembleDebug

The GitHub Actions workflow runs the same assemble command. A green workflow is a compile/package check only; it does not prove that a VPN tunnel works on a device.

## Next engineering milestones

1. Select and pin a reviewed Xray-core Android-compatible release/build; document its source, checksum, ABI support, and licenses.
2. Implement profile-to-Xray configuration conversion and strict validation.
3. Implement the real TUN-to-engine packet path and DNS/routing behavior; never mark CONNECTED before a proxied connectivity probe succeeds.
4. Add device/instrumentation tests for consent, service lifecycle, connect/disconnect, and failure recovery.
5. Add encrypted profile storage, safe subscription parsing, and bounded redacted logs.
6. Consider sing-box after the first engine path passes device-level tests.

Never commit server credentials or log raw configuration URLs/tokens.
