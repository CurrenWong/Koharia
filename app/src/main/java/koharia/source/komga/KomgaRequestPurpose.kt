package koharia.source.komga

import okhttp3.Request

internal enum class KomgaRequestPurpose {
    ProgressSync,
}

internal fun Request.Builder.komgaProgressSync(): Request.Builder =
    tag(KomgaRequestPurpose::class.java, KomgaRequestPurpose.ProgressSync)

internal val Request.isKomgaProgressSync: Boolean
    get() = tag(KomgaRequestPurpose::class.java) == KomgaRequestPurpose.ProgressSync
