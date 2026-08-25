package com.folio.read

import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 录制 Baseline Profile:真机按真实路径操作 App(冷启动→书架滑动→进书翻页→返回→切设置),
 * 系统记录实际用到的类/方法,生成比手写清单更精确的 baseline-prof.txt。
 * 运行:./gradlew :app:generateBaselineProfile(自动用已连接真机,设备需 Android 13+ 或 root)
 * 产物:自动复制到 app/src/release/generated/baselineProfiles/baseline-prof.txt
 * (release 变体专属源集,与 src/main 手写版共存:release 用录制版,其他变体用手写版)
 */
@RunWith(AndroidJUnit4::class)
class BaselineProfileGenerator {

    @get:Rule
    val baselineProfileRule = BaselineProfileRule()

    @Test
    fun generate() = baselineProfileRule.collect(packageName = "com.folio.read") {
        startActivityAndWait()
        // 等 App 首屏(书架)渲染完成
        device.waitForIdle()

        val w = device.displayWidth
        val h = device.displayHeight

        // 书架向上滑两屏(触发列表滚动与卡片惰性加载)
        repeat(2) {
            device.swipe(w / 2, h * 3 / 4, w / 2, h / 4, 30)
            device.waitForIdle()
        }
        // 滑回顶部,点开网格中第一本书
        device.swipe(w / 2, h / 4, w / 2, h * 3 / 4, 30)
        device.waitForIdle()
        device.click(w / 2, h * 2 / 7)
        device.waitForIdle()

        // 阅读页翻三页(点右半屏)
        repeat(3) {
            device.click(w * 3 / 4, h / 2)
            device.waitForIdle()
        }

        // 返回书架,切到设置页(两栏底部导航最右)
        device.pressBack()
        device.waitForIdle()
        device.click(w * 5 / 6, h - 300)
        device.waitForIdle()
    }
}
