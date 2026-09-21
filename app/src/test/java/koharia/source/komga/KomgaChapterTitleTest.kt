package koharia.source.komga

import io.mockk.every
import io.mockk.mockk
import koharia.komga.api.dto.BookDto
import koharia.komga.api.dto.BookMetadataDto
import koharia.komga.download.KomgaChapterMemo
import kotlinx.serialization.json.JsonObject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class KomgaChapterTitleTest {
    private val source = mockk<KomgaSource>().also {
        every { it.detailsChapterTitle(any()) } answers { callOriginal() }
        every { it.detailsChapterNumber(any()) } answers { callOriginal() }
        every { it.detailsChapterFileName(any()) } answers { callOriginal() }
    }

    @Test
    fun `source title needs no additional connection preference and preserves original text`() {
        val title = "JOJO 013 — The World"
        val memo = KomgaChapterMemo.buildMemo(
            "https://komga.test",
            BookDto(
                id = "book",
                name = "file.cbz",
                fileLastModified = "",
                sizeBytes = 1024L,
                metadata = BookMetadataDto(title = title),
            ),
            embeddedFileSize = "1 KB",
        )
        assertEquals(title, source.detailsChapterTitle(memo))
    }

    @Test
    fun `display metadata keeps title number and filename independent`() {
        val memo = KomgaChapterMemo.buildMemo(
            "https://komga.test",
            BookDto(
                id = "book",
                name = "JOJO-013.cbz",
                fileLastModified = "",
                metadata = BookMetadataDto(title = "The World", number = "013a", numberSort = 13F),
            ),
        )
        assertEquals("The World", source.detailsChapterTitle(memo))
        assertEquals("013a", source.detailsChapterNumber(memo))
        assertEquals("JOJO-013.cbz", source.detailsChapterFileName(memo))
    }

    @Test
    fun `filename mode preserves existing cover and filter flags`() {
        val manga = tachiyomi.domain.manga.model.Manga.create()
        val flags = tachiyomi.domain.manga.model.Manga
        val original = flags.CHAPTER_COVER_DISPLAY_COMFORTABLE or flags.CHAPTER_SHOW_UNREAD
        val changed = manga.copy(chapterFlags = original or flags.CHAPTER_DISPLAY_FILE_NAME)
        assertEquals(flags.CHAPTER_DISPLAY_FILE_NAME, changed.displayMode)
        assertEquals(
            manga.copy(chapterFlags = original).chapterFlags,
            changed.chapterFlags and flags.CHAPTER_DISPLAY_MASK.inv(),
        )
    }

    @Test
    fun `missing and blank source titles allow existing chapter name fallback`() {
        assertNull(source.detailsChapterTitle(JsonObject(emptyMap())))
        val memo = KomgaChapterMemo.buildMemo(
            "https://komga.test",
            BookDto(id = "book", name = "file.cbz", fileLastModified = "", metadata = BookMetadataDto(title = " ")),
        )
        assertNull(source.detailsChapterTitle(memo))
    }
}
