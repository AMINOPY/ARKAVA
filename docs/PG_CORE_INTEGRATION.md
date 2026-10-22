# pg-core integration plan for ARKAVA

## Upstream reference

- Repository: https://github.com/AMINOPY/pg-core
- Pinned reference commit: `0c8bd36fac4eb385945baea4be3bd2566d9ca743`
- Upstream project name: Chimera Core (pg-core)
- Upstream status: early scaffold, not production-ready.

The upstream Go program is **not** an Android library and is not compiled into the APK. ARKAVA is a native Kotlin Android app, so useful contracts and policies are being adapted at the Kotlin boundary. The exact upstream revision is pinned as a Git submodule for review and traceability.

## Concepts adopted

| pg-core area | ARKAVA adaptation |
|---|---|
| `internal/engine` adapter contract | `EngineAdapter` exposes explicit `EngineCapabilities`; all capabilities default to false until an adapter proves support. |
| `internal/orchestrator` | Lifecycle transitions remain serialized; failed startup/health checks must stop the adapter and release resources. |
| `internal/policy` candidate selector | `EngineCandidateSelector` deterministically prefers healthy candidates, then lower latency, then engine ID; degraded candidates are fallback-only. Unit tests cover ordering and invalid candidates. |
| `internal/config` | Validate profile structure and size before handing configuration to a native engine; never log raw profiles or credentials. |
| `internal/resilience` | Bounded retry/backoff with cancellation is a required follow-up; no unbounded reconnect loop. |
| `internal/logging` and `internal/events` | Prefer stable error codes and redacted lifecycle events rather than secret-bearing configuration or URLs. |
| `internal/diagnostics` | Keep probes bounded and certificate-verified; a running process alone is not proof of working tunnel traffic. |

## Non-negotiable Android networking rules

1. Only one selected engine may own the active traffic path at a time.
2. The Android `VpnService` owns the TUN descriptor and must coordinate descriptor/socket protection and DNS with the selected engine.
3. Do not switch engines or fall back to direct traffic after a tunnel-required failure unless a reviewed policy explicitly permits it.
4. Do not report `CONNECTED` until a bounded connectivity probe traversing the VPN path succeeds.
5. Engine availability is capability-based. Listing an engine ID does not mean that its binary/native library is bundled or ready.
6. Every native artifact must have a reviewed license, pinned version, documented ABIs, reproducible build instructions, and integrity checks.

## Current implementation status

- Kotlin lifecycle orchestration, explicit capability declarations, a deterministic candidate-selection policy with unit tests, and encrypted profile storage are present.
- The candidate selector is currently a tested policy utility, **not yet wired into connection startup**; there is not yet more than one real Android engine to choose between.
- pg-core is tracked as a source reference; its Go packages are not copied into the APK.
- No pg-core runtime, sing-box executable, Xray native library, TUN packet forwarding, DNS bridge, or end-to-end tunnel probe is wired into ARKAVA yet.
- The pg-core sing-box adapter starts an external executable and explicitly does not provide Android `VpnService` TUN integration. It must not be treated as a drop-in Android engine.

## Next engineering order

1. Verify the Android build and unit tests in CI.
2. Integrate one Android-compatible engine first (Xray/libXray), with a pinned and reviewed native artifact.
3. Implement and test the TUN descriptor handoff, protected sockets, DNS ownership, stop/cleanup, and real tunneled connectivity probe.
4. Add instrumentation tests on Android devices/emulators.
5. Only after one complete engine path works, evaluate sing-box and wire multi-engine candidate selection.

See the upstream [architecture](https://github.com/AMINOPY/pg-core/blob/main/docs/ARCHITECTURE.md), [sing-box adapter notes](https://github.com/AMINOPY/pg-core/blob/main/docs/SING_BOX_ADAPTER.md), and [security policy](https://github.com/AMINOPY/pg-core/blob/main/SECURITY.md).
