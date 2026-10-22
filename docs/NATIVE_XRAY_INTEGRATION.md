# Native Xray engine integration

## Current state

ARKAVA now has a Kotlin client for the official Invoke API and a service path
that loads a profile from encrypted storage, validates its Xray JSON, establishes
a TUN, registers a socket-protection controller, configures DNS, injects the
runtime descriptor into the Xray config and manages start/stop cleanup. The
service deliberately reports that tunnel connectivity is unverified even when
the Xray core reports running. The complete packet-forwarding path still needs
end-to-end device/emulator verification. The `engine-go/xray` package remains a
separate contract-test module, not a second native runtime.

## Pinned upstream dependency

The workflow builds the official [XTLS/libXray](https://github.com/XTLS/libXray)
source at commit `dbe95129fa96689c4643ddca52c278448eec6df1` (2026-10-21).
That source pins Xray-core to the dependency version in its own `go.mod`.
The generated `libXray.aar` is uploaded as a short-lived GitHub Actions artifact
for reproducible inspection. It is not checked into the source tree.

## Critical single-Go-runtime rule

The Android process must load exactly one Go runtime. Do not package the
standalone `engine-go` package as a second native library beside libXray.
Call the official `LibXray.invoke(...)` binding from Kotlin, or merge any
additional Go source into the same gomobile build. The current standalone Go
bridge is a contract/test module only.

## Required Android connection sequence

1. Obtain Android VPN consent with `VpnService.prepare()`.
2. Build and establish the `VpnService.Builder` TUN interface with explicit
   addresses, routes, DNS and MTU; retain its file descriptor for the engine.
3. Register libXray's dialer/socket-protection controller so engine sockets do
   not loop back into the VPN TUN.
4. Configure the Android DNS resolver with libXray's `SetDNS` before
   `runXray`; always call `ResetDNS` after stopping the core.
5. Add the runtime TUN file descriptor to the Xray config root `env` as
   `xray.tun.fd` before invoking `runXray`, according to the pinned upstream
   API. Do not use the removed `SetTunFd` API.
6. Only publish CONNECTED after native start succeeds and an end-to-end probe
   through the tunnel succeeds. On any failure, stop the core, reset DNS, close
   the TUN descriptor and report ERROR.
7. On disconnect/service destruction, stop Xray first, reset DNS, close TUN,
   remove the foreground notification and clear connection state.

## Verification boundary

A successful Go bridge unit-test run proves request/response handling only.
A successful AAR/APK build proves compilation and packaging, not packet
forwarding. End-to-end validation still requires an Android emulator/device and
a reachable test profile, checking that IPv4/IPv6 traffic and DNS route through
the TUN, that sockets are protected from recursion, and that cleanup restores
normal connectivity after both successful and failed starts.
