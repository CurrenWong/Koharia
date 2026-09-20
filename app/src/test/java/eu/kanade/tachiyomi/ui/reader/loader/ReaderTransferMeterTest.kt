package eu.kanade.tachiyomi.ui.reader.loader

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ReaderTransferMeterTest {
    @Test
    fun `meter combines active transfers and becomes idle after both finish`() {
        var now = 0L
        val meter = ReaderTransferMeter { now }
        meter.begin()
        meter.begin()
        meter.record(1_024)
        now = 500L
        meter.record(1_024)

        assertEquals(2, meter.state.value.activeTransfers)
        assertTrue(meter.state.value.bytesPerSecond > 0L)
        meter.end()
        assertTrue(meter.state.value.isBuffering)
        meter.end()
        assertFalse(meter.state.value.isBuffering)
    }

    @Test
    fun `reset clears samples and speed`() {
        val meter = ReaderTransferMeter { 1_000L }
        meter.begin()
        meter.record(2_048)
        meter.reset()
        assertEquals(ReaderBufferingState(), meter.state.value)
    }
}
