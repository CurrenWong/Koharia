package koharia.tts

/** Supported speech providers and their voice catalogues. */
sealed class TtsVendor(
    val id: String,

    val displayName: String,

    val needsApiKey: Boolean,

    val apiKeyHintUrl: String? = null,
) {
    /** Lookups read current credentials for each synthesis request. */
    abstract fun createEngine(
        apiKeyLookup: () -> String?,
        baseUrlLookup: () -> String?,
    ): TtsEngine

    abstract fun presetVoices(): List<Voice>

    abstract fun defaultVoiceId(): String

    data object Mimo : TtsVendor(
        id = "mimo",
        displayName = "MiMo TTS",
        needsApiKey = true,
        apiKeyHintUrl = "https://platform.xiaomimimo.com?ref=RD7JZG",
    ) {
        override fun createEngine(apiKeyLookup: () -> String?, baseUrlLookup: () -> String?): TtsEngine =
            MimoEngine(
                apiKeyProvider = apiKeyLookup,
                baseUrlProvider = { baseUrlLookup() ?: MimoEngine.DEFAULT_BASE_URL },
            )

        override fun presetVoices(): List<Voice> = MimoEngine.PRESET_VOICES

        override fun defaultVoiceId(): String = MimoEngine.DEFAULT_VOICE_ID
    }

    data object Edge : TtsVendor(
        id = "edge",
        displayName = "Microsoft Edge TTS",
        needsApiKey = false,
        apiKeyHintUrl = null,
    ) {
        override fun createEngine(apiKeyLookup: () -> String?, baseUrlLookup: () -> String?): TtsEngine {
            return EdgeEngine()
        }

        override fun presetVoices(): List<Voice> = EdgeEngine.PRESET_VOICES

        override fun defaultVoiceId(): String = EdgeEngine.DEFAULT_VOICE_ID
    }

    companion object {
        // Lazy initialization avoids nested object initialization cycles.
        val ALL: List<TtsVendor> by lazy { listOf(Edge, Mimo) }

        val DEFAULT: TtsVendor by lazy { Edge }

        fun fromId(id: String?): TtsVendor = ALL.firstOrNull { it.id == id } ?: DEFAULT
    }
}
