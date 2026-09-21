package koharia.diagnostics

import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import eu.kanade.presentation.reader.ReaderStatusIndicator
import eu.kanade.presentation.theme.TachiyomiTheme
import eu.kanade.tachiyomi.ui.reader.setting.ReaderStatusPosition
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class ReaderStatusStyleDeviceTest {
    @Test
    fun statusIsVisibleButExcludedFromPageSnapshotsAndDoesNotInterceptTouches(): Unit = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        assertEquals("app.koharia.dev.devicefixture", instrumentation.targetContext.packageName)
        val clicks = java.util.concurrent.atomic.AtomicInteger()
        ActivityScenario.launch(ComponentActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                activity.setContent {
                    TachiyomiTheme {
                        Box(Modifier.fillMaxSize().background(Color.White).clickable { clicks.incrementAndGet() }) {
                            ReaderStatusIndicator(
                                "Status Layer",
                                12,
                                100,
                                showStatus = true,
                                showClock = false,
                                showBattery = false,
                                contentColor = Color.Red,
                            )
                        }
                    }
                }
            }
            instrumentation.waitForIdleSync()
            delay(800)
            val snapshot = kotlinx.coroutines.CompletableDeferred<android.graphics.Bitmap>()
            scenario.onActivity { activity ->
                val decor = activity.window.decorView
                val bitmap = android.graphics.Bitmap.createBitmap(
                    decor.width,
                    decor.height,
                    android.graphics.Bitmap.Config.ARGB_8888,
                )
                android.view.PixelCopy.request(activity.window, bitmap, { result ->
                    if (result == android.view.PixelCopy.SUCCESS) {
                        snapshot.complete(bitmap)
                    } else {
                        bitmap.recycle()
                        snapshot.completeExceptionally(AssertionError("PixelCopy $result"))
                    }
                }, android.os.Handler(android.os.Looper.getMainLooper()))
            }
            val copied = kotlinx.coroutines.withTimeout(5_000) { snapshot.await() }
            val visible = instrumentation.uiAutomation.takeScreenshot()!!
            try {
                org.junit.Assert.assertTrue("Status must be visible on screen", redPixels(visible) > 0)
                assertEquals("Animation snapshot must contain only the page", 0, redPixels(copied))
            } finally {
                copied.recycle()
                visible.recycle()
            }
            val time = android.os.SystemClock.uptimeMillis()
            for (action in listOf(android.view.MotionEvent.ACTION_DOWN, android.view.MotionEvent.ACTION_UP)) {
                val event = android.view.MotionEvent.obtain(time, time + action * 50L, action, 100f, 300f, 0)
                event.source = android.view.InputDevice.SOURCE_TOUCHSCREEN
                try {
                    instrumentation.uiAutomation.injectInputEvent(event, true)
                } finally {
                    event.recycle()
                }
            }
            instrumentation.waitForIdleSync()
            assertEquals("Status window must pass touches to the reader", 1, clicks.get())
        }
    }

    private fun redPixels(bitmap: android.graphics.Bitmap): Int {
        val pixels = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        return pixels.count {
            android.graphics.Color.red(it) > 180 && android.graphics.Color.green(it) < 160 &&
                android.graphics.Color.blue(it) < 160
        }
    }

    @Test
    fun captureLightDarkAndLongTitleStatus(): Unit = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        assertEquals("app.koharia.dev.devicefixture", context.packageName)
        ActivityScenario.launch(ComponentActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                activity.setContent {
                    TachiyomiTheme {
                        Column {
                            for ((background, foreground, title) in listOf(
                                Triple(Color(0xFFF1EBDD), Color.Black, "第一章 · 清晨"),
                                Triple(Color(0xFF202125), Color.White, "第二章 · 夜行"),
                                Triple(Color.White, Color.Black, "这是一个用于验证截断后仍然保留页数的很长的章节名称"),
                            )) {
                                Box(Modifier.fillMaxWidth().height(190.dp).background(background)) {
                                    ReaderStatusIndicator(title, 10, 378, showStatus = true, contentColor = foreground)
                                }
                            }
                            Box(Modifier.fillMaxWidth().height(160.dp).background(Color(0xFFF1EBDD))) {
                                ReaderStatusIndicator(
                                    null,
                                    4,
                                    20,
                                    visiblePageStart = 3,
                                    showStatus = true,
                                    position = ReaderStatusPosition.TOP,
                                    contentColor = Color.Black,
                                )
                            }
                        }
                    }
                }
            }
            instrumentation.waitForIdleSync()
            delay(800)
            val screenshot = instrumentation.uiAutomation.takeScreenshot()
            assertNotNull(screenshot)
            val file = File(context.getExternalFilesDir(null), "reader-status-style.png")
            file.outputStream().use { screenshot!!.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
            screenshot?.recycle()
        }
    }
}
