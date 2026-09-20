package koharia.lanraragi

import koharia.domain.lanraragi.LanraragiEntry

enum class LanraragiArchiveOpenMode { READER, PAGE_PREVIEW, DETAILS }

enum class LanraragiEntryDestination { READER, PAGE_PREVIEW, DETAILS }

fun lanraragiEntryDestination(
    url: String,
    sourceId: Long,
    mode: LanraragiArchiveOpenMode,
    longClick: Boolean = false,
): LanraragiEntryDestination {
    val archivePrefix = "/lanraragi/$sourceId/${LanraragiEntry.Kind.ARCHIVE.name.lowercase()}/"
    if (!url.startsWith(archivePrefix)) return LanraragiEntryDestination.DETAILS
    if (longClick) return LanraragiEntryDestination.PAGE_PREVIEW
    return when (mode) {
        LanraragiArchiveOpenMode.READER -> LanraragiEntryDestination.READER
        LanraragiArchiveOpenMode.PAGE_PREVIEW -> LanraragiEntryDestination.PAGE_PREVIEW
        LanraragiArchiveOpenMode.DETAILS -> LanraragiEntryDestination.DETAILS
    }
}
