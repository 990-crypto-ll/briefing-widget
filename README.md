# 每日金融 + AI 快报 · 安卓 App + 桌面小组件

一个原生安卓应用：每天早上读取一份云端发布的 `feed.json` 快报数据，在手机桌面小组件和 App 内以「英文在前、中文紧随其后」的双语形式展示当天最重要的金融消息与 AI 用法/应用。

- 包名：`com.cooper.briefingwidget`
- 当前版本：**1.3**（versionCode 4）
- 纯原生实现，**零第三方库**：Kotlin + Activity + AppWidgetProvider/RemoteViews，网络用 `HttpURLConnection`，JSON 解析用系统自带的 `org.json`
- minSdk 26 / targetSdk 34，AGP 8.5.2 + Kotlin 1.9.24 + Gradle 8.7

## 功能

**桌面小组件**
- 显示当天日期、行情条和双语标题列表，每 30 分钟系统自动更新，带手动刷新按钮
- 点任意一条快报**直达该条详情**；点标题/空白处打开 App

**App**
- 紧凑头部（约占屏幕 15%）：标题+日期一行、行情单行横滑、未读数一行；右下悬浮刷新按钮
- 金融 / AI 分组卡片列表，行情涨跌按红涨绿跌着色
- **已读标记**：点过的条目变淡，头部显示未读条数
- **历史快报**：日期旁 ‹ › 翻看往日快报（读取同仓库的 `feed-<日期>.json`）
- 详情页：英文摘要在前、中文译文在后，带「查看原文」和「分享」
- ⚙ 设置弹窗可修改 feed 数据地址（App 与小组件共用）
- 深色模式跟随系统；拉取失败时自动回退到上次缓存，首次安装用内置首期数据，不会白屏

## 数据从哪来

App 本身不抓新闻。数据由外部每天早上 8:00（Asia/Shanghai）整理生成并发布到数据仓库
[briefing-feed](https://github.com/990-crypto-ll/briefing-feed)：

- 最新一期：`feed.json`，固定地址
  `https://raw.githubusercontent.com/990-crypto-ll/briefing-feed/main/feed.json`（已写死为 App 默认地址）
- 历史各期：`feed-2026-10-28.json` 这样的带日期副本

拉取链路：`FeedRepository` 拉取 → 成功写缓存 `filesDir/feed_cache.json` → 失败读缓存 → 无缓存读 APK 内置 `assets/feed.json`。

## feed.json 结构

```json
{
  "date": "2026-10-28",
  "fetchedAt": "2026-10-28T06:15:49.585Z",
  "quotes": [
    { "symbol": "QQQ", "name": "纳指100 ETF (QQQ)", "price": 721.25,
      "changePercent": 1.2707, "currency": "USD" }
  ],
  "items": [
    {
      "section": "finance",
      "title": "English title",
      "titleZh": "中文标题",
      "detail": "English summary…",
      "detailZh": "中文摘要…",
      "sourceName": "www.wsj.com",
      "sourceUrl": "https://…"
    }
  ]
}
```

- `section` 取值：`finance`（金融消息）/ `ai`（AI 用法与应用）
- `title`/`detail` 为英文，`titleZh`/`detailZh` 为中文翻译，界面永远英文在前、中文在后
- 注意：QQQ 的 `name` 是「纳指100 ETF (QQQ)」——721.25 是这只 ETF 的价格（美元/股），不是纳斯达克100指数点位

## 构建

1. 用 Android Studio（Hedgehog 或更新版本，自带 JDK 17）打开本工程根目录，等待 Gradle Sync
2. **Build → Build APK(s)**，产物在 `app/build/outputs/apk/debug/app-debug.apk`
3. 命令行：`./gradlew assembleDebug`（首次打开若缺 `gradle-wrapper.jar`，Android Studio 会自动补全，或先执行一次 `gradle wrapper`；本仓库因推送通道限制未包含该 jar，见 `.gitignore`）
4. 把 APK 传到手机安装（需允许「安装未知来源应用」，调试签名），或用 Android Studio 直接 Run 到真机

本工程已在构建机上实际编译并签名校验通过（v1.3），不是仅源码交付。

## 把小组件添加到桌面

1. 安装后先打开一次 App
2. 桌面长按空白处 → 小组件 → 找到「每日金融 + AI 快报」拖到桌面

## 目录结构

| 文件 | 作用 |
|---|---|
| `app/src/main/java/com/cooper/briefingwidget/Models.kt` | 数据模型，与 feed.json 结构一一对应 |
| `.../FeedRepository.kt` | feed 地址读写、网络拉取、缓存回退、历史日期数据、JSON 解析 |
| `.../MainActivity.kt` | 主界面：紧凑头部、分组列表、已读标记、历史切换、设置弹窗 |
| `.../DetailActivity.kt` | 详情页：双语摘要、查看原文、分享 |
| `.../BriefingWidgetProvider.kt` | 桌面小组件渲染与点击（条目直达详情） |
| `app/src/main/assets/feed.json` | 离线初始数据（首期 2026-10-28） |
