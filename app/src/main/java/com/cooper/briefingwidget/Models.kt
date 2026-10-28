package com.cooper.briefingwidget

/** feed.json 的数据模型，与 Cooper 侧生成的 feed.json 结构一一对应。 */

data class Quote(
    val symbol: String,
    val name: String,
    val price: Double,
    val changePercent: Double,
    val currency: String
)

data class BriefingItem(
    /** "finance" 或 "ai" */
    val section: String,
    /** 英文标题（在前） */
    val title: String,
    /** 中文标题（紧随其后） */
    val titleZh: String,
    /** 英文摘要（在前） */
    val detail: String,
    /** 中文摘要（紧随其后） */
    val detailZh: String,
    val sourceName: String,
    val sourceUrl: String
)

data class Feed(
    val date: String,
    val fetchedAt: String,
    val quotes: List<Quote>,
    val items: List<BriefingItem>
) {
    fun itemsOf(section: String): List<BriefingItem> = items.filter { it.section == section }
}
