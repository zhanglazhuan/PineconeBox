# PineCone OS 刷机安装程序 — 设计文档

**日期**: 2026-07-23  
**状态**: 设计完成，待实现  
**版本**: 1.0

---

## 1. 目标与背景

### 1.1 问题

由于 LineageOS 存在商业协议限制，不能将预装 LineageOS 的 SD 卡直接销售给用户。需要一种合规的分发方式：SD 卡仅含引导程序，用户开机后通过电视交互完成系统下载与安装。

### 1.2 目标

构建一个运行在树莓派上的 TV 端刷机程序，引导用户完成 Wi-Fi 连接、ROM 下载、APK 融合、系统刷写全流程。整个过程无需 PC 参与。

### 1.3 非目标

- 不防范中途断电（最多重刷，不损坏硬件）
- 不实现 OTA 增量更新（后续版本考虑）
- 不支持多系统引导

---

## 2. 整体架构

```
用户拿到 SD 卡（仅含引导程序 + 刷机工具）
     │
     ▼
插入树莓派 → 开机 → HDMI 输出到电视
     │
     ▼
┌──────────────────────────────────────────────┐
│         Raspberry Pi OS Lite (arm64)          │
│                                               │
│  ┌──────────────────────────────────────┐     │
│  │  刷机程序 (Python 3 + Pygame 2)      │     │
│  │                                      │     │
│  │  ① 启动页  (Logo + 初始化)          │     │
│  │  ② Wi-Fi   (扫描 + 密码输入)         │     │
│  │  ③ 下载    (~757MB ROM zip)         │     │
│  │  ④ 注入    (解压 + 融合 APK)        │     │
│  │  ⑤ 刷写    (dd 写卡 15GB)          │     │
│  │  ⑥ 完成    (提示关机重开)           │     │
│  └──────────────────────────────────────┘     │
│                                               │
│  存储空间 (P2 剩余 ~58GB):                      │
│    /data/pinecone-imgs/                       │
│      ├── baseos.zip          (下载缓存)       │
│      └── baseos.img          (解压后15GB)    │
└──────────────────────────────────────────────┘
```

### 刷写原理

树莓派启动时 kernel + initramfs 全部载入 RAM，之后可安全地用 `dd` 将 LineageOS 镜像写入整张 SD 卡。运行中的进程不受影响，`sync && reboot` 后从新系统引导。

### 刷写前后 SD 卡状态对比

**刷写前：**
| 分区 | 类型 | 大小 | 内容 |
|------|------|------|------|
| P1 | FAT32 | 256MB | 树莓派固件 + 内核 |
| P2 | ext4 | ~58GB | Pi OS Lite + 刷机程序 + 下载文件 |

**刷写后：**
| 分区 | 类型 | 大小 | 内容 |
|------|------|------|------|
| P1 | FAT32 | 128MB | LineageOS boot |
| V1 | ext4 | 3GB | system (含 PineCone Launcher) |
| V2 | ext4 | 384MB | vendor |
| V3 | ext4 | 16MB | misc |
| P3 | ext4 | ~59GB | userdata (首次启动自动扩容) |

---

## 3. 技术选型

| 层面 | 选择 | 理由 |
|------|------|------|
| 底层系统 | Raspberry Pi OS Lite (bookworm, arm64) | 开箱即用 Wi-Fi + ext4 工具 |
| UI 框架 | Pygame 2 | 直接 framebuffer 渲染，不需要 X11/Wayland |
| 下载 | Python `requests` + 断点续传 | 内置支持 Range header |
| 解压/注入 | Python `zipfile` + `subprocess(mount)` | 替代 bash 脚本中用到的 unzip/mount/dd |
| 刷写 | `dd` + 进度解析 | 直接块设备写入 |
| 分区检测 | `detect_partitions.py` | 已有脚本，纯 Python 无依赖 |
| 从 PC 制作工厂镜像 | Bash 脚本 `build_installer_img.sh` | 烧录 Pi OS + 拷贝程序 + 配置 systemd |

---

## 4. UI 设计

### 4.1 设计语言

```
配色:
  主背景     #0D1117  (暗色护眼)
  卡片/面板  #161B22
  主色调     #3FB950  (松果绿)
  强调蓝     #58A6FF
  警告橙     #F0883E
  错误红     #F85149
  文字主色   #E6EDF3
  文字辅色   #8B949E

字体:
  标题: 思源黑体 Bold, 48px
  正文: 思源黑体 Regular, 24px
  数字: JetBrains Mono, 28px

组件:
  按钮主色:  圆角 12px, 绿色填充
  按钮危险:  圆角 12px, 红色边框
  进度条:    圆角 8px, 渐变绿色填充
  卡片:      圆角 16px, 带 1px #30363D 边框
  输入框:    圆角 12px, 底部绿色发光

动画:
  页面切换: 300ms 淡入淡出
  进度条:   平滑增长
  按钮:    200ms 渐变 hover
  加载:    松果旋转
  完成:    Checkmark 弹跳
```

### 4.2 六个页面

| 页面 | 功能 | 交互 |
|------|------|------|
| 启动页 | Logo + 初始化进度 | 3 秒自动跳转 |
| Wi-Fi | 扫描列表 + 密码输入 + 连接 | 遥控器选择，OK 键确认 |
| 下载 | 进度条 + 速度 + 剩余时间 + 暂停 | 支持断点续传 |
| 注入 | 解压 + mount + 拷贝APK + 删除竞品 | 自动执行，步骤指示器 |
| 刷写 | dd 进度 + 警告"请勿关机" | 红底警告，不可取消 |
| 完成 | 成功提示 + 操作指引 | 30 秒倒计时自动关机 |

---

## 5. 组件清单

### 5.1 刷机程序（运行在 ARM Linux 上）

```
/opt/pinecone-installer/
├── main.py                 # 入口 + 状态机
├── ui.py                   # UI 组件库 (~300行)
├── config.py               # 下载 URL 配置
├── inject.py               # APK 注入引擎 (~100行)
├── flash.py                # dd 刷写引擎 (~50行)
├── screens/
│   ├── welcome.py          # 启动页
│   ├── wifi.py             # Wi-Fi 连接
│   ├── download.py         # 下载进度
│   ├── inject_progress.py  # 注入进度
│   ├── flash_progress.py   # 刷写进度
│   └── done.py             # 完成页
├── assets/
│   ├── logo.png
│   ├── font-hans.ttf       # 思源黑体
│   └── font-mono.ttf       # JetBrains Mono
└── app-debug.apk           # 内置 PineCone Launcher APK
```

### 5.2 工厂镜像制作工具（运行在开发者 PC 上）

```
scripts/build_installer_img.sh    # 制作可批量烧录的工厂镜像
```

### 5.3 下载服务器

```
开发: python3 -m http.server 8080 (localhost)
生产: 阿里云 OSS (后续配置)
```

### 5.4 APK 注入引擎接口

```python
# inject.py
def inject(rom_zip_path: str, work_dir: str) -> str:
    """
    完整注入流程：
    1. 从 rom_zip_path 解压出 .img
    2. mount system 分区 (offset=136314880)
    3. 拷贝 APK 到 /system/app/PineConeLauncher/
    4. 删除竞品 launcher (Trebuchet, Launcher3, LeanbackLauncher 等)
    5. umount
    返回修改后的 .img 路径
    """
```

### 5.5 刷写引擎接口

```python
# flash.py
def flash_image(img_path: str, device="/dev/mmcblk0", callback=None):
    """
    用 dd 将 .img 写入 SD 卡块设备。
    callback(progress, written_bytes, total_bytes, speed_str)
    """
```

---

## 6. 镜像制作流程

```
开发者 PC 执行:
  1. 下载 Raspberry Pi OS Lite 官方镜像
  2. 烧录到 SD 卡
  3. 挂载 rootfs 分区
  4. 拷贝 /opt/pinecone-installer/ 全部文件
  5. 写 systemd 服务 (pinecone-installer.service)
  6. 配置自动登录 + 禁用蓝牙等无关服务
  7. umount
  8. dd 整张卡 → pinecone-installer-v1.0.img
  9. 批量烧录到出货 SD 卡
```

---

## 7. 依赖

**ARM Linux 运行时：**
- `python3` (系统自带)
- `python3-pygame` (apt 安装，~5MB)
- `python3-requests` (系统自带)
- `unzip` (apt 安装)
- `e2fsprogs` (apt 安装)
- `mount`, `dd` (系统自带)

**开发者 PC 端：**
- Raspberry Pi OS Lite 官方镜像
- Bash + dd + systemd 配置知识

---

## 8. 未解决的问题

- ROM zip 托管在阿里云 OSS 的具体 Bucket 名称和 CDN 配置待定
- 是否需要实现网络检测超时后自动跳过的"离线模式"（ROM 提前内置在 SD 卡中）
- 系统版本升级时，已出售的 SD 卡刷机程序如何感知新的 ROM URL（可通过固定 URL 重定向）
