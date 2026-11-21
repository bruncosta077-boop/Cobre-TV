package app.cobre.tv

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import java.net.Inet4Address
import java.util.UUID

data class NearbyDesktop(val hostId: String, val name: String, val origin: String)

/** Discovers local Desktop endpoints; mDNS carries no access credential. */
class DesktopDiscovery(context: Context) {
    private val manager = context.applicationContext.getSystemService(Context.NSD_SERVICE) as NsdManager
    private var listener: NsdManager.DiscoveryListener? = null
    private var generation = 0

    fun start(onChanged: (List<NearbyDesktop>) -> Unit, onError: (String) -> Unit) {
        if (listener != null) return
        val runId = ++generation
        val found = linkedMapOf<String, NearbyDesktop>()
        val serviceHosts = mutableMapOf<String, String>()
        val activeNames = mutableSetOf<String>()
        val pending = ArrayDeque<NsdServiceInfo>()
        val pendingNames = mutableSetOf<String>()
        var resolving = false
        fun resolveNext() {
            if (runId != generation) return
            if (resolving || pending.isEmpty()) return
            val candidate = pending.removeFirst()
            pendingNames.remove(candidate.serviceName)
            resolving = true
            try {
                manager.resolveService(candidate, object : NsdManager.ResolveListener {
                    override fun onResolveFailed(info: NsdServiceInfo, errorCode: Int) {
                        if (runId != generation) return
                        resolving = false
                        resolveNext()
                    }
                    override fun onServiceResolved(info: NsdServiceInfo) {
                        if (runId != generation) return
                        parseResolvedDesktop(info)?.takeIf { info.serviceName in activeNames }?.let { desktop ->
                            found[desktop.hostId] = desktop
                            serviceHosts[info.serviceName] = desktop.hostId
                            onChanged(found.values.sortedBy { it.name.lowercase() })
                        }
                        resolving = false
                        resolveNext()
                    }
                })
            } catch (_: Exception) {
                if (runId != generation) return
                resolving = false
                resolveNext()
            }
        }
        val discovery = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(serviceType: String) = Unit
            override fun onServiceFound(serviceInfo: NsdServiceInfo) {
                if (runId != generation) return
                if (serviceInfo.serviceType.trimEnd('.') != SERVICE_TYPE) return
                activeNames.add(serviceInfo.serviceName)
                if (serviceInfo.serviceName !in serviceHosts && pendingNames.add(serviceInfo.serviceName)) {
                    pending.addLast(serviceInfo)
                    resolveNext()
                }
            }
            override fun onServiceLost(serviceInfo: NsdServiceInfo) {
                if (runId != generation) return
                activeNames.remove(serviceInfo.serviceName)
                pending.removeAll { it.serviceName == serviceInfo.serviceName }
                pendingNames.remove(serviceInfo.serviceName)
                serviceHosts.remove(serviceInfo.serviceName)?.let(found::remove)
                onChanged(found.values.sortedBy { it.name.lowercase() })
            }
            override fun onDiscoveryStopped(serviceType: String) { if (runId == generation) listener = null }
            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
                if (runId != generation) return
                listener = null
                onError("Não foi possível buscar computadores nesta rede (código $errorCode).")
            }
            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) { if (runId == generation) listener = null }
        }
        listener = discovery
        try { manager.discoverServices(SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, discovery) }
        catch (error: Exception) { listener = null; if (runId == generation) onError(error.message ?: "A descoberta local não está disponível nesta TV.") }
    }

    fun stop() {
        val current = listener ?: return
        generation++
        listener = null
        try { manager.stopServiceDiscovery(current) } catch (_: Exception) { listener = null }
    }

    private fun parseResolvedDesktop(info: NsdServiceInfo): NearbyDesktop? {
        val attributes = info.attributes
        val version = attributes["v"]?.toString(Charsets.UTF_8)
        val api = attributes["api"]?.toString(Charsets.UTF_8)
        val hostId = attributes["host"]?.toString(Charsets.UTF_8)?.lowercase().orEmpty()
        if (version != "1" || api != "v1" || !HOST_ID.matches(hostId) || runCatching { UUID.fromString(hostId) }.isFailure) return null
        val address = info.host as? Inet4Address ?: return null
        if (!isPrivateIpv4(address.hostAddress.orEmpty()) || info.port !in 1..65535) return null
        return NearbyDesktop(hostId, info.serviceName.take(48).ifBlank { "Cobre Desktop" }, "http://${address.hostAddress}:${info.port}")
    }

    private fun isPrivateIpv4(raw: String): Boolean {
        val octets = raw.split('.').map { it.toIntOrNull() ?: return false }
        if (octets.size != 4 || octets.any { it !in 0..255 }) return false
        val (a, b) = octets
        return a == 10 || a == 127 || (a == 192 && b == 168) || (a == 172 && b in 16..31) || (a == 169 && b == 254)
    }

    companion object {
        private const val SERVICE_TYPE = "_cobre._tcp"
        private val HOST_ID = Regex("^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$")
    }
}
