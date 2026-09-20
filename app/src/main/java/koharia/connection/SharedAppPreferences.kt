package koharia.connection

import android.app.Application
import eu.kanade.domain.base.BasePreferences
import eu.kanade.domain.track.service.TrackPreferences
import eu.kanade.tachiyomi.ui.reader.setting.ReaderPreferences
import koharia.epub.font.EpubFontId
import koharia.epub.settings.EpubLayoutPreferences
import koharia.epub.settings.EpubReaderPreferences
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import tachiyomi.core.common.preference.PreferenceScope
import tachiyomi.core.common.preference.PreferenceScopeProvider
import tachiyomi.core.common.preference.PreferenceStore
import tachiyomi.core.common.preference.ScopedPreferenceStore
import tachiyomi.domain.download.service.DownloadPreferences
import tachiyomi.domain.library.service.LibraryPreferences

class SharedAppPreferences(
    private val app: Application,
    private val preferenceStore: PreferenceStore,
) {
    fun store(): ScopedPreferenceStore = ScopedPreferenceStore(
        preferenceStore = preferenceStore,
        scopeProvider = SharedAppPreferenceScope,
    )

    fun readerPreferences() = ReaderPreferences(store())
    fun epubReaderPreferences() = EpubReaderPreferences(store())
    fun epubLayoutPreferences() = EpubLayoutPreferences(store())
    fun basePreferences() = BasePreferences(app, store())
    fun downloadPreferences() = DownloadPreferences(store())
    fun trackPreferences() = TrackPreferences(store())
    fun libraryPreferences() = LibraryPreferences(store())

    fun resetEpubFontSelection(fontId: EpubFontId) {
        val selected = epubLayoutPreferences().selectedFontId
        if (selected.get() == fontId.value) selected.set(EpubFontId.ORIGINAL.value)
    }
}

object ConnectionPreferenceScopes {
    const val SHARED_SCOPE_NAME = "connection_shared"

    fun connectionScopeName(connectionId: Long): String = "connection_$connectionId"
}

object SharedAppPreferenceScope : PreferenceScopeProvider {
    override fun currentScope() = PreferenceScope("connection_shared::", allowLegacyFallback = true)
    override fun scopeChanges(): Flow<PreferenceScope> = kotlinx.coroutines.flow.flowOf(currentScope())
}
