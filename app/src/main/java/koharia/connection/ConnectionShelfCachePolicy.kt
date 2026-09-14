package koharia.connection

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import java.util.concurrent.ConcurrentHashMap

/** All built-in server shelves use persisted data until an explicit update is requested. */
object ConnectionShelfCachePolicy {
    fun shouldRefresh(hasCache: Boolean, explicitUpdate: Boolean = false): Boolean = !hasCache || explicitUpdate
}

object ConnectionShelfUpdates {
    private val versions = ConcurrentHashMap<Long, Long>()
    private val events = MutableSharedFlow<Long>(extraBufferCapacity = 64)
    val changes = events.asSharedFlow()

    fun version(sourceId: Long): Long = versions[sourceId] ?: 0L

    fun notify(sourceId: Long) {
        versions[sourceId] = System.currentTimeMillis()
        events.tryEmit(sourceId)
    }
}
