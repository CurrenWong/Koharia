// Run with PLAYWRIGHT_MODULE and BROWSER_EXECUTABLE pointing to local installations.
// TTS_HIGHLIGHT_OUTPUT must point inside the repository's .test-artifacts directory.
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const { chromium } = require(process.env.PLAYWRIGHT_MODULE || 'playwright');
const root = path.resolve(__dirname, '../..');
const output = path.resolve(process.env.TTS_HIGHLIGHT_OUTPUT || path.join(root, '.test-artifacts/tts-highlight'));
assert.ok(output.startsWith(path.join(root, '.test-artifacts') + path.sep));
fs.mkdirSync(output, { recursive: true });
const source = fs.readFileSync(path.join(root, 'app/src/main/java/koharia/epub/EpubTtsHighlight.kt'), 'utf8');
const scripts = [...source.matchAll(/"""([\s\S]*?)"""/g)].map(match => match[1]);
assert.equal(scripts.length, 2);
const fragment = fs.readFileSync(path.join(root, 'app/src/main/java/koharia/epub/EpubReaderFragment.kt'), 'utf8');
const model = fragment.split('private val epubTtsTextModelBody =')[1].split('"""')[1];
const text = '窄屏中的朗读文字必须保持原有布局，不能在多行文字周围产生相互重叠的边框。';
const paragraphs = Array.from({ length: 12 }, (_, i) => `<p id="p${i}">第${i}段：${text}<em>嵌套<strong>强调😀</strong>文本</em>${text}<span hidden>隐藏正文</span><span aria-hidden="true">辅助文本</span></p>`).join('\n\n');
const results = [];

async function runCase(browser, fallback, columns) {
    const page = await browser.newPage({ viewport: { width: 320, height: 480 }, deviceScaleFactor: 1 });
    await page.setContent(`<!doctype html><html><head><style>
        html { font: 20px/1.45 sans-serif; }
        body { margin: 16px; }
        p { margin: 0 0 18px; } em { color: #315471; }
        mark { display: block !important; padding: 30px !important; border: 8px solid red !important; box-shadow: 0 0 0 15px orange !important; }
        ${columns ? 'html { height: 480px; overflow: hidden; } body { height: 448px; column-width: 288px; column-gap: 32px; column-fill: auto; }' : ''}
        </style></head><body>${paragraphs}</body></html>`);
    assert.equal(await page.evaluate(() => !!window.Highlight && !!CSS.highlights), true, 'Browser must support native highlights');
    await page.evaluate(() => { window.originalHighlight = window.Highlight; });
    if (fallback) await page.evaluate(() => { window.Highlight = undefined; });
    const snapshot = () => page.evaluate(() => ({
        html: document.body.innerHTML,
        text: document.body.textContent,
        width: document.scrollingElement.scrollWidth,
        height: document.scrollingElement.scrollHeight,
        blocks: [...document.querySelectorAll('p, em, strong')].map(el => {
            const r = el.getBoundingClientRect();
            return [r.x + scrollX, r.y + scrollY, r.width, r.height];
        }),
    }));
    const before = await snapshot();
    const plain = await page.evaluate(script => JSON.parse(eval('(' + script + ')()')), model);
    assert.equal(plain.ok, true);
    const apply = (start, end, scroll = false) => page.evaluate(({ script, start, end, scroll }) => {
        window.__ttsHighlightArgs = { start, end, scroll };
        return JSON.parse(eval('(' + script + ')()'));
    }, { script: scripts[0], start, end, scroll });
    const clear = () => page.evaluate(script => JSON.parse(eval('(' + script + ')()')), scripts[1]);
    const selected = () => page.evaluate(() => CSS.highlights.has('koharia-tts-active-sentence')
        ? [...CSS.highlights.get('koharia-tts-active-sentence')].map(range => range.toString()).join('')
        : [...document.querySelectorAll('mark.tts-active-sentence')].map(mark => mark.textContent).join(''));
    // Exact text-node boundary, nested inline elements, and a range crossing a paragraph.
    const start = plain.text.indexOf('嵌套');
    const end = plain.text.indexOf('第1段：') + 18;
    for (const [a, b] of [[start, end], [0, start], [start + 2, start + 8], [start, end]]) {
        const result = await apply(a, b);
        assert.equal(result.ok, true);
        assert.equal(result.markLen, b - a);
        assert.equal(result.walkerTotal, plain.text.length);
        assert.equal(result.highlighted, !fallback);
        assert.equal(await selected(), fallback ? '' : plain.text.slice(a, b));
        const during = await snapshot();
        assert.equal(during.text, before.text);
        assert.equal(during.width, before.width);
        assert.equal(during.height, before.height);
        assert.deepEqual(during.blocks, before.blocks);
        assert.equal(await page.evaluate(() => [...document.querySelectorAll('mark.tts-active-sentence')].every(mark =>
            mark.childNodes.length === 1 && mark.firstChild.nodeType === Node.TEXT_NODE &&
            getComputedStyle(mark).boxShadow === 'none' && getComputedStyle(mark).outlineStyle === 'none' &&
            getComputedStyle(mark).padding === '0px' && getComputedStyle(mark).display === 'inline')), true);
        assert.equal(await page.locator('mark.tts-active-sentence').count(), 0);
        assert.equal(during.html, before.html, 'Highlighting must not mutate publisher DOM');
    }
    const name = `${fallback ? 'fallback' : 'native'}-${columns ? 'columns' : 'narrow'}`;
    await page.screenshot({ path: path.join(output, name + '.png') });
    const visibleScroll = await apply(0, 2, true);
    assert.equal(visibleScroll.scroll.toX, visibleScroll.scroll.fromX);
    assert.equal(visibleScroll.scroll.toY, visibleScroll.scroll.fromY);
    assert.equal((await clear()).ok, true);
    assert.deepEqual(await snapshot(), before);
    // Invalid ranges must not leave a stale highlight.
    await apply(start, end);
    assert.equal((await apply(plain.text.length, plain.text.length + 1)).ok, false);
    assert.equal(await selected(), '');
    // Scroll uses a real first-line rect, not the bounding box of all columns.
    const target = plain.text.indexOf('第5段：');
    const scrolled = await apply(target, target + 30, true);
    assert.equal(scrolled.ok, true);
    assert.equal(scrolled.scroll.mode, columns ? 'paged' : 'scroll');
    assert.ok(scrolled.scroll.toX > 0 || scrolled.scroll.toY > 0);
    assert.ok(scrolled.scroll.rw <= 320, 'Scroll anchor must fit one line');
    await clear();
    assert.equal(await page.evaluate(() => CSS.highlights.has('koharia-tts-active-sentence')), false);
    assert.equal(await page.locator('#koharia-tts-highlight-style').count(), 0);
    const afterModel = await page.evaluate(script => JSON.parse(eval('(' + script + ')()')), model);
    assert.equal(afterModel.text, plain.text);
    await page.evaluate(() => { document.scrollingElement.scrollLeft = 0; document.scrollingElement.scrollTop = 0; });
    const whitespaceHasRect = await page.evaluate(() => {
        const range = document.createRange();
        range.selectNodeContents(document.getElementById('p5').previousSibling);
        return [...range.getClientRects()].some(rect => rect.width > 0 && rect.height > 0);
    });
    assert.equal(whitespaceHasRect, false);
    const leadingWhitespace = await apply(target - 2, target + 30, true);
    assert.equal(leadingWhitespace.ok, true);
    assert.equal(leadingWhitespace.scroll.mode, columns ? 'paged' : 'scroll');
    assert.ok(leadingWhitespace.scroll.toX > 0 || leadingWhitespace.scroll.toY > 0);
    await clear();
    // Cleanup also handles native highlights and legacy marks left by an earlier installation.
    await page.evaluate(() => {
        const node = document.getElementById('p0').firstChild;
        const mark = document.createElement('mark');
        mark.className = 'tts-active-sentence';
        node.parentNode.insertBefore(mark, node);
        mark.appendChild(node);
        const range = document.createRange();
        range.selectNodeContents(mark);
        CSS.highlights.set('koharia-tts-active-sentence', new window.originalHighlight(range));
        const style = document.createElement('style');
        style.id = 'koharia-tts-highlight-style';
        document.head.appendChild(style);
    });
    await clear();
    assert.equal(await page.evaluate(() => document.body.innerHTML), before.html);
    assert.equal(await page.evaluate(() => CSS.highlights.has('koharia-tts-active-sentence')), false);
    assert.equal(await page.locator('#koharia-tts-highlight-style').count(), 0);
    results.push({ name, textLength: plain.text.length, width: before.width, height: before.height, scroll: scrolled.scroll, leadingWhitespaceScroll: leadingWhitespace.scroll });
    await page.close();
}

async function structuralCase(browser, fallback) {
    const page = await browser.newPage({ viewport: { width: 320, height: 480 } });
    await page.setContent('<style>p{font:20px/1.3 sans-serif;width:260px}p>span:first-child{font-size:40px}p::first-letter{font-size:35px}</style><p>文字<span>结构选择器示例，用于证明高亮不会改变布局</span><ruby>汉<rt>han</rt></ruby>字形😀</p>');
    if (fallback) await page.evaluate(() => { window.Highlight = undefined; });
    const measure = () => page.evaluate(() => ({
        html: document.body.innerHTML,
        font: getComputedStyle(document.querySelector('span')).fontSize,
        rectangles: [...document.querySelectorAll('p,span,ruby,rt')].map(element => {
            const rect = element.getBoundingClientRect();
            return [rect.x, rect.y, rect.width, rect.height];
        }),
    }));
    const before = await measure();
    const applied = await page.evaluate(script => {
        window.__ttsHighlightArgs = { start: 0, end: 2, scroll: false };
        return JSON.parse(eval('(' + script + ')()'));
    }, scripts[0]);
    assert.equal(applied.ok, true);
    assert.equal(applied.highlighted, !fallback);
    assert.deepEqual(await measure(), before);
    await page.evaluate(script => eval('(' + script + ')()'), scripts[1]);
    assert.deepEqual(await measure(), before);
    results.push({ name: `structural-${fallback ? 'unsupported' : 'native'}`, font: before.font, rectangles: before.rectangles });
    await page.close();
}

async function localFixtureCase(browser) {
    if (!process.env.TTS_HIGHLIGHT_FIXTURE_HTML) return;
    const page = await browser.newPage({ viewport: { width: 320, height: 480 } });
    // The optional fixture is local user content; never issue document resource requests.
    await page.route('**/*', route => route.abort());
    await page.setContent(fs.readFileSync(process.env.TTS_HIGHLIGHT_FIXTURE_HTML, 'utf8'));
    const measure = () => page.evaluate(() => ({
        html: document.body.innerHTML,
        width: document.scrollingElement.scrollWidth,
        height: document.scrollingElement.scrollHeight,
        blocks: [...document.querySelectorAll('p,span,ruby,rt')].map(element => {
            const rect = element.getBoundingClientRect();
            return [rect.x, rect.y, rect.width, rect.height];
        }),
    }));
    const before = await measure();
    const plain = await page.evaluate(script => JSON.parse(eval('(' + script + ')()')), model);
    assert.equal(plain.ok, true);
    for (const start of [0, 15, 60, 120]) {
        const applied = await page.evaluate(({ script, start, end }) => {
            window.__ttsHighlightArgs = { start, end, scroll: false };
            return JSON.parse(eval('(' + script + ')()'));
        }, { script: scripts[0], start, end: Math.min(start + 100, plain.text.length) });
        assert.equal(applied.ok, true);
        assert.equal(applied.highlighted, true);
        assert.deepEqual(await measure(), before);
    }
    await page.screenshot({ path: path.join(output, 'local-fixture-native.png') });
    await page.evaluate(script => eval('(' + script + ')()'), scripts[1]);
    assert.deepEqual(await measure(), before);
    results.push({ name: 'local-fixture-native', textLength: plain.text.length, width: before.width, height: before.height, blockCount: before.blocks.length });
    await page.close();
}

async function preserveCrossAxisScroll(browser) {
    const page = await browser.newPage({ viewport: { width: 320, height: 480 } });
    await page.setContent('<style>body{width:700px;height:1200px;margin:0;padding-top:100px}p{margin:0;font:20px sans-serif}</style><p>横向页面高亮不应重置纵向位置</p>');
    await page.evaluate(() => { document.scrollingElement.scrollTop = 50; });
    const result = await page.evaluate(script => {
        window.__ttsHighlightArgs = { start: 0, end: 4, scroll: true };
        return JSON.parse(eval('(' + script + ')()'));
    }, scripts[0]);
    assert.equal(result.scroll.mode, 'paged');
    assert.equal(result.scroll.fromY, 50);
    assert.equal(result.scroll.toY, 50);
    assert.equal(result.scroll.toX, 0);
    results.push({ name: 'preserve-cross-axis-scroll', scroll: result.scroll });
    await page.close();
}

(async () => {
    const browser = await chromium.launch({ executablePath: process.env.BROWSER_EXECUTABLE, headless: true });
    try {
        for (const fallback of [false, true]) for (const columns of [false, true]) await runCase(browser, fallback, columns);
        for (const fallback of [false, true]) await structuralCase(browser, fallback);
        await preserveCrossAxisScroll(browser);
        await localFixtureCase(browser);
        const report = { browser: browser.version(), cases: results };
        fs.writeFileSync(path.join(output, 'browser-results.json'), JSON.stringify(report, null, 2));
        console.log(JSON.stringify(report, null, 2));
    } finally {
        await browser.close();
    }
})().catch(error => { console.error(error); process.exitCode = 1; });
