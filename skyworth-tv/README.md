# 山岚桌面 / SkyHome

参考 [归藏电视](https://github.com/op7418/guizang-tv) 的产品思路，以原生 Java 重新编写的创维电视简洁桌面。独立包名 `tv.skyhome`，可与原应用同时安装。

## 功能

- 常用应用采用靠左下的纯文字列表；焦点放大变白，不显示选中底色或应用图标。
- 子菜单与应用网格整体垂直居中，不显示重复标题；面包屑保留位置，父级在左边缘半隐。
- 全部应用自动发现并按中文名称排序，使用四列纯文字网格。
- 常用应用最多 9 项，支持添加、移除和前后排序。
- 五张离线照片与纯色背景；轮播间隔可选关闭、1、5、15、30 分钟。离开首页后暂停。
- 创维信号源选择、原桌面、投屏、系统设置快捷入口。
- 三级字号与屏幕边距；设置本地保存。
- 无网络权限、无账号、无广告，无第三方运行库或原生 ABI 依赖；Home 兼容使用需明确授权的可选辅助功能服务。

## 安装与操作

```sh
adb connect <电视IP>:5555
adb -s <电视IP>:5555 install --no-incremental -r skyhome-v1.2.1.apk
adb -s <电视IP>:5555 shell am start -n tv.skyhome/.MainActivity
```

电视若提示安装被取消，请在电视端允许这次安装。安装后在应用列表打开「山岚桌面」。

| 按键 | 操作 |
| --- | --- |
| 上下左右 | 移动焦点，应用较多时自动滚动 |
| 确认 | 打开应用或保存选项 |
| 长按确认 | 编辑应用常用状态与顺序 |
| 菜单 | 打开设置 |
| 返回 | 返回上一级并恢复焦点；首页显示离开选项 |
| 左 | 列表返回上一级；网格内移到左邻项，最左列返回上一级 |

设置页选中「字号」「屏幕边距」后按确认循环切换。1.2.1 的首页常用列表保持固定起点，子菜单无标题并整体居中，保留原来的常用应用与其他偏好。背景页选中照片立即保存；「静谧深色」暂停照片轮播。APK 已注册 HOME 入口；安装本身不修改系统配置。设置为默认桌面后，Home 返回首页并关闭当前面板。本机创维会绕过标准 Home，需要可选的 Home 辅助功能兼容服务，详见 [开机与 Home 配置](docs/HOME.md)。

## 构建

需要 JDK 17、Android SDK Platform 34、Build Tools 34.0.0 和 Python 3。

```sh
export JAVA_HOME=/path/to/jdk17
export ANDROID_HOME=/path/to/android-sdk
python3 scripts/build.py
```

该脚本直接调用 Android 官方 aapt2、javac、d8、zipalign、apksigner，生成并验证本地测试签名 APK。用于后续覆盖更新的签名密钥位于 `.local/test-signing.jks`，请保留此文件；它不进入源码版本管理。这个签名仅用于个人测试，正式发布应使用自行管理的发布密钥。

也提供标准 Gradle 工程，可用 Gradle 8.7 / AGP 8.5.2 导入 Android Studio；Gradle debug 签名与上述脚本不同，切换构建方式前需明确处理签名，不能覆盖安装后直接丢弃用户配置。

## 设备检查

文字界面说明与多级导航检查见 [文字界面与导航](docs/TEXT-UI.md)。

```sh
python3 scripts/device_check.py --adb /path/to/adb --serial <电视IP>:5555 --out /tmp/skyhome-qa
```

此检查会短暂操作电视界面、打开系统入口并截图，不修改应用偏好。Home 兼容已开启时，UIAutomator 可能抑制辅助功能服务，Home 检查应单独使用原生遥控事件。不要与遥控器操作同时进行。检查记录和截图放在指定输出目录，不提交包含个人应用信息的截图。

最低 Android 6 / API 23 是编译兼容下限，其他机型仍需单独实测。具体设备记录与边界见 [兼容性](docs/COMPATIBILITY.md)。

## 许可

参考项目采用 MIT 许可，保留其 [LICENSE](LICENSE)。新写代码亦按 MIT 许可提供。照片来自原项目已整理的 Unsplash 图库，适用照片自身许可，署名见 `app/src/main/assets/wallpapers/UNSPLASH-NOTICE.txt`。应用内「设置 → 关于与兼容性 → 许可与照片署名」也可查看。
