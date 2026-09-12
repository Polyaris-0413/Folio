# legado TXT 目录系统逐字节移植 实施计划

> **本计划取代** `docs/superpowers/plans/2026-09-04-legado-toc-system.md`（commit `76ac6b7`，仅作参考）。
> 取代原因见附甲「旧计划的三处不严谨」。

**Goal:** 把 legado-with-MD3 3.26.15 的本地 TXT 目录系统移植进 Folio，**以源码逐字节搬运为主体**：凡能原样搬的文件一律原样搬，只在「宿主边界」写声明式替身（新写配线代码），**不重新表达任何算法**。

**判定结论:** 可行。关键发现是——只要把 legado 那一圈宿主替身补齐，`TextFile.kt`（488 行，全部切章算法所在）可以做到**与源文件逐字节相同**。旧计划认为必须把「512KB 流式分块 + 字节偏移」改写成「整本 String + 字符偏移」，那个改写是**不必要的**，且正是用户指出的语义漂移。

**源/**目标约定: `<L>` = `C:/Users/Administrator/Downloads/legado-with-MD3-3.26.15/legado-with-MD3-3.26.15`；`<F>` = Folio 仓库根。

---

## 一、移植范围（判定依据）

扫描全量 import 闭包（1465 个源文件）后，legado 的「目录系统」实为**三套互不依赖的东西**：

| 子系统 | 组成 | 对 Folio 的适用性 |
|---|---|---|
| A. 本地 TXT 目录规则库 + 引擎 | `TxtTocRule` / `TxtTocRuleDao` / `TxtTocRuleRepository` / `TextFile` / `EncodingDetect` / `assets/defaultData/txtTocRule.json` | **本计划移植对象** |
| B. 在线书源目录 | `RefreshTocUseCase` / `GetChapterContentUseCase` / `BookChapterList` / `TocRule` / `WebBook` / `AnalyzeRule` | Folio 是纯本地阅读器，**无适用场景，不移植** |
| C. 目录 UI | `TocScreen`(1055) / `TocViewModel`(793) / `TxtRuleScreen`(462) / preview(511) | 逐字节搬运需连带 legado 自有设计系统（`LegadoTheme` + `ui.widget.components.*` 30+ 组件 + Koin + `BaseRuleViewModel` + `BaseComposeActivity` + `R.string` 100+ 键），与项目 AGENTS.md「设计语言为 Material Design / 遵循 M3 规范」直接冲突 → **UI 骨架在 Folio 侧用 M3 实现；其中可搬运的纯逻辑（契约/规则 CRUD）仍逐字搬运**，见 Phase 6 |

**明确不移植**（属 legado 自身生态，非目录系统核心，Folio 无对应需求）：
章节落库（`chapters` 表 / `BookChapter` 的 Room 注解，Folio 章节只在进程内存）、`upKind()` 书架分类富化、`Book.wordCount` 书架展示、规则的 WebDAV/上传/剪贴板导入导出、`ContentProcessor` 的净化与重分段规则、在线目录三件套。

---

## 二、移植清单（三种搬运方式，界限严格）

### A 类：逐字节搬运（目标：`git diff --no-index <L>/… <F>/…` 输出为空）

| 源文件 | 目标 | 行数 |
|---|---|---|
| `<L>/app/src/main/java/io/legado/app/data/entities/TxtTocRule.kt` | `<F>/app/src/main/java/io/legado/app/data/entities/TxtTocRule.kt` | 29 |
| `<L>/…/io/legado/app/data/dao/TxtTocRuleDao.kt` | `<F>/…/io/legado/app/data/dao/TxtTocRuleDao.kt` | 56 |
| `<L>/…/io/legado/app/data/repository/TxtTocRuleRepository.kt` | `<F>/…/io/legado/app/data/repository/TxtTocRuleRepository.kt` | 51 |
| `<L>/…/io/legado/app/model/localBook/TextFile.kt` | `<F>/…/io/legado/app/model/localBook/TextFile.kt` | 488 |
| `<L>/…/io/legado/app/utils/EncodingDetect.kt` | `<F>/…/io/legado/app/utils/EncodingDetect.kt` | 98 |
| `<L>/…/io/legado/app/utils/Utf8BomUtils.kt` | `<F>/…/io/legado/app/utils/Utf8BomUtils.kt` | 38 |
| `<L>/…/io/legado/app/exception/NoStackTraceException.kt` | `<F>/…/io/legado/app/exception/NoStackTraceException.kt` | 17 |
| `<L>/…/io/legado/app/exception/EmptyFileException.kt` | `<F>/…/io/legado/app/exception/EmptyFileException.kt` | 5 |
| `<L>/…/io/legado/app/lib/icu4j/*.java`（8 个） | `<F>/…/io/legado/app/lib/icu4j/*.java` | 3088 |
| `<L>/app/src/main/assets/defaultData/txtTocRule.json` | `<F>/app/src/main/assets/defaultData/txtTocRule.json` | 210 |

前提：`appDb` / `DefaultData` / `AppLog` / `LocalBook` / `Book` 等替身（C 类）先就位，否则编译不过。**搬运方式为整文件复制，禁止边看边重打。**

### B 类：裁剪式搬运（保留成员逐字节不动，只删除引向无关子系统的成员）

| 源文件 | 保留 | 删除（及删除理由） |
|---|---|---|
| `BookChapter.kt`(178) | 全部 17 个字段、`equals`/`hashCode`、`primaryStr()`、`titleMD5`、`getFileName()`、`getFontName()` | `RuleDataInterface` 及其 `variableMap`/`putVariable`/`putBigVariable`/`getBigVariable`（拉入 GSON + `RuleBigDataHelp`）；`getDisplayTitle()`（拉入 `AppConfig`/`ChineseUtils`/`ReplaceRule`/`RegexTimeoutException`/`toastOnUi`）；`getAbsoluteURL()`（拉入 `AnalyzeUrl`/`NetworkUtils`）；`@Entity`/`@Parcelize` 注解（Folio 不落库章节表） |
| `MD5Utils.kt` | `md5Encode(String?)`、`md5Encode16(String)` 的**语义**（md5 hex 小写；16 位 = `substring(8,24)`） | hutool `cn.hutool.crypto.digest` 依赖 → 换 `java.security.MessageDigest`。依据 AGENTS.md §2「语言/框架原生 API > 成熟第三方库」，不为此新增 hutool 依赖 |
| `StringUtils.kt`(280+) | `wordCountFormat(words: Int)`、`wordCountFormat(wc: String?)` 及其专用成员 | 该文件是 legado 工具杂货铺（Base64/GZIP/日期/中文数字），TextFile 只用 `wordCountFormat` 两个重载 |

### C 类：宿主替身（Folio 侧新写的配线，不含任何算法）

替身的存在意义：让 A 类文件**一行都不用改**。每个替身都必须写明它替代了 legado 的什么、以及为什么这样替代。

| 替身 | 路径 | 替代内容 |
|---|---|---|
| `AppLog` | `<F>/…/io/legado/app/constant/AppLog.kt` | 委托 Folio 已有 `com.folio.read.util.AppLog`；legado 的 `put(msg, e)` → `AppLog.w` |
| `appDb` | `<F>/…/io/legado/app/data/AppDatabaseShim.kt` | 顶层 `val appDb` 返回 `com.folio.read.data.AppDatabase.getInstance(appCtx)` |
| `DefaultData` | `<F>/…/io/legado/app/help/DefaultData.kt` | 只保留 `txtTocRules`（assets `defaultData/txtTocRule.json` + `org.json` 解析）。legado 原 `DefaultData` 是全应用默认数据总装载器，逐字搬会经 `DictRule → AnalyzeRule → BackstageWebView → …` 连到全应用（实测闭包 357 文件/6.5 万行），故只搬此一支 |
| `PortBook`（以 `Book` 类名置于 `io.legado.app.data.entities`） | `<F>/…/io/legado/app/data/entities/Book.kt` | TextFile/BookChapter 触及的字段与方法：`bookUrl`/`originName`/`name`/`intro`/`charset`/`tocUrl`/`wordCount`/`kind`/`latestChapterTime`/`totalChapterNum`/`isLocal` + `fileCharset()`（= `charset(charset ?: "UTF-8")`，与 legado `Book.kt:179` 同义）+ `getSplitLongChapter()`（由 Folio 设置项提供）。另加一个 `openStream` 钩子供 JVM 测试注入流 |
| `Book` 扩展 | `<F>/…/io/legado/app/help/book/BookExtensions.kt` | `isLocalModified()`：legado 语义「文件比上次解析更新」；Folio 的失效判定已在 `ReaderCache` 层以源文件指纹为 key，故此处恒 `false` 并由 Folio 层保证每次重解析时构造全新的 `Book`(charset=null,tocUrl="")，行为等价。`upKind()`：legado 用于书架分类富化，返回 `Unit` 且 TextFile 不读其结果 → 空实现 |
| `LocalBook` | `<F>/…/io/legado/app/model/localBook/LocalBook.kt` | 只提供 `getBookInputStream(book)`：把 SAF 文件按指纹缓存到 `cacheDir` 后返回 `FileInputStream`。**必须可 seek + `available()` 正确**，因为 `TextFile.getContent` 依赖 `bis.available()` 与 `bis.skip(start)` 做随机读（legado 本地路径即是 FileInputStream）。Folio 的 `EpubParser` 已有「SAF→cacheDir 临时文件」先例，沿用同一做法 |

### D 类：Folio 侧新增/改造（宿主适配）

| 项 | 内容 |
|---|---|
| Room | `AppDatabase` v8 → v9：注册 `TxtTocRule::class` + `txtTocRuleDao()`；`MIGRATION_8_9` 建表 SQL **从 `<L>/app/schemas/io.legado.app.data.AppDatabase/94.json` 的 `txtTocRules` createSql 取值**，不手写 |
| assets | `app/src/main/assets/defaultData/txtTocRule.json` |
| `Book.tocRule` | 新增列，语义 = legado `Book.tocUrl`（记住本书择优选中的正则；空 = 自动择优）。DAO + Repository 各一个方法 |
| 装载点 | `ReaderScreen.kt:167`、`ReaderTtsService.kt:274`、`ReaderPreWarm.kt:35` 三处传 `book.tocRule`，并把择优结果回写 |
| 缓存 | `ReaderCache.memChapters` 键加 `tocRule`；`ReaderCacheKeys` bump `ch4→ch5` / `p13→p14`（分章结果变化，旧页表必须失效） |
| 进度重定位 | 分章边界改变后 `currentChapterIndex` 会指错章。Folio 现为 `coerceIn`；legado 用 `BookHelp.getDurChapter`（标题 Jaccard 相似度）对齐。**本计划先按 legado 语义搬对齐算法**（Phase 5 决策点，见附乙） |
| 文案 | 「超长章节自动拆分」开关（对应 `getSplitLongChapter()`，legado 默认 `true`） |

### E 类：测试与闸门

| 闸门 | 内容 |
|---|---|
| 防漂移 diff（主闸门） | 脚本 `scripts/check-port-drift.sh`：对 A 类每个文件跑 `diff --no-index`，**必须零输出**；对 B 类跑 diff，输出必须只落在清单声明的删除范围内。这是「没有语义漂移」的最强证据——A 类文件逐字节相同则行为不可能不同 |
| 单元测试 | 默认规则 JSON 解析（26 条、id 负值、-2 规则串逐字相等）；`MD5Utils.md5Encode16` 已知向量；`getTocRule` 打分择优（间隔 >1000 计分、平局后者胜）——用 `PortBook.openStream` 注入内存流，纯 JVM 可测 |
| 真机回归 | 标准 TXT 目录正常；《获罪书》（`【2013年8月12日，雷阵雨】` 日记体）从「整本一章」变为按字数分章可跳转；自定义 `^【[^】]{1,30}】$` 规则生效；翻页/TTS 跨章/进度记忆不受影响 |

依 AGENTS.md §7：每阶段交付后必须**编译 + 安装 + 打开应用**。

---

## 三、实施阶段

- **Phase 0 前置：修正 schema 链（已按「落实」而非「删除」处理）。** `app/schemas/com.folio.read.data.AppDatabase/` 原有 1..8 与 **10**，缺 9；`10.json` 版本为 10，含 `txtTocRules` 表与 `books.tocRule` 列，而源码 `AppDatabase` 为 v8、`Book` 无 `tocRule`——这是某次未提交实验的残留（已被 git 跟踪）。它不是垃圾而是**上次同类尝试生成的真实 schema**，因此处置改为「让实现与之对齐」：按 v8→v9（建 `txtTocRules`）→v10（加 `books.tocRule`）两步迁移实现，使 Room 依次生成 `9.json` 与 `10.json`。验证标准是本次生成的 `10.json` 必须与仓库既有那份逐字节一致（含 `identityHash`）——已达成，故 schema 链 1..10 完整且无残留歧义。
- **Phase 1 C 类替身前置** → 编译通过。
- **Phase 2 A 类数据层**（`TxtTocRule`/Dao/Repository + assets + Room v9 + D 类 Room 项）→ 编译 + 单测。
- **Phase 3 A 类工具层**（icu4j / EncodingDetect / Utf8BomUtils / 异常）+ B 类（BookChapter / MD5Utils / StringUtils）→ 编译 + 单测。
- **Phase 4 A 类引擎**（`TextFile.kt`）+ `PortBook` + `LocalBook` + `Book` 扩展 → 编译 + 打分单测。
- **Phase 5 阅读链路对接**（`Book.tocRule`、三个装载点、缓存 key、进度重定位）→ 编译 + 安装 + 真机。
- **Phase 6 规则管理页 / 预览页**（Folio M3；`TxtTocRuleContract` 等纯契约与规则 CRUD 逻辑逐字搬运）→ 编译 + 安装 + 真机。
- **Phase 7 防漂移闸门入仓 + 全量回归 + 更新日志 + 提交。**

---

## 附甲：旧计划（`76ac6b7`）的三处不严谨

1. **不必要的语义改写。** 旧计划 Task 2 把 `TextFile.analyze(pattern)` 从「512KB 流式分块 + `curOffset` 字节偏移」改写为「整本 String + 字符偏移」，并自述需「保留字节计数（`toByteArray().size`）使阈值与 legado 同义」——承认了改写会漂、再用补丁把阈值语义贴回去。本计划实测证明该改写不必要：补齐宿主替身后 `TextFile.kt` 可逐字节不变。
2. **UI 层实为照理解重写。** Task 5/6 自述「列表交互对齐 legado TxtRuleScreen 的功能面，不照抄其组件树」。功能面对齐后重画组件树，正是用户界定的语义漂移。本计划的做法：可搬的纯逻辑（`TxtTocRuleContract`、规则 CRUD）逐字搬，Composable 骨架因宿主设计系统不同而在 Folio 侧用 M3 实现，并把「哪些搬、哪些画」逐项写死在清单里。
3. **事实性错误。** 旧计划 Global Constraints 写「minSdk 31」，但 legado 与 Folio 当前**都是 minSdk 26**（legado `app/build.gradle.kts:52`；Folio 于 `562e8ed` 从 31 降到 26），且 `android.icu` 自 API 24 起可用，minSdk 26 无碍。另外它写「AppDatabase v8→v9」而当时已存在 v10 的残留 schema，未察觉链已断裂。

## 附乙：本计划未决的一点（实现中会按 legado 语义处理，若你不同意请叫停）

**分章结果变化后的阅读进度重定位。** 旧单正则 → 新规则库/字数兜底后，同一本书的章节数与边界都会变，`Book.currentChapterIndex` 会指向错误的章。Folio 现状是 `coerceIn`（粗暴截断）。legado 的做法是 `BookHelp.getDurChapter`：用旧章名与新章名列表做相似度匹配，找回原位置。本计划按 legado 语义实现该对齐（属"移植"而非"取舍"）。若你希望保持 Folio 现有的 `coerceIn` 行为，请在 Phase 5 开始前叫停。
