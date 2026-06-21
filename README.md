# Auto Disable IPv6（自动关闭 IPv6）

[![Android CI](https://github.com/AkagiYui/AutoDisableIPv6/actions/workflows/android.yml/badge.svg)](https://github.com/AkagiYui/AutoDisableIPv6/actions/workflows/android.yml)

一个需要 **Root** 的 Android 工具：持续监听 Wi-Fi 连接，当连接到你配置的目标 SSID（按正则匹配）时，自动执行命令关闭 IPv6。

由于很多设备每次重新连接 Wi-Fi 都会重置这些内核设置，本应用会在**每次连接**时重新判断并执行，确保 IPv6 始终处于关闭状态。

## 执行的命令

连接到目标网络时，通过 `su -c` 以 Root 身份执行：

```shell
echo 0 | tee /proc/sys/net/ipv6/conf/<wlan>/accept_ra
echo 1 | tee /proc/sys/net/ipv6/conf/all/disable_ipv6
```

其中 `<wlan>` 为**运行时动态探测**的当前 Wi-Fi 网卡名（取自系统 `LinkProperties`，探测失败时回退为 `wlan0`）。

## 功能特性

- **总开关**：一键启用 / 停用整个功能。
- **目标 SSID 管理**：手动添加，支持**正则匹配**；可编辑、删除，也可在不删除的情况下单独停用某条规则。
- **持续后台监听**：以前台服务常驻，应用关闭后依然工作。以下场景都会触发判断与执行：
  - 开机后已自动连上目标 Wi-Fi；
  - 从非目标 SSID 切换到目标 SSID；
  - 从移动网络切回目标 Wi-Fi；
  - 应用未打开时发生的上述连接变化。
- **仅在解锁后执行**（可选）：若连接时处于锁屏，则等用户解锁后补执行一次。
- **失败重试**（可选）：首次失败后延迟 5 秒重试，最多 3 次；一旦网络切走或 SSID 不再匹配，立即取消后续重试。
- **开机自启** / **启动应用时恢复监听**（可选）。
- **测试 Root**：手动申请超级用户授权并验证 `su` 是否可用。
- **关闭电池优化**：引导将本应用加入电池优化白名单，避免被系统杀死。
- **日志**：记录触发、执行、成功 / 失败，**持久化保存**（重启后仍可查看），允许明文显示 SSID 与命令输出，可手动清除。
- **界面**：Material 3 + Jetpack Compose，内置中英文。

## 运行要求

- 已 **Root** 的设备（如 Magisk，需提供 `su`）。
- Android 7.0（API 24）及以上。

## 权限说明

| 权限 | 用途 |
| --- | --- |
| 定位 / 后台定位 | 读取当前已连接 Wi-Fi 的 SSID（Android 系统限制：不授予则无法获取 SSID） |
| 通知 | 显示前台服务的常驻通知（Android 13+） |
| 前台服务（specialUse） | 常驻监听网络变化 |
| 开机自启 | 重启后自动恢复监听 |
| 忽略电池优化 | 保证监听长期运行 |

> Root 授权不会在启动时索取，只有当你点击「测试 Root」或首次实际执行命令时才会弹出授权请求。

## 安装

- **下载预构建包**：在 [GitHub Actions](https://github.com/AkagiYui/AutoDisableIPv6/actions/workflows/android.yml) 中选择一次成功的运行，下载名为 `app-release-unsigned` 的构建产物。该 APK **未签名**，安装时可能需要允许「安装未知应用」，或自行用调试 / 自有密钥签名后安装。
- **或自行构建**（见下文）。

## 使用步骤

1. 安装并打开应用。
2. 在「设置」页授予 **定位 / 后台定位 / 通知** 权限。
3. 点击「测试 Root」，在弹出的超级用户请求中授予权限。
4. （建议）点击「关闭电池优化」。
5. 在「目标 SSID」中添加规则（正则），例如：
   - `MyHomeWiFi` —— 精确匹配名为 `MyHomeWiFi` 的网络；
   - `Home.*` —— 匹配所有以 `Home` 开头的网络；
   - `(Office|Lab)-5G` —— 匹配 `Office-5G` 或 `Lab-5G`。
6. 打开顶部的**总开关**。
7. 连接到目标 Wi-Fi，到「日志」页查看 触发 → 执行 → 成功 / 失败 的记录。

> 匹配采用**完整匹配**语义（`Matcher.matches()`），正则需要描述整个 SSID。

## 构建

需要 JDK 21 与 Android SDK（compileSdk 36.1）。

```shell
# 构建未签名 release APK
./gradlew assembleRelease
# 产物：app/build/outputs/apk/release/app-release-unsigned.apk

# 运行单元测试
./gradlew testDebugUnitTest
```

## 技术栈

- Kotlin、Jetpack Compose、Material 3
- 前台服务 + `ConnectivityManager` 网络回调
- DataStore（Preferences）+ `org.json` 持久化设置、SSID 规则与日志
- Kotlin 协程

## 持续集成

`.github/workflows/android.yml`：每次 `push` 与 `pull_request` 都会运行单元测试并构建未签名 APK，作为构建产物上传。

## 免责声明

本应用会以 Root 权限向 `/proc/sys/net/ipv6/...` 写入内核参数，请确认你了解相关操作的影响后再使用。日志会以明文形式记录 SSID 与命令输出。
