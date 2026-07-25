# Design: System Settings Back Button

**Date:** 2026-07-24
**Status:** Approved
**Scope:** Add "← 返回" back button to the top-left corner of every settings page and sub-page

## Goal

Improve navigation UX in the system settings module by adding a visible, interactive back button (← 返回) in the top-left corner. The button must support both **touch/mouse click** and **remote control D-pad focus + confirm**.

## Current State

All settings activities independently build their UI (mostly programmatic `LinearLayout`). There is no shared base class, no back button, and no consistent header. Users rely solely on the hardware back key.

| Activity | UI Style | Has Back Button |
|----------|----------|:---:|
| `ParentSettingsActivity` | XML LinearLayout + ListView | No |
| `DailyLimitFragment` | Programmatic LinearLayout | No |
| `BreakRuleFragment` | Programmatic LinearLayout | No |
| `TimeWindowFragment` | Programmatic LinearLayout | No |
| `CategoryLimitFragment` | Programmatic LinearLayout | No |
| `AppLimitFragment` | Programmatic LinearLayout | No |
| `CreditConfigFragment` | Programmatic LinearLayout | No |
| `UsageHistoryFragment` | Programmatic LinearLayout | No |
| `PinSetupActivity` | Programmatic LinearLayout | No |
| `UpdateActivity` | Programmatic LinearLayout | No |
| `SetupWizardActivity` | Programmatic LinearLayout | No |

## Design

### Approach: Shared `BaseSettingsActivity` (chosen over utility function and XML include)

Create one abstract base class that all settings activities extend. The base class provides a `setSettingsContent(title, contentView)` method that wraps any content view with a standard header bar containing the back button and title.

### New File

**`BaseSettingsActivity.kt`** — `launcher/app/src/main/java/com/pinecone/launcher/ui/guard/BaseSettingsActivity.kt`

```kotlin
abstract class BaseSettingsActivity : Activity() {
    fun setSettingsContent(title: String, contentView: View)
}
```

Internal layout structure produced by `setSettingsContent()`:

```
LinearLayout (vertical, background #FF1A1A2E, padding 32dp)
├── HeaderBar (horizontal LinearLayout)
│   ├── Button "← 返回"  — focusable, clickable → finish()
│   └── TextView title   — centered, 24sp, white
└── contentView           — the child-provided business UI
```

### Back Button Spec

| Property | Value |
|----------|-------|
| Text | `← 返回` |
| Text size | 20sp |
| Text color | `#FFFFFF` (white) |
| Background | Transparent (default button) |
| Focusable | `true` (D-pad navigation) |
| Clickable | `true` (touch/mouse) |
| Action | `finish()` |
| Focus highlight | Inherited from `Theme.Leanback` |

### Files Modified (11 files)

All editor activities and settings activities:
1. Change parent class: `Activity()` → `BaseSettingsActivity()`
2. Remove manual top padding / background / title setting (base class handles it)
3. Wrap business UI in `setSettingsContent("title", businessLayout)`

List:
1. `ParentSettingsActivity.kt` — refactor XML layout away, use programmatic approach
2. `DailyLimitFragment.kt`
3. `BreakRuleFragment.kt`
4. `TimeWindowFragment.kt`
5. `CategoryLimitFragment.kt`
6. `AppLimitFragment.kt`
7. `CreditConfigFragment.kt`
8. `UsageHistoryFragment.kt`
9. `PinSetupActivity.kt`
10. `UpdateActivity.kt`
11. `SetupWizardActivity.kt`

### Files Potentially Deleted

- `activity_parent_settings.xml` — `ParentSettingsActivity` will use programmatic UI like all others

### Interaction Flow

**Remote control:**
1. Enter settings page → focus defaults to first interactive element in content area
2. Press UP → focus moves to `← 返回` button in header
3. Press CONFIRM → `finish()`, return to parent page

**Touch / mouse:**
1. Click `← 返回` at top-left corner → `finish()`, return to parent page

### Non-scope

- `MainActivity` — it is the Home screen, no back button needed
- Android built-in WiFi/System settings pages — opened via `Intent`, not our UI

## Self-Review

- [x] No TBD or placeholders
- [x] All 11 settings activities are accounted for
- [x] Both touch and remote interaction paths are specified
- [x] Scope is focused: single concern (back button), single new file
