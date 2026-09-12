package io.legado.app.utils

import java.text.DecimalFormat
import java.util.regex.Pattern

/**
 * legado `io.legado.app.utils.StringUtils` 的裁剪版：只保留目录链路用到的字数格式化。
 *
 * 保留成员（[wordCountFormat] 两个重载、[isNumeric]、[wordCountFormatter]）与 legado 源文件
 * 逐字节同源，未做任何改写。其余成员属 legado 的工具杂货铺（Base64 / GZIP / 日期区间 /
 * 中文数字互转 / trim 等），与目录系统无关；`TextFile` 只用 `wordCountFormat` 计算每章字数
 * 与全书字数。
 *
 * 若后续搬运的 legado 代码需要此类中的其他成员，请回到源文件
 * `<legado>/app/src/main/java/io/legado/app/utils/StringUtils.kt` 复制对应函数，
 * 不要另起实现。
 */
@Suppress("unused", "MemberVisibilityCanBePrivate")
object StringUtils {

    private val wordCountFormatter by lazy {
        DecimalFormat("#.#")
    }

    /**
     * 是否数字
     */
    fun isNumeric(str: String): Boolean {
        val pattern = Pattern.compile("-?[0-9]+")
        val isNum = pattern.matcher(str)
        return isNum.matches()
    }

    fun wordCountFormat(words: Int): String {
        var wordsS = ""
        if (words > 0) {
            if (words > 10000) {
                val df = wordCountFormatter
                wordsS = df.format(words * 1.0f / 10000f.toDouble()) + "万字"
            } else {
                wordsS = words.toString() + "字"
            }
        }
        return wordsS
    }

    fun wordCountFormat(wc: String?): String {
        if (wc == null) return ""
        var wordsS = ""
        if (isNumeric(wc)) {
            val words: Int = wc.toInt()
            if (words > 0) {
                if (words > 10000) {
                    val df = wordCountFormatter
                    wordsS = df.format(words * 1.0f / 10000f.toDouble()) + "万字"
                } else {
                    wordsS = words.toString() + "字"
                }
            }
        } else {
            wordsS = wc
        }
        return wordsS
    }
}
