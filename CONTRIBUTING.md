# Contributing

[简体中文](#贡献指南)

Issues and pull requests are welcome in English or Chinese. Use a [bug report or feature request](https://github.com/yangjiantao/closed-test-exchange/issues/new/choose) to describe the change. See the [code of conduct](CODE_OF_CONDUCT.md) for participation guidelines.

1. Fork the repository and create a branch for your change.
2. Build with JDK 17 and Android SDK Platform 36. Run `./gradlew :app:assembleDebug :app:lintDebug` and, with an Android device or emulator connected, `./gradlew :app:connectedDebugAndroidTest`.
3. For visible changes, check both English and Simplified Chinese, including the report PNG when applicable. Keep both README files and the corresponding string resources in sync.
4. Open a pull request. Explain what changed and how you verified it. If you attach screenshots or logs, use sample apps and remove personal information.

## 贡献指南

欢迎用中文或英文提交 Issue 或 PR。可先[报告问题或提交建议](https://github.com/yangjiantao/closed-test-exchange/issues/new/choose)。参与前请阅读[行为准则](CODE_OF_CONDUCT.md#行为准则)。

1. Fork 仓库并创建改动分支。
2. 使用 JDK 17 和 Android SDK Platform 36 构建。运行 `./gradlew :app:assembleDebug :app:lintDebug`。连接 Android 设备或启动模拟器后，再运行 `./gradlew :app:connectedDebugAndroidTest`。
3. 涉及用户界面的改动，请在简体中文和英文下分别验证。影响报告时也检查导出的 PNG。同步更新两份 README 和对应字符串资源。
4. 提交 PR，说明改动和验证方法。附截图或日志时请使用示例应用并移除个人信息。
