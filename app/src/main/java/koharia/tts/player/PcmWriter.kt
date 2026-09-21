package koharia.tts.player

import kotlin.math.roundToLong

/** Returns the accepted byte count, retaining partial progress on failure or cancellation. */
internal fun writePcmFully(
    bytes: ByteArray,
    frameBytes: Int,
    sink: (ByteArray, Int, Int) -> Int,
    shouldContinue: () -> Boolean,
    waitForCapacity: () -> Unit,
): Int {
    require(frameBytes > 0) { "PCM frame size must be positive" }
    require(bytes.size % frameBytes == 0) { "PCM input must contain complete frames" }
    var written = 0
    while (written < bytes.size && shouldContinue()) {
        val remaining = bytes.size - written
        val result = runCatching { sink(bytes, written, remaining) }.getOrElse { return written }
        if (result < 0 || result > remaining || result % frameBytes != 0) return written
        if (result == 0) {
            if (!shouldContinue()) return written
            try {
                waitForCapacity()
            } catch (_: InterruptedException) {
                Thread.currentThread().interrupt()
                return written
            }
        } else {
            written += result
        }
    }
    return written
}

/** Round the duration to whole PCM16 frames before converting to bytes. */
internal fun silencePcmByteCount(sampleRate: Int, channels: Int, gapMs: Long, speed: Float): Int {
    require(sampleRate > 0 && channels > 0 && gapMs >= 0 && speed.isFinite() && speed > 0)
    val frames = (sampleRate.toDouble() * gapMs / 1000.0 * speed).roundToLong()
    val frameBytes = channels.toLong() * 2
    require(frames <= Int.MAX_VALUE / frameBytes) { "PCM silence exceeds buffer capacity" }
    return (frames * frameBytes).toInt()
}
