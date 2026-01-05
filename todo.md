# BLE 广播小说翻页器（小米手环7）- TODO

## 目标
在手机/平板端后台扫描手环通过 BLE Advertising 发出的广播数据，不建立连接；解析指令后通过无障碍服务模拟点击翻页。

## 通信协议（约定）
- **Service UUID（128-bit，随机生成）**：`c76393eb-1994-4b4d-b1e2-1d7bde0571fa`
  - 说明：手环端必须使用同一个 UUID 才能被过滤命中；同时可降低被其他设备干扰（非加密）。
- 指令：
  - `0x01`：上一页
  - `0x02`：下一页
- 数据位置：优先解析 **Service Data**（key 为该 UUID），若为空则回退到 **Manufacturer Data**（取第一个 entry）。

## 功能清单
- [x] Kotlin/Gradle 配置（kotlin-android + kotlin-gradle-plugin）
- [x] AndroidManifest：权限声明、前台服务、无障碍服务声明
- [x] 无障碍配置：`res/xml/accessibility_config.xml`
- [x] 前台服务 PageTurnerService：
  - [x] 监听 `ACTION_SCREEN_ON/OFF`
  - [x] 亮屏 `SCAN_MODE_LOW_LATENCY` 开启扫描
  - [x] 灭屏 `stopScan` 并释放资源（省电）
  - [x] 过滤：仅扫描包含 Service UUID 的广播
  - [x] 防抖：400ms 内忽略重复信号
  - [x] 解析 `0x01/0x02` 并通过 Broadcast 分发给无障碍服务
- [x] 无障碍服务 ClickAccessibilityService：
  - [x] 接收 Broadcast
  - [x] `dispatchGesture` 进行 **Tap**（50ms）
  - [x] 上一页：点击屏幕 5% 宽度处、50% 高度
  - [x] 下一页：点击屏幕 95% 宽度处、50% 高度
- [x] MainActivity：
  - [x] 动态申请权限（按系统版本）
  - [x] 引导开启无障碍
  - [x] 启动/停止前台服务
