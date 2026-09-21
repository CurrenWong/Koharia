package koharia.tts

import logcat.LogPriority
import tachiyomi.core.common.preference.Preference
import tachiyomi.core.common.preference.PreferenceStore
import tachiyomi.core.common.util.system.logcat

/** Reader settings shared across connections; voices retain a separate selection for each vendor. */
class TtsPreferences(preferenceStore: PreferenceStore) {

    val speedTenths: Preference<Int> = preferenceStore.getInt(KEY_SPEED_TENTHS, DEFAULT_SPEED_TENTHS)

    fun speed(): Float = speedTenths.get().coerceIn(MIN_SPEED_TENTHS, MAX_SPEED_TENTHS) / 10f

    val vendorId: Preference<String> = preferenceStore.getString(KEY_VENDOR_ID, DEFAULT_VENDOR_ID)

    val disclosureAcknowledged: Preference<Boolean> =
        preferenceStore.getBoolean(KEY_DISCLOSURE_ACK, false)

    private val voiceIdPrefs: Map<String, Preference<String>> =
        TtsVendor.ALL.associate { vendor ->
            vendor.id to preferenceStore.getString(
                KEY_VOICE_ID_PREFIX + vendor.id,
                vendor.defaultVoiceId(),
            )
        }

    init {
        migrateLegacyVoiceId(preferenceStore)
    }

    fun voiceIdFor(vendorId: String = this.vendorId.get()): Preference<String> =
        voiceIdPrefs.getValue(TtsVendor.fromId(vendorId).id)

    fun validVoiceIds(): Set<String> =
        TtsVendor.fromId(vendorId.get()).presetVoices().mapTo(mutableSetOf()) { it.id }

    private fun migrateLegacyVoiceId(store: PreferenceStore) {
        val legacy = store.getString(KEY_VOICE_ID_LEGACY, "")
        if (!legacy.isSet()) return

        val value = legacy.get()
        val owner = TtsVendor.ALL.firstOrNull { vendor ->
            vendor.presetVoices().any { it.id == value }
        }

        if (owner != null) {
            voiceIdPrefs[owner.id]?.set(value)
            logcat(LogPriority.INFO) {
                "[TtsPreferences] migrated legacy voice '$value' -> vendor '${owner.id}'"
            }
        } else {
            logcat(LogPriority.WARN) {
                "[TtsPreferences] dropping legacy voice '$value' (no vendor claims it)"
            }
        }
        legacy.delete()
    }

    companion object {
        const val MIN_SPEED_TENTHS = 5

        const val MAX_SPEED_TENTHS = 20

        const val DEFAULT_SPEED_TENTHS = 10

        private const val KEY_SPEED_TENTHS = "tts_speed_tenths"

        const val DEFAULT_VENDOR_ID = "edge"
        private const val KEY_VENDOR_ID = "tts_vendor_id"

        private const val KEY_DISCLOSURE_ACK = "tts_data_disclosure_acknowledged"

        const val DEFAULT_VOICE_ID = EdgeEngine.DEFAULT_VOICE_ID

        private const val KEY_VOICE_ID_LEGACY = "tts_voice_id"

        private const val KEY_VOICE_ID_PREFIX = "tts_voice_id_"

        @Deprecated(
            "Use validVoiceIds() — vendor-aware set",
            ReplaceWith("TtsPreferences(preferenceStore).validVoiceIds()"),
        )
        val VALID_VOICE_IDS: Set<String> = MimoEngine.PRESET_VOICES.mapTo(mutableSetOf()) { it.id }
    }
}
