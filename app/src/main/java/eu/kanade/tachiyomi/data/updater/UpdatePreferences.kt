package eu.kanade.tachiyomi.data.updater

import tachiyomi.core.common.preference.Preference
import tachiyomi.core.common.preference.PreferenceStore

class UpdatePreferences(preferenceStore: PreferenceStore) {
    val autoCheckUpdates: Preference<Boolean> = preferenceStore.getBoolean("auto_check_app_updates", true)
    val showUpdateReminders: Preference<Boolean> = preferenceStore.getBoolean("show_app_update_reminders", true)
}
