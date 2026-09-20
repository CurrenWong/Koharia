package eu.kanade.tachiyomi.ui.reader.loader

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.util.ArrayDeque

data class ReaderBufferingState(
    val activeTransfers: Int = 0,
    val bytesPerSecond: Long = 0L,
) {
    val isBuffering: Boolean get() = activeTransfers > 0
}

internal val EmptyReaderBufferingState: StateFlow<ReaderBufferingState> =
    MutableStateFlow(ReaderBufferingState())

internal class ReaderTransferMeter(
    private val clockMillis: () -> Long = System::currentTimeMillis,
) {
    private data class Sample(val at: Long, val bytes: Long)

    private val samples = ArrayDeque<Sample>()
    private var activeTransfers = 0
    private var windowBytes = 0L
    private var lastPublishedAt = Long.MIN_VALUE
    private val mutableState = MutableStateFlow(ReaderBufferingState())

    val state: StateFlow<ReaderBufferingState> = mutableState

    @Synchronized
    fun begin() {
        activeTransfers++
        publish(clockMillis())
    }

    @Synchronized
    fun record(byteCount: Long) {
        if (byteCount <= 0L || activeTransfers <= 0) return
        val now = clockMillis()
        samples.addLast(Sample(now, byteCount))
        windowBytes += byteCount
        if (lastPublishedAt == Long.MIN_VALUE || now - lastPublishedAt >= PUBLISH_INTERVAL_MILLIS) {
            publish(now)
        }
    }

    @Synchronized
    fun end() {
        activeTransfers = (activeTransfers - 1).coerceAtLeast(0)
        publish(clockMillis())
    }

    @Synchronized
    fun reset() {
        activeTransfers = 0
        samples.clear()
        windowBytes = 0L
        lastPublishedAt = Long.MIN_VALUE
        mutableState.value = ReaderBufferingState()
    }

    private fun publish(now: Long) {
        val cutoff = now - WINDOW_MILLIS
        while (samples.isNotEmpty() && samples.first().at < cutoff) {
            windowBytes -= samples.removeFirst().bytes
        }
        val speed = if (samples.isEmpty()) {
            0L
        } else {
            val elapsed = (now - samples.first().at).coerceAtLeast(MIN_SAMPLE_MILLIS)
            windowBytes * 1_000L / elapsed
        }
        mutableState.value = ReaderBufferingState(activeTransfers, speed.coerceAtLeast(0L))
        lastPublishedAt = now
    }

    private companion object {
        const val WINDOW_MILLIS = 1_000L
        const val MIN_SAMPLE_MILLIS = 100L
        const val PUBLISH_INTERVAL_MILLIS = 200L
    }
}
