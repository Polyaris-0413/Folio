package io.legado.app.utils

import java.security.MessageDigest

/**
 * 将字符串转化为 MD5。移植自 legado `io.legado.app.utils.MD5Utils`。
 *
 * 依赖替换：legado 原实现用 hutool 的 `cn.hutool.crypto.digest.DigestUtil.digester("MD5")`，
 * Folio 未引入 hutool；按项目规范「语言/框架原生 API 优先于成熟第三方库」改用 JDK 自带的
 * `java.security.MessageDigest`，输出语义与原实现一致：
 *  - [md5Encode] 返回 32 位小写十六进制字符串；
 *  - [md5Encode16] 返回其 [8, 24) 区间（16 位），即 hutool 版本 `substring(8, 24)` 的等价结果。
 *
 * 只保留移植集用到的两个方法；legado 原类还有 `md5Encode(InputStream)`，牵入 hutool 的流式
 * digest，Folio 目录链路（`BookChapter.getFileName`、`TextFile.getChapterList`）不需要。
 */
object MD5Utils {

    private const val HEX_DIGITS = "0123456789abcdef"

    fun md5Encode(str: String?): String {
        val digest = MessageDigest.getInstance("MD5").digest((str ?: "").toByteArray(Charsets.UTF_8))
        val hex = StringBuilder(digest.size * 2)
        for (b in digest) {
            val v = b.toInt() and 0xFF
            hex.append(HEX_DIGITS[v ushr 4]).append(HEX_DIGITS[v and 0x0F])
        }
        return hex.toString()
    }

    fun md5Encode16(str: String): String {
        var reStr = md5Encode(str)
        reStr = reStr.substring(8, 24)
        return reStr
    }
}
