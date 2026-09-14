package koharia.epub.service

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.readium.r2.shared.publication.Link
import org.readium.r2.shared.publication.Publication

class EpubPublicationValidationTest {
    @Test
    fun `empty publication is closed before navigator creation`() {
        val publication = mockk<Publication>(relaxed = true)
        every { publication.readingOrder } returns emptyList()
        assertThrows(IllegalArgumentException::class.java) { publication.requireReadableEpub("empty") }
        verify(exactly = 1) { publication.close() }
    }

    @Test
    fun `readable publication remains open`() {
        val publication = mockk<Publication>(relaxed = true)
        every { publication.readingOrder } returns listOf(mockk<Link>())
        publication.requireReadableEpub("empty")
        verify(exactly = 0) { publication.close() }
    }
}
