package app.cobre.tv

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

data class TvPairingChallenge(val deviceCode: String, val userCode: String, val expiresInSeconds: Long, val intervalSeconds: Long)
data class TvAccountSession(val email: String, val deviceName: String)
data class TvDesktopHost(val id: String, val name: String, val online: Boolean)
data class TvDesktopGrant(val id: String, val token: String, val expiresAt: Long, val consentExpiresAt: Long)

class TvIdException(message: String, val code: String? = null, val status: Int = 0, val retryAfterSeconds: Long? = null) : Exception(message)

/** Cobre ID pairing client. The TV holds only a Keystore-encrypted refresh token. */
class TvIdRepository(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("cobre-tv-id", Context.MODE_PRIVATE)
    private var accessToken: String? = null
    private var accessTokenExpiresAt = 0L
    val serverUrl: String get() = prefs.getString("server_url", "").orEmpty()
    val isConnected: Boolean get() = !readRefreshToken().isNullOrBlank()
    val savedEmail: String get() = prefs.getString("email", "").orEmpty()
    val desktopOrigin: String get() = prefs.getString("desktop_origin", "").orEmpty()
    val desktopHostId: String? get() = prefs.getString("desktop_host_id", null)
    val desktopGrantId: String? get() = prefs.getString("desktop_grant_id", null)
    val desktopGrantToken: String? get() = readSecret("desktop_grant_token")

    fun saveServerUrl(raw: String) {
        val normalized = normalizeUrl(raw)
        prefs.edit().putString("server_url", normalized).apply()
    }

    suspend fun checkServer() = withContext(Dispatchers.IO) {
        request("/health", "GET", null, authorize = false).optBoolean("ok")
    }

    fun saveDesktopOrigin(raw: String) {
        val normalized = normalizeDesktopOrigin(raw)
        prefs.edit().putString("desktop_origin", normalized).apply()
    }

    suspend fun desktops(): List<TvDesktopHost> = withContext(Dispatchers.IO) {
        val list = request("/v1/hosts", "GET", null, authorize = true).optJSONArray("hosts")
            ?: throw TvIdException("O Cobre Server retornou uma lista de computadores inválida.")
        (0 until list.length()).mapNotNull { index ->
            val host = list.optJSONObject(index) ?: return@mapNotNull null
            val id = host.optString("id")
            if (id.isBlank()) null else TvDesktopHost(id, host.optString("name", "Cobre Desktop"), host.optBoolean("online"))
        }
    }

    suspend fun requestDesktopAccess(hostId: String): Pair<String, Long> = withContext(Dispatchers.IO) {
        val body = JSONObject().put("scopes", org.json.JSONArray(listOf("library.read", "playback.read", "playback.control", "stream")))
        val result = request("/v1/hosts/$hostId/access-requests", "POST", body, authorize = true)
        result.getString("requestId") to result.optLong("expiresIn", 300L).coerceIn(15L, 600L)
    }

    suspend fun pollDesktopAccess(hostId: String, requestId: String): TvDesktopGrant? = withContext(Dispatchers.IO) {
        val result = request("/v1/hosts/$hostId/access-requests/$requestId", "GET", null, authorize = true)
        if (result.optString("status") == "pending") return@withContext null
        if (result.optString("status") != "redeemed") throw TvIdException("O servidor retornou um estado de autorização desconhecido.")
        val grantId = result.getString("grantId")
        val token = result.getString("grantToken")
        val expiresAt = result.optLong("expiresAt")
        val consentExpiresAt = result.optLong("consentExpiresAt")
        if (token.isBlank() || expiresAt <= System.currentTimeMillis()) throw TvIdException("A autorização recebida expirou. Tente novamente.")
        prefs.edit().putString("desktop_host_id", hostId).putString("desktop_grant_id", grantId)
            .putLong("desktop_token_expires_at", expiresAt).putLong("desktop_consent_expires_at", consentExpiresAt).apply()
        saveSecret("desktop_grant_token", token)
        TvDesktopGrant(grantId, token, expiresAt, consentExpiresAt)
    }

    suspend fun refreshDesktopGrant(): TvDesktopGrant = withContext(Dispatchers.IO) {
        val hostId = desktopHostId ?: throw TvIdException("Conecte novamente o computador.")
        val grantId = desktopGrantId ?: throw TvIdException("Conecte novamente o computador.")
        val result = request("/v1/hosts/$hostId/access-grants/$grantId/refresh", "POST", JSONObject(), authorize = true)
        val token = result.getString("grantToken")
        val expiresAt = result.optLong("expiresAt")
        val consentExpiresAt = result.optLong("consentExpiresAt")
        if (token.isBlank() || expiresAt <= System.currentTimeMillis()) throw TvIdException("O servidor não renovou a autorização do Desktop.")
        prefs.edit().putLong("desktop_token_expires_at", expiresAt).putLong("desktop_consent_expires_at", consentExpiresAt).apply()
        saveSecret("desktop_grant_token", token)
        TvDesktopGrant(grantId, token, expiresAt, consentExpiresAt)
    }

    fun clearDesktopGrant() {
        prefs.edit().remove("desktop_host_id").remove("desktop_grant_id")
            .remove("desktop_token_expires_at").remove("desktop_consent_expires_at").apply()
        removeSecret("desktop_grant_token")
    }

    suspend fun requestPairing(): TvPairingChallenge = withContext(Dispatchers.IO) {
        val device = JSONObject().put("name", android.os.Build.MODEL ?: "Cobre TV").put("type", "tv")
        val response = request("/v1/device/authorization", "POST", JSONObject().put("device", device), authorize = false)
        val code = response.optString("deviceCode")
        val userCode = response.optString("userCode")
        if (code.length !in 40..60 || !userCode.matches(Regex("[A-HJ-NP-Z2-9]{4}-[A-HJ-NP-Z2-9]{4}"))) {
            throw TvIdException("O servidor retornou um código de pareamento inválido.")
        }
        TvPairingChallenge(code, userCode, response.optLong("expiresIn", 600L).coerceIn(30L, 900L), response.optLong("interval", 5L).coerceIn(2L, 30L))
    }

    suspend fun awaitApproval(challenge: TvPairingChallenge): TvAccountSession = withContext(Dispatchers.IO) {
        val deadline = System.currentTimeMillis() + challenge.expiresInSeconds * 1000L
        var interval = challenge.intervalSeconds
        while (System.currentTimeMillis() < deadline) {
            try {
                val response = request("/v1/device/token", "POST", JSONObject().put("deviceCode", challenge.deviceCode), authorize = false)
                val user = response.optJSONObject("user")
                val device = response.optJSONObject("device")
                val refresh = response.optString("refreshToken")
                val access = response.optString("accessToken")
                if (user == null || device == null || refresh.isBlank() || access.isBlank()) throw TvIdException("O servidor não retornou uma sessão Cobre ID completa.")
                accessToken = access
                accessTokenExpiresAt = System.currentTimeMillis() + response.optLong("expiresIn", 900L).coerceIn(1L, 3600L) * 1000L
                saveRefreshToken(refresh)
                val email = user.optString("email")
                prefs.edit().putString("email", email).putString("device_id", device.optString("id")).apply()
                return@withContext TvAccountSession(email, device.optString("name", android.os.Build.MODEL ?: "Cobre TV"))
            } catch (error: TvIdException) {
                when (error.code) {
                    "authorization_pending" -> Unit
                    "slow_down" -> interval = (error.retryAfterSeconds ?: (interval + 5)).coerceIn(interval, 30L)
                    else -> throw error
                }
            }
            delay(interval * 1000L)
        }
        throw TvIdException("O código da TV expirou. Inicie o pareamento novamente.", "expired_token")
    }

    suspend fun restoreSession(): TvAccountSession = withContext(Dispatchers.IO) {
        synchronized(SESSION_REFRESH_LOCK) {
            // Presence and scheduled Desktop-grant renewal can wake together near expiry.
            val refresh = readRefreshToken() ?: throw TvIdException("Nenhuma sessão Cobre ID salva.")
            try {
                val response = request("/v1/refresh", "POST", JSONObject().put("refreshToken", refresh), authorize = false)
                val user = response.optJSONObject("user") ?: throw TvIdException("O servidor não retornou os dados da conta.")
                val device = response.optJSONObject("device") ?: throw TvIdException("O servidor não retornou os dados da TV.")
                val nextRefresh = response.optString("refreshToken")
                accessToken = response.optString("accessToken")
                if (accessToken.isNullOrBlank() || nextRefresh.isBlank()) throw TvIdException("O servidor não retornou uma sessão válida.")
                accessTokenExpiresAt = System.currentTimeMillis() + response.optLong("expiresIn", 900L).coerceIn(1L, 3600L) * 1000L
                saveRefreshToken(nextRefresh)
                prefs.edit().putString("email", user.optString("email")).putString("device_id", device.optString("id")).apply()
                TvAccountSession(user.optString("email"), device.optString("name", "Cobre TV"))
            } catch (error: Exception) {
                accessToken = null
                if (error is TvIdException && error.status == 401) clearSession()
                throw error
            }
        }
    }

    suspend fun logout() = withContext(Dispatchers.IO) {
        try { request("/v1/logout", "POST", JSONObject(), authorize = true) } finally { clearSession() }
    }

    suspend fun sendPresence(measurement: TvStreamMeasurement?) = withContext(Dispatchers.IO) {
        if (accessToken.isNullOrBlank() || accessTokenExpiresAt <= System.currentTimeMillis() + 60_000L) restoreSession()
        val meta = JSONObject().put("platform", "Android TV").put("appVersion", BuildConfig.VERSION_NAME)
        measurement?.let {
            meta.put("streamLatencyMs", it.latencyMs)
                .put("streamBytesPerSecond", it.bytesPerSecond)
                .put("streamAgeMs", it.ageMs)
        }
        request("/v1/presence", "POST", JSONObject().put("meta", meta), authorize = true)
    }

    private fun clearSession() {
        accessToken = null
        accessTokenExpiresAt = 0L
        prefs.edit().remove("refresh_token").remove("email").remove("device_id").apply()
        clearDesktopGrant()
    }

    private fun request(path: String, method: String, body: JSONObject?, authorize: Boolean): JSONObject {
        val base = normalizeUrl(serverUrl)
        if (base.isBlank()) throw TvIdException("Configure o endereço do Cobre Server.")
        val connection = (URL(base + path).openConnection() as HttpURLConnection).apply {
            instanceFollowRedirects = false
            connectTimeout = 6_000
            readTimeout = 8_000
            requestMethod = method
            setRequestProperty("Accept", "application/json")
            setRequestProperty("Cache-Control", "no-store")
            if (authorize) accessToken?.let { setRequestProperty("Authorization", "Bearer $it") }
            if (body != null) { doOutput = true; setRequestProperty("Content-Type", "application/json; charset=utf-8") }
        }
        try {
            if (body != null) connection.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
            val status = connection.responseCode
            val responseStream = if (status in 200..299) connection.inputStream else connection.errorStream
            val text = responseStream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
            val json = if (text.isBlank()) JSONObject() else runCatching { JSONObject(text) }.getOrElse { JSONObject() }
            if (status !in 200..299) {
                val retryAfter = connection.getHeaderField("Retry-After")?.toLongOrNull()
                throw TvIdException(json.optString("message", "Falha ao conectar com o Cobre Server."), json.optString("error").takeIf(String::isNotBlank), status, retryAfter)
            }
            return json
        } catch (error: TvIdException) {
            throw error
        } catch (error: Exception) {
            throw TvIdException(error.message?.takeIf(String::isNotBlank) ?: "Não foi possível alcançar o Cobre Server.")
        } finally { connection.disconnect() }
    }

    private fun normalizeUrl(raw: String): String {
        return try { TvNetworkEndpoint.normalize(raw, "Cobre Server") }
        catch (error: IllegalArgumentException) { throw TvIdException(error.message ?: "Endereço do Cobre Server inválido.") }
    }

    private fun normalizeDesktopOrigin(raw: String): String {
        return try { TvNetworkEndpoint.normalize(raw, "Cobre Desktop") }
        catch (error: IllegalArgumentException) { throw TvIdException(error.message ?: "Endereço do Desktop inválido.") }
    }

    private fun saveRefreshToken(token: String) {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, keystoreKey()) }
        val encrypted = cipher.iv + cipher.doFinal(token.toByteArray(Charsets.UTF_8))
        prefs.edit().putString("refresh_token", Base64.encodeToString(encrypted, Base64.NO_WRAP)).apply()
    }

    private fun saveSecret(key: String, value: String) {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, keystoreKey()) }
        val encrypted = cipher.iv + cipher.doFinal(value.toByteArray(Charsets.UTF_8))
        prefs.edit().putString(key, Base64.encodeToString(encrypted, Base64.NO_WRAP)).apply()
    }

    private fun readSecret(key: String): String? = runCatching {
        val bytes = Base64.decode(prefs.getString(key, null) ?: return null, Base64.NO_WRAP)
        if (bytes.size <= 12) return null
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.DECRYPT_MODE, keystoreKey(), GCMParameterSpec(128, bytes.copyOfRange(0, 12))) }
        String(cipher.doFinal(bytes.copyOfRange(12, bytes.size)), Charsets.UTF_8)
    }.getOrNull()

    private fun removeSecret(key: String) { prefs.edit().remove(key).apply() }

    private fun readRefreshToken(): String? = runCatching {
        val bytes = Base64.decode(prefs.getString("refresh_token", null) ?: return null, Base64.NO_WRAP)
        if (bytes.size <= 12) return null
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.DECRYPT_MODE, keystoreKey(), GCMParameterSpec(128, bytes.copyOfRange(0, 12))) }
        String(cipher.doFinal(bytes.copyOfRange(12, bytes.size)), Charsets.UTF_8)
    }.getOrNull()

    private fun keystoreKey(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        val alias = "cobre_tv_refresh_v1"
        (store.getKey(alias, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        generator.init(KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setRandomizedEncryptionRequired(true).build())
        return generator.generateKey()
    }

    companion object {
        private val SESSION_REFRESH_LOCK = Any()
    }
}
