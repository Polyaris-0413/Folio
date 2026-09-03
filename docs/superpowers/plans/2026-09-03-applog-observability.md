# 日志与错误治理实施计划（TODO 第 1 项）

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 统一日志出口 `AppLog`，release 构建剥离调试级日志（d），关键失败路径补警告日志，消除"出错无线索"的排障盲区。

**Architecture:** 新建 `util/AppLog` 薄封装（d/w/e 三级），所有现有 `android.util.Log` 直连调用收敛到它；release 剥离靠 R8 `-assumenosideeffects`（调用点与消息字符串拼接一并消除，w/e 保留供线上排障）。tag 维持现状（FolioReader/FolioTts/FolioLibrary/FolioFrame 已统一前缀），零行为变化除注明处。

**Tech Stack:** Kotlin, R8/ProGuard `-assumenosideeffects`

**Spec:** `docs/optimization-todo.md` 第 1 项

## Global Constraints

- tag 内容一律不变（含 LibraryAddScreen 两条刻意用 w 的历史决策——ColorOS 丢 debug 级日志的踩坑结论，注释保留）
- 正常回退分支（枚举解析、GBK 回退、指纹降级、unbindService 防御等 10 处）保持静默，不加日志
- 不引入新依赖；AppLog 无 v/i 方法（现状不用，YAGNI）
- 不做 TDD 单测：AppLog 是对 `android.util.Log` 的零逻辑转发（android.util.Log 无法在 JVM 实例化，项目无 mockable jar 设施），剥离正确性由 release 构建运行时验证（Task 5）

---

### Task 1: AppLog 封装 + 剥离规则

**Files:**
- Create: `app/src/main/java/com/folio/read/util/AppLog.kt`
- Modify: `app/proguard-rules.pro`

- [ ] **Step 1: 创建 AppLog**

```kotlin
package com.folio.read.util

import android.util.Log

/**
 * 统一日志出口:tag 保持 Folio 前缀;调试级 d 在 release 构建由 R8 整体剥离
 * (proguard-rules.pro 的 -assumenosideeffects,调用点与消息字符串拼接一并消除),
 * w/e 保留供线上排障。
 * 注意:部分 ROM(ColorOS)会丢弃 debug 级日志,需在那类设备上定位时临时改用 w。
 */
object AppLog {

    fun d(tag: String, msg: String) {
        Log.d(tag, msg)
    }

    fun w(tag: String, msg: String, tr: Throwable? = null) {
        if (tr == null) Log.w(tag, msg) else Log.w(tag, msg, tr)
    }

    fun e(tag: String, msg: String, tr: Throwable? = null) {
        if (tr == null) Log.e(tag, msg) else Log.e(tag, msg, tr)
    }
}
```

- [ ] **Step 2: proguard-rules.pro 追加规则**

在现有注释后追加：

```
# 统一日志出口 AppLog:release 剥离调试级日志(d),消息字符串拼接随调用点一并消除;
# 警告/错误(w/e)保留供线上排障
-assumenosideeffects class com.folio.read.util.AppLog {
    public void d(java.lang.String, java.lang.String);
}
```

- [ ] **Step 3: 编译验证**

Run: `gradlew assembleDebug`
Expected: BUILD SUCCESSFUL

---

### Task 2: Log 调用点收敛（22 处 → AppLog）

**Files:**
- Modify: `app/src/main/java/com/folio/read/ui/reader/ReaderTtsService.kt`
- Modify: `app/src/main/java/com/folio/read/ui/reader/ReaderTts.kt`
- Modify: `app/src/main/java/com/folio/read/ui/reader/ReaderScreen.kt`
- Modify: `app/src/main/java/com/folio/read/ui/library/LibraryAddScreen.kt`
- Modify: `app/src/main/java/com/folio/read/util/FrameJankLog.kt`

语义升级仅两处（失败分支从 d 升 w/e，用户可见失败此前却只有调试级日志）：
- `ReaderTtsService.kt:146` "tts init error"：d → `AppLog.e`（TTS 初始化失败）
- `ReaderTtsService.kt:264` "book null" 与 `:277` "chapters empty"：d → `AppLog.w`（TTS 加载失败分支，用户已弹 tts_load_failed）
其余级别一律不变。

- [ ] **Step 1: ReaderTtsService.kt** — `import android.util.Log` 删除，`import com.folio.read.util.AppLog` 加入；9 处调用替换（TAG 引用不变）：
  - `:125` `Log.d(TAG, "onCreate")` → `AppLog.d(TAG, "onCreate")`
  - `:141` → `AppLog.d(TAG, "tts init ready")`
  - `:146` → `AppLog.e(TAG, "tts init error")`
  - `:198` → `AppLog.d(TAG, ...)`
  - `:250` → `AppLog.d(TAG, "onDestroy")`
  - `:264` → `AppLog.w(TAG, "startReading: book null")`
  - `:277` → `AppLog.w(TAG, "startReading: chapters empty")`
  - `:317` → `AppLog.d(TAG, ...)`
  - `:362` → `AppLog.d(TAG, ...)`
- [ ] **Step 2: ReaderTts.kt** — 6 处全限定 `android.util.Log.d("FolioTts", ...)` → `AppLog.d(...)`，加 import（该文件需确认 126/137/154/278 多行调用的括号结构后逐一替换）
- [ ] **Step 3: ReaderScreen.kt** — `import android.util.Log` → `import com.folio.read.util.AppLog`；`:169` → `AppLog.e("FolioReader", "readBook 失败: $e", e)`；`:561` `:568` `:672` Log.d → AppLog.d
- [ ] **Step 4: LibraryAddScreen.kt** — `import android.util.Log` → `import com.folio.read.util.AppLog`；`:153` `:155` Log.w → AppLog.w（级别/内容不变，保持 ColorOS 可见性注释）
- [ ] **Step 5: FrameJankLog.kt** — `import android.util.Log` → `import com.folio.read.util.AppLog`；`:37` Log.w → AppLog.w
- [ ] **Step 6: 验证无残留**

Run: `grep -rn "android.util.Log\|Log\.\(d\|w\|e\|v\|i\)(" app/src/main/java/com/folio/read/ --include="*.kt" | grep -v "AppLog\|util/FrameJankLog.kt:4\|AppLog.kt"`
Expected: 仅 AppLog.kt 自身命中
Run: `gradlew assembleDebug`
Expected: BUILD SUCCESSFUL

---

### Task 3: 关键失败路径补警告（7 处）

**Files:**
- Modify: `app/src/main/java/com/folio/read/data/UpdateChecker.kt`
- Modify: `app/src/main/java/com/folio/read/ui/reader/ReaderTtsService.kt`（:274）
- Modify: `app/src/main/java/com/folio/read/ui/reader/ReaderPreWarm.kt`
- Modify: `app/src/main/java/com/folio/read/FolioApp.kt`
- Modify: `app/src/main/java/com/folio/read/data/BookTitleCleaner.kt`
- Modify: `app/src/main/java/com/folio/read/data/BookRepository.kt`（isReadable）
- Modify: `app/src/main/java/com/folio/read/MainActivity.kt`（:474）

选入依据：失败当下用户无感知、事后无任何痕迹，且后果可延迟显现。未选入的 runCatching 均为正常回退分支（详见 Global Constraints）。

- [ ] **Step 1: UpdateChecker.kt:61** — `runCatching { ... }.getOrElse { UpdateCheckResult.Failed }` 改为先记后回退：
  ```kotlin
  }.getOrElse { e ->
      AppLog.w("FolioUpdate", "检查更新失败: $e", e)
      UpdateCheckResult.Failed
  }
  ```
  加 `import com.folio.read.util.AppLog`
- [ ] **Step 2: ReaderTtsService.kt:274** — runCatching readBook 后改 `getOrElse { e -> AppLog.w(TAG, "TTS 整本解析失败: $e", e); null }`（配合 :277 已升 w，失败链完整）
- [ ] **Step 3: ReaderPreWarm.kt:28,31** — 两处 `runCatching { ... }.getOrNull()` 改 `getOrElse { e -> AppLog.w("FolioReader", "预读指纹查询失败: $e", e); null }` / `"预读解析失败: ..."`（注释「失败静默」同步更新为「失败记日志后跳过」）
- [ ] **Step 4: FolioApp.kt:56** — runCatching 加 `.getOrElse { e -> AppLog.w("FolioApp", "封面预热失败: $e", e) }`，更新注释「预热失败静默」→「失败记日志（组合侧同步渲染兜底不变）」
- [ ] **Step 5: BookTitleCleaner.kt:25** — `catch (e: Exception) { null }` 改 `catch (e: Exception) { AppLog.w("FolioClean", "AI 书名净化失败: $e", e); null }`
- [ ] **Step 6: BookRepository.kt isReadable** — `.getOrDefault(false)` 改 `getOrElse { e -> AppLog.w("FolioShelf", "可读性查询失败: $e", e); false }`
- [ ] **Step 7: MainActivity.kt:474** — runCatching takePersistableUriPermission 追加 `.getOrElse { e -> AppLog.w("FolioShelf", "持久化读权限失败: $e", e) }`（失败后果延迟到重启后打不开书，须留痕）
- [ ] **Step 8: 编译验证**

Run: `gradlew assembleDebug`
Expected: BUILD SUCCESSFUL

---

### Task 4: 全量验证

- [ ] **Step 1: 单测回归**

Run: `gradlew testDebugUnitTest`
Expected: 全部通过（现有 7 个测试文件无回归）

- [ ] **Step 2: debug 构建安装打开 + logcat**

Run: `gradlew installDebug`，启动 App，`adb logcat -s FolioReader:* FolioTts:* FolioFrame:*`
Expected: App 正常启动；debug 日志照常（如 FolioFrame 掉帧行、打开书后 FolioReader 解析相关）

- [ ] **Step 3: release 构建验证剥离**

Run: `gradlew assembleRelease` 安装，打开一本书翻页再退出，`adb logcat -s FolioReader:* FolioTts:*`
Expected: 翻页时无 `pages ch=` 计时日志（ReaderScreen:561 的 d 已被剥离）；启动书架正常
辅助静态确认（可选）：解包 release APK 后 `strings classes*.dex | grep "pages ch="` 无命中

- [ ] **Step 4: 日志残留静态确认**

Run: 对 release dex 全量 strings，确认 `FolioFrame` `FolioTts` 等 w/e tag 仍在（排障通道保留）
Expected: d 级消息模板消失、w/e 路径保留

---

### Task 5: 收尾

- [ ] **Step 1: changelog 判断** — 纯内部质量治理，用户无可见行为变化（性能仅微小正向），按「净更改」原则不记录
- [ ] **Step 2: Git 备份**

```bash
git add -A
git commit -m "refactor: 统一日志出口 AppLog;release 剥离调试日志(d),关键失败路径补警告(w/e)"
```
