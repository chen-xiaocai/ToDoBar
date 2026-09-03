# ToDoBar Sync

ToDoBar Sync 是 [ToDoBar](https://github.com/menubar-apps/ToDoBar) 的局域网同步分支，包含两个应用：

- macOS 菜单栏应用 **ToDoBar Sync**（bundle ID `com.chenxiaocai.ToDoBarSync`）
- Android 应用 **ToDoBar 收集箱**（package `com.chenxiaocai.todobar.inbox`，Android 10+）

手机可以离线记录事项。绑定时应用记住当前家庭 Wi‑Fi 的精确 SSID；存在待发送事项时，Android 只提交一个受该 SSID 约束的一次性任务。任务发现 Mac 后传输一次，无论成功或失败都不会重试，也没有周期任务、闹钟、前台服务、持续网络监听或长时间 mDNS 扫描。失败事项会留在本地，直到用户再次打开应用、手动同步或新增事项。

Mac 必须处于唤醒状态并运行 ToDoBar Sync。它通过 Bonjour (`_todobar-sync._tcp`) 被动监听，不依赖云服务。首次运行会只读导入原版 ToDoBar 的事项，原版应用及数据保持不变。

## 安全模型

配对二维码包含 256 位临时密钥。首次握手生成独立的 256 位会话密钥，分别存入 macOS Keychain 和 Android Keystore 包装的本地存储。协议使用 AES-256-GCM，元数据作为认证附加数据；每条手机事项有 UUID，Mac 持久化后才确认并以 UUID 去重。

日志记录 UUID、数量、长度、状态和完整错误栈。出于隐私与密钥安全要求，日志明确禁止记录待办正文、二维码内容、会话密钥及发布签名密钥。

## 构建

GitHub Actions 在 push/PR 时运行 Android 单元测试、Lint、APK 构建，以及 macOS 单元测试和通用架构构建。`v*` 标签还会生成带 SHA-256 校验文件的 GitHub Release。

Android release 构建需要 GitHub Secrets：`ANDROID_KEYSTORE_BASE64`、`ANDROID_KEYSTORE_PASSWORD`、`ANDROID_KEY_ALIAS`、`ANDROID_KEY_PASSWORD`。本仓库不包含任何真实待办、配对密钥或签名私钥。

## 续航验收

```shell
adb shell dumpsys jobscheduler com.chenxiaocai.todobar.inbox
adb shell dumpsys alarm | grep com.chenxiaocai.todobar.inbox
adb shell dumpsys batterystats com.chenxiaocai.todobar.inbox
adb shell dumpsys power | grep -i wake
```

原项目使用 MIT License，见 [LICENSE.md](LICENSE.md)。
