package com.folio.read.ui.theme

import android.app.WallpaperManager
import android.content.Context
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.materialkolor.hct.Hct
import com.materialkolor.scheme.DynamicScheme
import com.materialkolor.scheme.SchemeNeutral
import java.util.concurrent.ConcurrentHashMap

/**
 * 由种子色经 HCT 算法公式生成整套 M3 配色方案。
 * 使用 Neutral 变体:背景/表面为中性色(灰/白),主题色只出现在
 * primary 等强调角色(FAB、选中态、链接、强调文字)。
 */
fun folioColorScheme(seedColor: Color, darkTheme: Boolean): ColorScheme =
    toColorScheme(
        SchemeNeutral(
            sourceColorHct = Hct.fromInt(seedColor.toArgb()),
            isDark = darkTheme,
            contrastLevel = 0.0,
        ),
        darkTheme,
    )

/**
 * Material You 动态取色配色:壁纸主色做种子,沿用 Neutral 变体(与静态主题同算法,仅换种子)。
 * 曾用 TonalSpot 变体(官方动态取色默认),实测其配色新树首帧渲染 150-200ms,
 * 300ms 主题动画里新树长时间未就绪 → 切换露底黑闪;Neutral 变体渲染与静态一致(10ms)。
 * 非组合纯计算,可缓存/后台预热;系统 @Composable 版 dynamicLightColorScheme 无法预取,
 * 切换首帧壁纸提取+生成会造成 100ms+ 掉帧,故改用本路径。
 */
internal fun dynamicColorScheme(seedArgb: Int, darkTheme: Boolean): ColorScheme =
    toColorScheme(
        SchemeNeutral(
            sourceColorHct = Hct.fromInt(seedArgb),
            isDark = darkTheme,
            contrastLevel = 0.0,
        ),
        darkTheme,
    )

// 动态取色种子与配色缓存:壁纸提取是 Binder 调用、配色生成较贵,
// 冷启动后台预热到内存,用户切动态取色时直接命中,主题切换首帧不卡。
// 预热在 IO 线程写入、UI 线程(Crossfade 双树)读取,须用并发容器防 HashMap 结构损坏
@Volatile
internal var dynamicSeedArgb: Int = FolioSeedColor.toArgb()
internal val dynamicSchemeCache = ConcurrentHashMap<Boolean, ColorScheme>()

/** 冷启动预热:提取壁纸主色做种子,预生成深浅两套动态配色(IO 线程调用) */
internal fun warmDynamicSchemes(context: Context) {
    val seed = runCatching {
        WallpaperManager.getInstance(context).getWallpaperColors(WallpaperManager.FLAG_SYSTEM)?.primaryColor?.toArgb()
    }.getOrNull() ?: FolioSeedColor.toArgb()
    dynamicSeedArgb = seed
    dynamicSchemeCache[false] = dynamicColorScheme(seed, darkTheme = false)
    dynamicSchemeCache[true] = dynamicColorScheme(seed, darkTheme = true)
}

/** material-color-utilities 配色方案 → 全套 M3 角色映射 */
private fun toColorScheme(scheme: DynamicScheme, darkTheme: Boolean): ColorScheme =
    if (darkTheme) {
        darkColorScheme(
            primary = Color(scheme.primary),
            onPrimary = Color(scheme.onPrimary),
            primaryContainer = Color(scheme.primaryContainer),
            onPrimaryContainer = Color(scheme.onPrimaryContainer),
            secondary = Color(scheme.secondary),
            onSecondary = Color(scheme.onSecondary),
            secondaryContainer = Color(scheme.secondaryContainer),
            onSecondaryContainer = Color(scheme.onSecondaryContainer),
            tertiary = Color(scheme.tertiary),
            onTertiary = Color(scheme.onTertiary),
            tertiaryContainer = Color(scheme.tertiaryContainer),
            onTertiaryContainer = Color(scheme.onTertiaryContainer),
            error = Color(scheme.error),
            onError = Color(scheme.onError),
            errorContainer = Color(scheme.errorContainer),
            onErrorContainer = Color(scheme.onErrorContainer),
            background = Color(scheme.background),
            onBackground = Color(scheme.onBackground),
            surface = Color(scheme.surface),
            onSurface = Color(scheme.onSurface),
            surfaceVariant = Color(scheme.surfaceVariant),
            onSurfaceVariant = Color(scheme.onSurfaceVariant),
            outline = Color(scheme.outline),
            outlineVariant = Color(scheme.outlineVariant),
            inverseSurface = Color(scheme.inverseSurface),
            inverseOnSurface = Color(scheme.inverseOnSurface),
            inversePrimary = Color(scheme.inversePrimary),
            surfaceTint = Color(scheme.surfaceTint),
            surfaceDim = Color(scheme.surfaceDim),
            surfaceBright = Color(scheme.surfaceBright),
            surfaceContainerLowest = Color(scheme.surfaceContainerLowest),
            surfaceContainerLow = Color(scheme.surfaceContainerLow),
            surfaceContainer = Color(scheme.surfaceContainer),
            surfaceContainerHigh = Color(scheme.surfaceContainerHigh),
            surfaceContainerHighest = Color(scheme.surfaceContainerHighest),
        )
    } else {
        lightColorScheme(
            primary = Color(scheme.primary),
            onPrimary = Color(scheme.onPrimary),
            primaryContainer = Color(scheme.primaryContainer),
            onPrimaryContainer = Color(scheme.onPrimaryContainer),
            secondary = Color(scheme.secondary),
            onSecondary = Color(scheme.onSecondary),
            secondaryContainer = Color(scheme.secondaryContainer),
            onSecondaryContainer = Color(scheme.onSecondaryContainer),
            tertiary = Color(scheme.tertiary),
            onTertiary = Color(scheme.onTertiary),
            tertiaryContainer = Color(scheme.tertiaryContainer),
            onTertiaryContainer = Color(scheme.onTertiaryContainer),
            error = Color(scheme.error),
            onError = Color(scheme.onError),
            errorContainer = Color(scheme.errorContainer),
            onErrorContainer = Color(scheme.onErrorContainer),
            background = Color(scheme.background),
            onBackground = Color(scheme.onBackground),
            surface = Color(scheme.surface),
            onSurface = Color(scheme.onSurface),
            surfaceVariant = Color(scheme.surfaceVariant),
            onSurfaceVariant = Color(scheme.onSurfaceVariant),
            outline = Color(scheme.outline),
            outlineVariant = Color(scheme.outlineVariant),
            inverseSurface = Color(scheme.inverseSurface),
            inverseOnSurface = Color(scheme.inverseOnSurface),
            inversePrimary = Color(scheme.inversePrimary),
            surfaceTint = Color(scheme.surfaceTint),
            surfaceDim = Color(scheme.surfaceDim),
            surfaceBright = Color(scheme.surfaceBright),
            surfaceContainerLowest = Color(scheme.surfaceContainerLowest),
            surfaceContainerLow = Color(scheme.surfaceContainerLow),
            surfaceContainer = Color(scheme.surfaceContainer),
            surfaceContainerHigh = Color(scheme.surfaceContainerHigh),
            surfaceContainerHighest = Color(scheme.surfaceContainerHighest),
        )
    }

@Composable
fun FolioTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    // 结构恒定:静态配色在函数体顶层 remember 缓存,配色选择收敛在单个 remember 内。
    // 主题 Crossfade 双树组合结构一致时组合槽才可复用;曾把静态/动态配色分到 if/else
    // 组合分支,开关拨动时双树结构不同 → 整树完全重建 → 100ms+ 掉帧(黑闪)
    val staticLight = remember { folioColorScheme(FolioSeedColor, darkTheme = false) }
    val staticDark = remember { folioColorScheme(FolioSeedColor, darkTheme = true) }
    val colorScheme = remember(darkTheme, dynamicColor) {
        if (dynamicColor) {
            dynamicSchemeCache[darkTheme] ?: dynamicColorScheme(dynamicSeedArgb, darkTheme)
        } else if (darkTheme) {
            staticDark
        } else {
            staticLight
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
