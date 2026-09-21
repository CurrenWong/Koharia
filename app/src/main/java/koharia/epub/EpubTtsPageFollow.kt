package koharia.epub

import koharia.tts.progress.TtsProgressNotifier
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

internal fun EpubReaderUiState.ttsPageFollowLocation(
    visiblePage: Pair<String, Int>?,
    resourceProgression: Pair<String, Double>?,
): EpubTtsPageFollow.Location {
    fun matches(href: String): Boolean {
        val current = currentHref.orEmpty().substringBefore('#').substringBefore('?').trimStart('/')
        val reported = href.substringBefore('#').substringBefore('?').trimStart('/')
        return current.isNotEmpty() && reported.isNotEmpty() &&
            (current == reported || current.endsWith("/$reported") || reported.endsWith("/$current"))
    }
    return EpubTtsPageFollow.Location(
        currentHref,
        visiblePage?.takeIf { matches(it.first) }?.second ?: currentVisualPage,
        currentPosition,
        resourceProgression?.takeIf { matches(it.first) }?.second ?: progression,
    )
}

/** Follows explicit user navigation only, never locator changes caused by TTS highlighting. */
internal class EpubTtsPageFollow(
    private val scope: CoroutineScope,
    private val currentSession: () -> TtsProgressNotifier.Session?,
    private val follow: suspend (TtsProgressNotifier.Session) -> Unit,
    private val onPendingChanged: (TtsProgressNotifier.Session, Boolean) -> Unit = { _, _ -> },
    private val onCompletionReady: (TtsProgressNotifier.Session) -> Unit = {},
) {
    data class Location(val href: String?, val page: Int?, val position: Int, val progression: Double)

    private val mutablePending = MutableStateFlow(false)
    val pending = mutablePending.asStateFlow()
    private var origin: Location? = null
    private var target: Location? = null
    private var session: TtsProgressNotifier.Session? = null
    private var generation = 0L
    private var job: Job? = null
    private var dragging = false
    private var deferredCompletion: TtsProgressNotifier.Session? = null

    fun begin(session: TtsProgressNotifier.Session, location: Location, dragging: Boolean = false) {
        val previousOrigin = origin.takeIf { this.session == session && mutablePending.value }
        val completion = deferredCompletion.takeIf { it == session }
        cancel()
        this.session = session
        this.dragging = dragging
        deferredCompletion = completion
        origin = previousOrigin ?: location
        mutablePending.value = true
        onPendingChanged(session, true)
        if (!dragging) awaitNavigation()
    }

    fun endDrag() {
        if (!dragging) return
        dragging = false
        val location = target
        if (location != null) settle(location) else awaitNavigation()
    }

    fun deferCompletion(session: TtsProgressNotifier.Session): Boolean {
        if (!mutablePending.value || this.session != session) return false
        deferredCompletion = session
        return true
    }

    private fun awaitNavigation() {
        job?.cancel()
        job = scope.launch {
            // Allow resource loading and page settling; never expire an active drag.
            delay(5_000)
            finish()
        }
    }

    fun moved(location: Location) {
        if (session == null) return
        if (location == origin && target == null) return
        if (location == target) return
        target = location
        if (!dragging) settle(location)
    }

    private fun settle(location: Location) {
        val expected = session ?: return
        val request = ++generation
        job?.cancel()
        job = scope.launch {
            try {
                delay(250)
                if (request == generation && location != origin && currentSession() == expected) follow(expected)
            } finally {
                if (request == generation) finish()
            }
        }
    }

    private fun finish() {
        val completion = deferredCompletion
        cancel()
        if (completion != null && currentSession() == completion) onCompletionReady(completion)
    }

    fun cancel() {
        generation++
        job?.cancel()
        job = null
        origin = null
        target = null
        session?.let { onPendingChanged(it, false) }
        session = null
        dragging = false
        deferredCompletion = null
        mutablePending.value = false
    }
}
