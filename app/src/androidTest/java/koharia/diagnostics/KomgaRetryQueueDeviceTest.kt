package koharia.diagnostics

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.work.WorkInfo
import eu.kanade.tachiyomi.data.track.komga.KomgaApi
import eu.kanade.tachiyomi.data.track.komga.KomgaPageProgressRetryJob
import eu.kanade.tachiyomi.util.system.workManager
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class KomgaRetryQueueDeviceTest {
    @Test
    fun newerPositionReplacesOnlyItsOwnPendingWork() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val url = "https://example.invalid/api/v1/books/${UUID.randomUUID()}"
        val sourceId = Long.MAX_VALUE
        val baseline = KomgaApi.BookProgressSnapshot(
            url, null, null, 0, 10, false, null, false, false, null, null, 0, "fixture.cbz",
        )
        try {
            val first = KomgaPageProgressRetryJob.enqueue(sourceId, url, 1, 10, baseline)
            val other = KomgaPageProgressRetryJob.enqueue(sourceId - 1, url, 3, 10, baseline)
            val second = KomgaPageProgressRetryJob.enqueue(sourceId, url, 2, 10, baseline)
            val old = context.workManager.getWorkInfoById(first).get()
            org.junit.Assert.assertTrue(old == null || old.state == WorkInfo.State.CANCELLED)
            assertEquals(WorkInfo.State.ENQUEUED, context.workManager.getWorkInfoById(second).get()?.state)
            assertEquals(WorkInfo.State.ENQUEUED, context.workManager.getWorkInfoById(other).get()?.state)
        } finally {
            KomgaPageProgressRetryJob.cancel(sourceId, url)
            KomgaPageProgressRetryJob.cancel(sourceId - 1, url)
        }
    }
}
