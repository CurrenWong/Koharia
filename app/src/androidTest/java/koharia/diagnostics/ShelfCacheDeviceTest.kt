package koharia.diagnostics

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import cafe.adriel.voyager.core.model.screenModelScope
import eu.kanade.tachiyomi.source.sourcePreferences
import koharia.connection.ConnectionPreferences
import koharia.connection.LibraryConnectionProfile
import koharia.lanraragi.ui.LanraragiLibraryScreenModel
import koharia.source.lanraragi.LanraragiPreferences
import koharia.source.lanraragi.LanraragiSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

@RunWith(AndroidJUnit4::class)
class ShelfCacheDeviceTest {
    @Test
    fun missingCatalogueBootstrapsAndExistingCacheSurvivesNewSourceAndFailure(): Unit = runBlocking(Dispatchers.IO) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        assertEquals("app.koharia.dev.devicefixture", context.packageName)
        val id = Injekt.get<ConnectionPreferences>().allocateConnectionId()
        val profile = LibraryConnectionProfile(id, "lanraragi", "Shelf cache fixture")
        val preferences = LanraragiPreferences(id)
        preferences.save("http://127.0.0.1:38709/v80_test_shelf/lrr/", "fixture-key")
        val downloadedOnly = Injekt.get<eu.kanade.domain.base.BasePreferences>().downloadedOnly
        val previousDownloadedOnly = downloadedOnly.get()
        downloadedOnly.set(false)
        var source = LanraragiSource(context, profile)
        var model: LanraragiLibraryScreenModel? = null
        try {
            source.networkAvailable.value = true
            source.onRegistered()
            val bootstrapped = kotlinx.coroutines.withTimeoutOrNull(15000) {
                while (source.repository.lastSync(id) == 0L) delay(100)
                true
            }
            assertTrue(
                "valid=${source.hasValidConnection()} online=${source.networkAvailable.value} " +
                    "downloadedOnly=${downloadedOnly.get()} running=${source.status.value.running} " +
                    "error=${source.status.value.error}",
                bootstrapped == true,
            )
            val entries = source.repository.entries(id)
            assertTrue(entries.isNotEmpty())
            val oldTime = System.currentTimeMillis() - 7 * 24 * 60 * 60 * 1000L
            val generation = source.repository.lastSync(id) + 1
            source.repository.stage(id, generation, entries)
            source.repository.publish(id, generation, oldTime)
            source.close()
            source = LanraragiSource(context, profile)
            source.onRegistered()
            model = LanraragiLibraryScreenModel(source, null)
            withTimeout(5000) { while (!model.state.value.catalogLoaded) delay(50) }
            assertEquals(entries.size, model.state.value.entries.size)
            delay(1800)
            assertEquals("Even week-old cache must not refresh at startup", oldTime, source.repository.lastSync(id))
            source.refreshLibrary().getOrThrow()
            val refreshed = source.repository.lastSync(id)
            assertTrue(refreshed > oldTime)
            preferences.save("http://127.0.0.1:1/lrr/", "fixture-key")
            assertTrue(source.refreshLibrary().isFailure)
            assertEquals(refreshed, source.repository.lastSync(id))
            assertEquals(entries.size, source.repository.entries(id).size)
            val emptyGeneration = refreshed + 1
            source.repository.begin(id, emptyGeneration)
            source.repository.publish(id, emptyGeneration, refreshed)
            source.close()
            source = LanraragiSource(context, profile)
            source.onRegistered()
            delay(1800)
            assertTrue(source.repository.entries(id).isEmpty())
            assertEquals(refreshed, source.repository.lastSync(id))
            assertEquals(null, source.status.value.error)
        } finally {
            model?.screenModelScope?.cancel()
            source.close()
            source.repository.remove(id)
            sourcePreferences("source_$id").edit().clear().commit()
            downloadedOnly.set(previousDownloadedOnly)
        }
    }
}
