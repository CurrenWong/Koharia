package koharia.source.local

import eu.kanade.tachiyomi.source.sourcePreferences
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

internal class LocalLibraryFilterPreferences(sourceId: Long) {
    private val preferences = sourcePreferences("source_$sourceId")

    val enabled: Boolean get() = preferences.getBoolean("local_remember_filters", false)

    fun read(): Snapshot? = preferences.getString("local_saved_filters", null)
        ?.let { runCatching { Json.decodeFromString<Snapshot>(it) }.getOrNull() }
        ?.takeIf { it.version == FILTER_SNAPSHOT_VERSION }

    fun write(filters: LocalLibraryFilters, bookshelfId: String?, enabled: Boolean) {
        preferences.edit()
            .putBoolean("local_remember_filters", enabled)
            .apply {
                if (enabled) {
                    putString(
                        "local_saved_filters",
                        Json.encodeToString(Snapshot(filters = filters, bookshelfId = bookshelfId)),
                    )
                } else {
                    remove("local_saved_filters")
                }
            }
            .apply()
    }

    @Serializable
    data class Snapshot(
        val version: Int = FILTER_SNAPSHOT_VERSION,
        val filters: LocalLibraryFilters = LocalLibraryFilters(),
        val bookshelfId: String? = null,
    )

    private companion object {
        const val FILTER_SNAPSHOT_VERSION = 1
    }
}
