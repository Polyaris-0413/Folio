// Baseline Profile 录制模块(producer):跑 BaselineProfileGenerator 仪器测试,
// 把真实用到的类/方法录成 baseline-prof.txt,由 :app 的 generateBaselineProfile 消费并合并。
// 运行:./gradlew :app:generateBaselineProfile(设备需 Android 13+ 或 root)。
// 注意:须用虚拟机(模拟器)录制,真机跑会有问题(2026-09-01 用户实测;此前的"自动用已连接真机"说法与实际不符)
plugins {
    id("com.android.test")
    alias(libs.plugins.androidx.baselineprofile)
}

android {
    namespace = "com.folio.read.baselineprofile"
    compileSdk {
        version = release(37)
    }
    defaultConfig {
        minSdk = 31
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    // 指向被测 app:录制时先装 :app,再跑本模块测试规则操作它
    targetProjectPath = ":app"
    // AGP 9:com.android.test 模块自测(无 androidTest 源集,规则类放 src/main)
    experimentalProperties["android.experimental.self-instrumenting"] = true
}

dependencies {
    implementation(libs.androidx.junit)
    implementation(libs.androidx.benchmark.macro.junit4)
}
