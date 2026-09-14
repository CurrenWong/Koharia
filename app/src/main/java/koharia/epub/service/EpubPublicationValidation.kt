package koharia.epub.service

import org.readium.r2.shared.publication.Publication

internal fun Publication.requireReadableEpub(message: String) {
    if (readingOrder.isNotEmpty()) return
    try {
        throw IllegalArgumentException(message)
    } finally {
        close()
    }
}
