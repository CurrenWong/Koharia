package eu.kanade.tachiyomi.data.updater

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import tachiyomi.core.common.preference.InMemoryPreferenceStore

class UpdatePreferencesTest {
    @Test
    fun `automatic checks and reminders default on and remain independent`() {
        val preferences = UpdatePreferences(InMemoryPreferenceStore())
        assertTrue(preferences.autoCheckUpdates.get())
        assertTrue(preferences.showUpdateReminders.get())
        preferences.autoCheckUpdates.set(false)
        assertFalse(preferences.autoCheckUpdates.get())
        assertTrue(preferences.showUpdateReminders.get())
    }
}
