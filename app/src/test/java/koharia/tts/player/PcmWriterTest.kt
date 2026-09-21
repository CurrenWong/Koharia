package koharia.tts.player

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import kotlin.math.abs

class PcmWriterTest {

    private val pcm = byteArrayOf(1, 2, 3, 4, 5, 6)

    @Test
    fun `short writes advance the offset and retain remaining bytes`() {
        val accepted = mutableListOf<Byte>()
        val requests = mutableListOf<Pair<Int, Int>>()

        val written = writePcmFully(
            bytes = pcm,
            frameBytes = 2,
            sink = { bytes, offset, size ->
                requests.add(offset to size)
                val count = minOf(size, 2)
                accepted.addAll(bytes.slice(offset until offset + count))
                count
            },
            shouldContinue = { true },
            waitForCapacity = { error("Unexpected wait") },
        )

        written shouldBe pcm.size
        accepted shouldBe pcm.toList()
        requests shouldBe listOf(0 to 6, 2 to 4, 4 to 2)
    }

    @Test
    fun `failure after a short write returns only the accepted bytes`() {
        var calls = 0
        val written = writePcmFully(
            bytes = pcm,
            frameBytes = 2,
            sink = { _, _, _ -> if (++calls == 1) 2 else -1 },
            shouldContinue = { true },
            waitForCapacity = { error("Unexpected wait") },
        )

        written shouldBe 2
        calls shouldBe 2
    }

    @Test
    fun `exception after a short write retains partial progress`() {
        var calls = 0
        val written = writePcmFully(
            bytes = pcm,
            frameBytes = 2,
            sink = { _, _, _ ->
                if (++calls == 1) 2 else error("Output track unavailable")
            },
            shouldContinue = { true },
            waitForCapacity = { error("Unexpected wait") },
        )

        written shouldBe 2
        calls shouldBe 2
    }

    @Test
    fun `full buffer waits and retries the same bytes successfully`() {
        var capacityAvailable = false
        var waits = 0
        val requests = mutableListOf<Pair<Int, Int>>()
        val accepted = mutableListOf<Byte>()
        val written = writePcmFully(
            bytes = pcm,
            frameBytes = 2,
            sink = { bytes, offset, size ->
                requests.add(offset to size)
                if (capacityAvailable) {
                    accepted.addAll(bytes.slice(offset until offset + size))
                    size
                } else {
                    0
                }
            },
            shouldContinue = { true },
            waitForCapacity = {
                waits++
                capacityAvailable = true
            },
        )

        written shouldBe pcm.size
        waits shouldBe 1
        requests shouldBe listOf(0 to 6, 0 to 6)
        accepted shouldBe pcm.toList()
    }

    @Test
    fun `cancellation while waiting prevents another write`() {
        var active = true
        var calls = 0
        val written = writePcmFully(
            bytes = pcm,
            frameBytes = 2,
            sink = { _, _, _ ->
                calls++
                if (calls == 1) 2 else 0
            },
            shouldContinue = { active },
            waitForCapacity = { active = false },
        )

        written shouldBe 2
        calls shouldBe 2
    }

    @Test
    fun `cancelled writer never calls the sink`() {
        val written = writePcmFully(
            bytes = pcm,
            frameBytes = 2,
            sink = { _, _, _ -> error("Cancelled write reached the sink") },
            shouldContinue = { false },
            waitForCapacity = { error("Unexpected wait") },
        )

        written shouldBe 0
    }

    @Test
    fun `paused output can resume after repeated full buffer responses`() {
        var waits = 0
        val written = writePcmFully(
            bytes = pcm,
            frameBytes = 2,
            sink = { _, _, size -> if (waits < 10000) 0 else size },
            shouldContinue = { true },
            waitForCapacity = { waits++ },
        )

        written shouldBe pcm.size
        waits shouldBe 10000
    }

    @Test
    fun `unaligned input is rejected before reaching the sink`() {
        shouldThrow<IllegalArgumentException> {
            writePcmFully(
                bytes = ByteArray(21839),
                frameBytes = 2,
                sink = { _, _, _ -> error("Unaligned data reached the sink") },
                shouldContinue = { true },
                waitForCapacity = { error("Unaligned data must not wait") },
            )
        }
    }

    @Test
    fun `unaligned and oversized sink results stop without retrying a fractional tail`() {
        for (invalidResult in listOf(1, 3, 8)) {
            var calls = 0
            val written = writePcmFully(
                bytes = pcm,
                frameBytes = 2,
                sink = { _, _, _ -> if (++calls == 1) 2 else invalidResult },
                shouldContinue = { true },
                waitForCapacity = { error("Invalid sink result must not wait") },
            )

            written shouldBe 2
            calls shouldBe 2
        }
    }

    @Test
    fun `invalid frame size is rejected before reaching the sink`() {
        for (frameBytes in listOf(0, -2)) {
            shouldThrow<IllegalArgumentException> {
                writePcmFully(
                    bytes = pcm,
                    frameBytes = frameBytes,
                    sink = { _, _, _ -> error("Invalid frame size reached the sink") },
                    shouldContinue = { true },
                    waitForCapacity = { error("Unexpected wait") },
                )
            }
        }
    }

    @Test
    fun `silence at 1_3x consists of complete frames`() {
        silencePcmByteCount(24000, 1, 350, 1.3f) shouldBe 21840
    }

    @Test
    fun `every supported speed and output format writes silence without a fractional tail`() {
        for (speedTenths in 5..20) {
            for (channels in listOf(1, 2)) {
                for (sampleRate in listOf(24000, 44100, 48000)) {
                    val frameBytes = channels * 2
                    val count = silencePcmByteCount(sampleRate, channels, 350, speedTenths / 10f)
                    count % frameBytes shouldBe 0
                    val intendedFrames = sampleRate.toDouble() * 350 * speedTenths / 10000
                    (abs(count.toDouble() / frameBytes - intendedFrames) <= 0.501) shouldBe true
                    val written = writePcmFully(
                        bytes = ByteArray(count),
                        frameBytes = frameBytes,
                        sink = { _, offset, size ->
                            offset % frameBytes shouldBe 0
                            size % frameBytes shouldBe 0
                            minOf(size, frameBytes * 127)
                        },
                        shouldContinue = { true },
                        waitForCapacity = { error("A fractional frame left the sink unable to progress") },
                    )
                    written shouldBe count
                }
            }
        }
    }
}
