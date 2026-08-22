package com.folio.read.data

import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

/**
 * AI 书名净化:基于本地解析后的书名做二次清理。
 * 只处理「本地规则没删干净的残留噪声」,提示词约束只删不增(防止 AI 把本地已滤掉的词加回);
 * 失败(网络/空结果)返回 null,调用方保留本地解析结果;Semaphore 限制并发请求数。
 */
class BookTitleCleaner(
    private val aiConfig: AiConfig,
    private val maxConcurrent: Int = 3,
) {
    private val client = LlmClient(aiConfig)
    private val gate = Semaphore(maxConcurrent)

    suspend fun clean(title: String): String? = try {
        gate.withPermit {
            client.chat(
                systemPrompt = "基于以下书名,只删除版本、网站、广告等噪声词,保留书名主体,不要添加任何内容,只输出清理后的书名",
                userMessage = title,
            )
        }.trim().ifBlank { null }
    } catch (e: Exception) {
        null
    }
}
