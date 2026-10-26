# AutoLedger 记账本

一个纯原生 Android 自动记账 App：自动记录支付宝/微信的收支，配上 AI 毒舌辣评，还支持**家庭共享账单**。

## 功能特性

### 自动记账
- 监听支付宝、微信的支付通知，自动识别金额、收支方向、分类；
- 微信转账/收款通知不含金额，通过**无障碍服务**读取聊天窗口金额自动补全；
- 画面指纹去重 + 30 秒冷却，避免重复记录。

### 记账体验
- **待确认机制**：自动识别的账目可标记「待确认」，点击即可确认；
- **AI 毒舌辣评**：每笔支出自动生成一句毒舌吐槽（支持 OpenAI 兼容接口，可自定义端点/Key/模型）；
- **月度报告**：AI 生成当月毒舌消费报告；
- **统计**：环形分类占比图 + 分类/收支汇总；
- **CSV 导入**：支持微信账单 CSV 导入（按交易单号去重）。

### 家庭版（多人共享账单）
- 家庭账单：一家人共享一份账本，看全家总支出和每个人的消费；
- 成员消费汇总 + 明细（带时间、备注）；
- **消费备注**：给识别不出商家的账目手动备注「这是什么」，全家可见；
- **消费评论**：对某笔消费评论、回复，全家可见；
- 数据存 Cloudflare Workers + D1（免费额度，见下文「家庭版后端」）。

### 设置与工具
- 独立设置页：权限状态、自动记账/AI 辣评开关、深色模式（浅色/深色/跟随系统）、数据备份/恢复/清除、关于；
- 日志：通知 / 无障碍 / AI 三类日志，可查看、一键导出到 Download、清空；
- 账目编辑、删除、搜索、长按菜单。

## 技术栈

- **纯原生 Java**，无 AndroidX、无第三方依赖（网络用 `HttpURLConnection`，JSON 用 `org.json`）；
- AGP 8.2.2 / Gradle 8.2 / Java 17；
- compileSdk 34 / targetSdk 34 / minSdk 24；
- 本地存储 SQLite（`LedgerDbHelper`）。

## 构建

环境要求：JDK 17、Gradle 8.2、Android SDK（build-tools 34.0.0 / platforms android-34）。

```bash
# 在 android 目录执行
gradle assembleRelease
```

产物在 `android/app/build/outputs/apk/release/app-release.apk`（debug 签名，可直接安装）。

> 本地构建需自备 `android/local.properties`（SDK 路径）和 `android/debug.keystore`（签名密钥，`.gitignore` 已排除）。

## 家庭版后端（Cloudflare Workers + D1）

家庭版需要自建一个免费后端。步骤：

1. 在 Cloudflare 创建 **D1 数据库** `family-ledger` 和 **Worker**（绑定 D1，变量名 `DB`）；
2. D1 里执行建表 SQL：

```sql
CREATE TABLE IF NOT EXISTS transactions (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  family_code TEXT NOT NULL,
  member TEXT NOT NULL,
  amount REAL NOT NULL,
  category TEXT,
  merchant TEXT,
  source TEXT,
  note TEXT,
  comment TEXT,
  remark TEXT DEFAULT '',
  status INTEGER DEFAULT 1,
  time INTEGER NOT NULL
);

CREATE TABLE IF NOT EXISTS comments (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  family_code TEXT NOT NULL,
  transaction_id INTEGER NOT NULL,
  member TEXT NOT NULL,
  content TEXT NOT NULL,
  time INTEGER NOT NULL
);
```

3. Worker 提供以下接口（家庭码由 `POST /api/family` 用 `crypto.randomUUID()` 生成）：

| 方法 | 路径 | 说明 |
|---|---|---|
| POST | `/api/family` | 创建家庭，返回唯一家庭码 |
| POST | `/api/transaction` | 上传一笔消费 |
| GET | `/api/transactions?family_code=` | 拉取家庭账单 |
| GET | `/api/summary?family_code=` | 成员消费汇总 |
| POST | `/api/comment` | 提交评论 |
| GET | `/api/comments?family_code=` | 拉取评论 |

4. App 内「设置 → 家庭」填：成员昵称、家庭码（点「生成新码」自动获取）、服务器地址（你的 Worker 域名）。

## 目录结构

```
android/
├── app/src/main/
│   ├── java/space/pengtao/ledger/
│   │   ├── MainActivity.java            # 主界面（账目列表）
│   │   ├── SettingsActivity.java        # 设置页
│   │   ├── StatisticsActivity.java      # 统计页
│   │   ├── FamilyActivity.java          # 家庭账单页
│   │   ├── PayNotificationListener.java # 通知监听（自动记账核心）
│   │   ├── LedgerAccessibilityService.java # 无障碍（微信金额补读）
│   │   ├── LedgerDbHelper.java          # SQLite 存取
│   │   ├── AiClient.java                # AI 辣评/报告
│   │   ├── FamilyClient.java            # 家庭版云同步客户端
│   │   ├── CsvImporter.java             # 微信账单 CSV 解析
│   │   ├── NotifyHelper.java            # 通知
│   │   ├── ConfirmReceiver.java         # 通知确认按钮
│   │   ├── PieChartView.java            # 环形占比图
│   │   ├── AppSettings.java             # 设置存取
│   │   ├── ThemeUtil.java               # 主题切换
│   │   └── ...
│   ├── res/                             # 布局 / 资源
│   └── AndroidManifest.xml
└── build.gradle
```

## 隐私说明

- 账目默认存**本地 SQLite**，不联网；
- 只有启用家庭版并配置了家庭码后，消费数据才会上传到你自己的 Cloudflare Worker；
- AI 辣评会把你填的端点/Key 和单笔消费文本发给你配置的 AI 服务，不经过其他服务器。
