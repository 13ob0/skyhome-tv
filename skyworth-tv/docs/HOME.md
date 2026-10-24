# 开机进入山岚与 Home 兼容

目标设备：创维 55F1 / Android 10。更新：2026-10-24。

## 开机进入

APK 注册标准 `MAIN + HOME + DEFAULT` 入口，已通过 ADB 设为默认桌面：

```sh
adb -s <电视IP>:5555 shell cmd package set-home-activity --user 0 tv.skyhome/.MainActivity
```

已完成一次完整重启验证：`sys.boot_completed=1`，默认 HOME 与前台 Activity 均为山岚。无需电脑常驻或开机广播服务。

## Home 兼容

这台固件会绕过 Android 的默认 HOME，并在重启或模式切换时还原 `persist.service.model.*` / `persist.service.homepage.*`。直接修改厂商属性曾当场有效，但不能作为可靠的最终方案。

最终加入可选 `tv.skyhome/.HomeKeyService`。用户已于 2026-10-24 明确授权启用。服务具备按键过滤能力；另外仅订阅 `com.skyworth.officehomepage` 与 `com.tianci.movieplatform` 的窗口状态变化，识别已知系统桌面的组件名称后返回山岚。它不获取节点、窗口内容或文本，不记录按键，不联网。系统能力位为 8（过滤按键），不含读取窗口内容的能力。

手动点击山岚的「原桌面」时，短暂放行原桌面初始化窗口；没有禁用、卸载或修改原桌面与 HDMI 组件。Home 返回山岚时会关闭应用内部面板。

## 启用和关闭

山岚设置中提供「Home 键兼容」的系统辅助功能入口。亦可使用明确操作的 ADB 工具：

```sh
python3 scripts/home_service.py --adb /path/to/adb --serial <电视IP>:5555 --enable
python3 scripts/home_service.py --adb /path/to/adb --serial <电视IP>:5555 --disable
```

启用涉及安全设置及持续的辅助功能授权，需设备所有者明确同意。工具读取并保留其他服务，只添加或移除山岚的服务项，不覆盖已有辅助功能列表。

## 恢复本机修改前的桌面

原始记录保存在本机 `.local/home-config-before.json`，不进入源码包。本机修改前的标准 HOME 是归藏电视，创维办公模式目标是原办公桌面。恢复前先关闭山岚 Home 兼容服务，然后按需恢复：

```sh
adb -s <电视IP>:5555 shell setprop persist.service.homepage.pkg com.tianci.movieplatform
adb -s <电视IP>:5555 shell setprop persist.service.homepage.cls com.coocaa.homepage.vast.HomePageActivity
adb -s <电视IP>:5555 shell setprop persist.service.model.name OfficeMode
adb -s <电视IP>:5555 shell setprop persist.service.model.pkg com.skyworth.officehomepage
adb -s <电视IP>:5555 shell setprop persist.service.model.cls com.skyworth.officehomepage.view.HomepageActivity
adb -s <电视IP>:5555 shell cmd package set-home-activity --user 0 io.github.op7418.guizangtv/.FrameHomeActivity
```

最后一条要求归藏仍已安装；其他设备应使用它们自己的原始配置。卸载山岚前先恢复一个实际存在的桌面目标。

## 验证状态

已验证开机进入山岚；Home 从系统设置、HDMI 返回山岚成功。2026-10-24 用户现场确认：停留在创维原桌面时按实体遥控器 Home，已返回山岚。ADB 模拟 Home 与实体遥控器在该场景表现不同；实际遥控器结果单独记录。最终重启验证通过：开机完成标记为 1，前台为山岚，Home 服务授权保留且自动绑定；重启后从系统设置按 Home 成功回到山岚。

Home 检查不要同时运行 UIAutomator：其自动化会话可能临时抑制辅助功能服务，造成与实际遥控器不同的结果。
