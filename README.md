# ARKAVA VPN — Android core foundation

Android-native Kotlin foundation. UI design is intentionally deferred.

## Current scope
- Android Gradle application skeleton (Kotlin/JVM 17, Android API 26+)
- VPN consent entry point and foreground VpnService lifecycle shell
- Domain connection states and serialized engine adapter/orchestrator contract
- Defensive JSON/share-link validation
- No false-positive connection state: the service does not claim that a tunnel is connected

## Important status
This is the foundation scaffold, not a production-ready VPN yet. Xray-core and sing-box are not bundled or wired into packet forwarding in this commit. A real engine adapter, profile-to-engine conversion, TUN packet forwarding, DNS/routing handling, and device integration tests are required before traffic can be tunneled.

## Build
Requires JDK 17 and Android SDK platform 35. Open in Android Studio and sync Gradle, or run gradle :app:assembleDebug where Gradle and Android SDK are installed.

## Next milestones
1. Pin and bundle a reviewed Xray-core Android build, with ABI and license notices.
2. Implement and test the Xray engine adapter and config validation.
3. Establish Android TUN and packet forwarding in-process or through a correctly designed native bridge.
4. Add encrypted profile storage, subscription parsing, proxied connectivity checks and bounded redacted logs.
5. Add sing-box only after the first engine path passes device-level tests.

Never store server secrets in source control or log raw configuration URLs/tokens.
