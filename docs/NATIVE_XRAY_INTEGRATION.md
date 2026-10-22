# Native Xray engine integration

## Current state

ARKAVA's Kotlin service is currently a lifecycle/consent shell. It does not yet
establish a production TUN-to-Xray data path, so it must not display a connected
state or claim that traffic is protected. The `engine-go/xray` package tests the
libXray Invoke JSON contract with an injected function; it is not the Android
native binding.

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
A successful AAR build proves the native dependency can be compiled. Neither
alone proves packet forwarding. End-to-end validation still requires an Android
emulator/device and a reachable test profile, checking that traffic routes
through the TUN and that DNS and socket protection work without leaks.
