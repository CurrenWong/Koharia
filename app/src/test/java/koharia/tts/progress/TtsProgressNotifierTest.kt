package koharia.tts.progress

import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class TtsProgressNotifierTest {

    private val session = TtsProgressNotifier.Session(chapterId = 1, mangaId = 1, token = "session-1")

    private fun makeSentences(vararg ends: Int): List<TtsProgressNotifier.SentenceRef> =
        ends.mapIndexed { i, end ->
            val start = if (i == 0) 0 else ends[i - 1]
            TtsProgressNotifier.SentenceRef(index = i, startOffset = start, endOffset = end)
        }

    @Test
    fun `initial state is empty and unbound`() {
        val n = TtsProgressNotifier()
        n.isBound shouldBe false
        n.progress.value.currentIndex shouldBe -1
        n.progress.value.sentences shouldHaveSize 0
    }

    @Test
    fun `bind with sentences sets chapter and resets currentIndex to zero`() {
        val n = TtsProgressNotifier()
        val sents = makeSentences(5, 10, 20)

        n.bind("ch1", sents, session)

        n.isBound shouldBe true
        n.progress.value.chapterHref shouldBe "ch1"
        n.progress.value.session shouldBe session
        n.progress.value.sentences shouldHaveSize 3
        n.progress.value.currentIndex shouldBe 0
    }

    @Test
    fun `bind with empty sentences leaves currentIndex at minus one`() {
        val n = TtsProgressNotifier()
        n.bind("ch1", emptyList(), session)

        n.isBound shouldBe false
        n.progress.value.currentIndex shouldBe -1
    }

    @Test
    fun `setCurrent updates the current index`() {
        val n = TtsProgressNotifier()
        val sents = makeSentences(5, 10, 20)
        n.bind("ch1", sents, session)

        n.setCurrent(1)
        n.progress.value.currentIndex shouldBe 1

        n.setCurrent(2)
        n.progress.value.currentIndex shouldBe 2
    }

    @Test
    fun `setCurrent clamps negative to minus one sentinel and out-of-range positive to lastIndex`() {
        val n = TtsProgressNotifier()
        val sents = makeSentences(5, 10, 20)
        n.bind("ch1", sents, session)

        // 负数 → -1（"未开始"哨兵，与 Progress.fraction 的零路径对齐）
        n.setCurrent(-5)
        n.progress.value.currentIndex shouldBe -1

        // 超过 lastIndex → lastIndex
        n.setCurrent(99)
        n.progress.value.currentIndex shouldBe 2

        // 合法值
        n.setCurrent(1)
        n.progress.value.currentIndex shouldBe 1
    }

    @Test
    fun `setCurrent is a no-op when currentIndex does not change`() {
        val n = TtsProgressNotifier()
        val sents = makeSentences(5, 10, 20)
        n.bind("ch1", sents, session)
        n.setCurrent(1)
        // After setCurrent(1) we should be at 1; calling again with same value shouldn't crash
        n.setCurrent(1)
        n.progress.value.currentIndex shouldBe 1
    }

    @Test
    fun `setCurrent on empty sentences is a no-op`() {
        val n = TtsProgressNotifier()
        n.setCurrent(5) // should not throw
        n.progress.value.currentIndex shouldBe -1
        n.progress.value.sentences shouldHaveSize 0
    }

    @Test
    fun `clear resets to unbound empty state`() {
        val n = TtsProgressNotifier()
        n.bind("ch1", makeSentences(5, 10), session)
        n.setCurrent(1)

        n.clear()

        n.isBound shouldBe false
        n.progress.value.chapterHref shouldBe ""
        n.progress.value.session shouldBe null
        n.progress.value.currentIndex shouldBe -1
    }

    @Test
    fun `Progress currentStartOffset and currentEndOffset return sentence bounds`() {
        val n = TtsProgressNotifier()
        val sents = makeSentences(5, 12, 25)
        n.bind("ch1", sents, session)

        n.setCurrent(1)
        n.progress.value.currentStartOffset shouldBe 5
        n.progress.value.currentEndOffset shouldBe 12

        n.setCurrent(0)
        n.progress.value.currentStartOffset shouldBe 0
        n.progress.value.currentEndOffset shouldBe 5
    }

    @Test
    fun `Progress fraction increases monotonically across sentences`() {
        val n = TtsProgressNotifier()
        val sents = makeSentences(10, 20, 40)
        n.bind("ch1", sents, session)

        n.setCurrent(0)
        val f0 = n.progress.value.fraction

        n.setCurrent(1)
        val f1 = n.progress.value.fraction

        n.setCurrent(2)
        val f2 = n.progress.value.fraction

        (f1 > f0) shouldBe true
        (f2 > f1) shouldBe true
        f2 shouldBe 1.0
    }

    @Test
    fun `Progress fraction is zero when no sentence is bound`() {
        val n = TtsProgressNotifier()
        n.progress.value.fraction shouldBe 0.0
    }

    @Test
    fun `Progress fraction is zero when currentIndex is minus one sentinel`() {
        // -1 是"未开始"哨兵；setCurrent(-1) 保留为 -1 → fraction 走 0.0 短路
        val n = TtsProgressNotifier()
        val sents = makeSentences(5, 10)
        n.bind("ch1", sents, session)
        n.setCurrent(-1)
        n.progress.value.currentIndex shouldBe -1
        n.progress.value.fraction shouldBe 0.0
    }

    @Test
    fun `setCurrent clamps out-of-range positive values to lastIndex`() {
        val n = TtsProgressNotifier()
        val sents = makeSentences(5, 10, 20)
        n.bind("ch1", sents, session)

        n.setCurrent(99)
        n.progress.value.currentIndex shouldBe 2
    }

    @Test
    fun `progress StateFlow emits on changes - bind then setCurrent then clear`() = runTest {
        val n = TtsProgressNotifier()

        n.progress.first().let { it.sentences shouldHaveSize 0 }

        n.bind("ch1", makeSentences(5, 10), session)
        n.progress.first().let { it.sentences shouldHaveSize 2 }

        n.setCurrent(1)
        n.progress.first().currentIndex shouldBe 1

        n.clear()
        n.progress.first().sentences shouldHaveSize 0
    }

    @Test
    fun `progress StateFlow does not emit when setCurrent no-ops`() = runTest {
        val n = TtsProgressNotifier()
        n.bind("ch1", makeSentences(5, 10), session)
        n.setCurrent(1)

        // 拿一次当前值（应该就是 index=1）
        n.progress.first().currentIndex shouldBe 1

        // 再调用 setCurrent(1) 是 no-op；StateFlow.value 应该仍是 1
        n.setCurrent(1)
        n.progress.value.currentIndex shouldBe 1
    }

    @Test
    fun `rebind replaces previous sentences and resets current index`() {
        val n = TtsProgressNotifier()
        n.bind("ch1", makeSentences(5, 10), session)
        n.setCurrent(1)

        n.bind("ch2", makeSentences(7, 14, 21), session)

        n.progress.value.chapterHref shouldBe "ch2"
        n.progress.value.sentences shouldHaveSize 3
        n.progress.value.currentIndex shouldBe 0
    }

    @Test
    fun `rebind clears currentIndex when new chapter has no sentences`() {
        val n = TtsProgressNotifier()
        n.bind("ch1", makeSentences(5, 10), session)
        n.setCurrent(1)

        n.bind("ch2", emptyList(), session)

        n.progress.value.chapterHref shouldBe "ch2"
        n.progress.value.currentIndex shouldBe -1
    }

    @Test
    fun `matching href does not let a session belong to another book`() {
        val first = TtsProgressNotifier()
        val second = TtsProgressNotifier()
        val otherBook = session.copy(chapterId = 2, mangaId = 2, token = "other-book")
        first.bind("chapter1.xhtml", makeSentences(5), session)
        second.bind("chapter1.xhtml", makeSentences(5), otherBook)

        first.progress.value.chapterHref shouldBe second.progress.value.chapterHref
        session.belongsTo(otherBook.chapterId, otherBook.mangaId) shouldBe false
        session.belongsTo(session.chapterId, otherBook.mangaId) shouldBe false
        session.belongsTo(otherBook.chapterId, session.mangaId) shouldBe false
        session.belongsTo(session.chapterId, session.mangaId) shouldBe true
    }

    @Test
    fun `invalid book identities never match a session`() {
        TtsProgressNotifier.Session(0, 1, "invalid").belongsTo(0, 1) shouldBe false
        TtsProgressNotifier.Session(1, 0, "invalid").belongsTo(1, 0) shouldBe false
        TtsProgressNotifier.Session(-1, -1, "invalid").belongsTo(-1, -1) shouldBe false
    }

    @Test
    fun `new session for same chapter rejects stale events and delivers current identity`() = runTest {
        val notifier = TtsProgressNotifier()
        val completions = mutableListOf<TtsProgressNotifier.Session>()
        val failures = mutableListOf<TtsProgressNotifier.Session>()
        backgroundScope.launch(start = CoroutineStart.UNDISPATCHED) {
            notifier.chapterCompleted.collect { completions.add(it) }
        }
        backgroundScope.launch(start = CoroutineStart.UNDISPATCHED) {
            notifier.playbackFailed.collect { failures.add(it) }
        }
        notifier.bind("chapter1.xhtml", makeSentences(5), session)
        val restarted = session.copy(token = "session-2")
        notifier.bind("chapter1.xhtml", makeSentences(5), restarted)

        notifier.notifyChapterCompleted(session)
        notifier.notifyPlaybackFailed(session)
        runCurrent()
        completions shouldBe emptyList()
        failures shouldBe emptyList()

        notifier.notifyChapterCompleted(restarted)
        notifier.notifyPlaybackFailed(restarted)
        runCurrent()
        completions shouldBe listOf(restarted)
        failures shouldBe listOf(restarted)
        notifier.progress.value.session shouldBe restarted
    }

    @Test
    fun `clear prevents a previous session from delivering completion or failure`() = runTest {
        val notifier = TtsProgressNotifier()
        val completions = mutableListOf<TtsProgressNotifier.Session>()
        val failures = mutableListOf<TtsProgressNotifier.Session>()
        backgroundScope.launch(start = CoroutineStart.UNDISPATCHED) {
            notifier.chapterCompleted.collect { completions.add(it) }
        }
        backgroundScope.launch(start = CoroutineStart.UNDISPATCHED) {
            notifier.playbackFailed.collect { failures.add(it) }
        }
        notifier.bind("chapter1.xhtml", makeSentences(5), session)
        notifier.clear()

        notifier.notifyChapterCompleted(session)
        notifier.notifyPlaybackFailed(session)
        runCurrent()

        completions shouldBe emptyList()
        failures shouldBe emptyList()
        notifier.progress.value.session shouldBe null
    }

    @Test
    fun `navigation hold is scoped to the bound session and released on clear`() {
        val notifier = TtsProgressNotifier()
        notifier.setNavigationPending(session, true)
        notifier.pendingNavigation.value shouldBe null
        notifier.bind("ch1", makeSentences(5), session)
        notifier.setNavigationPending(session, true)
        notifier.pendingNavigation.value shouldBe session
        notifier.bind("ch1", makeSentences(5, 10), session)
        notifier.pendingNavigation.value shouldBe session
        val replacement = session.copy(token = "replacement")
        notifier.bind("ch1", makeSentences(5), replacement)
        notifier.pendingNavigation.value shouldBe null
        notifier.setNavigationPending(replacement, true)
        notifier.setNavigationPending(session, false)
        notifier.pendingNavigation.value shouldBe replacement
        notifier.clear()
        notifier.pendingNavigation.value shouldBe null
    }
}
