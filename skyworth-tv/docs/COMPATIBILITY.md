# 创维兼容性与验证边界

## 目标设备

2026-10-23 使用用户已连接的创维 55F1 / MT9638 实机检查。系统 Android 10 / API 29，32 位 ARM 用户空间，应用渲染尺寸 1920 × 1080，density 240；面板属性为 3840 × 2160。APK 无 NDK 动态库，不依赖特定 CPU ABI。

## 系统入口实现

| 能力 | 调用路径 |
| --- | --- |
| 信号源（1.2.4） | `com.skyworth.tv.sdk.provider` 查询实际端口；`METHOD_GOTO_SOURCE` 切换并回读当前输入 |
| 系统信号源菜单 | 创维 `coocaa.intent.action.SOURCE_DIALOG`，限定 `com.tianci.movieplatform` |
| 信号源回退 | 标准 `android.settings.TV_INPUT_SETTINGS`，不可用时提示用户 |
| 原桌面 | 创维 `coocaa.intent.action.HOME_MAIN`，限定 `com.tianci.movieplatform` |
| 系统设置 | 标准 `android.settings.SETTINGS`，失败再尝试创维设置应用 |
| 投屏 | 优先已安装的创维内置爱投屏，再尝试创维/当贝应用与创维工具入口 |

每次启动先解析 Activity，检查 exported、启用状态及调用权限；捕获无入口和权限异常，以文字提示回退。未复用小米的内部 HDMI 参数，也不写入系统厂商属性。

本机 `dumpsys tv_input` 的标准输入列表为空。1.2.4 改用系统菜单实际使用的创维 Provider 协议：查询 `system/source_list`、`system/current_source` 必须传非空 projection；列为 `sourceName`、`sourceIndex`、`displayName`。向 `method` 调用 `METHOD_GOTO_SOURCE`，Bundle 为 `name`、`index`、`flag=0`，返回 `ret` 后再轮询当前输入确认异步切换。本机 HDMI1 为 `HDMI / 0`、HDMI2 为 `HDMI / 1`；应用从返回列表获取映射，不硬编码端口。

Provider 在本机已导出，未声明组件调用权限；无额外权限的验证应用和山岚菜单均已完成 HDMI1/2 切换。其他型号需分别验证；读取失败或为空时显示重试与系统菜单入口。未解读或展示信号连接状态。

系统菜单初始化会产生原桌面窗口事件；打开时复用手动原桌面的 1.5 秒初始化放行，避免 Home 兼容服务将其立即拉回。全局按键过滤继续关闭。

注意：`com.coocaa.os.softsettings` 的 `SWITCH_SOURCE` / `SwitchSourceActivity` 在本机实际是播控牌照调试页，不能用作 HDMI 信号源入口。

## 性能与权限

- 使用 Android 原生 View；不依赖 WebView、Compose、联网服务或后台进程。
- 壁纸使用单线程异步解码、RGB565、采样尺寸限制；过期任务不会覆盖最新选择。
- 图片解码异常回退为深色背景。
- 每分钟更新时间；轮播只在首页前台运行，进入设置或切到其他应用即暂停。
- 对本机一次实测：进程总 PSS 为 30,685 KB，约 30 MB。这是运行快照，不代表长期峰值。
- Manifest 未请求网络、存储、悬浮窗或系统修改权限。可选 Home 辅助功能服务必须由用户明确授权；仅订阅两个创维桌面包的窗口组件信息，不具备获取屏幕内容的能力。
- APK 注册标准 HOME 入口，可设为默认桌面。本机厂商属性会被固件还原，最终使用用户已授权的 Home 兼容服务；没有禁用系统组件。

## 尚未验证

- 其他创维型号、酷开固件与其他品牌电视；API 23 仅为编译下限。
- 真实 HDMI 音视频播放、输入端口映射、手机投屏成功、长时间运行及待机唤醒。
- 其他机型的开机与 Home 目标；本机配置与验证记录见 [开机与 Home 配置](HOME.md)。
- 当前没有原项目的天气和 Codex 实验功能。

具体实机检查记录见 [验证记录](VALIDATION.md)。
