package koharia.epub

import io.kotest.matchers.shouldBe
import koharia.tts.progress.TtsProgressNotifier
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

class EpubTtsPageFollowTest {
    private val session = TtsProgressNotifier.Session(1, 2, "first")
    private val first = EpubTtsPageFollow.Location("chapter.xhtml", 1, 1, 0.0)
    private val second = first.copy(page = 2)

    @Test
    fun `reported viewport drives follow before whole book pagination is ready`() = runTest {
        val state = EpubReaderUiState(currentHref = "chapter.xhtml")
        var calls = 0
        val follow = EpubTtsPageFollow(this, { session }, { calls++ })
        follow.begin(session, state.ttsPageFollowLocation("chapter.xhtml" to 0, "chapter.xhtml" to 0.0))
        follow.moved(state.ttsPageFollowLocation("https://book/chapter.xhtml" to 1, "chapter.xhtml" to 0.2))
        advanceTimeBy(250)
        runCurrent()
        calls shouldBe 1
    }

    @Test
    fun `reports from another resource cannot replace the current location`() {
        val state = EpubReaderUiState(currentHref = "chapter.xhtml", currentVisualPage = 10, progression = 0.4)
        state.ttsPageFollowLocation("previous.xhtml" to 7, "previous.xhtml" to 0.9) shouldBe
            EpubTtsPageFollow.Location("chapter.xhtml", 10, 1, 0.4)
    }

    @Test
    fun `automatic locator updates never restart narration`() = runTest {
        val follows = mutableListOf<TtsProgressNotifier.Session>()
        val follow = EpubTtsPageFollow(this, { session }, { follows.add(it) })
        follow.moved(second)
        advanceTimeBy(2000)
        follows shouldBe emptyList()
    }

    @Test
    fun `rapid manual turns settle once at the last viewport`() = runTest {
        val pages = mutableListOf<Int?>()
        var visible = first
        val follow = EpubTtsPageFollow(this, { session }, { pages.add(visible.page) })
        follow.begin(session, first)
        visible = second
        follow.moved(visible)
        advanceTimeBy(200)
        follow.begin(session, visible)
        visible = second.copy(page = 3)
        follow.moved(visible)
        advanceTimeBy(250)
        runCurrent()
        pages shouldBe listOf(3)
        follow.pending.value shouldBe false
    }

    @Test
    fun `cancelled drag and a return to the original page do not restart`() = runTest {
        var calls = 0
        val follow = EpubTtsPageFollow(this, { session }, { calls++ })
        follow.begin(session, first)
        advanceTimeBy(5000)
        runCurrent()
        follow.pending.value shouldBe false
        follow.begin(session, first)
        follow.moved(second)
        advanceTimeBy(100)
        follow.moved(first)
        advanceTimeBy(250)
        runCurrent()
        calls shouldBe 0
    }

    @Test
    fun `duplicate locator events do not postpone the settled page`() = runTest {
        var calls = 0
        val follow = EpubTtsPageFollow(this, { session }, { calls++ })
        follow.begin(session, first)
        follow.moved(second)
        advanceTimeBy(200)
        follow.moved(second)
        advanceTimeBy(50)
        runCurrent()
        calls shouldBe 1
    }

    @Test
    fun `stop or a replacement session invalidates a pending follow`() = runTest {
        var active = session
        var calls = 0
        val follow = EpubTtsPageFollow(this, { active }, { calls++ })
        follow.begin(session, first)
        follow.moved(second)
        follow.cancel()
        advanceTimeBy(500)
        follow.begin(session, first)
        follow.moved(second)
        active = session.copy(token = "replacement")
        advanceTimeBy(500)
        calls shouldBe 0
        follow.pending.value shouldBe false
    }

    @Test
    fun `highlights stay suppressed through asynchronous text extraction`() = runTest {
        var calls = 0
        val follow = EpubTtsPageFollow(this, { session }, {
            delay(500)
            calls++
        })
        follow.begin(session, first)
        follow.moved(second)
        advanceTimeBy(300)
        follow.pending.value shouldBe true
        follow.cancel()
        advanceTimeBy(1000)
        calls shouldBe 0
        follow.pending.value shouldBe false
    }

    @Test
    fun `slow drag keeps pending until release and follows the settled page`() = runTest {
        var calls = 0
        val follow = EpubTtsPageFollow(this, { session }, { calls++ })
        follow.begin(session, first, dragging = true)
        advanceTimeBy(10_000)
        follow.pending.value shouldBe true
        follow.moved(second)
        advanceTimeBy(1000)
        calls shouldBe 0
        follow.endDrag()
        advanceTimeBy(250)
        runCurrent()
        calls shouldBe 1
        follow.pending.value shouldBe false
    }

    @Test
    fun `cancelled drag delivers deferred completion exactly once`() = runTest {
        val completions = mutableListOf<TtsProgressNotifier.Session>()
        val notifier = TtsProgressNotifier()
        notifier.bind(first.href!!, emptyList(), session)
        val follow = EpubTtsPageFollow(
            this,
            { session },
            {},
            notifier::setNavigationPending,
            { completions.add(it) },
        )
        follow.begin(session, first, dragging = true)
        follow.deferCompletion(session) shouldBe true
        advanceTimeBy(2000)
        notifier.pendingNavigation.value shouldBe session
        follow.endDrag()
        advanceTimeBy(5000)
        runCurrent()
        completions shouldBe listOf(session)
        notifier.pendingNavigation.value shouldBe null
    }

    @Test
    fun `stopping during navigation discards deferred completion`() = runTest {
        var completions = 0
        val follow = EpubTtsPageFollow(this, { session }, {}, onCompletionReady = { completions++ })
        follow.begin(session, first, dragging = true)
        follow.deferCompletion(session) shouldBe true
        follow.cancel()
        follow.endDrag()
        advanceTimeBy(10_000)
        completions shouldBe 0
    }

    @Test
    fun `successful manual follow discards completion of the replaced session`() = runTest {
        var active = session
        var completions = 0
        val follow = EpubTtsPageFollow(
            this,
            { active },
            { active = session.copy(token = "new") },
            onCompletionReady = { completions++ },
        )
        follow.begin(session, first)
        follow.deferCompletion(session) shouldBe true
        follow.moved(second)
        advanceTimeBy(250)
        runCurrent()
        completions shouldBe 0
    }
}
