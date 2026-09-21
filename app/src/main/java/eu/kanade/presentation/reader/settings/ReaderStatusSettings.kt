package eu.kanade.presentation.reader.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import eu.kanade.tachiyomi.ui.reader.setting.ReaderPreferences
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.components.CheckboxItem
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.util.collectAsState

@Composable
internal fun ReaderStatusSettings(preferences: ReaderPreferences) {
    CheckboxItem(label = stringResource(MR.strings.pref_show_page_number), pref = preferences.showPageNumber)
    val enabled by preferences.showPageNumber.collectAsState()
    if (enabled) {
        CheckboxItem(
            label = stringResource(MR.strings.reader_status_show_chapter),
            pref = preferences.showReaderChapterTitle,
        )
        CheckboxItem(label = stringResource(MR.strings.reader_status_show_pages), pref = preferences.showReaderPages)
        CheckboxItem(label = stringResource(MR.strings.reader_status_show_clock), pref = preferences.showReaderClock)
        CheckboxItem(
            label = stringResource(MR.strings.reader_status_show_battery),
            pref = preferences.showReaderBattery,
        )
    }
}
