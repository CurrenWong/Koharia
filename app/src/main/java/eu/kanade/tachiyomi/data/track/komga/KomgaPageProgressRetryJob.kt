package eu.kanade.tachiyomi.data.track.komga

import android.app.Application
import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import eu.kanade.tachiyomi.util.system.workManager
import kotlinx.coroutines.CancellationException
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.security.MessageDigest
import java.util.UUID
import java.util.concurrent.TimeUnit

class KomgaPageProgressRetryJob(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val sourceId = inputData.getLong("source", -1)
        val url = inputData.getString("url") ?: return Result.failure()
        return try {
            Injekt.get<KomgaProgressSyncService>().retryPageProgress(
                sourceId,
                url,
                inputData.getInt("page", -1),
                inputData.getInt("total", 0),
                inputData.getInt("expectedPage", -1),
                inputData.getBoolean("expectedCompleted", false),
                inputData.getString("expectedDate"),
            )
            Result.success()
        } catch (error: Exception) {
            if (error is CancellationException) throw error
            Result.retry()
        }
    }

    companion object {
        private fun name(sourceId: Long, url: String): String = "komga-page-$sourceId-" +
            MessageDigest.getInstance("SHA-256").digest(url.toByteArray()).joinToString("") { "%02x".format(it) }

        fun enqueue(sourceId: Long, url: String, page: Int, total: Int, baseline: KomgaApi.BookProgressSnapshot): UUID {
            val request = OneTimeWorkRequestBuilder<KomgaPageProgressRetryJob>()
                .setInputData(
                    workDataOf(
                        "source" to sourceId,
                        "url" to url,
                        "page" to page,
                        "total" to total,
                        "expectedPage" to (baseline.pageIndex ?: -1),
                        "expectedCompleted" to baseline.completed,
                        "expectedDate" to baseline.readDate,
                    ),
                )
                .setConstraints(Constraints(requiredNetworkType = NetworkType.CONNECTED))
                .setInitialDelay(10, TimeUnit.SECONDS)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 10, TimeUnit.SECONDS)
                .build()
            Injekt.get<Application>().workManager.enqueueUniqueWork(
                name(sourceId, url),
                ExistingWorkPolicy.REPLACE,
                request,
            )
            return request.id
        }

        fun cancel(sourceId: Long, url: String) {
            Injekt.get<Application>().workManager.cancelUniqueWork(name(sourceId, url))
        }
    }
}
