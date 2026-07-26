package com.pinecone.pinecone.ui.theme

import androidx.compose.ui.graphics.Color

// ═══════════════════════════════════════════════════════════
//  Apple-Style Spatial Color System
//  Inspired by tvOS / visionOS depth + vibrancy model
// ═══════════════════════════════════════════════════════════

// ── Depth Layer 0: Background ──
val PineBackground  = Color(0xFF0A0A14)   // Deep space black (was #1A1A2E)
val PineBackgroundAlt = Color(0xFF10101E)  // Subtle variant for gradients

// ── Depth Layer 1: Surfaces / Glass ──
val PineSurface     = Color(0xFF161625)   // Card base (was #252545)
val PineGlass       = Color(0x0FFFFFFF)   // Glass panel: rgba(255,255,255, 0.06)
val PineGlassBorder = Color(0x1AFFFFFF)   // Glass border: rgba(255,255,255, 0.10)

// ── Depth Layer 2: Elevated / Floating ──
val PineElevated    = Color(0xFF1E1E32)   // Dialog, popup background
val PineElevatedBorder = Color(0x26FFFFFF) // rgba(255,255,255, 0.15)

// ── Accent System ──
val PinePrimary      = Color(0xFF5E9EFF)  // Apple Blue variant
val PinePrimaryDim   = Color(0xFF3A7DE0)  // Pressed / inactive state
val PineSecondary    = Color(0xFFFF9F43)  // Warm orange — learning motivation cue

// ── Legacy alias (backward compatible) ──
val PineAccent       = PinePrimary        // was #4FC3F7
val PineHighlight    = Color(0x1A5E9EFF)  // rgba(94,158,255, 0.10) — subtle highlight
val PineHighlightSolid = Color(0xFF2A3A5A) // Solid highlight for backgrounds

// ── System Status Colors (iOS-style) ──
val PineSuccess      = Color(0xFF30D158)  // iOS Green
val PineWarning      = Color(0xFFFFD60A)  // iOS Yellow
val PineError        = Color(0xFFFF453A)  // iOS Red

// ── Text Hierarchy ──
val PineTextPrimary   = Color.White               // #FFFFFF
val PineTextSecondary = Color(0xFF9898B0)         // Muted gray (was #B0B0C0)
val PineTextMuted     = Color(0xFF5C5C78)         // Disabled / placeholder (was #888888)
val PineTextOnAccent  = Color(0xFF0A0A14)         // Text on accent background

// ── Legacy structural colors (re-mapped) ──
val PineTopBar        = Color(0x0D5E9EFF)         // Glass top bar: rgba(94,158,255, 0.05)
val PineSidebar       = Color(0x08FFFFFF)         // Glass sidebar: rgba(255,255,255, 0.03)
val PineCardBorder    = Color(0x14FFFFFF)         // Card border: rgba(255,255,255, 0.08)

// ── Focus System ──
val PineFocusRing     = PinePrimary               // Focus ring color
val PineFocusGlow     = Color(0x335E9EFF)         // Focus outer glow: rgba(94,158,255, 0.20)

// ── Category Accent Palette (for card gradients) ──
val PineCatBlue       = Color(0xFF3B82F6)   // 理科 / 编程
val PineCatGreen      = Color(0xFF34D399)   // 生物 / 自然
val PineCatPurple     = Color(0xFF8B5CF6)   // 文史 / 语言
val PineCatOrange     = Color(0xFFF97316)   // 纪录片 / 探索
val PineCatPink       = Color(0xFFEC4899)   // AI / 科创
val PineCatTeal       = Color(0xFF14B8A6)   // 地理 / 天文
val PineCatDefault    = Color(0xFF6366F1)   // 默认

// ═══════════════════════════════════════════════════════════
//  Material 3 compatibility colors (untouched)
// ═══════════════════════════════════════════════════════════
val Purple80 = Color(0xFFD0BCFF)
val PurpleGrey80 = Color(0xFFCCC2DC)
val Pink80 = Color(0xFFEFB8C8)
val Purple40 = Color(0xFF6650a4)
val PurpleGrey40 = Color(0xFF625b71)
val Pink40 = Color(0xFF7D5260)
