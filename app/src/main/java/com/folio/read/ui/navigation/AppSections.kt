package com.folio.read.ui.navigation

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import com.folio.read.R

/**
 * 底部导航的各个 section,移植自 Finito 的 AppSections。
 * 采用轻量状态切换(selectedSection),暂不引入导航库。
 * 每个 tab 两套图标:未选中描边(FILL0)/选中实心(FILL1),由 AppNavBar 交叉淡化切换。
 */
enum class AppSections(
    @StringRes val labelRes: Int,
    @DrawableRes val iconRes: Int,
    @DrawableRes val selectedIconRes: Int,
) {
    Shelf(R.string.nav_shelf, R.drawable.ic_book, R.drawable.ic_book_filled),
    Library(R.string.nav_shelf_library, R.drawable.ic_shelves, R.drawable.ic_shelves_filled),
}

/** 底栏显示顺序:阅读 → 书架 */
val mainRoutes: List<AppSections> = listOf(
    AppSections.Shelf,
    AppSections.Library,
)
