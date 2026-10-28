package com.cooper.briefingwidget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.view.View
import android.widget.RemoteViews

/**
 * 桌面小组件：顶部是「每日金融 + AI 快报」和日期，下面是当天标题列表
 * （每条英文标题在前、中文翻译紧随其后，最多显示 8 条）。
 *
 * - 点击小组件主体打开 App 主界面；
 * - 点击「刷新」按钮通过 PendingIntent 广播 [ACTION_REFRESH] 重新拉取 feed.json；
 * - 系统也会按 appwidget-provider 里 updatePeriodMillis 周期性调用 onUpdate；
 * - 拉取在后台线程完成，失败时 [FeedRepository] 自动回退缓存/内置数据。
 */
class BriefingWidgetProvider : AppWidgetProvider() {

    companion object {
        const val ACTION_REFRESH = "com.cooper.briefingwidget.ACTION_REFRESH"

        private val ITEM_VIEW_IDS = intArrayOf(
            R.id.widget_item_1, R.id.widget_item_2, R.id.widget_item_3, R.id.widget_item_4,
            R.id.widget_item_5, R.id.widget_item_6, R.id.widget_item_7, R.id.widget_item_8
        )

        fun updateAllWidgets(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val component = ComponentName(context, BriefingWidgetProvider::class.java)
            val ids = manager.getAppWidgetIds(component)
            if (ids.isEmpty()) return

            // 先用已缓存数据立即渲染，避免等待网络时小组件空白
            render(context, manager, ids, FeedRepository.loadFeedBlockingFromCacheOnly(context))

            Thread {
                val feed = FeedRepository.loadFeedBlocking(context)
                render(context, manager, ids, feed)
            }.start()
        }

        private fun render(
            context: Context,
            manager: AppWidgetManager,
            ids: IntArray,
            feed: Feed?
        ) {
            val views = RemoteViews(context.packageName, R.layout.widget_layout)
            views.setTextViewText(R.id.widget_date, feed?.date ?: "")

            val items = feed?.items ?: emptyList()
            ITEM_VIEW_IDS.forEachIndexed { index, viewId ->
                val item = items.getOrNull(index)
                if (item == null) {
                    views.setViewVisibility(viewId, View.GONE)
                } else {
                    views.setViewVisibility(viewId, View.VISIBLE)
                    val zh = if (item.titleZh.isNotEmpty()) "\n${item.titleZh}" else ""
                    views.setTextViewText(viewId, "• ${item.title}$zh")
                }
            }

            // 点击小组件任意位置（标题、日期、每条快报、空白处）都打开 App
            val openIntent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val openPending = PendingIntent.getActivity(
                context, 0, openIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widget_root, openPending)
            views.setOnClickPendingIntent(R.id.widget_title, openPending)
            views.setOnClickPendingIntent(R.id.widget_date, openPending)
            // 每条快报单独绑定：点哪条就直接打开哪条的详情
            ITEM_VIEW_IDS.forEachIndexed { index, viewId ->
                val item = items.getOrNull(index)
                if (item == null) {
                    views.setOnClickPendingIntent(viewId, openPending)
                } else {
                    val detailIntent = Intent(context, DetailActivity::class.java).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                        putExtra(DetailActivity.EXTRA_TITLE, item.title)
                        putExtra(DetailActivity.EXTRA_TITLE_ZH, item.titleZh)
                        putExtra(DetailActivity.EXTRA_DETAIL, item.detail)
                        putExtra(DetailActivity.EXTRA_DETAIL_ZH, item.detailZh)
                        putExtra(DetailActivity.EXTRA_SOURCE_NAME, item.sourceName)
                        putExtra(DetailActivity.EXTRA_SOURCE_URL, item.sourceUrl)
                        putExtra(DetailActivity.EXTRA_SECTION, item.section)
                    }
                    val detailPending = PendingIntent.getActivity(
                        context, 100 + index, detailIntent,
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                    )
                    views.setOnClickPendingIntent(viewId, detailPending)
                }
            }

            // 刷新按钮
            val refreshIntent = Intent(context, BriefingWidgetProvider::class.java).apply {
                action = ACTION_REFRESH
            }
            val refreshPending = PendingIntent.getBroadcast(
                context, 1, refreshIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widget_refresh, refreshPending)

            for (id in ids) {
                manager.updateAppWidget(id, views)
            }
        }
    }

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        updateAllWidgets(context)
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == ACTION_REFRESH) {
            updateAllWidgets(context)
        }
    }
}
