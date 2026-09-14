package koharia.connection

import koharia.connection.ui.SeriesMetadataEditScreen
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.ObjectInputStream
import java.io.ObjectOutputStream

class SeriesMetadataScreenSerializationTest {
    @Test
    fun `screen survives Java serialization with only persistent identity`() {
        val screen = SeriesMetadataEditScreen(42L)
        val bytes = ByteArrayOutputStream().also { output ->
            ObjectOutputStream(output).use { it.writeObject(screen) }
        }.toByteArray()
        val restored = ObjectInputStream(ByteArrayInputStream(bytes)).use { it.readObject() }
        val id = SeriesMetadataEditScreen::class.java.getDeclaredField("mangaId").apply { isAccessible = true }
        assertEquals(42L, id.get(restored))
    }
}
