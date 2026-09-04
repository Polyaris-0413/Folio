# legado TXT 目录系统完整移植 实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 将 legado-with-MD3 3.26.15 的 TXT 目录系统（规则库 + 打分择优 + 正则/字数双路分章 + 长章拆分 + 规则管理/预览 UI + 逐本规则记忆）完整移植到 Folio，代码优先原样移植而非重写。

**Architecture:** Folio 保持「整本 String 解析、每章独立 content」的既有阅读架构不变；移植 legado `TextFile` 的目录分析算法（打分器逐行照搬，字节偏移域适配为字符偏移域，字节长度判断保留 UTF-8 字节计数语义），产出物化为 Folio 的 `Chapter(title, content)`。规则库（Room 表 + assets JSON 1:1 复制）与规则 UI 为新增子系统；epub/mobi 路径不动。

**Tech Stack:** Kotlin / Jetpack Compose / Room（现有）/ DataStore（现有）/ org.json（平台自带，解析默认规则 JSON）/ android.icu（平台自带，编码探测）

**Spec:** 本计划自含设计背景（见下节）。源码参照库：`C:\Users\Administrator\Downloads\legado-with-MD3-3.26.15`（下称 `<LEGADO>`，源文件以 `<L:路径>` 表示；Folio 仓库根 `C:\Users\Administrator\StudioProjects\Folio`，模块根 `app/src/main/java/com/folio/read` 下称 `<F>`）。

## 设计背景（Spec 摘要）

- 触发 bug：《获罪书》（日记体 TXT，章节标记为 `【2013年8月12日，雷阵雨】` 样式）在 Folio 目录只有一个空条目——现行单正则 0 匹配，兜底"整本当一章、标题置空"（`TextLoader.kt:92-94`）。
- legado 对同类书：默认规则集同样 0 匹配，但兜底是**按字数分章**（每 10KB 换行处切、合成标题"第N章(序号)"），目录始终可跳转。
- 用户决策：完整移植 legado 目录系统；**能移植代码就直接移植代码，禁止按理解仿造**。允许的两类必要适配（其余一律照搬）：
  1. **域适配**：legado 流式字节块 + 字节偏移（`analyze` 的 while-block 循环）→ Folio 整本 String 的**字符偏移**。分章行为的每个语义点（前言章、连续标题合并、`toc.lastOrNull` 更新、超长拆分、末章并入）逐条保留；长度阈值判断**保留字节计数**（UTF-8 `toByteArray().size`），使 10KB/100KB 阈值与 legado 同义。
  2. **宿主适配**：`appDb/book/LocalBook` 等依赖替换为 Folio 等价物（DAO、`Book.tocRule` 字段、`contentResolver`）；UI 层骨架用 Folio 现有 M3 组件实现（legado UI 依赖 Koin/reorderable 等其自身生态，不引入），但**功能面必须对齐**（见 Task 7/8 清单）。
- 明确不移植（属 legado 自身生态而非目录系统核心，Folio 无对应需求）：章节落库与 `BookChapter.url`/MD5（Folio 章节只在进程内存）、`upKind`/wordCount 书架展示、规则的 WebDAV/文件导入导出（只保留"恢复内置规则"）、ContentProcessor 的净化/重分段规则。

## Global Constraints

- minSdk 31 / targetSdk 37 / Kotlin + Compose（`app/build.gradle.kts`），不新增任何第三方依赖。
- 注释只写代码表达不了的原因与出处（踩坑根因、数值来源、设计取舍）；移植的类/函数保留 legado 原注释，注明源文件出处。
- 现有设计语言：新 UI 用 FolioTheme + M3 token（圆角 12dp、AnimationTokens 档位、`FolioTopBar`、`ListItemExt`）。
- 每个任务完成后：`JAVA_HOME="C:/Program Files/Android/Android Studio/jbr" ./gradlew assembleDebug` 编译通过 + 单测通过 + git 提交；涉及行为变化需真机验证（设备已连 adb）。
- 页表缓存版本：`ChapterRuleVersion` 由 `"ch4"` bump 至 `"ch5"`（ReaderCacheKeys.kt:4），`TextProcessVersion` 由 `"p13"` bump 至 `"p14"`（分章应用域由整本改为逐章）。
- AppDatabase 当前 version 8；本次加表 + 加字段 → version 9，迁移链 1..8 不可改动。

---

### Task 1: 规则库数据层（实体/DAO/迁移/默认规则）

**Files:**
- Create: `<F>data/TxtTocRule.kt`、`<F>data/TxtTocRuleDao.kt`、`<F>data/TxtTocRuleRepository.kt`、`<F>data/DefaultTocRules.kt`、`<F>../../assets/defaultData/txtTocRule.json`（即 `app/src/main/assets/defaultData/txtTocRule.json`）
- Modify: `<F>data/AppDatabase.kt`
- Test: `<F>`（JVM）`app/src/test/java/com/folio/read/DefaultTocRulesTest.kt`

**Interfaces:**
- Produces: `data class TxtTocRule(id: Long, name: String, rule: String, example: String?, serialNumber: Int, enable: Boolean)`；`TxtTocRuleDao`（observeAll/all/enabled/count/get/insert/update/delete/deleteDefault/minOrder/maxOrder）；`TxtTocRuleRepository(appContext)`（`flowAll(): Flow<List<TxtTocRule>>`、`all(): List<TxtTocRule>`、`enabledRules(): List<TxtTocRule>`、`save(rule)`、`delete(rule)`、`importBuiltInRules()`）；`DefaultTocRules.get(): List<TxtTocRule>`。

- [ ] **Step 1: 复制默认规则 JSON（原样）**

```bash
mkdir -p "C:/Users/Administrator/StudioProjects/Folio/app/src/main/assets/defaultData"
cp "C:/Users/Administrator/Downloads/legado-with-MD3-3.26.15/app/src/main/assets/defaultData/txtTocRule.json" "C:/Users/Administrator/StudioProjects/Folio/app/src/main/assets/defaultData/txtTocRule.json"
```

不改动 JSON 内容（含 legado 的 -100 默认分章占位规则）。

- [ ] **Step 2: 移植实体与 DAO**

`TxtTocRule.kt` 从 `<L:data/entities/TxtTocRule.kt>` L7-29 逐行复制，仅改包名为 `com.folio.read.data`、`tableName` 保持 `"txtTocRules"`。`TxtTocRuleDao.kt` 从 `<L:data/dao/TxtTocRuleDao.kt>` L13-56 逐行复制（全部方法保留，含 flowSearch/getByIds/minOrder/maxOrder），改包名与 import（`TxtTocRule` 指向 Folio 实体）；`flowSearch`/`getByIds` 虽暂无调用方也保留——它们属于 DAO 的完整接口。

- [ ] **Step 3: 注册进 AppDatabase（version 8→9）**

`AppDatabase.kt`：`@Database(entities = [Book::class, TxtTocRule::class], version = 9)`；`@Deprecated` 不适用——新增 `abstract fun txtTocRuleDao(): TxtTocRuleDao`；新增迁移并追加进 `addMigrations`：

```kotlin
private val MIGRATION_8_9 = object : Migration(8, 9) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `txtTocRules` (`id` INTEGER NOT NULL, " +
                "`name` TEXT NOT NULL, `rule` TEXT NOT NULL, `example` TEXT, " +
                "`serialNumber` INTEGER NOT NULL, `enable` INTEGER NOT NULL, PRIMARY KEY(`id`))"
        )
    }
}
```

字段类型与 `<L>` 实体一致（id 非自增，Long 主键）。

- [ ] **Step 4: 默认规则加载（DefaultTocRules + Repository）**

`DefaultTocRules.kt`：语义照搬 `<L:help/DefaultData.kt>` L65-71（assets 读 → 解析 → 失败静默空表），JSON 解析用 org.json 替代 GSON：

```kotlin
object DefaultTocRules {
    fun get(context: Context): List<TxtTocRule> = runCatching {
        val arr = JSONArray(
            context.assets.open("defaultData/txtTocRule.json").use { it.readBytes().decodeToString() },
        )
        List(arr.length()) { i ->
            arr.getJSONObject(i).let {
                TxtTocRule(
                    id = it.getLong("id"), name = it.getString("name"),
                    rule = it.getString("rule"), example = it.optString("example"),
                    serialNumber = it.getInt("serialNumber"), enable = it.getBoolean("enable"),
                )
            }
        }
    }.getOrElse { AppLog.w("FolioToc", "内置目录规则解析失败: $it", it); emptyList() }
}
```

`TxtTocRuleRepository.kt`：薄封装照 `<L:data/repository/TxtTocRuleRepository.kt>` 的职责（`flowAll`/增删改查），另实现 `importBuiltInRules()`——语义照 `<L:help/DefaultData.kt>` L118-121 `importDefaultTocRules`：先 `txtTocRuleDao.deleteDefault()`（id<0）再批量 insert 默认规则；以及 `ensureDefaults()`（count==0 时自动插入，语义照 `<L:model/localBook/TextFile.kt>` L475-485 `getTocRules` 的兜底分支）。

- [ ] **Step 5: 写失败测试（JSON 解析 + DAO 语义不可 JVM 测，测纯函数部分）**

`DefaultTocRulesTest.kt`：`testImplementation("org.json:json:20240303")` 加进 `app/build.gradle.kts` dependencies（仅测试域）。测试断言：解析 24 条、id 负值为主、serialNumber 0..24 连续、-2 条目的 rule 含 `第\\s{0,4}` 字面片段。解析函数需以 `(json: String) -> List<TxtTocRule>` 重载暴露供 JVM 测试（assets 版委托它）。

- [ ] **Step 6: 跑测试与编译**

Run: `./gradlew :app:testDebugUnitTest --tests "*DefaultTocRules*"` → PASS；`./gradlew assembleDebug` → BUILD SUCCESSFUL。

- [ ] **Step 7: Commit**

```bash
git add -A && git commit -m "feat: 移植 legado TXT 目录规则库(实体/DAO/v9迁移/默认规则 assets)"
```

---

### Task 2: 目录分析引擎 TextFile（核心，字符域适配移植）

**Files:**
- Create: `<F>ui/reader/TextFile.kt`、`<F>ui/reader/EncodingDetect.kt`
- Test: `app/src/test/java/com/folio/read/TextFileTest.kt`（获罪书样本内置）

**Interfaces:**
- Consumes: Task 1 的 `TxtTocRule`/`enabledRules()`。
- Produces（后续任务全部依赖，签名固定）:

```kotlin
// ui/reader/TextFile.kt
object TextFile {
    /** 打分择优:规则列表 + 文本头部 → 命中最多的规则;0 命中返回 null(语义同 <L> TextFile.kt:444-470) */
    fun getTocRule(rules: List<TxtTocRule>, headContent: String): Regex?
    /** 正则分章(语义同 <L> analyze(pattern) L154-338,字符偏移域);splitLongChapter=超长拆分开关 */
    fun analyze(text: String, pattern: Regex?, splitLongChapter: Boolean): List<Chapter>
    /** 无规则按字数分章(语义同 <L> analyze() L343-439);字符域,字节计数判断 */
    fun analyzeNoToc(text: String, start: Int = 0, end: Int = text.length): List<Chapter>
    /** TXT 完整入口:择优 → analyze / 兜底;返回 (章节列表, 选中的规则文本, 字数) */
    fun getChapterList(text: String, rules: List<TxtTocRule>, splitLongChapter: Boolean): TocResult
}
data class TocResult(val chapters: List<Chapter>, val tocRule: String, val wordCount: Int)
```

- [ ] **Step 1: 移植 EncodingDetect**

`EncodingDetect.kt` 从 `<L:utils/EncodingDetect.kt>` 复制其核心（L55-59 的 `CharsetDetector` 用法展开为完整类）：`fun getEncode(bytes: ByteArray): String = android.icu.text.CharsetDetector().setText(bytes).detect()?.name ?: "UTF-8"`。minSdk 31 平台自带 android.icu，无需依赖。

- [ ] **Step 2: 写失败测试（行为样本全部来自 legado 语义）**

`TextFileTest.kt` 用例（纯 JVM，`Chapter`/`TextFile` 均为纯 Kotlin）：
1. **打分择优**：构造含 5 个 `第一章 xxx` + 1 个 `1、yyy` 的文本 → `getTocRule(defaultRules, head)` 返回的 pattern 即 -2「目录」规则（serialNumber=1），断言 `rule` 字符串等于 JSON 中 -2 的 rule。
2. **打分间隔计分**：同一规则密集匹配（相邻匹配间隔 <1000 字符）只计 1 分——构造 10 行连续 `第一章`，对照 10 行间隔 1500 字符的 `第一章`，后者胜出（若仅前者存在则 num=1，两者都存在时后者 num 更大）。
3. **正则分章 + 前言章**：`"前言内容\n\n第一章 A\n正文a\n\n第二章 B\n正文b"` → 3 章：title 为 `前言`、`第一章 A`、`第二章 B`；前言章 content 含"前言内容"。
4. **字数分章**：构造 40KB 中文文本（无任何规则可命中）→ `analyzeNoToc` 切出 ≥4 章，每章 ≤10*1024 UTF-8 字节（末段 <100 字节并入上一章：追加 90 字节尾巴断言章数不增）；标题匹配 `第\d+章\(\d+\)`。
5. **长章拆分**：构造规则命中 1 次、单章 150KB 中文 → `analyze(splitLongChapter=true)` 该章被拆成 ≥2 章且标题为 `原标题(N)`；`splitLongChapter=false` 时不拆。
6. **获罪书样本**：内置 20 行 `【2013年8月X日，XX】` 样式 + 每行后 800 字符填充（全文约 17KB）→ 默认启用规则集打分为 0 → `getChapterList` 走字数分章，章数 ≥1 且无空标题。

- [ ] **Step 3: 跑测试确认 FAIL**

Run: `./gradlew :app:testDebugUnitTest --tests "*TextFileTest*"` → FAIL（TextFile 未定义）。

- [ ] **Step 4: 移植引擎实现**

`TextFile.kt` 按下表从 `<L:model/localBook/TextFile.kt>` 移植，**每一处行为分支逐行对照源文件**，禁止凭记忆重写：

| 源（legado TextFile.kt） | 目标 | 适配说明 |
|---|---|---|
| `getTocRule` L444-470 | `getTocRule` | 1:1 保留：`rules.reversed()` 尝试顺序、`num >= maxNum` 平局后者胜、`matcher.start() - start > 1000` 间隔计分。`toPattern(Pattern.MULTILINE)` → `toRegex(RegexOption.MULTILINE)`，try-catch `PatternSyntaxException` → `AppLog.w` 后 continue |
| `analyze(pattern)` L154-338 | `analyze` | 域适配（唯一非 1:1 处，逐条核对）：流式 while-block + `curOffset` 字节偏移 → 单遍全文本字符偏移；`matcher.find()` 循环、`seekPos`（=上一匹配结束）、前言章（首个匹配前非空内容 → `title="前言"`、>500 截 intro 的逻辑删除 intro 仅保留章节）、`toc.lastOrNull()` end/wordCount 更新、`getSplitLongChapter()` 超长拆分（L204-228/L317-333 递归调 `analyzeNoToc`）、标题 `matcher.group()`。字节长度判断 `chapterContent.toByteArray(charset).size` → `toByteArray(Charsets.UTF_8).size`（阈值 102400 同义保留） |
| `analyze(fileStart,fileEnd)` L343-439 | `analyzeNoToc` | 域适配：块循环删除（整段在内存）；`maxLengthWithNoToc = 10 * 1024` 保留为**字节**判断；在 `(start, end)` 区间内从 `maxLengthWithNoToc` 处向后找最近换行切章（L391-397 换行回退语义）；标题 `第${blockPos}章(${chapterPos})`（L401）；结尾 L423-436：剩余 >100 **字符**（原为字节，域内简化）或 toc 空时独立成章否则并入上一章 |
| `getChapterList` L83-110 | `getChapterList` | 适配：`book.tocUrl` 探测/复用逻辑上移到 Task 4 的调用方（Folio 把已选规则经参数传入）；本函数接收**全文本 + 规则列表**，探测输入 = 文本头部 512000 **字节** decode（原 `bufferSize=512000`，L87-99 语义）；`book.upKind/wordCount` 不移植（wordCount 数值在 TocResult 返回，展示不做） |

`Chapter` 沿用 `<F>ui/reader/TextLoader.kt:18` 现有 data class。`wordCount` 计算：整本非空白字符数（对齐 `<L>` L306-308 的累计口径即可，不求字节级一致）。

- [ ] **Step 5: 跑测试确认 PASS**

Run: `./gradlew :app:testDebugUnitTest --tests "*TextFileTest*"` → 全部 PASS。

- [ ] **Step 6: Commit**

```bash
git add -A && git commit -m "feat: 移植 legado TXT 目录分析引擎(打分择优/正则分章/字数兜底/长章拆分)"
```

---

### Task 3: 阅读链路对接（readBook 改造 + Book.tocRule + 缓存失效）

**Files:**
- Modify: `<F>ui/reader/TextLoader.kt`、`<F>ui/reader/ChapterDetector.kt`、`<F>ui/reader/ReaderCache.kt`、`<F>ui/reader/ReaderCacheKeys.kt`、`<F>data/Book.kt`、`<F>data/BookDao.kt`、`<F>data/BookRepository.kt`、`<F>data/AppDatabase.kt`（MIGRATION_8_9 追加 books 列）、`<F>ui/reader/ReaderScreen.kt:145-182`、`<F>ui/reader/ReaderPreWarm.kt:29-40`、`<F>ui/reader/ReaderTtsService.kt:270-286`
- Test: 改写 `app/src/test/java/com/folio/read/BuildTxtChaptersTest.kt`

**Interfaces:**
- Consumes: `TextFile.getChapterList(text, rules, splitLongChapter): TocResult`、`TxtTocRuleRepository.enabledRules()`、`TxtTocRuleRepository.ensureDefaults()`。
- Produces: `fun readBook(context: Context, book: Book, tocRule: String?): List<Chapter>`（epub/mobi 不变，TXT 分支走引擎）；`Book.tocRule: String = ""`（DB 列）；`BookRepository.updateTocRule(id: Long, rule: String)`；`ReaderCache.memoryLoadChapters(bookId, sourceFp, tocRule)` / `memoryStoreChapters(bookId, sourceFp, tocRule, chapters)`。

- [ ] **Step 1: 改写 BuildTxtChaptersTest 为引擎行为回归（先红）**

旧测试针对 `buildTxtChapters`（将删除）。新测试（复用 Task 2 引擎）：标准 `第一章` 书 → 正则分章章名正确；无标题书 → 字数分章非空标题；`前言` 场景。此步先删除旧断言使编译失败（红），实现后转绿。

- [ ] **Step 2: Book 表加列 + DAO**

`Book.kt` 增 `val tocRule: String = ""`（注释：记忆该书选中的 TXT 目录正则，空=自动择优；对应 legado Book.tocUrl 语义）。`BookDao` 增：

```kotlin
@Query("UPDATE books SET tocRule = :rule WHERE id = :id")
suspend fun updateTocRule(id: Long, rule: String)
```

`AppDatabase.MIGRATION_8_9` 追加 `db.execSQL("ALTER TABLE books ADD COLUMN tocRule TEXT NOT NULL DEFAULT ''")`；`addMigrations` 已含 8_9。`BookRepository` 增 `suspend fun updateTocRule(id: Long, rule: String) = dao.updateTocRule(id, rule)`。

- [ ] **Step 3: readBook 接引擎**

`TextLoader.readBook` 改签名为 `readBook(context: Context, book: Book, tocRule: String?): List<Chapter>`（调用方三处同步：ReaderScreen.kt:167 传 `loaded.tocRule`；ReaderTtsService.kt:274；ReaderPreWarm.kt:35）。TXT 分支：

```kotlin
else -> {
    val rules = TxtTocRuleRepository(context).apply { ensureDefaults() }.enabledRules()
    val result = TextFile.getChapterList(readText(context, book.filePath), rules, splitLongChapter = true)
    // tocRule=null 时把择优结果写回书档案(对应 <L> book.tocUrl = getTocRule(...) 语义)
    if (tocRule == null && result.tocRule.isNotBlank()) {
        // 由调用方持久化,避免引擎依赖 DAO;此处仅返回——见下 Step 4 的持久化点
    }
    result.chapters
}
```

注意：**持久化点**在 `ReaderScreen`/`ReaderTtsService` 装载处（`repo.updateTocRule(book.id, result.tocRule)`，仅当 `book.tocRule.isBlank()` 且 `result.tocRule.isNotBlank()`），引擎保持纯函数。

- [ ] **Step 4: 切章域迁移（原文分章，逐章加工）**

`processParagraphs(text: String)` 拆为两层（对齐 legado 语义：分章在原文、加工在章内）：
- 保留行加工（trim/全角缩进/空行丢弃）为 `fun indentLines(lines: List<String>): String`；
- 删除"前言插入"分支（`firstTitleIndex`/`前言` 行插入，TextLoader.kt:78-84）——前言章由引擎产生；
- `isTitleLine` 的标题行豁免保留（`ChapterDetector.isTitleLine` 不删，`TitleRegex` 常量保留仅供缩进豁免与 `stripLeadingTitle` 无关的既有调用）；
- `buildTxtChapters` 整体删除（引擎取代），`detectChapterStarts` 删除（`ChapterDetector` 只留 `isTitleLine` + `TitleRegex`）。

epub/mobi 的 `indentContent`/`stripLeadingTitle` 不动。

- [ ] **Step 5: 缓存 key 带上规则 + 版本 bump**

`ReaderCache`：`memChapters: Triple` → 四元组（bookId, sourceFp, tocRule, chapters），load/store 增 tocRule 参数（规则变更后同 fp 不会拿到旧章节）；调用方三处传 `book.tocRule`。`ReaderCacheKeys.kt`：`ChapterRuleVersion = "ch5"`、`TextProcessVersion = "p14"`。

- [ ] **Step 6: 跑测试与编译**

Run: `./gradlew :app:testDebugUnitTest` → PASS；`./gradlew assembleDebug` → BUILD SUCCESSFUL。

- [ ] **Step 7: 真机冒烟**

安装 debug 包：标准 TXT（书架现有哈利波特等）目录正常分章；`/sdcard/我的小说/获罪书.txt` 重新打开（书架先删除再从书库添加，触发重新解析）→ 目录出现合成章节（"第N章(序号)"），翻页/跳章/进度记忆正常。

- [ ] **Step 8: Commit**

```bash
git add -A && git commit -m "feat: TXT 切章接入 legado 目录引擎;Book.tocRule 记忆择优规则;缓存版本 bump"
```

---

### Task 4: splitLongChapter 设置项

**Files:**
- Create: `<F>data/SplitChapterSettingsRepository.kt`
- Modify: `<F>ui/reader/TextLoader.kt`（readBook 参数）、`<F>ui/settings/SettingsScreen.kt`（阅读组）、`<F>ui/reader/ReaderScreen.kt`/`ReaderPreWarm.kt`/`ReaderTtsService.kt`（传参）
- Test: 无独立测试（DataStore 模式照抄，Task 3 已覆盖引擎分支）

**Interfaces:**
- Consumes: Task 2 `analyze(..., splitLongChapter)`。
- Produces: `SplitChapterSettingsRepository(context)`：`val splitLongChapter: Flow<Boolean>`（默认 true，legado `ReadConfig.splitLongChapter` 同默认值）、`suspend fun setEnabled(enabled: Boolean)`。

- [ ] **Step 1: 仓库照抄 PageTurnSettingsRepository 模式**

`dataStore(name = "split_chapter_settings")` + `booleanPreferencesKey("split_long_chapter")`，解析失败回落 true（PageTurnSettingsRepository.kt:24-29 模式）。

- [ ] **Step 2: readBook 接收开关**

`readBook(context, book, tocRule, splitLongChapter: Boolean = true)`；调用方在装载协程里 `splitRepo.splitLongChapter.first()` 取值传入（ReaderScreen/PreWarm/TTS 三处同构）。

- [ ] **Step 3: 设置页 UI**

`SettingsScreen.kt` 阅读组（:271-298）`groupItemShape(0, 1)` → `(0, 2)`，新增开关项「超长章节自动拆分」+ 说明文案（仿 legado：无目录规则的书按字数分章；单章超 100KB 时拆分），回调接 `appScope.launch { repo.setEnabled(it) }`（MainActivity.kt:848-885 接线模式）。变更后需重分章才生效——入口 UI 提示"重新打开本书生效"（ chapters 缓存 key 含 fp 不含该开关，重开书即重算）。

- [ ] **Step 4: 编译 + 真机 + Commit**

编译安装：关闭开关 → 获罪书重开 → 章节数变为按 100KB 拆分前的粒度（若规则 0 命中则整体一章分）→ 打开开关恢复。提交：

```bash
git add -A && git commit -m "feat: 超长章节自动拆分设置项(默认开,同 legado splitLongChapter)"
```

---

### Task 5: 规则管理页（列表/编辑/启停/排序/恢复内置）

**Files:**
- Create: `<F>ui/toc/TxtTocRuleManageScreen.kt`、`<F>ui/toc/TxtTocRuleEditSheet.kt`
- Modify: `<F>ui/settings/SettingsScreen.kt`（入口）、`<F>MainActivity.kt`（覆盖层状态）
- Test: 无 UI 测试，编译 + 真机

**Interfaces:**
- Consumes: `TxtTocRuleRepository`（Task 1 全部方法）。
- Produces: `@Composable fun TxtTocRuleManageScreen(onBack: () -> Unit, onPickRule: ((String) -> Unit)? = null, initialRule: String? = null)`——**双模式**：`onPickRule == null` 为管理模式；非空为"选择规则"模式（legado TxtTocRuleActivity 的 pick 语义：列表顶部高亮 `initialRule` 对应条目，点击直接回调选中的 rule 字符串）。该签名是 Task 6 预览页与书级入口的依赖。

- [ ] **Step 1: 管理页骨架**

`FolioTopBar(title="TXT 目录规则")` + `LazyColumn` 渲染 `repo.flowAll().collectAsState(initial=emptyList())`，按 serialNumber 排序（DAO 已排序）。每条 `ListItem`：headline=规则名，supporting=example（`example` 判空），trailing=启用 `Switch`（onCheckedChange → `repo.save(rule.copy(enable=it))`，语义同 legado 启停）。列表交互对齐 legado TxtRuleScreen（`<L:ui/book/toc/rule/TxtRuleScreen.kt>` L86 起的功能面，不照抄其组件树）：
- 顶栏 actions：`+`（新建）、overflow（恢复内置规则——`importBuiltInRules()`，确认对话框）。
- 条目点击 → 编辑 Sheet；长按 → 多选删除（`repo.delete`）；排序用条目 trailing 的上/下移按钮（`repo.save(rule.copy(serialNumber = 相邻规则序号))` 交换实现，替代 legado 的拖动，不引入 reorderable 依赖）。

- [ ] **Step 2: 编辑 Sheet**

`TxtTocRuleEditSheet`（ModalBottomSheet，模式仿 SettingsScreen 的 PageTurnSheet）：字段 name/rule/example（OutlinedTextField），正则实时校验（`runCatching { rule.toRegex(RegexOption.MULTILINE) }`，失败红字提示）；示例行测试框（输入样例行，显示是否命中——语义同 legado RuleEditSheet 的 onTest，`<L>TxtRuleScreen.kt:293-319`）；保存/删除按钮（`repo.save/delete`）。新规则 id=`System.currentTimeMillis()`（同 legado 默认）。

- [ ] **Step 3: pick 模式**

`initialRule != null` 时：条目列表当前书规则高亮（primary 色），点击条目调 `onPickRule(rule.rule)` 并返回；不弹编辑。

- [ ] **Step 4: 入口接线**

设置页阅读组加第三项（`groupItemShape(0, 3)`）「TXT 目录规则」→ MainActivity 覆盖层 `showTocRuleManage`（AboutScreen 模式，MainActivity.kt:899-922 同构）。

- [ ] **Step 5: 编译 + 真机 + Commit**

验证：新建规则（`^第.+章$`）保存出现于列表；禁用 -2 后打开哈利波特 → 目录失效走字数分章（验证 enabledRules 生效）；恢复内置后复原。提交：

```bash
git add -A && git commit -m "feat: TXT 目录规则管理页(增删改/启停/排序/恢复内置/选择模式)"
```

---

### Task 6: 规则预览页 + 书级入口（换规则 → 重分章）

**Files:**
- Create: `<F>ui/toc/TxtTocRulePreviewScreen.kt`
- Modify: `<F>ui/reader/TocOverlay.kt`（顶栏 action）、`<F>ui/reader/ReaderScreen.kt`（规则变更重载）、`<F>data/BookRepository.kt`
- Test: 编译 + 真机端到端

**Interfaces:**
- Consumes: `TxtTocRuleManageScreen(onPickRule=...)`（Task 5）、`TextFile.analyze`（Task 2）、`BookRepository.updateTocRule`（Task 3）、`ReaderCache`（Task 3 key 含 tocRule）。
- Produces: `@Composable fun TxtTocRulePreviewScreen(book: Book, currentRule: String, onBack: () -> Unit)`；`TocOverlay` 新参数 `onOpenTocRule: (() -> Unit)?`。

- [ ] **Step 1: 预览页（语义照 legado TxtTocRulePreviewViewModel L98-267）**

页面 = 当前书 + 全部规则列表。每条规则懒计算"命中章节数"（后台 `Dispatchers.Default` 逐条 `TextFile.analyze(readText, [rule→as pattern], splitLongChapter=true)` 计数，语义同 legado `computeChaptersLazy`；-100 占位规则显示"字数分章"其章数来自 `analyzeNoToc`）；当前书规则高亮。交互对齐 legado 预览页功能面：
- 点规则 → 展开该规则的前 50 个章节标题（`Chapter.title` 列表，legado 上限 500，Folio 收敛 50 防内存）；
- 「应用」→ 持久化（Step 2）并返回；
- 「管理规则」入口 → 跳管理页。
- 预览计算输入文本 = `readText(context, book.filePath)`（缓存于页面 remember，多规则复用）。

- [ ] **Step 2: 应用规则链路**

`BookRepository.updateTocRule` 落库 → 返回 ReaderScreen。ReaderScreen 重载机制：`LaunchedEffect(bookId)` → 改为 `LaunchedEffect(bookId, tocRuleRev)`，新增 `var tocRuleRev by remember { mutableIntStateOf(0) }`；TocOverlay 顶栏加 action「规则」（图标 `ic_toc` 复用或 `ic_settings`，`onOpenTocRule`）→ 覆盖层打开预览页（`AnimatedVisibility` 同 TocOverlay 模式）→ 应用后 `tocRuleRev++` → 重载流程：`repo.getBook`（拿新 tocRule）→ 走既有装载路径（缓存 key 含 tocRule，自动 miss 重算）→ `curChapter = book.currentChapterIndex.coerceIn(...)` 保持进度。

- [ ] **Step 3: 管理页 pick 回流**

预览页「管理规则」→ `TxtTocRuleManageScreen(onPickRule = { 返回预览页并作为当前选择 })`（同 legado OpenManagePage 语义，Activity 栈改为覆盖层状态机：预览页内嵌管理页切换）。

- [ ] **Step 4: 编译 + 真机端到端 + Commit**

端到端剧本：打开《获罪书》→ 目录页点「规则」→ 预览页看到各规则命中数（多数 0、字数分章 N 章）→ 新建规则 `^【[^】]{1,30}】$` 并应用 → 目录变为**日期章节列表** → 点某章正确跳转 → 退出重进进度与目录保持（tocRule 已记忆）→ 在管理页删除该规则 → 重开书回退到字数分章。提交：

```bash
git add -A && git commit -m "feat: 目录规则预览页与书级换规则入口(应用后重分章并记忆)"
```

---

### Task 7: 端到端回归 + 收尾

**Files:**
- Modify: `<F>ui/reader/ReaderScreen.kt`（如有冒烟发现的小修）
- Test: 全量单测 + 真机回归

- [ ] **Step 1: 全量测试与编译**

Run: `./gradlew :app:testDebugUnitTest assembleDebug` → 全绿。

- [ ] **Step 2: 真机回归清单**

1. 标准书（哈利波特/阿西莫夫/龙蛇演义等 TXT）：目录、翻页、TTS 朗读跨章、±2 章预加载日志（`FolioReader pages ch=…`）、进度记忆全部正常。
2. 获罪书：默认字数分章可用；自定义【】规则可用（如 Task 6 剧本）。
3. epub（若有样本）：不受影响（EpubParser 路径未动）。
4. 规则管理：启停/排序/编辑/恢复内置全操作一遍。
5. release 构建安装验证（`assembleRelease` + 正式签名安装）。

- [ ] **Step 3: 更新日志**

`C:\Users\Administrator\Desktop\Folio更新日志.txt` 未发布段：新增条目「TXT 目录规则系统（移植自 legado）：规则管理/预览、按字数分章兜底、超长章节拆分」（归"新增"）；改进条目「无标准章节结构的 TXT 书籍目录可用性」。

- [ ] **Step 4: Commit**

```bash
git add -A && git commit -m "docs: 目录系统移植收尾(更新日志)"
```

---

## Self-Review

- **Spec 覆盖**：规则库（Task 1）、打分择优（Task 2）、正则分章+前言（Task 2）、字数兜底（Task 2）、长章拆分（Task 2/4）、逐本记忆（Task 3/6）、管理 UI（Task 5）、预览 UI（Task 6）、书级入口（Task 6）、编码探测（Task 2 Step 1）、缓存失效（Task 3）——对齐"完整移植"范围；明确不移植项已在 Spec 摘要列出。
- **Placeholder 扫描**：无 TBD/TODO；所有"移植"步骤给了源文件+行号+适配点；UI 层因宿主组件差异给出功能面对照（legado 源行号），不属占位。
- **类型一致性**：`TocResult(chapters, tocRule, wordCount)`（Task 2 定义，Task 3 消费）；`readBook(context, book, tocRule, splitLongChapter)`（Task 3 签名，Task 4 扩参）；`TxtTocRuleManageScreen(onBack, onPickRule?, initialRule?)`（Task 5 定义，Task 6 消费）；`ReaderCache.memoryLoadChapters(bookId, sourceFp, tocRule)`（Task 3 定义，Task 6 消费）——已核对一致。
