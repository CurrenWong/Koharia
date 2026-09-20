package eu.kanade.presentation.reader

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.text.format.DateFormat
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BatteryChargingFull
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import eu.kanade.tachiyomi.ui.reader.loader.ReaderBufferingState
import eu.kanade.tachiyomi.ui.reader.setting.ReaderStatusPosition
import kotlinx.coroutines.delay
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.i18n.stringResource
import java.util.Date

internal data class ReaderBatteryState(val percent: Int, val charging: Boolean)

internal fun formatReaderTransferSpeed(bytesPerSecond: Long): String {
    val value = bytesPerSecond.coerceAtLeast(0L).toDouble()
    return when {
        value >= 1024.0 * 1024.0 -> "%.1f MB/s".format(value / (1024.0 * 1024.0))
        value >= 1024.0 -> "%.0f KB/s".format(value / 1024.0)
        else -> "${value.toLong()} B/s"
    }
}

@Composable
fun ReaderStatusIndicator(
    chapterTitle: String?,
    currentPage: Int,
    totalPages: Int,
    visiblePageStart: Int = currentPage,
    percentageOnly: Boolean = false,
    showStatus: Boolean,
    showChapterTitle: Boolean = true,
    showClock: Boolean = true,
    showBattery: Boolean = true,
    position: ReaderStatusPosition = ReaderStatusPosition.BOTTOM,
    modifier: Modifier = Modifier,
) {
    if (!showStatus) return
    val context = LocalContext.current
    var now by remember { mutableStateOf(Date()) }
    var battery by remember { mutableStateOf(context.currentBatteryState()) }

    LaunchedEffect(showStatus, showClock) {
        if (!showStatus || !showClock) return@LaunchedEffect
        while (true) {
            val wait = 60_000L - System.currentTimeMillis() % 60_000L
            delay(wait.coerceAtLeast(1_000L))
            now = Date()
        }
    }
    DisposableEffect(showStatus, showBattery) {
        if (!showStatus || !showBattery) return@DisposableEffect onDispose {}
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                battery = intent.toReaderBatteryState()
            }
        }
        context.registerReceiver(receiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        onDispose { runCatching { context.unregisterReceiver(receiver) } }
    }

    val pageText = if (showStatus) {
        readerPageIndicatorText(currentPage, totalPages, visiblePageStart, percentageOnly)
    } else {
        null
    }
    val leftText = readerStatusLeftText(chapterTitle, pageText, showChapterTitle)

    Box(modifier = modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .align(if (position == ReaderStatusPosition.TOP) Alignment.TopCenter else Alignment.BottomCenter)
                .fillMaxWidth()
                .windowInsetsPadding(
                    if (position == ReaderStatusPosition.TOP) WindowInsets.statusBars else WindowInsets.navigationBars,
                )
                .padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(modifier = Modifier.weight(1f)) {
                if (leftText.isNotEmpty()) {
                    StatusSurface(modifier = Modifier.widthIn(max = 320.dp)) {
                        Text(
                            text = leftText,
                            fontSize = 11.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
            if (showClock || showBattery) {
                StatusSurface {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(7.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (showClock) StatusText(DateFormat.getTimeFormat(context).format(now))
                        if (showBattery) {
                            battery?.let { value ->
                                if (value.charging) {
                                    Icon(
                                        imageVector = Icons.Outlined.BatteryChargingFull,
                                        contentDescription = null,
                                        modifier = Modifier.size(14.dp),
                                    )
                                }
                                StatusText("${value.percent}%")
                            }
                        }
                    }
                }
            }
        }
    }
}

internal fun readerStatusLeftText(
    chapterTitle: String?,
    pageText: String?,
    showChapterTitle: Boolean,
): String = listOfNotNull(
    chapterTitle?.takeIf { showChapterTitle && it.isNotBlank() },
    pageText,
).joinToString(" · ")

@Composable
fun ReaderBufferingIndicator(
    buffering: ReaderBufferingState,
    enabled: Boolean,
    modifier: Modifier = Modifier,
) {
    var displayedBuffering by remember { mutableStateOf(buffering) }
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(enabled, buffering) {
        if (enabled && buffering.isBuffering) {
            displayedBuffering = buffering
            visible = true
        } else if (visible) {
            delay(BUFFERING_LINGER_MILLIS)
            visible = false
        }
    }
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        AnimatedVisibility(
            visible = visible,
            modifier = Modifier.offset(y = 40.dp),
            enter = fadeIn(),
            exit = fadeOut(tween(durationMillis = BUFFERING_FADE_MILLIS)),
        ) {
            StatusSurface {
                StatusText(
                    if (displayedBuffering.bytesPerSecond > 0L) {
                        stringResource(
                            MR.strings.reader_buffering_speed,
                            formatReaderTransferSpeed(displayedBuffering.bytesPerSecond),
                        )
                    } else {
                        stringResource(MR.strings.reader_buffering_waiting)
                    },
                )
            }
        }
    }
}

@Composable
private fun StatusSurface(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.extraLarge,
        color = Color.Black.copy(alpha = 0.32f),
        contentColor = Color.White.copy(alpha = 0.9f),
    ) {
        Box(modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)) { content() }
    }
}

@Composable
private fun StatusText(text: String) {
    Text(text = text, fontSize = 11.sp, maxLines = 1)
}

private fun Context.currentBatteryState(): ReaderBatteryState? =
    registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))?.toReaderBatteryState()

private fun Intent.toReaderBatteryState(): ReaderBatteryState? {
    val level = getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
    val scale = getIntExtra(BatteryManager.EXTRA_SCALE, -1)
    if (level < 0 || scale <= 0) return null
    val status = getIntExtra(BatteryManager.EXTRA_STATUS, BatteryManager.BATTERY_STATUS_UNKNOWN)
    return ReaderBatteryState(
        percent = (level * 100 / scale).coerceIn(0, 100),
        charging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
            status == BatteryManager.BATTERY_STATUS_FULL,
    )
}

private const val BUFFERING_LINGER_MILLIS = 650L
private const val BUFFERING_FADE_MILLIS = 350
