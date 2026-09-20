package eu.kanade.presentation.reader.appbars

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Bookmark
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.BrightnessMedium
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.SwapHoriz
import androidx.compose.material.icons.outlined.ViewWeek
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.painterResource
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.ui.reader.setting.ComicReaderToolbarAction
import eu.kanade.tachiyomi.ui.reader.setting.ReaderOrientation
import eu.kanade.tachiyomi.ui.reader.setting.ReadingMode
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.i18n.stringResource

@Composable
fun ReaderBottomBar(
    actions: List<ComicReaderToolbarAction>,
    readingMode: ReadingMode,
    onClickReadingMode: () -> Unit,
    orientation: ReaderOrientation,
    onClickOrientation: () -> Unit,
    cropEnabled: Boolean,
    onClickCropBorder: () -> Unit,
    doublePages: Boolean,
    supportsPageLayout: Boolean,
    shiftDoublePages: Boolean,
    bookmarked: Boolean,
    settingsSelected: Boolean,
    onClickBrightness: () -> Unit,
    onClickBackground: () -> Unit,
    onClickPageLayout: () -> Unit,
    onClickShiftDoublePages: () -> Unit,
    onClickBookmark: () -> Unit,
    onClickSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .pointerInput(Unit) {},
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        actions.forEach { action ->
            when (action) {
                ComicReaderToolbarAction.READING_MODE -> IconButton(onClick = onClickReadingMode) {
                    Icon(painterResource(readingMode.iconRes), stringResource(MR.strings.viewer))
                }
                ComicReaderToolbarAction.ORIENTATION -> IconButton(onClick = onClickOrientation) {
                    Icon(orientation.icon, stringResource(MR.strings.rotation_type))
                }
                ComicReaderToolbarAction.CROP -> IconButton(onClick = onClickCropBorder) {
                    Icon(
                        painterResource(if (cropEnabled) R.drawable.ic_crop_24dp else R.drawable.ic_crop_off_24dp),
                        stringResource(MR.strings.pref_crop_borders),
                    )
                }
                ComicReaderToolbarAction.BRIGHTNESS -> IconButton(onClick = onClickBrightness) {
                    Icon(Icons.Outlined.BrightnessMedium, stringResource(MR.strings.pref_custom_brightness))
                }
                ComicReaderToolbarAction.BACKGROUND -> IconButton(onClick = onClickBackground) {
                    Icon(Icons.Outlined.Palette, stringResource(MR.strings.pref_reader_theme))
                }
                ComicReaderToolbarAction.PAGE_LAYOUT -> IconButton(
                    enabled = supportsPageLayout,
                    onClick = onClickPageLayout,
                ) {
                    Icon(Icons.Outlined.ViewWeek, stringResource(MR.strings.pref_page_layout))
                }
                ComicReaderToolbarAction.SHIFT_DOUBLE_PAGES -> IconButton(
                    enabled = doublePages,
                    onClick = onClickShiftDoublePages,
                ) {
                    Icon(
                        Icons.Outlined.SwapHoriz,
                        stringResource(MR.strings.pref_shift_double_pages),
                        tint = if (shiftDoublePages) {
                            androidx.compose.material3.MaterialTheme.colorScheme.primary
                        } else {
                            androidx.compose.material3.LocalContentColor.current
                        },
                    )
                }
                ComicReaderToolbarAction.BOOKMARK -> IconButton(onClick = onClickBookmark) {
                    Icon(
                        if (bookmarked) Icons.Outlined.Bookmark else Icons.Outlined.BookmarkBorder,
                        stringResource(MR.strings.action_bookmark),
                    )
                }
            }
        }

        IconButton(onClick = onClickSettings) {
            Icon(
                imageVector = Icons.Outlined.Settings,
                contentDescription = stringResource(MR.strings.action_settings),
                tint = if (settingsSelected) {
                    androidx.compose.material3.MaterialTheme.colorScheme.primary
                } else {
                    androidx.compose.material3.LocalContentColor.current
                },
            )
        }
    }
}
