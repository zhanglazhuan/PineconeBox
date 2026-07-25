# Design: Top Tab Navigation Redesign

**Date:** 2026-07-25
**Status:** Approved
**Scope:** Redesign launcher from single BrowseSupportFragment to top-tab + left-sidebar layout

## Goal

Replace the flat 16-row BrowseSupportFragment with a 3-tab top navigation bar (资源 / App / 设置), each tab driving a filtered set of left-sidebar headers and right-side card rows.

## Current State

- `MainActivity` uses a single `BrowseSupportFragment` with `HEADERS_ENABLED`
- All 16 category rows flat in one adapter
- No top-level navigation

## Target Layout

```
┌──────────────────────────────────────────┐
│  [ 🌐 资源 ]  [ 📱 App ]  [ ⚙️ 设置 ]    │  top tab bar (56dp)
├────┬─────────────────────────────────────┤
│ 英语│ [card] [card] [card] [card]         │
│ 绘本│                                      │  BrowseSupportFragment
│ 纪录│ [card] [card] [card] [card] [card]  │  (HEADERS_ENABLED)
│ ... │                                      │
└────┴─────────────────────────────────────┘
```

## Approach: Custom TopBar + Dynamic BrowseSupportFragment (方案 A)

Reuse Leanback's `BrowseSupportFragment` for the header sidebar and card rows. Add a custom horizontal button bar above it. Tab switching rebuilds the fragment's adapter.

## Data Organization

| Tab | HeaderItem IDs | Categories |
|-----|---------------|------------|
| 🌐 资源 | 0-8 | 英语专区, 绘本阅读, 纪录片, 学习App, AI工具, 在线学习, 纪录片/视频, 编程启蒙, 百科/工具 |
| 📱 App | 10-15 | 视频影音, 阅读听书, 知识学习, 创意启蒙, 益智休闲, 实用工具 |
| ⚙️ 设置 | 20-21 | 系统设置, 家长设置 |

## Files Changed

| File | Change |
|------|--------|
| `activity_main.xml` | LinearLayout wrapping top bar + FragmentContainerView |
| `MainActivity.kt` | Extract data per tab, add tab bar logic, `switchTab()` to rebuild fragment adapter |

## Interaction

- **Remote:** L/R between tabs at top, confirm activates tab → focus jumps to left sidebar → Up/Down between headers → L/R to cards
- **Touch:** Tap tab, tap header, tap card

## Non-scope

- Tab transition animations (can add later)
- Persisting last-selected tab across restarts
