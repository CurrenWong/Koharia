package koharia.crash

import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.pdf.PdfDocument
import androidx.activity.ComponentActivity
import androidx.core.view.children
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.hippo.unifile.UniFile
import eu.kanade.tachiyomi.ui.reader.loader.PdfPageLoader
import eu.kanade.tachiyomi.ui.reader.viewer.ReaderPageImageView
import eu.kanade.tachiyomi.ui.reader.writeReaderPageBitmap
import kotlinx.coroutines.runBlocking
import okio.Buffer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

@RunWith(AndroidJUnit4::class)
class ReaderCrashRegressionTest {
    @Test
    fun corruptSelectedImageReportsOneError() {
        val errors = AtomicInteger()
        val ready = CountDownLatch(1)
        ActivityScenario.launch(ComponentActivity::class.java).use { scenario ->
            lateinit var view: ReaderPageImageView
            scenario.onActivity { activity ->
                view = ReaderPageImageView(activity)
                view.onImageLoadError = {
                    errors.incrementAndGet()
                    ready.countDown()
                }
                activity.setContentView(view)
                view.setImage(Buffer().writeUtf8("not an image"), false, ReaderPageImageView.Config(0))
                view.onPageSelected(true)
            }
            assertTrue("Decoder should report an error", ready.await(10, TimeUnit.SECONDS))
            InstrumentationRegistry.getInstrumentation().waitForIdleSync()
            assertEquals(1, errors.get())
            scenario.onActivity { view.recycle() }
        }
    }

    @Test
    fun pdfBitmapPageCanBeExportedWithoutEncodedStream(): Unit = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val input = File.createTempFile("export-test-", ".pdf", context.cacheDir)
        val output = File.createTempFile("export-test-", ".png", context.cacheDir)
        try {
            val document = PdfDocument()
            try {
                val page = document.startPage(PdfDocument.PageInfo.Builder(200, 300, 1).create())
                page.canvas.drawColor(Color.RED)
                document.finishPage(page)
                input.outputStream().use(document::writeTo)
            } finally {
                document.close()
            }
            val loader = PdfPageLoader(context, checkNotNull(UniFile.fromFile(input)))
            try {
                val page = loader.getPages().single()
                assertNull(page.stream)
                writeReaderPageBitmap(page, output)
                val bitmap = checkNotNull(BitmapFactory.decodeFile(output.path))
                try {
                    assertTrue(bitmap.width > 0 && bitmap.height > 0)
                    assertEquals(Color.RED, bitmap.getPixel(bitmap.width / 2, bitmap.height / 2))
                } finally {
                    bitmap.recycle()
                }
            } finally {
                loader.recycle()
            }
        } finally {
            input.delete()
            output.delete()
        }
    }
}
