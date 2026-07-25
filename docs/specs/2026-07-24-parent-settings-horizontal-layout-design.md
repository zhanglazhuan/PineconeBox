# 家长设置页面 - 苹果风格水平布局

**日期:** 2026-07-24
**类型:** UI 布局调整

## 目标

将家长设置页面（`ParentSettingsActivity`）的列表项从 Android 默认的上下布局（`simple_list_item_2`）改为 iOS 风格的左右水平布局。

## 当前状态

- 使用 `android.R.layout.simple_list_item_2`，text1 在上、text2 在下
- 所有设置项在一个 ListView 中显示

## 设计方案

### 新建 `res/layout/item_parent_setting.xml`

水平 LinearLayout 布局：

```
┌──────────────────────────────────────────┐
│  ⏱️ 每日累计时长          2 小时 30 分  > │
└──────────────────────────────────────────┘
```

- 左侧：label（title），`layout_weight=0`，固定宽度或 wrap_content
- 中间：弹簧（空 View，`layout_weight=1`）将右侧内容推到行尾
- 右侧：value（subtitle）+ 箭头 `>`

### 修改 `ParentSettingsActivity.kt`

- 将 `ArrayAdapter` 的 layout 参数从 `android.R.layout.simple_list_item_2` 改为 `R.layout.item_parent_setting`
- 移除 `getView()` 中对 `android.R.id.text1`/`text2` 的依赖（这些是 simple_list_item_2 的内部 ID）
- 使用自定义 ID 绑定 label 和 value 的 TextView

### 不变

- `activity_parent_settings.xml` 保持不变（ListView 容器不需要改）
- 数据模型 `SettingsItem` 保持不变
- 点击事件处理保持不变
