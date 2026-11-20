# 封测互测 · Closed Test Exchange

封测互测是一款 Android 封闭测试记录工具。它把要跟进的应用放进同一份清单，记录每个应用的启动日期，并生成可分享的测试报告图片。

## 功能

- **应用列表**：列出手机上可从启动器打开的应用，按首次安装时间从新到旧排序。点按应用卡片可加入或移出测试清单。
- **测试清单**：启动清单中的应用后记录当日打卡，同一天只计一次。清单显示累计天数和 14 天目标进度。
- **自动测试**：用户启动后依次打开清单中的应用，切换间隔约为 15 秒。悬浮窗显示进度和停止按钮，需要用户授予悬浮窗权限。
- **分享测试报告**：在测试清单底部生成报告，可在本机预览、保存或通过系统分享。

测试清单和打卡记录保存在本机。报告记录应用启动和打卡进度。

## 本地构建

需要 JDK 17、Android SDK Platform 36，以及 Android SDK 命令行工具或 Android Studio。将 `ANDROID_HOME` 指向 SDK 目录，或由 Android Studio 在项目根目录生成 `local.properties`。

```sh
./gradlew :app:assembleDebug
```

Windows 上使用 `gradlew.bat :app:assembleDebug`。构建产物位于 `app/build/outputs/apk/debug/app-debug.apk`。也可以直接用 Android Studio 打开项目并运行 `app`。

应用包名为 `com.shanmei.tools.testing`，支持 Android 7.0 及以上版本。

## 许可证

项目使用 [MIT License](LICENSE)。
