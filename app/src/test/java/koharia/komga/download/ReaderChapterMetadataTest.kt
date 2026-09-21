package koharia.komga.download

import eu.kanade.domain.chapter.model.toDbChapter
import koharia.connection.ConnectionChapterMetadata
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import tachiyomi.domain.chapter.model.Chapter

class ReaderChapterMetadataTest {
    @Test
    fun `opening and refreshing reading progress preserves original title and embedded size`() {
        val memo = buildJsonObject {
            put(KomgaChapterMemo.BOOK_URL, "https://komga.test/api/v1/books/11")
            put(KomgaChapterMemo.BOOK_TITLE, "卷 11")
            put(KomgaChapterMemo.EMBEDDED_FILE_SIZE, "257.2 MiB")
            put("providerExtra", "keep")
        }
        val chapter = Chapter.create().copy(id = 11, name = "11 - 卷 11 (257.2 MiB)", memo = memo)
        val readerChapter = chapter.toDbChapter()
        assertEquals(memo, readerChapter.memo)
        val refreshed = KomgaChapterMemo.mergePublicationMetadata(
            existing = readerChapter.memo,
            bookUrl = "https://komga.test/api/v1/books/11",
            fileHash = "hash",
            fileLastModified = null,
            sizeBytes = 269_693_747,
            fileName = "11.cbz",
            isEpub = false,
            pagesCount = 200,
        )
        assertEquals("卷 11", KomgaChapterMemo.readFingerprint(refreshed)?.bookTitle)
        assertEquals(
            "11 - 卷 11",
            ConnectionChapterMetadata.removeTrailingEmbeddedFileSize(readerChapter.name, refreshed),
        )
        assertEquals(memo["providerExtra"], refreshed["providerExtra"])
    }
}
