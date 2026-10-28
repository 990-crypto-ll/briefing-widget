package com.cooper.briefingwidget

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.HapticFeedbackConstants
import android.widget.Button
import android.widget.TextView
import android.widget.Toast

/** 详情页：英文摘要在前、中文译文在后，底部有「查看原文」按钮。 */
class DetailActivity : Activity() {

    companion object {
        const val EXTRA_TITLE = "extra_title"
        const val EXTRA_TITLE_ZH = "extra_title_zh"
        const val EXTRA_DETAIL = "extra_detail"
        const val EXTRA_DETAIL_ZH = "extra_detail_zh"
        const val EXTRA_SOURCE_NAME = "extra_source_name"
        const val EXTRA_SOURCE_URL = "extra_source_url"
        const val EXTRA_SECTION = "extra_section"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_detail)

        val title = intent.getStringExtra(EXTRA_TITLE) ?: ""
        val titleZh = intent.getStringExtra(EXTRA_TITLE_ZH) ?: ""
        val detail = intent.getStringExtra(EXTRA_DETAIL) ?: ""
        val detailZh = intent.getStringExtra(EXTRA_DETAIL_ZH) ?: ""
        val sourceName = intent.getStringExtra(EXTRA_SOURCE_NAME) ?: ""
        val sourceUrl = intent.getStringExtra(EXTRA_SOURCE_URL) ?: ""
        val section = intent.getStringExtra(EXTRA_SECTION) ?: ""

        val tag = findViewById<TextView>(R.id.detail_tag)
        when (section) {
            "finance" -> {
                tag.text = "金融"
                tag.setBackgroundResource(R.drawable.bg_tag_finance)
                tag.setTextColor(getColor(R.color.accent_finance))
            }
            "ai" -> {
                tag.text = "AI"
                tag.setBackgroundResource(R.drawable.bg_tag_ai)
                tag.setTextColor(getColor(R.color.accent_ai))
            }
            else -> {
                tag.text = "快讯"
                tag.setBackgroundResource(R.drawable.bg_tag_other)
                tag.setTextColor(getColor(R.color.accent_other))
            }
        }

        findViewById<TextView>(R.id.detail_title).text = title
        findViewById<TextView>(R.id.detail_title_zh).text = titleZh
        findViewById<TextView>(R.id.detail_body).text = detail
        findViewById<TextView>(R.id.detail_body_zh).text = detailZh
        findViewById<TextView>(R.id.detail_source).text =
            if (sourceName.isNotEmpty()) "来源：$sourceName" else ""

        findViewById<Button>(R.id.share_button).setOnClickListener { v ->
            v.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
            val shareText = buildString {
                append(title)
                if (titleZh.isNotEmpty()) append("\n").append(titleZh)
                if (sourceUrl.isNotEmpty()) append("\n").append(sourceUrl)
            }
            val send = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, shareText)
            }
            startActivity(Intent.createChooser(send, "分享这条快报"))
        }

        findViewById<Button>(R.id.open_source_button).setOnClickListener { v ->
            v.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
            if (sourceUrl.isEmpty()) {
                Toast.makeText(this, "这条没有原文链接", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val viewIntent = Intent(Intent.ACTION_VIEW, Uri.parse(sourceUrl))
            startActivity(viewIntent)
        }
    }
}
