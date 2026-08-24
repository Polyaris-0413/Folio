package com.folio.read.ui.theme

/**
 * 全局动画数值(时长 + 缓动),分档管理:
 * 同类动作用同一档;新增动画先查档位复用,没有合适档位才新增,避免数值散落。
 */
object AnimationTokens {
    /** 微交互(ms):圆角过渡、小部件状态变化 */
    const val Micro = 100

    /** 一般动效(ms):卡片展开/收起 */
    const val Medium = 250

    /** 页面/内容切换(ms):tab 淡入淡出 */
    const val Large = 300

    /** 页面跳转轻推(ms):1/16 屏宽水平滑动,移植自 Book's Story(其页面统一用该过渡) */
    const val XL = 350
}
