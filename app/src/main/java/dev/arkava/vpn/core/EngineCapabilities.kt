package dev.arkava.vpn.core

/**
 * Explicit engine feature declarations.
 *
 * Defaults are deliberately false: an adapter must opt in only after its
 * implementation and platform tests prove the capability exists.
 */
data class EngineCapabilities(
    val tun: Boolean = false,
    val dns: Boolean = false,
    val routing: Boolean = false,
    val healthChecks: Boolean = false
)
