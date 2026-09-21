package koharia.epub

/** Uses the same visible-text offsets as the TTS text model. */
internal val EPUB_APPLY_SENTENCE_HIGHLIGHT_BODY =
    """
    function() {
        if (!document.body) return JSON.stringify({ ok: false, reason: 'no-body' });
        var args = window.__ttsHighlightArgs;
        if (!args) return JSON.stringify({ ok: false, reason: 'no-args' });
        var start = args.start | 0;
        var end = args.end | 0;
        var scroll = !!args.scroll;
        if (start < 0 || end <= start) return JSON.stringify({ ok: false, reason: 'bad-range' });
        function clearExistingMarks() {
            if (window.CSS && CSS.highlights) CSS.highlights.delete('koharia-tts-active-sentence');
            var style = document.getElementById('koharia-tts-highlight-style');
            if (style) style.remove();
            var marks = document.querySelectorAll('mark.tts-active-sentence');
            for (var i = 0; i < marks.length; i++) {
                var mark = marks[i];
                while (mark.firstChild) mark.parentNode.insertBefore(mark.firstChild, mark);
                mark.parentNode.removeChild(mark);
            }
            if (marks.length && document.body) document.body.normalize();
        }
        function isSkippableEl(el) {
            if (!el || el.nodeType !== 1) return false;
            var tag = el.tagName ? el.tagName.toUpperCase() : '';
            if (tag === 'SCRIPT' || tag === 'STYLE' || tag === 'NOSCRIPT') return true;
            // [hidden] 属性 / aria-hidden — 与 ChapterTextExtractor.NON_RENDERED_SELECTOR 对齐
            if (el.hasAttribute && el.hasAttribute('hidden')) return true;
            if (el.getAttribute && el.getAttribute('aria-hidden') === 'true') return true;
            // 已知 Readium 不渲染的导航元素
            var epubType = el.getAttribute && el.getAttribute('epub:type');
            if (epubType === 'toc' || epubType === 'landmarks' || epubType === 'page-list') return true;
            if (tag === 'NAV') return true;
            // 计算样式：display:none / visibility:hidden 同样跳过
            // 拿不到 computedStyle 时（head 等）忽略，不影响正文
            try {
                var cs = window.getComputedStyle ? window.getComputedStyle(el) : null;
                if (cs) {
                    if (cs.display === 'none') return true;
                    if (cs.visibility === 'hidden') return true;
                }
            } catch (e) {}
            return false;
        }

        function makeTextWalker() {
            var root = document.body;
            return document.createTreeWalker(root, NodeFilter.SHOW_TEXT, {
                acceptNode: function(node) {
                    var p = node.parentNode;
                    while (p && p !== root) {
                        if (isSkippableEl(p)) return NodeFilter.FILTER_REJECT;
                        p = p.parentNode;
                    }
                    return NodeFilter.FILTER_ACCEPT;
                }
            });
        }

        function scrollRangesIntoView(ranges) {
            if (!scroll) return null;
            var scroller = document.scrollingElement || document.documentElement;
            if (!scroller) return null;
            var vw = Math.max(1, window.innerWidth || scroller.clientWidth || 1);
            var vh = Math.max(1, window.innerHeight || scroller.clientHeight || 1);
            var rect = null;
            for (var r = 0; r < ranges.length && !rect; r++) {
                var rects = ranges[r].getClientRects();
                for (var i = 0; i < rects.length; i++) {
                    if (rects[i].width > 0 && rects[i].height > 0) { rect = rects[i]; break; }
                }
            }
            if (!rect) return null;
            var hRange = Math.max(0, scroller.scrollWidth - scroller.clientWidth);
            var vRange = Math.max(0, scroller.scrollHeight - scroller.clientHeight);
            var info = {
                vw: vw, vh: vh,
                hRange: Math.round(hRange), vRange: Math.round(vRange),
                fromX: Math.round(scroller.scrollLeft), fromY: Math.round(scroller.scrollTop),
                rl: Math.round(rect.left), rt: Math.round(rect.top),
                rw: Math.round(rect.width), rh: Math.round(rect.height)
            };
            // 不能用 scrollIntoView：
            //  - 分页（多栏横向）下它按 inline:'nearest' 只滚"刚好露出"的最小距离，
            //    会停在一栏中间 → 页面看起来"翻不到位"；
            //  - block:'center' 又把文字纵向推上去，分页下导致最后一行被切。
            // Readium 分页的翻页本质就是 WebView 的 scrollX，这里直接按整栏对齐。
            if (hRange > 1) {
                var dir = 'ltr';
                try { dir = String(window.getComputedStyle(scroller).direction || 'ltr'); } catch (e) {}
                // 用"相对当前视口的栏偏移"而非绝对栏号：RTL 下 scrollLeft 为负值也能成立
                var anchor = dir === 'rtl' ? rect.right : rect.left;
                var delta = dir === 'rtl' ? (Math.ceil(anchor / vw) - 1) : Math.floor(anchor / vw);
                // 一次最多翻一页（防被强行拽回），且不回翻——
                // 当句子起点落在上一栏时回翻一页、下一句起点又落在后一栏，
                // 会出现"回翻 → 再前翻"的抖动；直接钳为 0 只前进。
                if (delta > 1) delta = 1;
                if (delta < 0) delta = 0;
                if (delta !== 0) scroller.scrollLeft = scroller.scrollLeft + delta * vw;
                info.mode = 'paged';
                info.dir = dir;
                info.delta = delta;
            } else if (vRange > 1) {
                // Keep the current viewport while the first visible text fragment is on screen.
                if (rect.top < 0 || rect.bottom > vh) {
                    var target = scroller.scrollTop + rect.top - (vh - rect.height) / 2;
                    if (target < 0) target = 0;
                    if (target > vRange) target = vRange;
                    scroller.scrollTop = target;
                }
                info.mode = 'scroll';
            } else {
                info.mode = 'static';
            }
            info.toX = Math.round(scroller.scrollLeft);
            info.toY = Math.round(scroller.scrollTop);
            return info;
        }


        clearExistingMarks();
        var walker = makeTextWalker();
        var segments = [];
        var offset = 0;
        var node;
        while ((node = walker.nextNode())) {
            var length = String(node.nodeValue || '').length;
            var from = Math.max(0, start - offset);
            var to = Math.min(length, end - offset);
            if (from < to) segments.push({ node: node, from: from, to: to });
            offset += length;
        }
        if (end > offset || segments.length === 0) {
            return JSON.stringify({ ok: false, reason: 'range-not-found' });
        }
        var ranges = segments.map(function(segment) {
            var range = document.createRange();
            range.setStart(segment.node, segment.from);
            range.setEnd(segment.node, segment.to);
            return range;
        });
        var text = ranges.map(function(range) { return range.toString(); }).join('');
        var nativeHighlight = false;
        if (window.CSS && CSS.highlights && window.Highlight) {
            try {
                var style = document.createElementNS('http://www.w3.org/1999/xhtml', 'style');
                style.id = 'koharia-tts-highlight-style';
                style.textContent = '::highlight(koharia-tts-active-sentence) {' +
                    'background-color: rgba(140, 150, 120, 0.22); color: inherit;}';
                (document.head || document.documentElement).appendChild(style);
                var highlight = new window.Highlight();
                ranges.forEach(function(range) { highlight.add(range); });
                CSS.highlights.set('koharia-tts-active-sentence', highlight);
                nativeHighlight = true;
            } catch (e) {
                clearExistingMarks();
            }
        }
        // Unsupported WebViews keep the original text DOM and still follow the reading range.
        var scrollInfo = scrollRangesIntoView(ranges);
        return JSON.stringify({
            ok: true,
            highlighted: nativeHighlight,
            marks: nativeHighlight ? ranges.length : 0,
            markLen: text.length,
            walkerTotal: offset,
            snippet: text.slice(0, 60),
            scroll: scrollInfo
        });
    }
    """.trimIndent()

internal val EPUB_CLEAR_SENTENCE_HIGHLIGHT_BODY =
    """
    function() {
        try {
            if (window.CSS && CSS.highlights) CSS.highlights.delete('koharia-tts-active-sentence');
            var style = document.getElementById('koharia-tts-highlight-style');
            if (style) style.remove();
            var marks = document.querySelectorAll('mark.tts-active-sentence');
            for (var i = 0; i < marks.length; i++) {
                var mark = marks[i];
                while (mark.firstChild) mark.parentNode.insertBefore(mark.firstChild, mark);
                mark.parentNode.removeChild(mark);
            }
            if (marks.length && document.body) document.body.normalize();
            return JSON.stringify({ ok: true });
        } catch (e) {
            return JSON.stringify({ ok: false, reason: String(e) });
        }
    }
    """.trimIndent()
