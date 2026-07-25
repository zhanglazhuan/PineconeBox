# PineCone OS OTA 自动更新系统 — 设计文档

**日期**: 2026-07-24  
**状态**: 设计完成，待实现  
**版本**: 1.0

---

## 1. 目标与范围

### 1.1 目标

为 PineCone OS 构建基于 HTTP 的 OTA 更新系统。支持 Launcher APK 的远程检测、下载、校验、安装，无需重新刷机。GuardService 后台定时检查，家长确认后一键更新。

### 1.2 范围

- 只做 **Launcher APK 更新**（~15MB），不做 ROM 更新
- 自建 HTTP Update Server（开发期本地，生产期切阿里云 OSS）
- 下载机制复用 installer 的 HTTP 流式下载 + 断点续传
- 更新安装需要 system app 权限（root），无需重启设备

### 1.3 非目标

- 不做 ROM 级更新（需 dd 覆写 SD 卡，风险大）
- 不做增量更新（delta patch）
- 不做灰度发布 / 分组推送

---

## 2. 整体架构

```
┌───────────────────────────────────────────────────────────┐
│                     Update Server                          │
│                                                            │
│  GET /api/manifest.json                                    │
│  {                                                          │
│    "launcher": {                                            │
│      "versionCode": 2,                                     │
│      "versionName": "1.1.0",                               │
│      "url": "/files/app-release.apk",                      │
│      "size": 15234567,                                     │
│      "sha256": "abc...",                                   │
│      "changelog": "- 修复闪退\n- 新增功能"                   │
│    }                                                        │
│  }                                                          │
│                                                            │
│  /files/app-release.apk    ← 静态文件                       │
└───────────────────────────────────────────────────────────┘
          │  HTTP GET
          ▼
┌───────────────────────────────────────────────────────────┐
│                   PineCone OS (RPi5)                      │
│                                                            │
│  GuardService (:guard 进程)                                 │
│  ├── UpdateChecker.kt     (每天检查一次)                    │
│  ├── UpdateInstaller.kt   (下载+校验+替换+重启)             │
│  └── GuardStateListener   (通知 UI)                        │
│                                                             │
│  家长设置 UI                                                │
│  ├── 检查更新 入口                                          │
│  └── 更新详情 + 进度                                        │
└───────────────────────────────────────────────────────────┘
```

---

## 3. Server 端

### 3.1 目录结构

```
update-server/
├── server.py              # 开发用 Python HTTP 服务
├── files/                  # 静态文件
│   ├── manifest.json       # 版本清单
│   └── app-release.apk     # 最新 APK
└── deploy.sh               # 部署脚本
```

### 3.2 manifest.json 结构

```json
{
  "launcher": {
    "versionCode": 2,
    "versionName": "1.1.0",
    "url": "/files/app-release.apk",
    "size": 15234567,
    "sha256": "e3b0c44298...",
    "changelog": "- 修复绘本阅读闪退\n- 新增英语词典功能"
  },
  "minApiLevel": 36,
  "minAppVersion": 1
}
```

### 3.3 生产部署（阿里云 OSS）

```
OSS Bucket: pinecone-updates/
├── manifest.json
├── app-release-v2.apk
└── app-release-v1.apk  (保留旧版，用于回滚)
```

URL 切换通过 `config.py` 的 `BASE_URL_LOCAL` / `BASE_URL_OSS` 控制。

---

## 4. Client 端

### 4.1 新增组件

```
guard/src/main/java/com/pinecone/guard/
├── api/
│   └── UpdateResult.kt         # 更新结果密封类
└── engine/
    ├── UpdateChecker.kt         # HTTP 检查 + 版本比较
    └── UpdateInstaller.kt       # 下载 + SHA256 + 替换文件

app/src/main/java/com/pinecone/launcher/ui/guard/
└── UpdateActivity.kt            # 更新详情 + 进度 UI
```

### 4.2 UpdateChecker 接口

```kotlin
class UpdateChecker(context: Context) {
    suspend fun checkForUpdate(): UpdateResult
}

sealed class UpdateResult {
    object UpToDate : UpdateResult()
    data class Available(
        val versionCode: Int, val versionName: String,
        val url: String, val size: Long, val sha256: String,
        val changelog: String
    ) : UpdateResult()
    data class Error(val message: String) : UpdateResult()
}
```

### 4.3 UpdateInstaller 接口

```kotlin
class UpdateInstaller(context: Context) {
    suspend fun downloadAndInstall(
        update: UpdateResult.Available,
        onProgress: (Float) -> Unit
    ): Result<Unit>
}
```

流程：
1. HTTP 流式下载 APK 到私有目录（支持 Range 断点续传）
2. SHA256 校验
3. 替换 `/system/app/PineConeLauncher/PineConeLauncher.apk`
4. chmod 644 + 重启 Launcher 进程

### 4.4 检查频率

GuardService 每天凌晨 3:00 执行一次检查（低流量时段）。家长可在设置中手动触发。

---

## 5. UI 入口

家长设置新增一行：

```
🆕 检查更新        当前版本 1.0.0 · 已是最新
                    ↑ 无更新时灰色

🆕 发现新版本 1.1.0  ← 绿色圆点闪烁
    15 MB · 点击查看详情
```

点击进入更新详情页：版本号、大小、changelog、[立即更新] 按钮 → 下载进度 → 安装完成。

---

## 6. 版本号联动

```
app/build.gradle.kts          manifest.json
──────────────────            ─────────────
versionCode = 1               "versionCode": 1
versionCode = 2               "versionCode": 2   ← 发版时同步修改
```

**发布流程**：改 versionCode → 编译 APK → 更新 manifest (sha256 + changelog) → 上传 server。

---

## 7. 与 Installer 复用

Installer 的 HTTP 下载能力被复用：
- `installer/screens/download.py` 的 `requests.get(stream=True)` + 断点续传（Range header）
- UpdateInstaller 使用相同的 HTTP 流式下载 + 进度回调模式
- `config.py` 的 DownloadConfig 类复用于 OTA URL 配置

---

## 8. 未解决的问题

- SHA256 校验性能：15MB APK 在 RPi5 上计算哈希约 0.3 秒，可接受
- APK 替换后，是否需要触发 Android dex2oat 重优化？实测后确认
