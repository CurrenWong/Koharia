package eu.kanade.presentation.more.settings.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowDownward
import androidx.compose.material.icons.outlined.ArrowUpward
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import eu.kanade.presentation.components.AppBar
import eu.kanade.presentation.util.Screen
import eu.kanade.tachiyomi.ui.reader.setting.ComicReaderToolbarAction
import eu.kanade.tachiyomi.ui.reader.setting.EpubReaderToolbarAction
import eu.kanade.tachiyomi.ui.reader.setting.ReaderPreferences
import eu.kanade.tachiyomi.ui.reader.setting.ReaderToolbarActions
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.components.material.Scaffold
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.util.collectAsState
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

enum class ReaderToolbarType { COMIC, BOOK }

class ReaderToolbarSettingsScreen(
    private val type: ReaderToolbarType,
) : Screen() {
    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val preferences = remember { Injekt.get<ReaderPreferences>() }
        val title = stringResource(
            if (type == ReaderToolbarType.COMIC) {
                MR.strings.comic_reader_toolbar_settings
            } else {
                MR.strings.book_reader_toolbar_settings
            },
        )

        Scaffold(
            topBar = {
                AppBar(title = title, navigateUp = navigator::pop)
            },
        ) { padding ->
            LazyColumn(
                modifier = Modifier.padding(padding),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                item {
                    Text(
                        text = stringResource(MR.strings.reader_toolbar_settings_summary),
                        modifier = Modifier.padding(16.dp),
                    )
                }
                when (type) {
                    ReaderToolbarType.COMIC -> item {
                        val value by preferences.comicToolbarActions.collectAsState()
                        val selected = remember(value) { ReaderToolbarActions.comic(value) }
                        ToolbarSection(
                            title = title,
                            entries = ComicReaderToolbarAction.entries,
                            selected = selected,
                            label = { comicActionLabel(it) },
                            onChange = { preferences.comicToolbarActions.set(ReaderToolbarActions.encode(it)) },
                            onReset = {
                                preferences.comicToolbarActions.set(
                                    ReaderToolbarActions.encode(ReaderToolbarActions.defaultComic),
                                )
                            },
                        )
                    }
                    ReaderToolbarType.BOOK -> item {
                        val value by preferences.epubToolbarActions.collectAsState()
                        val selected = remember(value) { ReaderToolbarActions.epub(value) }
                        ToolbarSection(
                            title = title,
                            entries = EpubReaderToolbarAction.entries,
                            selected = selected,
                            label = { epubActionLabel(it) },
                            onChange = { preferences.epubToolbarActions.set(ReaderToolbarActions.encodeEpub(it)) },
                            onReset = {
                                preferences.epubToolbarActions.set(
                                    ReaderToolbarActions.encodeEpub(ReaderToolbarActions.defaultEpub),
                                )
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun <T> ToolbarSection(
    title: String,
    entries: List<T>,
    selected: List<T>,
    label: @Composable (T) -> String,
    onChange: (List<T>) -> Unit,
    onReset: () -> Unit,
) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(title, modifier = Modifier.weight(1f))
            TextButton(onClick = onReset) { Text(stringResource(MR.strings.action_reset)) }
        }
        entries.forEach { action ->
            val index = selected.indexOf(action)
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Checkbox(
                    checked = index >= 0,
                    enabled = index >= 0 || selected.size < ReaderToolbarActions.MAX_OPTIONAL_ACTIONS,
                    onCheckedChange = { checked ->
                        onChange(if (checked) selected + action else selected - action)
                    },
                )
                Text(label(action), modifier = Modifier.weight(1f))
                IconButton(
                    enabled = index > 0,
                    onClick = { onChange(selected.swap(index, index - 1)) },
                ) { Icon(Icons.Outlined.ArrowUpward, null) }
                IconButton(
                    enabled = index >= 0 && index < selected.lastIndex,
                    onClick = { onChange(selected.swap(index, index + 1)) },
                ) { Icon(Icons.Outlined.ArrowDownward, null) }
            }
        }
    }
}

private fun <T> List<T>.swap(first: Int, second: Int): List<T> {
    if (first !in indices || second !in indices) return this
    return toMutableList().apply {
        val value = this[first]
        this[first] = this[second]
        this[second] = value
    }
}

@Composable
private fun comicActionLabel(action: ComicReaderToolbarAction): String = stringResource(
    when (action) {
        ComicReaderToolbarAction.READING_MODE -> MR.strings.viewer
        ComicReaderToolbarAction.ORIENTATION -> MR.strings.rotation_type
        ComicReaderToolbarAction.CROP -> MR.strings.pref_crop_borders
        ComicReaderToolbarAction.BRIGHTNESS -> MR.strings.pref_custom_brightness
        ComicReaderToolbarAction.BACKGROUND -> MR.strings.pref_reader_theme
        ComicReaderToolbarAction.PAGE_LAYOUT -> MR.strings.pref_page_layout
        ComicReaderToolbarAction.SHIFT_DOUBLE_PAGES -> MR.strings.pref_shift_double_pages
        ComicReaderToolbarAction.BOOKMARK -> MR.strings.action_bookmark
    },
)

@Composable
private fun epubActionLabel(action: EpubReaderToolbarAction): String = stringResource(
    when (action) {
        EpubReaderToolbarAction.CONTENTS -> MR.strings.epub_reader_toc
        EpubReaderToolbarAction.NIGHT_MODE -> MR.strings.epub_reader_quick_night_mode
        EpubReaderToolbarAction.FONT -> MR.strings.pref_epub_font_family
        EpubReaderToolbarAction.BRIGHTNESS -> MR.strings.pref_custom_brightness
        EpubReaderToolbarAction.SEARCH -> MR.strings.action_search
        EpubReaderToolbarAction.TTS -> MR.strings.reader_read_aloud
        EpubReaderToolbarAction.ORIENTATION -> MR.strings.rotation_type
        EpubReaderToolbarAction.MORE -> MR.strings.label_more
    },
)
