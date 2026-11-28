package app.cobre.tv

import java.net.URI

/** Allows cleartext HTTP only on a private LAN or inside a Tailscale tailnet. */
internal object TvNetworkEndpoint {
    fun normalize(raw: String, serviceName: String): String {
        val value = raw.trim().trimEnd('/')
        if (value.isBlank()) return ""
        val uri = runCatching { URI(value) }
            .getOrElse { throw IllegalArgumentException("Endereço do $serviceName inválido.") }
        val scheme = uri.scheme?.lowercase()
        val host = uri.host?.lowercase()?.trimEnd('.').orEmpty()
        if (scheme !in setOf("http", "https") || host.isBlank() ||
            uri.userInfo != null || uri.query != null || uri.fragment != null ||
            uri.rawPath !in listOf("", "/") || (uri.port != -1 && uri.port !in 1..65535)
        ) {
            throw IllegalArgumentException("Informe somente o endereço base do $serviceName.")
        }
        if (scheme == "http" && !isAllowedHttpHost(host)) {
            throw IllegalArgumentException("Por segurança, HTTP só é aceito na rede local ou pela VPN Tailscale.")
        }
        return value
    }

    internal fun isAllowedHttpHost(host: String): Boolean =
        isPrivateIpv4(host) || isTailscaleIpv4(host) || isTailscaleMagicDns(host)

    private fun isPrivateIpv4(host: String): Boolean {
        val octets = host.split('.').map { it.toIntOrNull() ?: return false }
        if (octets.size != 4 || octets.any { it !in 0..255 }) return false
        val (a, b) = octets
        return a == 10 || a == 127 || (a == 192 && b == 168) ||
            (a == 172 && b in 16..31) || (a == 169 && b == 254)
    }

    private fun isTailscaleIpv4(host: String): Boolean {
        val octets = host.split('.').map { it.toIntOrNull() ?: return false }
        return octets.size == 4 && octets.all { it in 0..255 } &&
            octets[0] == 100 && octets[1] in 64..127
    }

    private fun isTailscaleMagicDns(host: String): Boolean =
        host.endsWith(".ts.net") && MAGIC_DNS.matches(host)

    private val MAGIC_DNS = Regex("^[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?(?:\\.[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?)*\\.ts\\.net$")
}
