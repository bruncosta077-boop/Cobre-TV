package app.cobre.tv

import android.net.Uri
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL

data class DesktopTrack(
    val id: Int,
    val title: String,
    val artist: String,
    val album: String,
    val year: Int,
    val durationSeconds: Long,
    val trackNumber: Int,
)

data class DesktopAlbum(val id: String, val title: String, val artist: String, val tracks: List<DesktopTrack>) {
    val coverTrackId: Int get() = tracks.first().id
}

/** Loads metadata and stream URLs directly from a paired Desktop. The Cobre Server never carries audio. */
class DesktopLibraryRepository {
    suspend fun load(origin: String, grantToken: String): List<DesktopAlbum> = withContext(Dispatchers.IO) {
        require(grantToken.isNotBlank()) { "Autorize esta TV no Cobre Desktop antes de carregar a biblioteca." }
        val base = normalizeOrigin(origin)
        val endpoint = Uri.parse(base).buildUpon().appendPath("v1").appendPath("library").build()
        val connection = (URL(endpoint.toString()).openConnection() as HttpURLConnection).apply {
            instanceFollowRedirects = false
            connectTimeout = 6_000
            readTimeout = 20_000
            requestMethod = "GET"
            setRequestProperty("Accept", "application/json")
            setRequestProperty("Cache-Control", "no-store")
            setRequestProperty("Authorization", "Bearer $grantToken")
        }
        val tracks = try {
            val status = connection.responseCode
            if (status != HttpURLConnection.HTTP_OK) {
                val detail = connection.errorStream?.bufferedReader()?.use { it.readText() }.orEmpty()
                throw IllegalStateException(when (status) {
                    HttpURLConnection.HTTP_UNAUTHORIZED -> "A permissão da TV expirou ou foi revogada. Conecte o computador novamente."
                    HttpURLConnection.HTTP_FORBIDDEN -> "A permissão aprovada não inclui acesso à biblioteca. Revogue e autorize novamente."
                    HttpURLConnection.HTTP_UNAVAILABLE -> "O Desktop não conseguiu validar a autorização no Cobre Server."
                    else -> "Desktop respondeu HTTP $status ${detail.take(120)}".trim()
                })
            }
            val bytes = connection.inputStream.use { input ->
                val output = java.io.ByteArrayOutputStream()
                val buffer = ByteArray(16 * 1024)
                var total = 0
                while (true) {
                    val read = input.read(buffer)
                    if (read < 0) break
                    total += read
                    if (total > MAX_CATALOG_BYTES) throw IllegalStateException("O catálogo do Desktop excede o limite de segurança.")
                    output.write(buffer, 0, read)
                }
                output.toByteArray()
            }
            val list = JSONObject(bytes.toString(Charsets.UTF_8)).optJSONArray("tracks")
                ?: throw IllegalStateException("O Desktop retornou um catálogo inválido.")
            buildList {
                for (index in 0 until list.length()) {
                    val row = list.optJSONObject(index) ?: continue
                    val id = row.optInt("id", -1)
                    if (id < 0) continue
                    add(DesktopTrack(
                        id = id,
                        title = row.optString("title").ifBlank { "Faixa sem título" },
                        artist = row.optString("artist").ifBlank { "Artista desconhecido" },
                        album = row.optString("album").ifBlank { "Sem álbum" },
                        year = row.optInt("year").coerceAtLeast(0),
                        durationSeconds = row.optLong("durationSeconds").coerceAtLeast(0),
                        trackNumber = row.optInt("trackNumber").coerceAtLeast(0),
                    ))
                }
            }
        } finally { connection.disconnect() }

        tracks.groupBy { "${it.artist.trim().lowercase()}\u0000${it.album.trim().lowercase()}" }
            .map { (key, items) ->
                val ordered = items.sortedWith(compareBy<DesktopTrack> { it.trackNumber.takeIf { n -> n > 0 } ?: Int.MAX_VALUE }.thenBy { it.title.lowercase() })
                DesktopAlbum(key, ordered.first().album, ordered.first().artist, ordered)
            }
            .sortedWith(compareBy<DesktopAlbum> { it.artist.lowercase() }.thenBy { it.title.lowercase() })
    }

    fun streamUri(origin: String, trackId: Int): Uri = Uri.parse(normalizeOrigin(origin)).buildUpon()
        .appendPath("v1").appendPath("tracks").appendPath(trackId.toString()).appendPath("stream").build()

    suspend fun loadCover(origin: String, trackId: Int, grantToken: String): Bitmap? = withContext(Dispatchers.IO) {
        val endpoint = Uri.parse(normalizeOrigin(origin)).buildUpon().appendPath("v1").appendPath("tracks")
            .appendPath(trackId.toString()).appendPath("cover").build()
        val connection = (URL(endpoint.toString()).openConnection() as HttpURLConnection).apply {
            instanceFollowRedirects = false
            connectTimeout = 6_000
            readTimeout = 10_000
            setRequestProperty("Authorization", "Bearer $grantToken")
            setRequestProperty("Cache-Control", "no-store")
        }
        try {
            val contentLength = connection.getHeaderField("Content-Length")?.toLongOrNull() ?: -1L
            if (connection.responseCode != HttpURLConnection.HTTP_OK || contentLength > MAX_COVER_BYTES) return@withContext null
            val bytes = connection.inputStream.use { input ->
                val output = java.io.ByteArrayOutputStream()
                val buffer = ByteArray(8192)
                var total = 0
                while (true) {
                    val read = input.read(buffer)
                    if (read < 0) break
                    total += read
                    if (total > MAX_COVER_BYTES) return@withContext null
                    output.write(buffer, 0, read)
                }
                output.toByteArray()
            }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        } catch (_: Exception) { null } finally { connection.disconnect() }
    }

    private fun normalizeOrigin(raw: String): String {
        return TvNetworkEndpoint.normalize(raw, "Cobre Desktop")
    }

    companion object {
        private const val MAX_CATALOG_BYTES = 8 * 1024 * 1024
        private const val MAX_COVER_BYTES = 1024 * 1024
    }
}
