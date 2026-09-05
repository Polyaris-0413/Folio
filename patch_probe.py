import io

p = r"app/src/main/java/com/folio/read/ui/reader/ReaderScreen.kt"
s = io.open(p, encoding="utf-8").read()
n = 0

def rep(old, new, tag):
    global s, n
    assert old in s, "MISS: " + tag
    s = s.replace(old, new, 1)
    n += 1

rep(
"""                                if (scrolling && !ttsScroll && currentTtsActive) {
                                    userLeftTts = true
                                    ttsService?.stopReadingAt(currentCh, currentPos.second)
                                }""",
"""                                if (scrolling && !ttsScroll && currentTtsActive) {
                                    AppLog.d("FolioTtsUi", "dragStop ch=$currentCh pos=${currentPos.second}")
                                    userLeftTts = true
                                    ttsService?.stopReadingAt(currentCh, currentPos.second)
                                }""",
"dragStop")

rep(
"""                            if (pageInPager < base || pageInPager >= base + realPages) {
                                if (ttsActive) {""",
"""                            if (pageInPager < base || pageInPager >= base + realPages) {
                                AppLog.d("FolioTtsUi", "sentinelHit ch=$ch page=$pageInPager base=$base realPages=$realPages tts=$ttsActive")
                                if (ttsActive) {""",
"sentinelHit")

rep(
"""                            if (!highlightOnPage && ttsActive) {
                                // 滑页/跳章:标记用户已离开并同步停朗读(不走 intent,避免异步延迟""",
"""                            if (!highlightOnPage && ttsActive) {
                                AppLog.d("FolioTtsUi", "mismatchStop ch=$ch abs=$abs hl=${hl?.chapter}:${hl?.start}")
                                // 滑页/跳章:标记用户已离开并同步停朗读(不走 intent,避免异步延迟""",
"mismatchStop")

rep(
"""                            if (rc == curChapter) return@LaunchedEffect // 朗读启动(rc==cur):记录即可,不跟随""",
"""                            if (rc == curChapter) return@LaunchedEffect // 朗读启动(rc==cur):记录即可,不跟随
                            AppLog.d("FolioTtsUi", "follow rc=$rc cur=$curChapter hl=${ttsHighlight?.chapter}:${ttsHighlight?.start}")""",
"follow")

rep(
"""                            if (target != pagerState.currentPage) {
                                // 瞬间跳转(用户实测平滑滚动效果不理想,已回滚;定位准确、无动画干扰)""",
"""                            if (target != pagerState.currentPage) {
                                AppLog.d("FolioTtsUi", "ttsScroll ch=$ch target=$target")
                                // 瞬间跳转(用户实测平滑滚动效果不理想,已回滚;定位准确、无动画干扰)""",
"ttsScroll")

io.open(p, "w", encoding="utf-8", newline="").write(s)
print("patched", n)
