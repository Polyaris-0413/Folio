package com.folio.read.ui.components

/*
 * 封面书名排版(CoverTitle)移植自 legado(https://github.com/gedoor/legado)
 * 经 legado-with-MD3(https://github.com/HapeLee/legado-with-MD3)参考
 * SPDX-License-Identifier: GPL-3.0-only
 */

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import android.graphics.Typeface
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.text.TextUtils
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.materialkolor.palettes.TonalPalette
import kotlin.math.abs
import kotlin.math.floor

/**
 * 封面渐变:官方 TonalPalette(Google material-color-utilities)按书标识哈希生成,每本书专属不撞色。
 * 色相跳过 55..100° 黄绿段(低 chroma 下呈土色,观感差);chroma 36 = 官方「表达色」档;
 * tone 50→35 上浅下深:白字对比度上端 ≈4.49:1、下端 ≈7.78:1,覆盖大文本 3:1 与普通文本 4.5:1
 * (依据 tone 即 CIE L*,对比度=(1.05)/(Y+0.05),Y=((L*+16)/116)³)。
 * 曾试 45→30(对比度最稳但整体偏暗,用户感觉暗),回退到 50→35 折中档。
 * 种子用 dedupKey(文件稳定标识)而非书名:书名净化(本地/AI)改变时颜色不跳变,
 * 颜色始终是"这本书"的身份色而非"这个名字"的。
 */
fun bookCoverGradient(seed: String): List<Color> {
    // 哈希 → 0..309,再跳过 55..99 土色段映射到 0..354,保证不撞土色
    val raw = abs(seed.hashCode()) % 310
    val hue = if (raw < 55) raw.toDouble() else (raw + 45).toDouble()
    val palette = TonalPalette.fromHueAndChroma(hue, 36.0)
    return listOf(
        Color(palette.tone(50)),
        Color(palette.tone(35)),
    )
}

/**
 * 横排书名判定:书名含拉丁字母占比 >30% 强制横排(与书架 CoverArtwork 同口径)。
 * 阿拉伯数字不触发全横排——竖排时由 renderCoverBitmap 做「数字横排、中文竖排」混合,
 * 避免整体横排把中文也横排(2026-08-26 用户反馈「有数字就全横排很诡异」)。
 */
fun isHorizontalTitle(title: String): Boolean =
    title.count { it in 'A'..'Z' || it in 'a'..'z' }.toFloat() / title.length > 0.3f

/** 封面位图进程级缓存:书架重组(退出阅读页弹回)时目的地被销毁重建,remember 会丢,位图须跨组合存活 */
object CoverCache {
    private val cache = mutableMapOf<String, Bitmap>()

    fun get(key: String, render: () -> Bitmap): Bitmap = cache.getOrPut(key, render)
}

/** 竖排封面位图:渐变底 + 白字竖排(字号=宽÷8,列间距=字宽×0.2,行距=字宽×1.2×1.05),与 CoverTitle 竖排分支同口径 */
fun renderCoverBitmap(width: Int, height: Int, title: String, gradient: List<Color>): Bitmap {
    val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bmp)
    canvas.drawRect(
        0f, 0f, width.toFloat(), height.toFloat(),
        Paint().apply {
            shader = LinearGradient(
                0f, 0f, 0f, height.toFloat(),
                gradient.map { it.toArgb() }.toIntArray(), null, Shader.TileMode.CLAMP,
            )
        },
    )
    val textSize = width / 8f
    val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        this.textSize = textSize
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        color = android.graphics.Color.WHITE
    }
    // 竖排混合排版:连续阿拉伯数字合并成一个 token(横排占一行),其余单字各自竖排。
    // 中文竖排规范=数字转横贴字旁(2026-08-26 用户反馈「数字竖排被拆成两列很怪」,而整体横排
    // 又把中文也横排了「更诡异」)→ 只横排数字,中文保持竖排)。
    val chars = title.filter { it.isLetterOrDigit() }
    val tokens = buildList {
        var i = 0
        while (i < chars.length) {
            if (chars[i].isDigit()) {
                var j = i
                while (j < chars.length && chars[j].isDigit()) j++
                add(chars.substring(i, j))
                i = j
            } else {
                add(chars[i].toString())
                i++
            }
        }
    }
    val charHeight = textSize * 1.2f
    val perColumn = floor(height * 0.6f / charHeight).toInt().coerceAtLeast(1)
    val columns = tokens.chunked(perColumn)
    val columnGap = textSize * 0.2f
    val totalW = columns.size * textSize + (columns.size - 1) * columnGap
    // drawText 的 x 是文字左缘:列块左缘对齐居中(此前多加了 textSize/2 导致整体右偏)
    var x = (width - totalW) / 2f
    // 每行盒高与 CoverTitle 的 lineHeight 一致;基线按字体度量把字形垂直居中于行盒
    val lineH = charHeight * 1.05f
    val fm = textPaint.fontMetrics
    // 数字横排画笔:与正文同字号同字体;超列宽时按比例缩小,保证不溢出封面
    val digitPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = textPaint.typeface
        color = android.graphics.Color.WHITE
    }
    for (column in columns) {
        val blockH = column.size * lineH
        val blockTop = (height - blockH) / 2f
        var y = blockTop + (lineH - (fm.descent - fm.ascent)) / 2f - fm.ascent
        for (token in column) {
            if (token.length == 1 && !token[0].isDigit()) {
                canvas.drawText(token, x, y, textPaint)
            } else {
                // 数字 token 横排:以 textSize 测量,超列宽则等比缩小后居中于列位
                digitPaint.textSize = textSize
                val tw = digitPaint.measureText(token)
                if (tw > textSize) digitPaint.textSize = textSize * (textSize / tw)
                canvas.drawText(token, x + (textSize - digitPaint.measureText(token)) / 2f, y, digitPaint)
            }
            y += lineH
        }
        x += textSize + columnGap
    }
    return bmp
}

/** 横排封面位图(仅非 Compose 场景用,书架横排仍走 Compose CoverTitle):
 * 与 CoverTitle 同口径——字号=宽÷8、粗体白字、居中、最多 3 行、占宽 80%;
 * StaticLayout 负责折行与省略。 */
fun renderHorizontalCoverBitmap(width: Int, height: Int, title: String, gradient: List<Color>): Bitmap {
    val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bmp)
    canvas.drawRect(
        0f, 0f, width.toFloat(), height.toFloat(),
        Paint().apply {
            shader = LinearGradient(
                0f, 0f, 0f, height.toFloat(),
                gradient.map { it.toArgb() }.toIntArray(), null, Shader.TileMode.CLAMP,
            )
        },
    )
    val textSize = width / 8f
    val textPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        this.textSize = textSize
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        color = android.graphics.Color.WHITE
    }
    val layout = StaticLayout.Builder
        .obtain(title, 0, title.length, textPaint, (width * 0.8f).toInt())
        .setAlignment(Layout.Alignment.ALIGN_CENTER)
        .setMaxLines(3)
        .setEllipsize(TextUtils.TruncateAt.END)
        .setIncludePad(false)
        .build()
    canvas.save()
    canvas.translate((width - layout.width) / 2f, (height - layout.height) / 2f)
    layout.draw(canvas)
    canvas.restore()
    return bmp
}
