package koharia.lanraragi.ui

import android.graphics.pdf.PdfDocument
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import coil3.fetch.ImageFetchResult
import coil3.request.Options
import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.model.Page
import koharia.connection.LibraryConnectionProfile
import koharia.source.local.LocalBookshelf
import koharia.source.local.LocalFolderSource
import koharia.source.local.LocalLibraryConfig
import koharia.source.local.LocalLibraryContentType
import koharia.source.local.LocalLibraryFilters
import koharia.source.local.LocalLibraryLocator
import koharia.source.local.LocalLibraryOrganizationMode
import koharia.source.local.LocalLibraryPreferences
import koharia.source.local.LocalLibraryRootConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Semaphore
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import tachiyomi.domain.chapter.model.Chapter
import tachiyomi.domain.manga.repository.MangaRepository
import tachiyomi.domain.source.service.SourceManager
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.io.File
import java.nio.file.Files

@RunWith(AndroidJUnit4::class)
class LocalPreviewRegressionDeviceTest {
    @Test
    fun pdfPreviewSurvivesLoaderCleanupAndSortUsesInsertionAndFileTimes() = runBlocking(Dispatchers.IO) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        assertEquals("app.koharia.dev.devicefixture", context.packageName)
        val directory = Files.createTempDirectory(context.cacheDir.toPath(), "preview-regression-").toFile()
        val sourceId = 9_700_000_000_000L + System.currentTimeMillis()
        val repository: MangaRepository = Injekt.get()
        val preferences = LocalLibraryPreferences(sourceId, Json)
        try {
            preferences.setConfig(
                LocalLibraryConfig(
                    roots = listOf(
                        LocalLibraryRootConfig(
                            id = "root",
                            treeUri = directory.toURI().toString(),
                            contentType = LocalLibraryContentType.COMICS,
                            bookshelfId = "comics",
                        ),
                    ),
                    bookshelves = listOf(
                        LocalBookshelf(
                            "comics",
                            "Comics",
                            LocalLibraryContentType.COMICS,
                            LocalLibraryOrganizationMode.INDIVIDUAL_FILES,
                        ),
                    ),
                    setupCompleted = true,
                ),
            )
            val source = LocalFolderSource(
                context,
                sourceId,
                "Preview fixture",
                LibraryConnectionProfile(sourceId, "local-folder", "Preview fixture"),
            )
            val first = createPdf(directory.resolve("Z-first.pdf"))
            assertTrue(first.setLastModified(1_600_000_020_000L))
            source.refreshLibrary().getOrThrow()
            val firstManga = repository.getMangaBySourceId(sourceId).single()
            val second = createPdf(directory.resolve("A-second.pdf"))
            assertTrue(second.setLastModified(1_600_000_010_000L))
            source.refreshLibrary().getOrThrow()
            val secondManga = repository.getMangaBySourceId(sourceId).single { it.id != firstManga.id }
            assertEquals(
                listOf(firstManga.id, secondManga.id),
                source.browseIndexedLibrary("", filters = LocalLibraryFilters(sort = 1)).map { it.id },
            )
            assertEquals(
                listOf(secondManga.id, firstManga.id),
                source.browseIndexedLibrary("", filters = LocalLibraryFilters(sort = 2)).map { it.id },
            )
            val chapter = Chapter.create().copy(
                id = -sourceId,
                mangaId = firstManga.id,
                url = LocalLibraryLocator.chapterUrl(sourceId, "root", first.name),
            )
            val manager = object : SourceManager by Injekt.get<SourceManager>() {
                override fun get(sourceKey: Long): Source? = if (sourceKey == sourceId) source else null
            }
            val permits = Semaphore(2)
            val result = LanraragiPreviewImageFetcher(
                LanraragiPreviewImage(firstManga, chapter, Page(0), false),
                Options(context),
                permits,
                manager,
            ).fetch()
            assertTrue(result is ImageFetchResult)
            val image = (result as ImageFetchResult).image
            assertTrue(image.width > 0 && image.height > 0)
            // Loader has already been recycled: the returned image must remain usable.
            val target = android.graphics.Bitmap.createBitmap(
                image.width,
                image.height,
                android.graphics.Bitmap.Config.ARGB_8888,
            )
            try {
                image.draw(android.graphics.Canvas(target))
            } finally {
                target.recycle()
            }
            assertEquals(2, permits.availablePermits)
        } finally {
            repository.deleteMangaBySourceId(sourceId)
            context.getSharedPreferences("source_$sourceId", 0).edit().clear().commit()
            check(directory.canonicalFile.parentFile == context.cacheDir.canonicalFile)
            directory.deleteRecursively()
        }
    }

    private fun createPdf(file: File): File {
        val document = PdfDocument()
        try {
            val page = document.startPage(PdfDocument.PageInfo.Builder(200, 300, 1).create())
            page.canvas.drawColor(android.graphics.Color.WHITE)
            document.finishPage(page)
            file.outputStream().use(document::writeTo)
        } finally {
            document.close()
        }
        return file
    }
}
