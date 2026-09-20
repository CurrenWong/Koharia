package eu.kanade.tachiyomi.ui.reader.setting

enum class ComicReaderToolbarAction(val id: String) {
    READING_MODE("reading_mode"),
    ORIENTATION("orientation"),
    CROP("crop"),
    BRIGHTNESS("brightness"),
    BACKGROUND("background"),
    PAGE_LAYOUT("page_layout"),
    SHIFT_DOUBLE_PAGES("shift_double_pages"),
    BOOKMARK("bookmark"),
}

enum class EpubReaderToolbarAction(val id: String) {
    CONTENTS("contents"),
    NIGHT_MODE("night_mode"),
    FONT("font"),
    BRIGHTNESS("brightness"),
    SEARCH("search"),
    TTS("tts"),
    ORIENTATION("orientation"),
    MORE("more"),
}

internal object ReaderToolbarActions {
    const val MAX_OPTIONAL_ACTIONS = 4

    val defaultComic = listOf(
        ComicReaderToolbarAction.READING_MODE,
        ComicReaderToolbarAction.ORIENTATION,
        ComicReaderToolbarAction.CROP,
    )
    val defaultEpub = listOf(
        EpubReaderToolbarAction.CONTENTS,
        EpubReaderToolbarAction.NIGHT_MODE,
        EpubReaderToolbarAction.MORE,
    )

    fun comic(value: String): List<ComicReaderToolbarAction> = parse(
        value = value,
        entries = ComicReaderToolbarAction.entries,
        id = ComicReaderToolbarAction::id,
        defaults = defaultComic,
    )

    fun epub(value: String): List<EpubReaderToolbarAction> = parse(
        value = value,
        entries = EpubReaderToolbarAction.entries,
        id = EpubReaderToolbarAction::id,
        defaults = defaultEpub,
    )

    fun encode(actions: List<ComicReaderToolbarAction>): String = actions
        .distinct()
        .take(MAX_OPTIONAL_ACTIONS)
        .joinToString(",", transform = ComicReaderToolbarAction::id)

    fun encodeEpub(actions: List<EpubReaderToolbarAction>): String = actions
        .distinct()
        .take(MAX_OPTIONAL_ACTIONS)
        .joinToString(",", transform = EpubReaderToolbarAction::id)

    private fun <T> parse(
        value: String,
        entries: List<T>,
        id: (T) -> String,
        defaults: List<T>,
    ): List<T> {
        if (value.isBlank()) return emptyList()
        val byId = entries.associateBy(id)
        return value.split(',')
            .mapNotNull { byId[it] }
            .distinct()
            .take(MAX_OPTIONAL_ACTIONS)
            .ifEmpty { defaults }
    }
}
