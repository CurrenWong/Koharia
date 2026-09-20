package eu.kanade.tachiyomi.ui.reader.setting

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ReaderToolbarActionsTest {
    @Test
    fun `comic actions drop unknown duplicates and values beyond four`() {
        assertEquals(
            listOf(
                ComicReaderToolbarAction.CROP,
                ComicReaderToolbarAction.BOOKMARK,
                ComicReaderToolbarAction.PAGE_LAYOUT,
                ComicReaderToolbarAction.BRIGHTNESS,
            ),
            ReaderToolbarActions.comic("crop,unknown,crop,bookmark,page_layout,brightness,background"),
        )
    }

    @Test
    fun `empty values allow hiding every optional action`() {
        assertEquals(emptyList<ComicReaderToolbarAction>(), ReaderToolbarActions.comic(""))
        assertEquals(emptyList<EpubReaderToolbarAction>(), ReaderToolbarActions.epub(""))
    }
}
