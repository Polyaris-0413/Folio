# 「TXT 目录规则」页 设计优化方案

> 依据：项目 AGENTS.md 要求遵循 Material 3 技能（`C:/Users/Administrator/.zcode/skills/material-3`），
> 并以 Folio 已建立的页面约定为对照基准。评审对象：
> `app/src/main/java/com/folio/read/ui/toc/TxtTocRuleOverlay.kt`、`TocRules.kt`。
> 结论先行：**只有顶栏那条真正需要新图标，且有不用图标的替代做法；其余 7 条不需要任何新图标。**

## 一、P0：三处硬伤（建议先修）

### P0-1 「章数」放错层级，且会随试算进度跳高
- 现状：`TxtTocRuleOverlay.kt:148-158` 把章数放进 `overlineContent`；未算出时不渲染该槽
  （`156` 行 `else -> Unit`）。
- 问题：`overline` 是 headline 上方的标签层级（M3 `component-catalog.md:836`），而"章数"是
  **每条可变元数据**，属 `trailing-supporting-text`。槽位出现/消失会让 ListItem 在
  Two-line 与 Three-line 之间反复跳高——后台逐条试算填入时列表在抖。
- 修改：章数移出 `overline`；若保留该槽则**恒渲染**（未算完时占位），颜色由 `primary` 改
  `onSurfaceVariant`。对应 Folio 约定：尾部元数据一律 `trailingContent` + `bodyMedium` +
  `onSurfaceVariant`（`SettingsScreen.kt:290-295, 370-375`）。
- 图标：不需要。

### P0-2 `primary` 被两件事同时用，选中信号被稀释
- 现状：当前规则名用 `primary`（`:162-163`），**每一条**的章数也用 `primary`（`:261-267`）。
- 问题：全列表都出现 primary 时"当前规则"不再突出；且 11sp 的 labelSmall 用 primary 压在
  `surfaceContainerHigh` 上，对比度无保证（M3 要求正文 4.5:1）。
- 修改：当前规则改用 **trailing `ic_check` + primary tint** 双通道表达（Folio 选择面板既有做法
  `SettingsScreen.kt:514-521, 708-715`），章数改用 `onSurfaceVariant`。
- 图标：复用现有 `R.drawable.ic_check`。

### P0-3 一行里塞了三种交互，编辑不可发现
- 现状：`:178-187` —— 整行 `combinedClickable`（点击=应用规则/长按=编辑）套着一个自带
  `onCheckedChange` 的 Switch；没有可见的编辑入口，只靠一句提示文字"长按可编辑"。
- 问题：与 Folio 自身约定冲突——**Folio 所有开关行都不整行可点击**，只有导航型行才加
  `.clickable`（`SettingsScreen.kt:178-192, 255-272, 309-321` vs `:299, 307, 351`）。
  误触风险高，且长按对 TalkBack 不友好（缺 `onLongClickLabel`）。
- 修改：行尾加 `IconButton(ic_more_vert)` → `DropdownMenu`（编辑 / 删除）；"应用规则"在 pick
  模式改为 trailing `ic_check` 的显式选择。去掉整行 `combinedClickable`。
- 图标：复用现有 `ic_more_vert`；删除项可用 `ic_shelf_delete`。

## 二、P1：与项目自身设计语言脱节

| 编号 | 现状 | 修改 | 依据 | 需要新图标 |
|---|---|---|---|---|
| P1-4 | 顶栏平铺两个 TextButton（「新建」「恢复内置」） | 「新建」改为 icon button；「恢复内置」降级到 `ic_more_vert` 溢出菜单（它是删除全部预置规则的破坏性操作，不该与「新建」同级） | `navigation-patterns.md:301-313` | **是**：`add`、`settings_backup_restore`（不加也可：用文字菜单项） |
| P1-5 | 规则项是裸 ListItem，无分组卡片/分隔/leading | 套用 `listItemColors()` + `groupItemShape(index,count)` + `groupItemSpacing`；或至少加 `HorizontalDivider(outlineVariant)` | `typography-and-shape.md:163-169`、`SKILL.md:510`、`ListItemExt.kt:40,47,91-96` | 否 |
| P1-6 | 无宽屏约束；头部/空态用裸 padding，且 `top = 6.dp` 破 4dp 网格 | `widthIn(max = 600.dp)` + `contentPadding`；`6.dp` → `8.dp`；间距收敛为命名常量 | `SettingsScreen.kt:160-166`、`layout-and-responsive.md:20,46,733` | 否 |

**注**：P1-5 尤其值得做——这个页面的**入口本身就是一张分组卡片**（`SettingsScreen.kt:301-308`，
`ic_toc`），进去之后视觉语言断裂。

## 三、P2：表单项

| 编号 | 现状 | 修改 | 依据 | 需要新图标 |
|---|---|---|---|---|
| P2-7 | 三个 `OutlinedTextField` 塞进 `AlertDialog` 的 text 槽 | 改为 `ModalBottomSheet`（Folio 多字段表单的既有范式，IME 安全且可滚动） | `component-catalog.md:176`、`SettingsScreen.kt:592-660` | 否 |
| P2-8 | 只有正则校验有错误提示；名称缺失无提示；三个字段都无 placeholder | 名称空时 `isError` + `supportingText`；补 placeholder；字段间距 12dp | `component-catalog.md:545-581`、`SKILL.md:486-491` | 否 |
| P2-9 | 删除按钮在表单正文里；删除/恢复的确认按钮与保存按钮都没有强调色/层级 | 删除移到动作区并用 `colorScheme.error`；确认按钮用具体动词并设 error 色；保存用 filled | `SKILL.md:80,480`、`color-system.md:162`、`component-catalog.md:17` | 否 |
| P2-10 | 删除确认按钮复用了书架模块的「移除」文案；恢复确认用通用「确定」 | 各加一条具体动词文案 | `component-catalog.md:359-361` | 否 |
| P2-11 | 同一界面两个入口动效不一致：阅读页无过渡，设置页有滑动 | 阅读页也用 `AnimatedVisibility(fadeIn + slideInHorizontally, tween(XL))` | `Animation.kt:16-18` | 否 |

另：`CountLabel` 显式设 `labelSmall` 属冗余（overline 默认即 `labelSmall`）；supporting 行在
有 example 时显示示例、无 example 时显示原始正则，**同一槽位语义漂移**，正则建议加等宽或前缀标注。

## 四、要下载的图标（Material Symbols）

按 AGENTS.md：图标由使用者从 Material Symbols 下载到 `C:/Users/Administrator/Downloads`。
当前该目录下**没有任何图标文件**。

| 图标名 | 用途 | 可替代方案 |
|---|---|---|
| `add` | 顶栏「新建」 | 保持文字按钮（现状） |
| `settings_backup_restore` | 溢出菜单「恢复内置」 | 菜单项只用文字，不带图标 |
| `edit` | 溢出菜单「编辑」 | 用文字菜单项，或复用 `ic_more_vert` 入口 |

现有可直接复用：`ic_more_vert`、`ic_arrow_back`、`ic_toc`、`ic_check`、`ic_shelf_delete`。

## 五、建议的实施顺序

1. P0-1、P0-2（信息层级与选中信号）——同一处改动，风险低、观感提升最直接，不需要新图标
2. P0-3（拆解行内交互）——涉及交互语义变化，需实机过一遍
3. P1-5（列表套分组卡片）——让页面与入口的视觉语言接上
4. P2-7、P2-8（表单换 ModalBottomSheet + 补校验）
5. P1-4（顶栏图标化，等图标到位）、P1-6、P2-9～P2-11

每步做完按项目规范编译 + 安装 + 真机过目。
