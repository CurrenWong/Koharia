package eu.kanade.tachiyomi.ui.reader

import android.graphics.Bitmap
import eu.kanade.tachiyomi.ui.reader.model.ReaderPage
import java.io.File

internal fun writeReaderPageBitmap(page: ReaderPage, destination: File) {
    val bitmap = checkNotNull(page.bitmap).invoke()
    try {
        destination.outputStream().use {
            check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it))
        }
    } finally {
        bitmap.recycle()
    }
}
