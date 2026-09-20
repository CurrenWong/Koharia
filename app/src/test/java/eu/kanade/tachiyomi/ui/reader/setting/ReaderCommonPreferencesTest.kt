package eu.kanade.tachiyomi.ui.reader.setting

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import tachiyomi.core.common.preference.InMemoryPreferenceStore

class ReaderCommonPreferencesTest {
    @Test
    fun `reading status keeps the existing visibility key and adds independent common options`() {
        val store = InMemoryPreferenceStore()
        val preferences = ReaderPreferences(store)

        assertFalse(preferences.showPageNumber.get())
        assertEquals(ReaderStatusPosition.BOTTOM, preferences.readerStatusPosition.get())
        assertTrue(preferences.showReaderChapterTitle.get())
        assertTrue(preferences.showReaderClock.get())
        assertTrue(preferences.showReaderBattery.get())

        preferences.showPageNumber.set(true)
        assertTrue(preferences.showPageNumber.get())
    }

    @Test
    fun `comic custom background keeps legacy theme values and stores a selected color`() {
        val preferences = ReaderPreferences(InMemoryPreferenceStore())

        assertEquals(1, preferences.readerTheme.get())
        preferences.readerCustomBackgroundColor.set(0xFFCCBBAA.toInt())
        preferences.readerTheme.set(ReaderPreferences.CUSTOM_BACKGROUND_THEME)

        assertEquals(ReaderPreferences.CUSTOM_BACKGROUND_THEME, preferences.readerTheme.get())
        assertEquals(0xFFCCBBAA.toInt(), preferences.readerCustomBackgroundColor.get())
    }
}
