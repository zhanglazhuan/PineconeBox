# PineCone OS — Apple 风格交互审查与优化方案

> **审查日期**: 2026-07-26
> **审查工具**: `ui-ux-pro-max` + `frontend-design` 双 skill 联合审查
> **核心目标**: 将当前 Android TV 桌面改造为 Apple 风格的、让用户"无法误认为其他产品"的独特视觉与交互体验

---

## 第一部分：当前状态审计 (Current State Audit)

### 1.1 产品画像

| 维度 | 当前状态 |
|------|---------|
| 产品类型 | Android TV 教育内容桌面启动器 |
| 目标用户 | 中国 K-12 学生 & 家长 |
| 使用场景 | 客厅大屏 (40-65")，遥控器操作，2-4m 观看距离 |
| 技术栈 | Kotlin + Jetpack Compose, Material 3 |
| 当前风格 | 暗色主题，深蓝紫底 (#1A1A2E)，青色强调 (#4FC3F7) |

### 1.2 交互架构审计

当前结构：
```
┌──────────────────────────────────────────────┐
│  HomeTabRow  [🌐 网站 | 📱 App | ⚙️ 设置]    │  ← 56dp 高度
├────────┬─────────────────────────────────────┤
│Sidebar │  ContentGrid / WebLandingPage        │
│220dp   │                                      │
│        │  ┌────┐ ┌────┐ ┌────┐ ┌────┐       │
│ · 首页  │  │卡片│ │卡片│ │卡片│ │卡片│       │
│ ▾ 🏛 国字号│ └────┘ └────┘ └────┘ └────┘       │
│   · 教育部│  ┌────┐ ┌────┐ ┌────┐ ┌────┐       │
│   · 科普  │  │卡片│ │卡片│ │卡片│ │卡片│       │
│ ▸ 🌟 优质站│ └────┘ └────┘ └────┘ └────┘       │
│   ...     │                                      │
└───────────┴──────────────────────────────────────┘
```

#### ✅ 做得好的地方
- **结构清晰**: 三 Tab 架构 + 左侧分类导航 + 右侧内容 是标准的 TV 信息架构
- **分组合理**: 网站 Tab 下按"国字号/优质站/可视化/欧美/纪录片/AI"分组，语义清晰
- **设置页 iPad 风格**: SettingsList 的圆角分组卡片 + 分割线 + `›` 箭头是典型的 iOS Settings 模式
- **Jetpack Compose**: 声明式 UI，便于迭代重构
- **暗色主题**: 适合 TV 使用场景（客厅暗光环境）

#### ❌ 需要改进的地方

| # | 问题 | 严重度 | 位置 |
|---|------|--------|------|
| 1 | **Emoji 作为图标** —— Tab 标题("🌐""📱""⚙️")、侧边栏分组("🏛""🌟""🔬""🌍""🎬""🤖")、设置项、列表标签全部使用 Emoji。违反 UI/UX Pro Max 规则 §4：应使用 SVG/矢量图标 | 🔴 高 | 全局 |
| 2 | **无焦点动效** —— ResourceCard 点击态只有背景色切换，无缩放、无阴影提升、无高光环。TV 遥控器焦点导航时缺乏视觉反馈 | 🔴 高 | ResourceCard.kt |
| 3 | **侧边栏层级混乱** —— Web 侧边栏有两级（分组 > 分类），但分组用蓝色文字 + 箭头、分类用缩进白色文字，层级区分仅靠颜色缩进，无视觉层级感 | 🟡 中 | SidebarPanel.kt |
| 4 | **Tab 栏过于朴素** —— 仅文字 + 背景色变化，缺乏现代 TV 应有的胶囊/下划线指示器动效 | 🟡 中 | HomeTabRow.kt |
| 5 | **色彩系统单调** —— 全局几乎只用 3 种颜色（深蓝底、浅蓝强调、白色文字），缺乏层次和情感温度 | 🟡 中 | Color.kt |
| 6 | **排版未定制** —— 全部使用系统默认字体 (FontFamily.Default)，无品牌感。字号层级只有 3 级（22/16/14 sp） | 🟡 中 | Type.kt |
| 7 | **WebLandingPage 搜索框位置突兀** —— 全宽 60% 居中搜索框 + 卡片的布局缺乏视觉焦点和引导 | 🟢 低 | WebLandingPage.kt |
| 8 | **卡片设计雷同** —— 所有卡片同质化严重：圆形字母头像 + 标题 + 副标题，无法快速区分类别 | 🟢 低 | ResourceCard.kt |
| 9 | **无转场动画** —— Tab 切换、侧边栏展开/折叠、页面跳转均为瞬间切换，无过渡 | 🟡 中 | MainScreen.kt |
| 10 | **滚动生硬** —— 侧边栏和内容区滚动无惯性/弹性效果，操作手感"像切换 PPT" | 🟢 低 | 全局 |

---

## 第二部分：Apple 风格设计系统 (Apple-Style Design System)

### 2.1 设计哲学：Apple 的 5 个核心原则

| 原则 | 含义 | 在此项目中的映射 |
|------|------|-----------------|
| **Depth & Layering (深度分层)** | UI 不是平面，通过透明度/blur/阴影创造空间感 | 卡片浮于背景之上，侧边栏 vs 内容区有明确的空间层级 |
| **Clarity (清晰)** | 文字在任何距离都可读，功能一目了然 | TV 远距离阅读：字号放大、对比度强化、信息密度降低 |
| **Deference (顺从内容)** | UI 退后，内容凸显。装饰最少化 | 减少分割线/边框，用留白和透明层代替 |
| **Fluidity (流畅)** | 每一个状态变化都有平滑过渡 | 焦点移动、Tab 切换、页面转场全部动画 |
| **Haptic & Tactile (触感)** | 每次交互都有物理回应感 | 遥控器方向键导航有"真实移动感"，非瞬时跳切 |

### 2.2 色彩系统 (Color Palette)

对标 Apple tvOS / visionOS 的 **Frosted Glass + Vibrant Accent** 体系。

```
┌─────────────────────────────────────────────────────────────┐
│  Apple 风格 Pinecone 色彩系统                                │
├───────────────┬──────────┬──────────────────────────────────┤
│  Token        │ Hex      │  用途                            │
├───────────────┼──────────┼──────────────────────────────────┤
│  Background   │ #0A0A14  │  最深层背景 (类似 tvOS 深空黑)     │
│  Surface      │ #161625  │  卡片、面板基色                    │
│  Glass        │ rgba(255,255,255, 0.06) │ 毛玻璃面板       │
│  GlassBorder  │ rgba(255,255,255, 0.10) │ 玻璃边框         │
│  Primary      │ #5E9EFF  │  主强调色 (Apple Blue 变体)       │
│  PrimaryDim   │ #3A7DE0  │  按压/非活跃态                     │
│  Secondary    │ #FF9F43  │  次要强调 (温暖橙，学习激励感)      │
│  TextPrimary  │ #FFFFFF  │  主要文字                          │
│  TextSecondary│ #9898B0  │  次要文字/副标题                   │
│  TextMuted    │ #5C5C78  │  禁用/占位文字                     │
│  Success      │ #30D158  │  完成/已安装 (iOS Green)           │
│  Warning      │ #FFD60A  │  提示/警告 (iOS Yellow)            │
│  Error        │ #FF453A  │  错误/删除 (iOS Red)               │
│  FocusRing    │ #5E9EFF  │  焦点环 (2dp, 外发光 blur 8dp)    │
└───────────────┴──────────┴──────────────────────────────────┘
```

**与当前的对比**:
| 维度 | 当前 | Apple 风格 |
|------|------|-----------|
| 背景深度 | 单一 #1A1A2E | 多层: Background → Surface → Glass → Card |
| 强调色 | 仅一个 #4FC3F7 | 主蓝 #5E9EFF + 暖橙 #FF9F43 + 系统色体系 |
| 文本层级 | 白色 / #B0B0C0 两级 | 4 级: 白→灰→暗灰→禁用 |
| 材质感 | 纯色平面 | 毛玻璃透明 + 微边框 |

### 2.3 字体系统 (Typography)

针对 **TV 4m 观看距离** 优化的字号层级：

| Token | 字号 | 字重 | 用途 |
|-------|------|------|------|
| `displayLarge` | 48sp | Bold | 首页大标题 / 时间 |
| `headlineLarge` | 32sp | SemiBold | 分类标题 |
| `headlineMedium` | 24sp | Medium | 卡片标题 |
| `bodyLarge` | 18sp | Regular | 卡片副标题 |
| `bodyMedium` | 15sp | Regular | 侧边栏项目 |
| `labelMedium` | 13sp | Medium | 标签/徽标 |

**字体建议**: 使用 **HarmonyOS Sans** (鸿蒙字体，中文阅读友好) 或 **Noto Sans SC** Medium。避免当前 `FontFamily.Default` (Roboto，中文字形一般)。

### 2.4 空间系统 (Spacing Scale)

基于 8dp 基数的 Apple 风格间距系统：

| Token | 值 | 用途 |
|-------|-----|------|
| `space-xs` | 4dp | 图标与文字间距 |
| `space-sm` | 8dp | 紧密关联元素间 |
| `space-md` | 16dp | 卡片内 padding |
| `space-lg` | 24dp | 卡片间 / 区块间 |
| `space-xl` | 32dp | 内容区 padding |
| `space-2xl` | 48dp | 大区块间隔 |
| `space-3xl` | 64dp | 页面级间隔 |

---

## 第三部分：交互优化方案 (Interaction Redesign)

### 3.1 D-Pad 焦点导航系统 (核心优化)

**当前问题**: 卡片无焦点态，仅点击态切换背景色。遥控器导航"盲操"困难。

**Apple 风格方案** — 四阶段焦点反馈：

```
焦点状态机：
  Rest (静止)    →  Card 透明底，Scale 1.0, 无阴影
  Hover (悬停)   →  背景变 Glass (rgba白0.08), Scale 1.03, 阴影提升
  Press (按下)   →  Scale 0.97, 阴影下降, 50ms
  Release (释放) →  回弹到 Hover 或 Rest
```

**具体实现**:
```kotlin
// 焦点动画
Modifier.animateContentSize()  // Compose 内置
Modifier.scale(focusScale)     // Focus 1.0 → 1.03
Modifier.shadow(elevation)     // Rest 0dp → Hover 8dp
Modifier.border(2.dp, FocusRing, CircleShape) // 焦点环
```

**关键规则**（来自 ui-ux-pro-max §1 Accessibility + §2 Touch）:
- 焦点环最小可见宽度: 3dp
- 焦点移动动画: 200ms ease-out（非瞬间跳切）
- 卡片最小可聚焦尺寸: 120dp × 80dp（远大于 44×44dp 触屏最小值）

### 3.2 Tab 栏重新设计

```
当前:  [🌐 网站] [📱 App] [⚙️ 设置]     ← 文字 + 背景色
Apple: ───●─────────────────────────    ← 胶囊指示器 + 滑动动效
         网站   App   设置
```

- 去掉 Emoji，改用 SF Symbols 风格的线条图标（24dp）
- 选中态: 白色胶囊背景 + 图标/文字变为系统蓝
- 切换动画: 指示器位移动画 250ms spring()
- 焦点支持: Tab 按钮可被遥控器上下键导航

### 3.3 侧边栏重新设计

```
当前:                              Apple 风格:
┌──────────────────┐              ┌──────────────────────┐
│ 🏠 首页           │              │ ⊙  首页              │ ← 圆点图标
│ ▸ 🏛 国字号       │              │ ──────────────────── │
│   教育部          │              │ ▼ 国字号官方平台      │ ← 分组标题 13sp
│   科普科学        │              │   教育部智慧教育体系   │ ← 12sp 缩进
│ ▾ 🌟 优质站       │              │   科普科学类          │
│   语文/古诗文     │              │   文史阅读与语言文字   │
│   理科/实验       │              │ ▶ 优质学习网站        │ ← 折叠态
│   ...            │              │ ▶ 可视化交互学习      │
└──────────────────┘              └──────────────────────┘
```

**改进点**:
1. **Emoji → 线条图标** (SF Symbols 风格，与 Apple 生态一致)
2. **分组头 13sp Medium** + 项目 14sp Regular，字号对比建立层级
3. **当前分类指示器**: 蓝色竖线 (3dp) 而非整体背景变色
4. **折叠动画**: `AnimatedVisibility` 展开/收起, 200ms
5. **减法**: 去掉"🏛"等 emoji，用简洁文字 + 图标代替

### 3.4 卡片组件重新设计

```
当前卡片:                          Apple 风格卡片:
┌────────────────┐                ┌──────────────────────┐
│     (●)       │                │  ┌──────────────┐    │  ← 圆角封面图
│      A        │                │  │    🧪        │    │  ← 128dp × 96dp
│   课程标题     │                │  │  科学实验    │    │
│   分类名       │                │  └──────────────┘    │
└────────────────┘                │  科学实验            │  ← 16sp Medium
                                   │  理科/实验          │  ← 13sp secondary
                                   └──────────────────────┘
                                    ↑ 毛玻璃底 + 微边框
```

**改进点**:
1. **封面图替代首字母圆圈** — 用每个类别的渐变色块 + 图标 (如理科=蓝色烧瓶图标)
2. **毛玻璃卡片底座**: `background(rgba(255,255,255,0.06))` + `border(1dp, rgba(255,255,255,0.10))`
3. **焦点态**: Scale 1.03 + 外阴影 + 蓝色焦点环
4. **卡片圆角**: 16dp (当前 12dp)
5. **类型徽标**: 右上角半透明标签 "官方" / "互动" / "视频"

---

## 第四部分：动效系统 (Motion & Animation)

### 4.1 动效总则

遵循 Apple HIG + ui-ux-pro-max §7 Animation 规则：

| 规则 | 值 |
|------|-----|
| 默认动效时长 | 200ms |
| 焦点移动时长 | 150ms |
| Tab 切换时长 | 250ms spring() |
| 页面转场时长 | 300ms |
| 缓动曲线 | ease-out (进场), ease-in (退场) |
| 尊重无障碍设置 | 检测 `reduced-motion` 时全部降级到 0ms |

### 4.2 场景动效清单

| 场景 | 动效 | 时长 | 备注 |
|------|------|------|------|
| 卡片获得焦点 | Scale 1→1.03 + 阴影提升 | 150ms | 所有可聚焦元素 |
| 卡片失去焦点 | Scale 1.03→1 + 阴影消失 | 150ms | |
| 卡片按下 | Scale 1.03→0.97 | 50ms | 物理按压感 |
| Tab 切换 | 指示器平移 + 内容 crossfade | 250ms | AnimatedContent |
| 侧边栏展开/折叠 | AnimatedVisibility 垂直展开 | 200ms | |
| 首页加载 | 卡片 stagger 淡入 (每张 50ms 间隔) | 50ms × N | 首次进入 |
| 设置项点击 | 短暂闪烁 + 推进新页面 | 150ms + 页面转场 | |
| 搜索框聚焦 | 宽度展开 60%→80% | 200ms | 视觉焦点引导 |
| 错误/空状态 | 图标弹入 scale 0→1 spring | 400ms | 情感化反馈 |

---

## 第五部分：Spatial UI — Apple tvOS/visionOS 风格改造

这是整个改造的**差异化卖点 (Signature Element)**。借鉴 visionOS 的空间设计语言，为 TV 大屏创造"纵深感"：

### 5.1 三层空间模型

```
┌──────────────────────────────────────────────┐
│  Z=0  背景层 (Background)                     │
│  · 深色渐变底 (#0A0A14→#161625)               │
│  · 可选: 微妙的几何渐变/暗角                    │
├──────────────────────────────────────────────┤
│  Z=1  玻璃层 (Glass Panels)                   │
│  · 侧边栏: rgba(255,255,255, 0.04) + blur     │
│  · Tab 栏: rgba(255,255,255, 0.06)            │
│  · 内容区卡片: rgba(255,255,255, 0.06)         │
│  · 边框: 1dp rgba(255,255,255, 0.08)          │
│  · 模拟 backdrop-blur 效果 (Compose 用        │
│    Modifier.blur() 或半透明+渐变替代)           │
├──────────────────────────────────────────────┤
│  Z=2  悬浮层 (Floating Elements)              │
│  · 焦点提示环 (蓝色发光)                        │
│  · Tooltip / 弹窗                             │
│  · Toast / 通知                               │
│  · 应用图标弹窗 (App 启动确认)                  │
└──────────────────────────────────────────────┘
```

### 5.2 Compose 中实现毛玻璃

由于 Jetpack Compose 不支持原生 `backdrop-filter: blur()`，采用替代方案：

```kotlin
// 方案 A: 半透明色 + 微渐变模拟玻璃感 (性能好，TV 可用)
Modifier.background(
    Brush.verticalGradient(
        colors = listOf(
            Color.White.copy(alpha = 0.08f),
            Color.White.copy(alpha = 0.04f)
        )
    )
)

// 方案 B: RenderEffect blur (Android 12+)
Modifier.blur(radius = 12.dp) // 仅适用于背景
```

### 5.3 自适应背景 (Adaptive Background)

tvOS 的顶部区域会根据选中的内容微妙变化。对 PineCone 而言：
- 选中"网站"Tab → 背景微偏蓝
- 选中"App"Tab → 背景微偏紫
- 选中"设置"Tab → 背景保持深黑

实现方式: `animateColorAsState()` + 背景渐变目标值随 Tab 切换。

---

## 第六部分：信息架构优化

### 6.1 当前 IA 问题

```
🌐 网站 (6 分组, 30+ 分类, 100+ 条目)     ← 信息量巨大，子级无预览
📱 App  (动态分类, 来自 apps.json)         ← 数量可控
⚙️ 设置 (3 分类, 15 条目)                  ← 结构清晰
```

- "网站" Tab 信息过载，30+ 个分类堆叠在侧边栏
- 用户每次要找内容必须展开分组→点击分类→扫描网格，三步操作

### 6.2 Apple 风格优化方案

```
Tab 1: 🧭 发现 (Discover)    ← 原"网站"，但增加顶层入口
  ├─ 首页 (搜索 + AI 推荐 + 最近浏览)
  ├─ 分类浏览 (侧边栏，简化到 5-6 大类)
  └─ 收藏夹

Tab 2: 📦 应用 (Apps)        ← 保持不变，优化视觉
  └─ 分类网格 + 安装状态标识

Tab 3: ⚙️ 设置 (Settings)    ← 保持不变，结构已接近 iOS Settings
```

**核心变化**: 增加"发现"首页作为默认着陆点（当前已有 WebLandingPage），强化其 AI 推荐和最近浏览功能。

---

## 第七部分：具体实施清单 (Implementation Checklist)

### Phase 1: Design Token 替换 (1-2 天)

- [ ] `Color.kt` 替换为新色彩系统 (Background/Surface/Glass/Primary/Secondary/FocusRing/Text×3/Status×3)
- [ ] `Theme.kt` 更新 `PineconeDarkColorScheme`，映射新 tokens 到 Material 3 color scheme
- [ ] `Type.kt` 添加完整字号层级 (displayLarge → labelMedium)，替换系统默认字体为 Noto Sans SC
- [ ] 全局 Emoji 扫描 → 替换为 Material Icons / 自定义矢量图标

### Phase 2: 基础组件重构 (2-3 天)

- [ ] `ResourceCard.kt` — 全新卡片设计：封面图 + 毛玻璃底 + 焦点动画 + 焦点环
- [ ] `HomeTabRow.kt` — 胶囊指示器 + 滑动动画 + Tab 图标化
- [ ] `SidebarPanel.kt` — 层级视觉重建 + Emoji→Icon + AnimatedVisibility
- [ ] `WebLandingPage.kt` — 搜索框 focus 动画 + 推荐卡片 stagger 动画

### Phase 3: 动效系统 (1-2 天)

- [ ] 添加 `AnimationPresets.kt` (统一动效参数: duration/easing/spring)
- [ ] 所有可聚焦组件添加焦点态动画 (scale + shadow + border)
- [ ] `AnimatedContent` 包裹内容区，Tab 切换 crossfade
- [ ] 侧边栏折叠 AnimatedVisibility
- [ ] 首页首次加载 stagger 动画

### Phase 4: Spatial UI 实验 (2-3 天)

- [ ] 三层空间模型实现 (背景渐变 + 玻璃面板 + 悬浮焦点环)
- [ ] 自适应背景色 (Tab 切换微调背景渐变)
- [ ] 玻璃面板效果（半透明渐变 + renderEffect blur on Android 12+）
- [ ] 卡片阴影系统 (elevation 0→4→8→16 dp)

### Phase 5: TV 专项优化 (1 天)

- [ ] 全页面 D-Pad 焦点路径测试 (保证所有元素可达)
- [ ] 远距离可读性验证 (最小字号 13sp → 至少 2m 可读)
- [ ] 焦点动画性能测试 (RPi5 流畅度验证)
- [ ] reduced-motion 无障碍检测集成

---

## 第八部分：前后对比预览 (Before/After)

### Tab Bar

```
Before:  [🌐 网站]  [📱 App]  [⚙️ 设置]     ← Emoji + 背景色块
After:   ━━━━━●━━━━━━━━━━━━━━━━━━━━━━━━━     ← 胶囊滑动
         发现     应用      设置               ← 矢量 icon + 文字
           ↑ 选中态: 白底蓝字胶囊
```

### Card

```
Before:                          After:
┌──────────┐                    ┌───────────────────┐
│   (●)   │  ← 首字母圆圈        │  ┌─────────────┐  │ ← 128×96dp 封面
│    A    │                    │  │   🔬 实验   │  │   渐变背景+图标
│ 课程标题 │  ← 14sp 白色        │  └─────────────┘  │
│ 分类名   │  ← 11sp 灰色        │  课程标题         │ ← 16sp
└──────────┘                    │  分类名           │ ← 13sp
  12dp 圆角                      │         🏷 官方   │ ← 标签
                                 └───────────────────┘
                                   16dp 圆角 + 玻璃底
```

### Sidebar

```
Before:                          After:
🏠 首页                           ⊙  首页
🏛 国字号                         ─────────────────
  教育部智慧教育...                ▼ 国字号官方平台
  科普科学类                        教育部智慧教育体系
  文史阅读...                       科普科学类
▸ 🌟 优质站                          文史阅读与语言文字
▸ 🔬 可视化                        ▶ 优质学习网站
▸ 🌍 欧美                          ▶ 可视化交互学习
▸ 🎬 纪录片                        ▶ 欧美课标同步
▸ 🤖 AI 通识                      ▶ 纪录片专区
                                  ▶ AI 通识学习
```

---

## 第九部分：风险评估

| 风险 | 等级 | 缓解措施 |
|------|------|---------|
| Compose blur 性能在 RPi5 上不佳 | 中 | 用半透明渐变代替真实 blur；保留 `RenderEffect` 作为 Android 12+ 可选增强 |
| Emoji→Icon 工作量大 (100+ 条目) | 中 | 优先替换 Tab/侧边栏分组；卡片用渐变色块替代 |
| 动画在 60fps TV 上卡顿 | 低 | 使用 `spring()` + `graphicsLayer` 硬件加速；避免 layout 动画 |
| 改动量大，引入回归 bug | 中 | 分 Phase 增量提交；每个 Phase 独立可测试 |

---

## 附录：参考资源

- **Apple tvOS Human Interface Guidelines**: 深度分层、焦点系统、远距离设计
- **Apple visionOS Spatial Design**: 玻璃材质、深度感知、注视交互
- **UI/UX Pro Max Skill DB**: `--domain style "Spatial UI (VisionOS)"`, `--domain ux "focus states"`
- **Frontend Design Skill**: 字体配对、排版个性、标志性元素原则

---

> **设计箴言**: *"真正的简洁不是没有东西可加，而是没有东西可以拿掉。"* — 乔纳森·艾夫
>
> 我们的签名元素 (Signature): **毛玻璃 + 空间深度 + 物理焦点动画** — 三者结合创造出一个"活着的"、有触感的 TV 界面，这在当前 Android TV 生态中是独一无二的。
