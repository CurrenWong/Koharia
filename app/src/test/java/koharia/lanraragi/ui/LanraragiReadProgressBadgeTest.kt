package koharia.lanraragi.ui

import eu.kanade.presentation.library.components.MangaReadProgressDisplay
import koharia.domain.lanraragi.LanraragiEntry
import koharia.domain.lanraragi.LanraragiReadState
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class LanraragiReadProgressBadgeTest {
    @Test
    fun `archive uses pending local percentage and tank counts completed members`() {
        val first = LanraragiEntry(
            id = "a",
            kind = LanraragiEntry.Kind.ARCHIVE,
            title = "A",
            pageCount = 10,
            progress = 1,
        )
        val second = LanraragiEntry(
            id = "b",
            kind = LanraragiEntry.Kind.ARCHIVE,
            title = "B",
            pageCount = 5,
            progress = 5,
        )
        val tank = LanraragiEntry(
            id = "t",
            kind = LanraragiEntry.Kind.TANK,
            title = "Tank",
            members = listOf("a", "b"),
        )
        val result = lanraragiReadProgress(
            42L,
            listOf(first, second, tank),
            listOf(LanraragiReadState("a", pageIndex = 4, totalPages = 10, readAt = 10, pending = true)),
        )

        assertEquals(50, result.getValue("/lanraragi/42/archive/a").readCount)
        assertEquals(MangaReadProgressDisplay.PERCENTAGE, result.getValue("/lanraragi/42/archive/a").display)
        assertEquals(1, result.getValue("/lanraragi/42/tank/t").readCount)
        assertEquals(2, result.getValue("/lanraragi/42/tank/t").totalChapterCount)
    }
}
