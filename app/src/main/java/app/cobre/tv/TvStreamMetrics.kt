package app.cobre.tv

import android.os.SystemClock
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.TransferListener

data class TvStreamMeasurement(val latencyMs: Long, val bytesPerSecond: Long, val ageMs: Long)

/** Measures only the authorized Desktop audio endpoint. It never records track names or file paths. */
@OptIn(UnstableApi::class)
class TvStreamMetrics(private val clockMs: () -> Long = SystemClock::elapsedRealtime) : TransferListener {
    private val lock = Any()
    private var requestStartMs = 0L
    private var windowStartMs = 0L
    private var windowBytes = 0L
    private var firstByteLatencyMs: Long? = null
    private var lastRateBytesPerSecond: Long? = null
    private var measuredAtMs = 0L

    override fun onTransferInitializing(source: DataSource, dataSpec: DataSpec, isNetwork: Boolean) = Unit

    override fun onTransferStart(source: DataSource, dataSpec: DataSpec, isNetwork: Boolean) {
        if (!isDesktopAudio(dataSpec, isNetwork)) return
        synchronized(lock) {
            requestStartMs = clockMs()
            windowStartMs = requestStartMs
            windowBytes = 0
            firstByteLatencyMs = null
            lastRateBytesPerSecond = null
        }
    }

    override fun onBytesTransferred(source: DataSource, dataSpec: DataSpec, isNetwork: Boolean, bytesTransferred: Int) {
        if (!isDesktopAudio(dataSpec, isNetwork) || bytesTransferred <= 0) return
        synchronized(lock) {
            val now = clockMs()
            if (requestStartMs == 0L) {
                requestStartMs = now
                windowStartMs = now
            }
            if (firstByteLatencyMs == null) {
                firstByteLatencyMs = (now - requestStartMs).coerceAtLeast(0)
                measuredAtMs = now
            }
            windowBytes += bytesTransferred
            val elapsed = now - windowStartMs
            if (elapsed >= WINDOW_MS) saveRate(now, elapsed)
        }
    }

    override fun onTransferEnd(source: DataSource, dataSpec: DataSpec, isNetwork: Boolean) {
        if (!isDesktopAudio(dataSpec, isNetwork)) return
        synchronized(lock) {
            val elapsed = clockMs() - windowStartMs
            if (elapsed > 0 && windowBytes > 0) saveRate(clockMs(), elapsed)
            requestStartMs = 0
        }
    }

    fun recent(): TvStreamMeasurement? = synchronized(lock) {
        val age = clockMs() - measuredAtMs
        val latency = firstByteLatencyMs ?: return@synchronized null
        val rate = lastRateBytesPerSecond ?: return@synchronized null
        if (measuredAtMs == 0L || age !in 0..MAX_AGE_MS || rate <= 0) return@synchronized null
        TvStreamMeasurement(latency.coerceIn(0, 120_000), rate.coerceIn(1, 1_000_000_000), age)
    }

    private fun saveRate(now: Long, elapsed: Long) {
        lastRateBytesPerSecond = (windowBytes * 1000L / elapsed).coerceIn(1, 1_000_000_000)
        measuredAtMs = now
        windowStartMs = now
        windowBytes = 0
    }

    private fun isDesktopAudio(dataSpec: DataSpec, isNetwork: Boolean): Boolean =
        isNetwork && dataSpec.uri.scheme in HTTP_SCHEMES && AUDIO_STREAM_PATH.matches(dataSpec.uri.path.orEmpty())

    companion object {
        const val MAX_AGE_MS = 120_000L
        private const val WINDOW_MS = 5_000L
        private val HTTP_SCHEMES = setOf("http", "https")
        private val AUDIO_STREAM_PATH = Regex("/v1/tracks/\\d+/stream")
    }
}
