# PineCone OS 防沉迷系统设计文档

**日期**: 2026-07-23  
**状态**: 设计完成，待实现  
**版本**: 1.0

---

## 1. 目标与范围

### 1.1 目标

为 PineCone OS（松果智学）构建一个纯本地、无服务器的家长控制防沉迷系统。家长设置规则，孩子受规则约束，防止过度使用屏幕。

### 1.2 管理模式

**家长管理模式（A 模式）**：家长设定规则、持有 PIN，孩子是被管理者。规则设置界面受 PIN 保护，孩子无法访问或修改。

### 1.3 功能范围

支持五种规则维度：

- **每日累计时长**：每天总使用时间上限
- **强制休息间隔**：连续使用 N 分钟后强制休息 M 分钟
- **可用时间段**：按工作日/周末分别配置允许使用的时段
- **内容分类限制**：按分类（英语、阅读、纪录片、App）分别设定每日时长
- **App 单独限制**：指定 App（如 B站）的每日时长限制

超出限额后：
- 柔性提示 + 5 分钟宽限期
- 宽限期消耗信用积分
- 积分耗尽后系统级硬锁屏

### 1.4 关键交互说明

**"主动提前结束"触发方式**：在孩子使用的任意 App 中，按遥控器 Home 键回到主屏幕后，主屏幕底部出现一个"✅ 今天够了"按钮。孩子点击后即触发提前结束，系统锁定设备并计算积分奖励。

**"暂停防沉迷"功能**：家长设置中的"⏸️ 暂停防沉迷（今天不限制）"选项，仅对当日有效。凌晨 00:00 自动恢复规则。可用于特殊情况（如孩子生病在家、假期等）。

**PIN 安全策略**：
- 首次使用时引导创建 6 位数字 PIN
- 连续错误 3 次 → 锁定 15 分钟
- 无"忘记 PIN"功能（纯本地系统，无远程重置通道）
- 遗忘 PIN 的唯一恢复方式：重刷 ROM（这本身就是一种防篡改保护——如果孩子能"忘记 PIN"来重置，那就等于绕过了）

### 1.5 非目标

- 无远程服务器、无云同步
- 无多设备联动
- 无法防御刷入非 PineCone ROM 的场景（物理极限）

---

## 2. 系统架构

### 2.1 整体架构

```
┌─────────────────────────────────────────────────────────┐
│                     PineCone OS                          │
│                                                          │
│  ┌──────────────────┐    ┌───────────────────────────┐   │
│  │ PineCone Launcher │    │   GuardService (独立进程)   │   │
│  │  (system/app)     │    │   (foreground service)    │   │
│  │                   │    │                           │   │
│  │ • 家长设置 UI      │←──→│ • 使用追踪 (UsageTracker)  │   │
│  │ • PIN 验证        │ IPC │ • 规则引擎 (RuleEngine)    │   │
│  │ • 锁屏界面         │    │ • 信用管理 (CreditManager) │   │
│  │ • 提醒浮层         │    │ • 安全存储 (SecureStorage) │   │
│  └────────┬─────────┘    └──────────┬────────────────┘   │
│           │                         │                    │
│           │              ┌──────────▼────────────────┐   │
│           │              │   DeviceAdminReceiver     │   │
│           │              │   • 系统级锁屏             │   │
│           │              │   • 防卸载保护             │   │
│           │              └───────────────────────────┘   │
│           │                                              │
│  ┌────────▼──────────────────────────────────────────┐   │
│  │              Android Framework                     │   │
│  │  UsageStatsManager  │  AlarmManager  │  Keystore   │   │
│  └────────────────────────────────────────────────────┘   │
└─────────────────────────────────────────────────────────┘
```

### 2.2 双进程设计

- **主进程** (`com.pinecone.launcher`)：Launcher UI，家长设置界面，锁屏/提醒界面
- **守护进程** (`com.pinecone.launcher:guard`)：GuardService 前台服务，追踪使用、执行规则、管理积分

两个进程通过 Messenger/AIDL 通信，双向守护（互相检测存活并重启对方）。

---

## 3. 规则模型

### 3.1 数据结构

```kotlin
data class RuleSet(
    val version: Int,
    val dailyTotalLimit: DurationMinutes?,     // 每日总时长（null = 不限）
    val categoryLimits: List<CategoryLimit>,
    val appLimits: List<AppLimit>,
    val timeWindows: List<TimeWindow>,
    val breakRule: BreakRule?,
    val creditConfig: CreditConfig
)

data class CategoryLimit(
    val categoryId: String,       // "english", "reading", "documentary", "apps"
    val dailyMinutes: Int?        // null = 不限
)

data class AppLimit(
    val packageName: String,
    val appLabel: String,
    val dailyMinutes: Int
)

data class TimeWindow(
    val name: String,
    val startHour: Int, val startMinute: Int,
    val endHour: Int, val endMinute: Int,
    val daysOfWeek: Set<Int>       // 1=Monday (ISO)
)

data class BreakRule(
    val usageMinutes: Int,         // 使用多久后触发休息
    val breakMinutes: Int          // 休息时长
)

data class CreditConfig(
    val weeklyTotal: Int,          // 每周积分总额（默认 100）
    val resetDay: DayOfWeek,       // 重置日（默认周一）
    val overtimeCostPerMin: Int,   // 超时扣分/分钟（默认 5）
    val earlyStopRewardPerMin: Int // 提前结束奖励/分钟（默认 2）
)
```

### 3.2 规则优先级（最严格优先）

1. **时间段限制**（最高）：不在可用时段 → 直接锁屏
2. **强制休息**：连续使用超时 → 弹出休息提醒
3. **App 限制**：单个 App 达限 → 该 App 不可用
4. **分类限制**：分类总时长达限 → 该分类下全部不可用
5. **每日总时长**（最低，兜底）：总时长用尽 → 进入宽限期或锁屏

### 3.3 默认规则示例

```
每日总时长:        2 小时 30 分钟

时间段:
  • 周一至周五:    16:00 – 21:00
  • 周六日:        08:00 – 21:00

分类限制:
  • 🔤 英语学习:   每天 60 分钟
  • 📚 绘本阅读:   不限
  • 🎬 纪录片:     不限
  • 🧩 学习 App:   每天 45 分钟

App 限制:
  • B站:           每天 30 分钟

强制休息:
  • 每 40 分钟休息 10 分钟
```

---

## 4. 信用积分系统

### 4.1 积分规则

| 参数 | 值 |
|------|-----|
| 初始积分 | 100 分 / 周 |
| 重置周期 | 每周一 00:00（可配置） |
| 超时继续使用 | **-5 分/分钟** |
| 主动提前结束 | **+2 分/分钟** |
| 积分上限 | 每周初始值（不跨周累积） |

### 4.2 积分生命周期

```
周一 00:00  →  积分自动重置为 100
正常使用     →  积分不变
超时宽限     →  每分钟扣 5 分
主动提前结束  →  每节省 1 分钟加 2 分（不超上限）
积分归零     →  设备硬锁定，需家长 PIN 解锁
下周一 00:00 →  积分自动重置为 100
```

### 4.3 宽限期流程

```
达到每日限额
     │
     ├─ 积分 > 0 → 弹出宽限选择：
     │   • [立即休息] → 退出，可获提前结束积分奖励
     │   • [继续使用] → 5 分钟宽限期，每分钟扣 5 分
     │       │
     │       ├─ 积分够扣 → 5 分钟后再次询问
     │       └─ 积分耗尽 → 硬锁屏
     │
     └─ 积分 = 0 → 直接硬锁屏（无宽限选项）
```

---

## 5. 执行流程 & 用户体验

### 5.1 正常使用提醒时间线

| 距离限额 | 提醒方式 |
|---------|---------|
| 剩余 15 分钟 | 右上角 Toast："今天学习时间还剩 15 分钟" |
| 剩余 5 分钟 | 底部半透明横幅（持续 5 秒） |
| 剩余 1 分钟 | 全屏过渡动画 + 60 秒倒计时 |
| 限额到达 | 全屏宽限选择界面 |

### 5.2 强制休息

```
连续使用达到设置间隔（默认 40 分钟）
    → 全屏："🌿 该休息了，起来走走，看看远方"
    → 倒计时（如 10 分钟），不可跳过
    → 倒计时结束自动恢复
```

### 5.3 不在可用时段

```
时间段外按 Home 键 → 锁屏界面
显示："⏰ 当前不在可用时段"
     "开放时间：周一至周五 16:00-21:00"
```

### 5.4 App 达限

```
打开已超限 App → Toast："哔哩哔哩 今日时间已用完（30/30分钟）"
→ App 不启动，返回主屏幕
```

### 5.5 硬锁屏

```
DeviceAdmin 系统级锁屏：

    🔒  今天的屏幕时间已用完
        信用积分: 0
        下周一会自动重置

    [ 家长解锁 (PIN) ]

    🏠 主页仍可查看（只读）
    📞 紧急电话仍可用
```

### 5.6 主动提前结束（积分奖励）

```
孩子主动选择"今天够了"
    → 计算节省的分钟数
    → 奖励积分（+2/分钟，不超上限）
    → 通知："很棒！提前结束，积分 +X"
```

---

## 6. 防篡改策略

### 6.1 攻击面 × 防御矩阵

| 攻击手段 | 防御措施 | 门槛 |
|---------|---------|------|
| 改系统时间（回调） | 双时钟交叉校验 (elapsedRealtime vs wallClock) | 中 |
| 改系统时间（快进） | 用 elapsedRealtime 计算真实时长 | 中 |
| 强制停止 Launcher | GuardService 独立进程运行 | 低 |
| 强制停止 Guard | Foreground Service + 双向进程守护 | 中 |
| 清除 App 数据 | 双路径互备存储 + checksum 交叉验证 | 高 |
| 删除加密文件 | 三处存储交叉恢复 | 高 |
| ROOT 后改文件 | HMAC 签名 + Keystore 加密 | root |
| 安全模式启动 | 启动延迟检测 + 锁屏 | 中 |
| 恢复出厂设置 | ROM 默认锁定状态 | 极高 |
| 刷其他 ROM | 无法防御（物理极限） | 极高 |

### 6.2 双时钟校验

```kotlin
data class TimeSnapshot(
    val elapsedRealtime: Long,   // SystemClock.elapsedRealtime() — 单调递增
    val wallClock: Long,          // System.currentTimeMillis() — 可被修改
    val accumulatedUsage: Long    // 当日累计使用秒数
)

fun validateTimeIntegrity(last: TimeSnapshot, current: TimeSnapshot): TimeStatus {
    val wallDelta = current.wallClock - last.wallClock
    val allowedDrift = 60_000L   // 允许 1 分钟 NTP 校时误差
    return when {
        wallDelta < -allowedDrift → TimeStatus.TAMPERED_BACKWARD  // 时间回调 → 锁屏
        else                      → TimeStatus.VALID
    }
}
```

### 6.3 三处存储交叉恢复

| 位置 | 路径 | 加密 |
|------|------|------|
| A | `/data/data/<app>/shared_prefs/guard_rules.xml` | EncryptedSharedPreferences |
| B | `/data/data/<app>/files/.guard_backup` | AES-256-GCM + HMAC |
| C | `/data/system/pinecone_guard/checksum` | HMAC 校验哈希 |

写入策略：A/B 双写，C 存校验哈希。  
读取策略：A 损坏 → B 恢复；都损坏 → 设备锁定。

### 6.4 ROM 层默认锁定

ROM 构建时 `pinecone.guard.provisioned=false`。GuardService 检测到未初始化 → 设备锁定，仅允许家长初始化设置。恢复出厂后回到此状态。

### 6.5 misc 分区指纹

设备首次启动时在 `/misc` 分区写入不可擦除的设备指纹。恢复出厂不会清除该分区。检测到重复初始化 → 记录重置次数 + 锁定 + 警告。

---

## 7. 组件清单

### 7.1 新增文件

```
launcher/app/src/main/java/com/pinecone/launcher/
├── guard/
│   ├── GuardService.kt              # 核心守护服务（独立进程，前台服务）
│   ├── GuardWatchdog.kt             # 双向进程守护
│   ├── RuleEngine.kt                # 规则评估引擎（5 种规则）
│   ├── CreditManager.kt             # 信用积分管理
│   ├── UsageTracker.kt              # 封装 UsageStatsManager
│   ├── SecureStorage.kt             # 加密存储 + 多路径备份 + HMAC
│   ├── TimeGuard.kt                 # 双时钟校验 + 时间异常检测
│   ├── DeviceAdminReceiver.kt       # DeviceAdmin 回调
│   ├── BootReceiver.kt              # 开机自启 GuardService
│   └── GuardNotifier.kt             # 提醒管理（Toast/横幅/全屏/锁屏）
├── ui/
│   ├── ParentSettingsActivity.kt    # 家长设置主页
│   ├── PinSetupActivity.kt          # 首次 PIN 设置
│   ├── PinVerificationDialog.kt     # PIN 验证弹窗
│   ├── DailyLimitEditorFragment.kt  # 每日累计时长编辑
│   ├── BreakRuleEditorFragment.kt   # 强制休息间隔编辑
│   ├── TimeWindowEditorFragment.kt  # 可用时段编辑
│   ├── CategoryLimitEditorFragment.kt  # 内容分类限制编辑
│   ├── AppLimitEditorFragment.kt    # App 单独限制编辑
│   ├── CreditConfigFragment.kt      # 积分总额与重置周期
│   ├── UsageHistoryFragment.kt      # 使用统计
│   ├── LockScreenActivity.kt        # 系统锁屏界面
│   ├── FullScreenWarningActivity.kt # 全屏宽限提醒
│   └── BreakReminderActivity.kt     # 强制休息界面
└── model/
    ├── RuleSet.kt                    # 规则数据类
    ├── CreditAccount.kt             # 积分账户数据类
    ├── UsageSnapshot.kt             # 使用快照数据类
    └── TimeSnapshot.kt              # 时间快照数据类
```

### 7.2 修改文件

| 文件 | 变更 |
|------|------|
| `AndroidManifest.xml` | 新增权限、Service、Receiver、DeviceAdmin 声明 |
| `MainActivity.kt` | 在设置行新增"[🔒 家长设置]"入口（PIN 保护） |
| `build.gradle.kts` / `libs.versions.toml` | 新增 `security-crypto` 依赖 |

### 7.3 存储方案

| 存储内容 | 位置 | 加密方式 |
|---------|------|---------|
| 规则配置 (RuleSet) | EncryptedSP + 备份文件 | AES-256-GCM |
| 信用积分 (CreditAccount) | EncryptedSP + 备份文件 | AES-256-GCM |
| 今日使用数据 (UsageSnapshot) | EncryptedSP + 备份文件 | AES-256-GCM |
| 时间快照链 (TimeSnapshot) | 文件 + checksum | HMAC-SHA256 |
| 使用历史（7天） | 文件 | 普通（无敏感信息） |
| PIN 哈希 | Keystore 内部 | bcrypt |

### 7.4 权限声明

```xml
<uses-permission android:name="android.permission.PACKAGE_USAGE_STATS" />
<uses-permission android:name="android.permission.RECEIVE_BOOT_COMPLETED" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE_SYSTEM_EXEMPTED" />
<uses-permission android:name="android.permission.BIND_DEVICE_ADMIN" />
```

### 7.5 依赖

```toml
androidx-security-crypto = { module = "androidx.security:security-crypto", version = "1.1.0-alpha06" }
```

仅额外引入一个加密库，其余全部使用 Android 标准 API。

---

## 8. 家长设置 UI

### 8.1 设置主页结构

```
⚙️ 家长设置（需 PIN）
├── ⏱️  每日累计时长             2 小时 30 分
├── 🔄  强制休息间隔           每 40 分 休 10 分
├── 📅  可用时段
│      ├─ 周一至周五           16:00 – 21:00
│      └─ 周六日               08:00 – 21:00
├── 📂  内容分类限制
├── 📱  App 单独限制             3 个已设置
├── ⭐  信用积分
│      ├─ 每周积分总额              100 分
│      └─ 重置日                   每周一
├── 📊  使用统计
├── 🔑  修改 PIN
└── ⏸️  暂停防沉迷（今天不限制）
```

### 8.2 各子页面

详见设计讨论中的完整 UI 布局，核心交互采用 TV 遥控器友好的列表+预设按钮模式。

### 8.3 首次使用流程

1. 家长点击"[家长设置]" → 检测无 PIN → 引导创建 6 位 PIN
2. PIN 创建后 → 进入默认规则，家长可按需调整
3. 调整完毕 → GuardService 激活，防沉迷生效

### 8.4 PIN 安全

- 6 位数字 PIN，通过数字键盘输入
- 存储时用 bcrypt 哈希，不存明文
- 连续错误 3 次 → 锁定 15 分钟
- 没有"忘记 PIN"功能（本地系统，无法远程重置；若遗忘需重刷 ROM）

---

## 9. 重启/断电/断网场景

| 场景 | 影响 | 处理 |
|------|------|------|
| 正常重启 | 数据从加密文件恢复 | BootReceiver → 自动拉活 GuardService |
| 断电 | 同重启 | 同上 |
| 断网 | 无影响 | 全本地运行 |
| 改时间（回调） | 最大威胁 | 双时钟校验 → 检测到即锁屏 |
| 改时间（快进） | 有限 | 用 elapsedRealtime 计算真实时长 |
| 安全模式 | 绕过风险 | 启动延迟检测 + 锁定 |

---

## 10. 实现顺序建议

按优先级分阶段实现：

**Phase 1 — 核心骨架**
- GuardService + BootReceiver（双进程基础）
- UsageTracker（使用追踪）
- SecureStorage（加密存储）
- RuleEngine（规则评估）

**Phase 2 — 家长 UI**
- ParentSettingsActivity + PIN 系统
- 所有规则编辑子页面
- 使用统计页面

**Phase 3 — 执行与提醒**
- GuardNotifier（提醒层级）
- 宽限期流程
- DeviceAdmin 系统锁屏
- 强制休息界面

**Phase 4 — 积分系统**
- CreditManager
- 积分 UI 展示
- 提前结束奖励

**Phase 5 — 安全加固**
- TimeGuard 双时钟校验
- 三处存储交叉恢复
- ROM 层默认锁定

---

## 11. 未解决的问题

- misc 分区指纹写入需要确认 LineageOS 23.2 上该分区的写入权限
- 前台服务在 Android TV 上的通知栏行为需要实测验证
- UsageStatsManager 在 Android TV (SDK 36) 上的权限授权流程需实测
