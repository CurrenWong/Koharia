package eu.kanade.presentation.reader

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ReaderStatusIndicatorTest {
    @Test
    fun `transfer speed chooses compact binary units`() {
        assertEquals("512 B/s", formatReaderTransferSpeed(512))
        assertEquals("2 KB/s", formatReaderTransferSpeed(2_048))
        assertEquals("2.0 MB/s", formatReaderTransferSpeed(2L * 1_024 * 1_024))
    }

    @Test
    fun `page range remains compatible with the old indicator`() {
        assertEquals("4–5 / 20", readerPageIndicatorText(5, 20, 4, false))
    }

    @Test
    fun `chapter and page information share the left reader status`() {
        assertEquals("Chapter 7 · 4–5 / 20", readerStatusLeftText("Chapter 7", "4–5 / 20", true))
        assertEquals("4–5 / 20", readerStatusLeftText("Chapter 7", "4–5 / 20", false))
    }
}
