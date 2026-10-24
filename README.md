# 山岚桌面 / SkyHome

一个为创维电视编写的轻量 Android 桌面：大字常用应用、离线壁纸，以及电视遥控器操作。

以 [归藏电视](https://github.com/op7418/guizang-tv) 为参考重新编写，源码位于 [`skyworth-tv/`](skyworth-tv/)。当前实测设备为 **创维 55F1 / Android 10**，其他型号与固件需要分别验证。

## 功能

- 简约文字列表，焦点放大变白；常用应用添加、移除和排序。
- 子菜单与应用网格无标题、整体垂直居中，父级在左边缘半隐；面包屑保留当前位置。
- 五张离线照片、纯色背景与定时轮播。
- 创维 HDMI 信号源菜单、投屏、系统设置和原桌面入口。
- 字号、屏幕边距与遥控器焦点适配。
- 标准默认桌面入口；可选的 Home 辅助功能兼容服务，不获取屏幕内容。

## 安装

当前版本为 1.2.1，采用无标题、子菜单垂直居中的文字界面。

从 [v1.2.1 公开测试版](https://github.com/13ob0/skyhome-tv/releases/tag/v1.2.1) 下载 APK，通过电视本地安装或 ADB 安装：

```sh
adb connect <电视IP>:5555
adb -s <电视IP>:5555 install --no-incremental -r skyhome-v1.2.1.apk
adb -s <电视IP>:5555 shell am start -n tv.skyhome/.MainActivity
```

建议先作为普通应用试用，确认遥控器、应用和信号源可用，再按文档配置默认桌面与 Home。基础桌面无需账号、网络权限或辅助功能；**Home 兼容服务是可选功能，需要用户明确授权**。

信号源目前使用系统选择菜单，已验证 HDMI1 切换；未完成真实外接设备音视频、全部创维机型与长期待机验证。

## 构建与文档

需要 JDK 17、Android SDK Platform 34、Build Tools 34.0.0 和 Python 3：

```sh
cd skyworth-tv
export JAVA_HOME=/path/to/jdk17
export ANDROID_HOME=/path/to/android-sdk
python3 scripts/build.py
```

脚本生成本地测试签名 APK；首次构建会在 `.local/` 创建签名密钥，后续覆盖更新应保留它。正式分发应自行管理签名。

- [使用与构建说明](skyworth-tv/README.md)
- [版本记录](skyworth-tv/CHANGELOG.md)
- [开机与 Home 配置](skyworth-tv/docs/HOME.md)
- [实机验证记录](skyworth-tv/docs/VALIDATION.md)

本仓库管理源码、资源、构建脚本和文档。APK、源码压缩包、本机 SDK、设备配置备份和签名密钥不进入 Git。

## 许可与来源

代码按 [MIT License](LICENSE) 开源，保留参考项目的版权与许可说明。照片适用 [Unsplash License](https://unsplash.com/license)，不属于代码 MIT 许可；完整摄影师署名与来源见 [照片说明](skyworth-tv/app/src/main/assets/wallpapers/UNSPLASH-NOTICE.txt) 和 [NOTICE](NOTICE.md)。
