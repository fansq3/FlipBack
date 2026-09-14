# FlipBack — 相册翻翻乐（Android 平板 MVP）

FlipBack 把本地相册照片变成 Memory Match 卡片：选择 2–8 张照片，每张照片自动生成一对，洗牌后进行翻牌配对。

## 当前功能

- Android 平板横屏优先布局
- 从系统文件/相册选择 2–8 张照片
- 本地读取，不上传云端
- 自动成对、随机洗牌
- 翻牌动画
- 配对成功保持正面，失败后自动盖回
- 记录步数
- 完成反馈
- 记住上次选择的照片（相册提供者支持持久 URI 权限时）
- “重新洗牌”可直接再玩一局

## 隐私

应用没有联网权限，不包含账号、广告或分析 SDK。图片通过 Android Storage Access Framework 读取，只有用户主动选择的照片会被应用访问。

## 生成 APK

最省事的方法见 `BUILD_APK.md`。项目自带 `.github/workflows/build-apk.yml`，上传 GitHub 后可由 Actions 自动生成 debug APK。

当前构建配置：Android Gradle Plugin 9.4.0、Gradle 9.6、JDK 17、compileSdk/targetSdk 36、minSdk 23。

## APK 输出位置

`app/build/outputs/apk/debug/app-debug.apk`

## 下一阶段

第一版先验证“相册 → 卡片 → 配对”的核心体验。锁屏版本建议等游戏本体稳定后，再基于具体平板厂商/Android 版本选择 Live Wallpaper、锁屏小组件或系统级入口方案。
