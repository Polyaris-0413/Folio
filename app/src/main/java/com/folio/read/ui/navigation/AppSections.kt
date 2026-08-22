package com.folio.read.ui.navigation

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import com.folio.read.R

/**
 * 底部导航的各个 section,移植自 Finito 的 AppSections。
 * 采用轻量状态切换(selectedSection),暂不引入导航库。
 */
enum class AppSections(
    @StringRes val labelRes: Int,
    @DrawableRes val iconRes: Int,
) {
    Shelf(R.string.nav_shelf, R.drawable.ic_book),
    Stats(R.string.nav_stats, R.drawable.ic_nav_stats),
    Settings(R.string.nav_settings, R.drawable.ic_nav_settings),
}

/** 底栏显示顺序:书架 → 统计 → 设置 */
val mainRoutes: List<AppSections> = listOf(
    AppSections.Shelf,
    AppSections.Stats,
    AppSections.Settings,
)
