# Privacy notice

[简体中文](#隐私说明)

- The app queries launchable Android apps and displays their names, icons, and install order. It uses each selected app’s package name to keep its checklist membership, latest launch time, and total check-in days in a local database.
- The floating window requires Android’s **Display over other apps** permission. The app asks for it when you choose **Open in order**. You can revoke it in Android Settings.
- Before opening a test app or starting a batch, the app makes an HTTPS request to the public Google Play storefront to check connectivity. The request carries no checklist or check-in data.
- The app has no account, analytics, or upload service. A PNG report includes the names, icons, and installed versions of the apps in your checklist, their check-in status and totals, and the report date. Reports are held in the app cache for preview and sharing. If you save or share a report, the destination app or recipient receives a copy.
- Removing an app from the checklist keeps its counters in case you add it again. To erase the local checklist and counters, clear this app’s storage in Android Settings or uninstall it. Delete any exported files separately.

For questions about data handling, use the [project’s GitHub issue tracker](https://github.com/yangjiantao/closed-test-exchange/issues/new/choose). For a privacy or security issue that involves private information, use the [private reporting instructions](SECURITY.md).

## 隐私说明

- 应用查询设备上可从启动器打开的应用，显示名称、图标和安装先后顺序。加入清单后，应用通过包名在本地数据库中保存清单状态、最近一次启动时间和累计打卡天数。
- 使用**依次打开**时，应用会请求 Android 的**显示在其他应用上层**权限，用于显示浮窗。可以在系统设置中撤销权限。
- 在打开一个测试应用或启动一批测试前，应用会向 Google Play 的公开商店页面发起 HTTPS 请求，检查连通性。请求不携带测试清单或打卡数据。
- 应用没有账号、统计分析或上传服务。PNG 图片包含清单中的应用名称、图标、安装版本、打卡状态、累计天数和报告日期。预览和分享使用缓存中的图片。保存或分享后，所选位置、应用或收件人会收到一份副本。
- 移出清单会保留对应应用的记录，重新加入时可继续累计。要清除本机清单和记录，可在 Android 设置中清除应用存储或卸载应用。已经导出的报告需单独删除。

有关数据处理的问题可到 [GitHub Issues](https://github.com/yangjiantao/closed-test-exchange/issues/new/choose) 反馈。涉及私人信息的隐私或安全问题请按[私下报告指引](SECURITY.md#安全问题报告)处理。
