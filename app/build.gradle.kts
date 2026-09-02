plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.aboutlibraries)
    alias(libs.plugins.ksp)
    // Baseline Profile 录制:app 内建生成任务 :app:generateBaselineProfile,录制产物自动落
    // src/release/generated/baselineProfiles(release 变体专属源集,与 src/main 手写版共存)
    alias(libs.plugins.androidx.baselineprofile)
}

android {
    namespace = "com.folio.read"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.folio.read"
        minSdk = 31
        targetSdk = 37
        versionCode = 5
        versionName = "2.2.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            // release 编译优化全开:R8 混淆压缩 + 资源收缩 + 默认优化规则
            optimization {
                enable = true
            }
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            // 临时用 debug 签名(基准测试/本机验证要安装;正式发布换成 D 盘 key)
            signingConfig = signingConfigs.getByName("debug")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
    }
}

// 手动登记非依赖来源的开源代码:AboutLibraries 自动扫描只认 Gradle 依赖,
// 从其他项目移植的代码放 collect.configPath 的 libraries/ 下(JSON 定义库与协议),
// 构建时合并进 aboutlibraries.json,才会出现在「开源声明」列表(含协议全文)。
aboutLibraries {
    collect {
        configPath = layout.projectDirectory.dir("config")
    }
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.navigation.compose)
    // Baseline Profile:启动/首帧热点类提前 AOT,需配合 app/src/main/generated/baselineProfiles/baseline-prof.txt
    implementation(libs.androidx.profileinstaller)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    implementation(libs.androidx.documentfile)
    implementation(libs.androidx.media)
    implementation(libs.material.color.utilities)
    implementation(libs.aboutlibraries.compose.m3)
    implementation(libs.okhttp)
    implementation(libs.jsoup)
    testImplementation(libs.junit)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    // Baseline Profile 录制模块:generateBaselineProfile 消费其产物,合并进 release
    baselineProfile(project(":baselineprofile"))
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}