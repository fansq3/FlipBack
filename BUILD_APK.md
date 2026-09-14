# 不装 Android Studio：用 GitHub 自动生成 APK

1. 在 GitHub 新建一个空仓库（Private / Public 都可以）。
2. 把本 ZIP 解压后的 **全部文件** 上传到仓库根目录，包括隐藏目录 `.github`。
3. 打开仓库的 **Actions** 页面。
4. 点左侧 **Build FlipBack APK** → **Run workflow**。
5. 构建成功后，在该次运行页面底部的 **Artifacts** 下载 `FlipBack-debug-apk`。
6. 解压得到 `app-debug.apk`，传到安卓平板安装即可。

> 如果系统提示“禁止安装未知应用”，请只给你用于打开 APK 的文件管理器/浏览器临时开启该权限，装完可关闭。

## 本地构建

如果电脑已有新版 Android Studio，也可以直接打开项目根目录，等待 Gradle Sync 完成，然后使用：

`Build > Build APK(s)`

APK 默认输出：

`app/build/outputs/apk/debug/app-debug.apk`
