# PineCone OS 日志系统设计规格

> **日期**: 2026-07-26
> **状态**: 设计定稿
> **目标**: 为 Pinecone Android TV 桌面构建全功能日志系统，覆盖用户行为埋点、使用习惯统计、错误采集、本地滚动存储、定时上传、服务端接收

---

## 1. 需求概述

### 1.1 核心目标

| 维度 | 用途 | 消费方 |
|------|------|--------|
| 行为埋点 | 记录用户点击了什么分类、哪个 Tab、搜索行为等 | 产品分析 / 推荐优化 |
| 使用习惯 | Session 时长、每日屏幕时间、分类偏好 | 家长报告 / 防沉迷 |
| 错误采集 | 崩溃、ANR、网络异常、防沉迷触发 | 开发诊断 / Bug 修复 |

### 1.2 关键约束

| 约束 | 决策 |
|------|------|
| 服务器 | 自有服务器，REST API |
| 用户标识 | 设备 ID + 防沉迷账号 UUID (`AccountStorage.accountUuid`) |
| 隐私策略 | 谨慎模式：不上传内容标题、完整 URL、搜索关键词；错误日志保留堆栈 |
| 家长控制 | 上传开关 (SharedPreferences), 默认开启 |

---

## 2. 整体架构

```
┌──────────────────────────────────────────────────────────┐
│                     Pinecone 客户端                        │
│                                                            │
│  App Layer (所有业务代码)                                    │
│  │  PineconeLogger.log(event)                              │
│  ▼                                                         │
│  ┌──────────────────────────────────────────────────┐     │
│  │              PineconeLogger (单例)                 │     │
│  │  · log(event: LogEvent)          写入入口          │     │
│  │  · flush()                       强制刷盘          │     │
│  │  · getLocalStats(): UsageReport  本地查询(家长用)   │     │
│  │  · setUploadEnabled(Boolean)     家长开关          │     │
│  └──────┬───────────────────────────────────────────┘     │
│         │                                                  │
│    ┌────▼──────────┐    ┌──────────────┐                   │
│    │  LogWriter     │    │  LogUploader  │                   │
│    │  (文件 I/O)    │    │  (WorkManager)│                   │
│    │  · JSON Lines  │◄───│  · 定时扫描    │                   │
│    │  · 2MB 滚动    │    │  · 批量上传    │                   │
│    │  · 7天保留     │    │  · 失败重试    │                   │
│    └───────────────┘    └──────┬───────┘                   │
│                                │                            │
│                        ┌───────▼──────────┐                │
│                        │  PrivacyFilter   │                │
│                        │  · 脱敏敏感字段   │                │
│                        │  · 家长开关控制   │                │
│                        └───────┬──────────┘                │
│                                │ HTTPS POST                 │
└────────────────────────────────┼────────────────────────────┘
                                 │
┌────────────────────────────────┼────────────────────────────┐
│                     自有服务器  │                             │
│                                ▼                            │
│  POST /api/v1/logs/batch  ←────┘                           │
│         │                                                   │
│    ┌────▼──────────┐    ┌──────────────┐                   │
│    │  Log Ingestion │───▶│  ClickHouse  │  ← 分析查询       │
│    │  (认证+校验)    │    │  (实时)       │                   │
│    └───────┬────────┘    └──────────────┘                   │
│            │                                                │
│            ▼                                                │
│    ┌──────────────┐    ┌──────────────┐                    │
│    │  S3 / minIO  │    │  Redis       │  ← 去重缓存        │
│    │  (冷存储)     │    │  (dedup)     │                    │
│    └──────────────┘    └──────────────┘                    │
└────────────────────────────────────────────────────────────┘
```

---

## 3. 模块职责

| 模块 | 文件 | 职责 | 依赖 |
|------|------|------|------|
| `PineconeLogger` | `log/PineconeLogger.kt` | 对外唯一 API，单例，线程安全 | LogWriter, PrivacyFilter |
| `LogEvent` | `log/LogEvent.kt` | 所有事件类型的 sealed class 定义 | 无 |
| `LogWriter` | `log/LogWriter.kt` | JSON Lines 文件写入、滚动、清理、本地查询 | 无 (纯 I/O) |
| `PrivacyFilter` | `log/PrivacyFilter.kt` | 上传前脱敏：去标题/去 URL/去搜索词 | 无 |
| `LogUploader` | `log/LogUploader.kt` | WorkManager 定时任务，批量上传，重试逻辑 | LogWriter, PrivacyFilter, OkHttp/HttpURLConnection |
| `LogConfig` | `log/LogConfig.kt` | 上传开关、WiFi-only 模式、服务器 URL 配置 | SharedPreferences |

---

## 4. Event Schema

### 4.1 事件基类

```kotlin
sealed class LogEvent {
    abstract val timestamp: Long       // System.currentTimeMillis()
    abstract val sessionId: String     // UUID，开机到关机为一个 session
    abstract val eventType: String     // 序列化为 "t" 字段
}
```

### 4.2 行为事件 (Behavior Events)

| 事件 | 类型码 | 关键字段 | 脱敏策略 |
|------|--------|---------|---------|
| `ItemClickEvent` | `item_click` | itemId, categoryName, tabName, sourcePosition | ❌ 去掉 title, url |
| `TabSwitchEvent` | `tab_switch` | fromTab, toTab, dwellTimeMs | ✅ 全保留 |
| `SidebarSelectEvent` | `sidebar_select` | categoryName, groupName, depth | ✅ 全保留 |
| `SearchEvent` | `search` | resultCount, sourceTab | ❌ 去掉搜索关键词 |
| `AppLaunchEvent` | `app_launch` | pkgName, launchSource | ✅ 全保留 |
| `WebBrowsingEvent` | `web_browsing` | durationMs, domain | ❌ 只保留域名, 去完整 URL |

### 4.3 使用习惯事件 (Usage Events)

| 事件 | 类型码 | 关键字段 | 生成时机 |
|------|--------|---------|---------|
| `SessionSummaryEvent` | `session_summary` | sessionDurationMs, tabDurations, totalClicks, topCategories | session 结束时 |
| `DailySummaryEvent` | `daily_summary` | date, totalScreenTimeMs, peakHour, categoryDistribution | 每日凌晨 |

### 4.4 系统事件 (System Events)

| 事件 | 类型码 | 关键字段 | 脱敏策略 |
|------|--------|---------|---------|
| `CrashEvent` | `crash` | throwableClass, stackTrace, crashSource, appVersion | ✅ 全保留(诊断必须) |
| `JankEvent` | `jank` | durationMs, description, threadStackSample | ✅ 全保留 |
| `GuardEvent` | `guard` | action, remainingMinutes, creditBalance | ✅ 全保留 |
| `SystemEvent` | `system` | event, metadata map | ✅ 全保留 |
| `NetworkErrorEvent` | `network_error` | endpoint, httpCode, durationMs | ✅ 全保留 |

### 4.5 JSON Lines 示例

```jsonl
{"ts":1722000123456,"sid":"a1b2c3...","t":"item_click","itemId":1001,"cat":"教育部","tab":"网站","pos":3}
{"ts":1722000123789,"sid":"a1b2c3...","t":"tab_switch","from":"网站","to":"App","dwell":45000}
{"ts":1722000150000,"sid":"a1b2c3...","t":"crash","clazz":"NullPtrException","trace":"...","ver":"v0.1.0"}
```

---

## 5. 本地文件管理

### 5.1 目录结构

```
/data/data/com.pinecone.pinecone/files/logs/
├── events_2026-07-26_001.log        # 当前写入文件
├── events_2026-07-25_001.uploaded   # 已上传标记 (48h 后清理)
├── events_2026-07-25_002.log        # 滚动切分文件 (未上传)
├── .writer_lock                     # 多进程写锁
```

### 5.2 滚动策略

| 触发条件 | 阈值 | 行为 |
|---------|------|------|
| 文件大小 | ≥ 2 MB | 切分新文件 (`_002`, `_003`...) |
| 文件年龄 | > 24 小时 | 切分新文件 |
| Crash 保护 | 当前文件 ≥ 512 KB 且写入 crash | 强制切分 (崩溃日志优先落地) |

### 5.3 清理策略

| 规则 | 值 | 说明 |
|------|-----|------|
| `.uploaded` 文件保留 | 48 小时 | 给服务端足够消费时间后删除 |
| `.log` 未上传文件保留 | 7 天 | 超过后强制删除 |
| 日志目录总大小硬上限 | 50 MB | 超出则删最老的 `.uploaded` 文件 |

### 5.4 LogWriter 接口

```kotlin
class LogWriter(private val logDir: File) {
    companion object {
        const val MAX_FILE_SIZE   = 2 * 1024 * 1024L    // 2MB
        const val MAX_FILE_AGE_MS = 24 * 3600_000L       // 24h
        const val RETENTION_DAYS  = 7
        const val MAX_TOTAL_SIZE  = 50 * 1024 * 1024L   // 50MB
    }
    
    fun appendLine(jsonLine: String)       // 追加一行 (线程安全)
    fun markUploaded(file: File)           // 标记已上传
    fun pendingUploadFiles(): List<File>   // 获取待上传文件列表
    fun queryLocal(since: Long, limit: Int): List<LogEvent>  // 本地查询
    private fun enforceSizeLimit()         // 磁盘空间检查+清理
}
```

### 5.5 并发安全

- 写入: `synchronized(fileLock)` 串行化同一进程内调用
- 多进程: `.writer_lock` + `FileLock` 保护
- 读操作: 只读已完成文件（非当前写入文件）

---

## 6. 上传策略

### 6.1 四级触发机制

| 优先级 | 触发条件 | 行为 |
|--------|---------|------|
| P0 强制 | Crash 事件写入 | 立即上传该文件，跳过 WiFi 检查 |
| P1 主动 | WorkManager PeriodicWork，每 60 min (flex 30 min) | 扫描 → 合并 → 批量 POST |
| P2 被动 | 设备空闲 + WiFi + 充电中 (Constraint) | WorkManager 自动选择时机 |
| P3 兜底 | 每日凌晨 03:00 (AlarmManager) | 清掉所有积压文件 |

### 6.2 上传流程

```
1. 检查家长上传开关 → 关闭则跳过
2. 网络检查 (ConnectivityManager, 仅WiFi上传, 可配置关闭)
3. 扫描 pendingUploadFiles()
4. 按 500 条/批 分片
5. PrivacyFilter 脱敏
6. HTTPS POST → 200 OK / 4xx / 5xx
7. 标记 .uploaded
```

### 6.3 重试策略

| 状态码 | 行为 |
|--------|------|
| 200 | 成功，标记 `.uploaded` |
| 4xx | 不重试，记录 `NetworkErrorEvent` |
| 5xx | 指数退避重试: 1s → 2s → 4s → 8s → 放弃，下次 WorkManager 周期再试 |
| 网络超时 (15s) | 同上 |

### 6.4 关键参数

| 参数 | 值 | 理由 |
|------|-----|------|
| WorkManager 周期 | 60 min | 覆盖每次 session |
| 单批最大条数 | 500 | 请求体 ≤ 512KB |
| 请求超时 | 15s | TV WiFi 网络 |
| 最大重试 | 3 次 (指数退避) | 避免浪费电量 |
| Crash 即时上传 | 是 | 错误时效性优先 |

---

## 7. 隐私过滤器

```kotlin
object PrivacyFilter {
    /** 上传前对每条事件做脱敏 */
    fun sanitize(event: LogEvent): LogEvent {
        return when (event) {
            is ItemClickEvent    -> event  // 不包含 title/url, 无需脱敏
            is SearchEvent       -> event  // 不包含搜索词, 无需脱敏
            is WebBrowsingEvent  -> event  // 只有 domain, 无需脱敏
            is CrashEvent        -> event.copy(stackTrace = truncate(event.stackTrace, 2048))
            else                 -> event  // 其他事件全保留
        }
    }
    
    private fun truncate(s: String, maxLen: Int) =
        if (s.length <= maxLen) s else s.take(maxLen) + "\n...truncated"
}
```

### 隐私边界总结

| 字段 | 本地日志 | 上传到服务器 |
|------|---------|------------|
| `item.title` (内容标题) | ✅ 记录 | ❌ 不上传 |
| `item.category` (分类名) | ✅ 记录 | ✅ 上传 |
| `actionUrl` (完整 URL) | ✅ 记录 | ❌ 不上传 |
| `domain` (域名) | ✅ 记录 | ✅ 上传 |
| `pkgName` (应用包名) | ✅ 记录 | ✅ 上传 |
| 搜索关键词 | ✅ 记录 | ❌ 不上传 |
| 搜索结果数 | ✅ 记录 | ✅ 上传 |
| `stackTrace` (崩溃堆栈) | ✅ 记录 | ✅ 上传 (截断到 2KB) |

---

## 8. 服务器端 API

### 8.1 Endpoints

```
Base URL: https://api.pineconeos.com/api/v1

POST   /logs/batch           # 批量上传日志
GET    /logs/stats            # 家长查询使用报告
POST   /devices/register      # 设备注册 (获取 JWT)
POST   /devices/refresh       # 刷新 Token
GET    /logs/health           # 健康检查
```

### 8.2 POST /logs/batch

**Request:**
```json
POST /api/v1/logs/batch
Authorization: Bearer <device_token>
Content-Type: application/json

{
  "device_id": "pinecone-rpi5-abc123",
  "account_uuid": "550e8400-...",
  "app_version": "v0.1.0",
  "android_version": "14",
  "batch_seq": 42,
  "events": [
    { "ts": 1722000123456, "sid": "...", "t": "item_click", ... }
  ]
}
```

**Response (200):**
```json
{
  "received": 487,
  "duplicates": 0,
  "errors": [],
  "next_expected_seq": 43
}
```

**Error Responses:**
- `400` — batch 过大 (>500)、格式错误
- `401` — token 无效或过期
- `429` — 频率限制 (100 req/min/device)

### 8.3 GET /logs/stats

**Request:** `GET /api/v1/logs/stats?account_uuid=xxx&date=2026-07-26`

**Response (200):**
```json
{
  "date": "2026-07-26",
  "total_screen_time_min": 135,
  "peak_hour": 20,
  "categories": [
    { "name": "纪录片", "clicks": 12, "duration_min": 45 }
  ],
  "top_domains": ["smartedu.cn", "bilibili.com"],
  "crash_count": 0
}
```

### 8.4 认证流程

```
设备首次启动:
  1. POST /api/v1/devices/register
     { device_id, account_uuid, app_version }
  2. 服务端签發 JWT (90 天过期)
  3. 客户端存 JWT → SharedPreferences
  4. 后续请求带 Authorization: Bearer <jwt>
  5. Token 过期前 7 天刷新 POST /devices/refresh
  
降级: 服务器不可用时，日志继续写本地，不丢弃
```

### 8.5 服务端存储

**ClickHouse (热数据):**
```sql
CREATE TABLE log_events (
    ts              DateTime64(3),
    server_ts       DateTime DEFAULT now(),
    device_id       String,
    account_uuid    String,
    app_version     String,
    event_type      LowCardinality(String),
    session_id      String,
    batch_seq       UInt32,
    payload         String,        -- 原始 JSON
    cat_name        String DEFAULT '',
    tab_name        String DEFAULT '',
    crash_class     String DEFAULT ''
)
ENGINE = MergeTree
PARTITION BY toYYYYMM(ts)
ORDER BY (device_id, ts, event_type)
TTL ts + INTERVAL 90 DAY;
```

**S3 / minIO (冷数据):** 按 `device_id/yyyy/mm/dd/` 分区，全量备份

**Redis (去重):** `SETEX log:dedup:<device_id>:<batch_seq> 604800 1`

**聚合视图 (家长报告):**
```sql
CREATE MATERIALIZED VIEW daily_usage_mv
ENGINE = SummingMergeTree
PARTITION BY toYYYYMM(date)
ORDER BY (account_uuid, date)
TTL date + INTERVAL 365 DAY
AS SELECT
    toDate(ts) AS date,
    account_uuid,
    countIf(event_type = 'item_click') AS total_clicks,
    countIf(event_type = 'crash') AS crash_count
FROM log_events GROUP BY date, account_uuid;
```

---

## 9. 文件清单 (客户端待创建)

```
pinecone/app/src/main/java/com/pinecone/pinecone/log/
├── PineconeLogger.kt        # 单例，对外 API
├── LogEvent.kt              # 所有事件类型定义
├── LogWriter.kt             # 文件写入 + 滚动 + 清理
├── PrivacyFilter.kt         # 脱敏规则
├── LogUploader.kt           # WorkManager + 上传逻辑
├── LogConfig.kt             # 配置管理 (开关/URL/WiFi-only)
└── LogSession.kt            # Session 生命周期管理
```

### 9.1 需要埋点的位置

| 位置 | 事件 | 触发时机 |
|------|------|---------|
| `MainScreen.kt` | `ItemClickEvent` | onItemClick() |
| `MainScreen.kt` | `TabSwitchEvent` | onTabSelected() |
| `MainScreen.kt` | `SidebarSelectEvent` | onCategorySelect() / onGroupToggle() |
| `WebLandingPage.kt` | `SearchEvent` | 搜索执行时 |
| `WebViewActivity.kt` | `WebBrowsingEvent` | onPause/onDestroy (计算时长) |
| `MainScreen.kt` | `AppLaunchEvent` | handleItemClick() pkg: 分支 |
| `MainActivity.kt` | `SystemEvent("boot")` | onCreate() |
| `GuardClientHolder` | `GuardEvent` | 防沉迷触发回调 |
| `Thread.setDefaultUncaughtExceptionHandler` | `CrashEvent` | 全局捕获 |
| `Choreographer` | `JankEvent` | 主线程卡顿检测 (≥2s) |

---

## 10. 自检清单 (Spec Self-Review)

- [x] 无 TBD / TODO 占位符
- [x] Event Schema 覆盖行为、习惯、错误三大类
- [x] 文件滚动和清理策略明确了具体数值
- [x] 上传策略覆盖正常/异常/边界 (Crash 即时、WiFi-only、重试、兜底)
- [x] 隐私边界表明确列出了一个字段的本地 vs 上传差异
- [x] 服务端 API 定义了 Request/Response/Error 全部三种状态
- [x] 认证方案覆盖注册、过期刷新、降级
- [x] 存储方案分热(ClickHouse) + 冷(S3) + 去重(Redis)
- [x] 客户端文件清单和埋点位置明确
- [x] 模块职责表声明了每个文件的单一职责和依赖
