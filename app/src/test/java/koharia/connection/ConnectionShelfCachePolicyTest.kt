package koharia.connection

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ConnectionShelfCachePolicyTest {
    @Test
    fun `startup and resume retain valid caches including empty catalogues`() {
        assertFalse(ConnectionShelfCachePolicy.shouldRefresh(hasCache = true))
        assertTrue(ConnectionShelfCachePolicy.shouldRefresh(hasCache = false))
        assertTrue(ConnectionShelfCachePolicy.shouldRefresh(hasCache = true, explicitUpdate = true))
    }
}
