package com.cooper.briefingwidget

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.content.res.ColorStateList
import android.os.Bundle
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.view.HapticFeedbackConstants
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.ListView
import android.widget.TextView
import android.widget.Toast
import java.time.LocalDate
import java.util.Locale

/**
 * App 主界面（紧凑版）：深蓝头部卡只占顶部一小条（标题+日期一行、行情单行横滑、
 * 未读数+状态一行），右下角悬浮刷新按钮；右上角齿轮进设置弹窗改 feed 地址；
 * 头部 ‹ › 可翻看历史日期的快报。列表为卡片式，点过的条目会标记已读并变淡。
 */
class MainActivity : Activity() {

    private lateinit var dateText: TextView
    private lateinit var quotesText: TextView
    private lateinit var statusText: TextView
    private lateinit var unreadChip: TextView
    private lateinit var listView: ListView

    private var currentFeed: Feed? = null
    private var latestDate: String? = null
    private var viewingDate: String? = null // null 表示正在看最新一期

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        dateText = findViewById(R.id.main_date)
        quotesText = findViewById(R.id.main_quotes)
        statusText = findViewById(R.id.status_text)
        unreadChip = findViewById(R.id.unread_chip)
        listView = findViewById(R.id.briefing_list)

        findViewById<Button>(R.id.refresh_button).setOnClickListener { v ->
            v.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
            viewingDate = null
            refresh(force = true)
        }
        findViewById<TextView>(R.id.settings_button).setOnClickListener { v ->
            v.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
            showSettingsDialog()
        }
        findViewById<TextView>(R.id.prev_day_button).setOnClickListener { v ->
            v.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
            shiftDay(-1)
        }
        findViewById<TextView>(R.id.next_day_button).setOnClickListener { v ->
            v.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
            shiftDay(+1)
        }

        listView.setOnItemClickListener { _, view, position, _ ->
            val adapter = listView.adapter as? BriefingAdapter ?: return@setOnItemClickListener
            val row = adapter.getRow(position)
            if (row is Row.Entry) {
                view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
                markRead(currentFeed?.date ?: "", row.item)
                adapter.notifyDataSetChanged()
                updateUnreadChip()
                val item = row.item
                val intent = Intent(this, DetailActivity::class.java).apply {
                    putExtra(DetailActivity.EXTRA_TITLE, item.title)
                    putExtra(DetailActivity.EXTRA_TITLE_ZH, item.titleZh)
                    putExtra(DetailActivity.EXTRA_DETAIL, item.detail)
                    putExtra(DetailActivity.EXTRA_DETAIL_ZH, item.detailZh)
                    putExtra(DetailActivity.EXTRA_SOURCE_NAME, item.sourceName)
                    putExtra(DetailActivity.EXTRA_SOURCE_URL, item.sourceUrl)
                    putExtra(DetailActivity.EXTRA_SECTION, item.section)
                }
                startActivity(intent)
                overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
            }
        }

        refresh(force = false)
    }

    private fun showSettingsDialog() {
        val input = EditText(this).apply {
            setText(FeedRepository.getFeedUrl(this@MainActivity))
            setSingleLine(true)
            hint = "feed.json 地址"
            setPadding(48, 32, 48, 8)
        }
        AlertDialog.Builder(this)
            .setTitle("快报数据地址")
            .setView(input)
            .setPositiveButton("保存") { _, _ ->
                val url = input.text.toString().trim()
                if (url.isEmpty()) {
                    Toast.makeText(this, "地址不能为空", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                FeedRepository.setFeedUrl(this, url)
                Toast.makeText(this, "已保存，正在刷新", Toast.LENGTH_SHORT).show()
                viewingDate = null
                refresh(force = true)
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun shiftDay(delta: Long) {
        val base = viewingDate ?: currentFeed?.date ?: latestDate ?: return
        val target = try {
            LocalDate.parse(base).plusDays(delta).toString()
        } catch (e: Exception) {
            return
        }
        val latest = latestDate
        if (latest != null && target > latest) {
            Toast.makeText(this, "已经是最新一期", Toast.LENGTH_SHORT).show()
            return
        }
        if (latest != null && target == latest) {
            viewingDate = null
            refresh(force = true)
            return
        }
        statusText.text = "正在加载 $target …"
        FeedRepository.loadFeedForDate(this, target) { feed ->
            runOnUiThread {
                if (feed != null) {
                    viewingDate = target
                    showFeed(feed)
                    statusText.text = "历史快报 · $target"
                } else {
                    Toast.makeText(this, "$target 还没有快报", Toast.LENGTH_SHORT).show()
                    statusText.text = ""
                }
            }
        }
    }

    private fun refresh(force: Boolean) {
        statusText.text = "正在加载…"
        FeedRepository.loadFeed(this, force) { feed, message ->
            runOnUiThread {
                if (viewingDate != null) return@runOnUiThread // 正在看历史，不让最新刷新覆盖
                if (feed != null) {
                    if (latestDate == null || feed.date > latestDate!!) latestDate = feed.date
                    showFeed(feed)
                    statusText.text = message ?: ""
                } else {
                    statusText.text = message ?: "加载失败"
                }
            }
        }
    }

    private fun showFeed(feed: Feed) {
        currentFeed = feed
        dateText.text = feed.date
        quotesText.text = buildQuotesText(feed.quotes)

        val rows = mutableListOf<Row>()
        val finance = feed.itemsOf("finance")
        if (finance.isNotEmpty()) {
            rows.add(Row.Header("金融消息"))
            finance.forEach { rows.add(Row.Entry(it)) }
        }
        val ai = feed.itemsOf("ai")
        if (ai.isNotEmpty()) {
            rows.add(Row.Header("AI 用法与应用"))
            ai.forEach { rows.add(Row.Entry(it)) }
        }
        val grouped = finance.size + ai.size
        if (grouped < feed.items.size) {
            rows.add(Row.Header("其他"))
            feed.items.filter { it.section != "finance" && it.section != "ai" }
                .forEach { rows.add(Row.Entry(it)) }
        }
        listView.adapter = BriefingAdapter(rows)
        updateUnreadChip()
    }

    // ---------- 已读标记 ----------

    private fun readKey(date: String, item: BriefingItem): String = "$date|${item.title}"

    private fun readKeys(): MutableSet<String> {
        val prefs = getSharedPreferences("briefing_read", MODE_PRIVATE)
        return prefs.getStringSet("read_keys", emptySet())?.toMutableSet() ?: mutableSetOf()
    }

    private fun isRead(item: BriefingItem): Boolean =
        readKeys().contains(readKey(currentFeed?.date ?: "", item))

    private fun markRead(date: String, item: BriefingItem) {
        val keys = readKeys()
        keys.add(readKey(date, item))
        // 只保留最近 400 条已读记录，避免无限增长
        val trimmed = keys.toList().takeLast(400).toSet()
        getSharedPreferences("briefing_read", MODE_PRIVATE).edit()
            .putStringSet("read_keys", trimmed).apply()
    }

    private fun updateUnreadChip() {
        val feed = currentFeed
        if (feed == null) {
            unreadChip.text = ""
            return
        }
        val keys = readKeys()
        val unread = feed.items.count { !keys.contains(readKey(feed.date, it)) }
        unreadChip.text = if (unread > 0) "未读 $unread 条" else "已全部读完"
    }

    // ---------- 行情与分组样式 ----------

    /** 行情条：名称浅色、价格白色、涨跌红涨绿跌，分隔符弱化。 */
    private fun buildQuotesText(quotes: List<Quote>): CharSequence {
        val nameColor = getColor(R.color.header_sub)
        val priceColor = getColor(R.color.header_text)
        val upColor = getColor(R.color.up_red)
        val downColor = getColor(R.color.down_green)
        val sb = SpannableStringBuilder()
        quotes.forEachIndexed { index, q ->
            if (index > 0) {
                val s0 = sb.length
                sb.append("   ·   ")
                sb.setSpan(ForegroundColorSpan(nameColor), s0, sb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
            var start = sb.length
            sb.append(q.name)
            sb.setSpan(ForegroundColorSpan(nameColor), start, sb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            start = sb.length
            sb.append(String.format(Locale.US, " %.2f ", q.price))
            sb.setSpan(ForegroundColorSpan(priceColor), start, sb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            val up = q.changePercent >= 0
            val sign = if (up) "▲" else "▼"
            start = sb.length
            sb.append(String.format(Locale.US, "%s%.2f%%", sign, kotlin.math.abs(q.changePercent)))
            sb.setSpan(
                ForegroundColorSpan(if (up) upColor else downColor),
                start, sb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            )
        }
        return sb
    }

    private fun sectionStyle(section: String): SectionStyle = when (section) {
        "finance" -> SectionStyle("金融", R.drawable.bg_tag_finance, R.color.accent_finance)
        "ai" -> SectionStyle("AI", R.drawable.bg_tag_ai, R.color.accent_ai)
        else -> SectionStyle("快讯", R.drawable.bg_tag_other, R.color.accent_other)
    }

    private data class SectionStyle(val label: String, val bgRes: Int, val colorRes: Int)

    sealed class Row {
        data class Header(val text: String) : Row()
        data class Entry(val item: BriefingItem) : Row()
    }

    private inner class BriefingAdapter(private val rows: List<Row>) : BaseAdapter() {

        fun getRow(position: Int): Row = rows[position]

        override fun getCount(): Int = rows.size

        override fun getItem(position: Int): Any = rows[position]

        override fun getItemId(position: Int): Long = position.toLong()

        override fun getViewTypeCount(): Int = 2

        override fun getItemViewType(position: Int): Int =
            if (rows[position] is Row.Header) 0 else 1

        override fun isEnabled(position: Int): Boolean = rows[position] is Row.Entry

        override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
            val inflater = LayoutInflater.from(parent?.context ?: this@MainActivity)
            return when (val row = rows[position]) {
                is Row.Header -> {
                    val view = convertView ?: inflater.inflate(R.layout.list_item_header, parent, false)
                    val section = when {
                        row.text.contains("金融") -> "finance"
                        row.text.contains("AI") -> "ai"
                        else -> "other"
                    }
                    val style = sectionStyle(section)
                    view.findViewById<TextView>(R.id.header_text).text = row.text
                    view.findViewById<TextView>(R.id.header_sub).text = when (section) {
                        "finance" -> "FINANCE"
                        "ai" -> "AI · APPS"
                        else -> "MORE"
                    }
                    view.findViewById<View>(R.id.header_dot).backgroundTintList =
                        ColorStateList.valueOf(getColor(style.colorRes))
                    view
                }
                is Row.Entry -> {
                    val view = convertView ?: inflater.inflate(R.layout.list_item_briefing, parent, false)
                    val style = sectionStyle(row.item.section)
                    val tag = view.findViewById<TextView>(R.id.item_tag)
                    tag.text = style.label
                    tag.setBackgroundResource(style.bgRes)
                    tag.setTextColor(getColor(style.colorRes))
                    view.findViewById<TextView>(R.id.item_title).text = row.item.title
                    view.findViewById<TextView>(R.id.item_title_zh).text = row.item.titleZh
                    view.findViewById<TextView>(R.id.item_source).text = row.item.sourceName
                    view.alpha = if (isRead(row.item)) 0.55f else 1.0f
                    view
                }
            }
        }
    }
}
