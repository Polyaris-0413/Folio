package com.folio.read.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/** GitHub 最新 release 信息 */
data class LatestRelease(
    /** 版本号,如 v1.0.1(与 release tag 一致) */
    val version: String,
    /** release 页面地址(浏览器打开供下载) */
    val htmlUrl: String,
)

/** 检查更新结果:区分「有新版本」「仓库无 release」「失败」,供 UI 分别反馈 */
sealed interface UpdateCheckResult {
    data class Latest(val release: LatestRelease) : UpdateCheckResult
    /** 仓库尚无任何 release(HTTP 404) */
    data object NoRelease : UpdateCheckResult
    /** 网络失败/其他错误 */
    data object Failed : UpdateCheckResult
}

/**
 * 检查 GitHub 最新 release(公开接口,无需认证)。
 * 区分 404(无 release)与网络失败,调用方据此给出不同反馈。
 */
class UpdateChecker {

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    suspend fun checkLatest(): UpdateCheckResult = withContext(Dispatchers.IO) {
        runCatching {
            val request = Request.Builder()
                .url("https://api.github.com/repos/Polyaris-0413/Folio/releases/latest")
                .header("Accept", "application/vnd.github+json")
                .build()
            client.newCall(request).execute().use { response ->
                when {
                    response.code == 404 -> UpdateCheckResult.NoRelease
                    !response.isSuccessful -> UpdateCheckResult.Failed
                    else -> {
                        val json = JSONObject(response.body?.string().orEmpty())
                        val version = json.optString("tag_name", "")
                        if (version.isBlank()) {
                            UpdateCheckResult.Failed
                        } else {
                            UpdateCheckResult.Latest(
                                LatestRelease(version = version, htmlUrl = json.optString("html_url", "")),
                            )
                        }
                    }
                }
            }
        }.getOrElse { UpdateCheckResult.Failed }
    }
}

/**
 * 版本号比较(仅支持 x.y.z 三段数字)。
 * 返回 >0 表示 a 新于 b,=0 相同,<0 表示 a 旧于 b。
 * 非法格式按 0 处理(避免比较异常)。
 */
fun compareVersions(a: String, b: String): Int {
    fun parts(v: String): List<Int> = v.trim().removePrefix("v").removePrefix("V")
        .split('.')
        .map { it.toIntOrNull() ?: 0 }
    val pa = parts(a)
    val pb = parts(b)
    for (i in 0 until maxOf(pa.size, pb.size)) {
        val x = pa.getOrElse(i) { 0 }
        val y = pb.getOrElse(i) { 0 }
        if (x != y) return x - y
    }
    return 0
}
