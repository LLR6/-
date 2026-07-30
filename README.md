# LR-沉浸式有声小说

一款面向 Android 手机和平板的本地优先有声小说播放器。项目不是界面空壳：TXT/ZIP 导入、编码检测、章节/句子/对白/情绪分析、Room 书架、系统 TTS、断点进度、前台播放服务、MediaSession 控制和睡眠定时均有真实实现。

> 项目不会模仿或冒充特定真人。默认方案是原创的“低沉悬疑”叙事参数；声音质量最终取决于设备安装的中文系统 TTS。沙哑、气声、混响和环境声需要后续接入合法授权的高质量 TTS/DSP 引擎。

## 当前可用功能

- Android 8.0（API 26）起，目标 SDK 36。
- 手机底部导航、平板导航轨；平板横屏播放器为章节、正文、参数三栏。
- 通过系统文件选择器导入 TXT 或包含多个 TXT 的 ZIP。
- UTF-8、UTF-16、GBK/GB2312/GB18030 自动检测；原始文件不被修改。
- 按行流式复制和解析，按 500 句批量写入 Room，避免把整本书一次性放进界面内存。
- “第一章 / 第1章 / 卷一 / 序章 / 楔子 / 番外 / Chapter 1”等章节标题。
- 旁白/对白、默认说话人、14 种情绪的规则分析；长按句子可人工修正。
- 书架、章节列表、正文当前句高亮、点击任一句开播、全文搜索、书签。
- 设备系统中文 TTS；每本书独立保存语速、音调、音量。
- Android 前台服务、MediaSessionCompat、通知栏/锁屏/蓝牙耳机播放控制、音频焦点和拔耳机暂停。
- 分钟定时、自定义分钟、当前章节结束停止，最后一分钟对后续句子逐步降音量。
- WorkManager 分句缓存基础设施；单句失败可独立重试。
- 本地备份包实现、音频缓存管理接口、云端 TTS 抽象接口与安全占位实现。

## 尚需合法服务或后续迭代的部分

- 系统 TTS 通常只能可靠控制语速、音调、音量，不能真正生成沙哑度、气声、磁性感、混响和多人广播剧级声线。
- `CloudTtsEngine` 已预留，但没有硬编码任何服务商或密钥。接入时必须使用自己的安全后端，并在上传文本前向用户明确提示。
- 背景音乐、环境音混音、整书批量导出和 EPUB 解析属于后续阶段；Media3 依赖已经纳入工程，缓存音频可平滑迁移到 ExoPlayer 播放队列。
- 自动人物识别是本地规则模型，长篇作品中建议在角色页和句子长按菜单中人工校正。

## 技术结构

```text
app/src/main/java/com/lr/immersiveaudiobook/
├── data/           Room、DataStore、导入与仓储
├── domain/         章节、对白、情绪与句子分析
├── tts/            统一 TTS 接口、系统实现、云端安全占位
├── playback/       前台服务、MediaSession、音频焦点、睡眠定时
├── cache/          音频缓存与 WorkManager
├── backup/         本地 ZIP 备份/恢复
└── ui/             Compose 手机/平板自适应界面
```

数据库表：`novels`、`chapters`、`sentences`、`characters`、`bookmarks`、`annotations`。外键使用级联删除，数据库启用 WAL；正式升级时应新增显式 Migration，不要使用破坏性迁移。

## Android Studio 编译

1. 安装 Android Studio 稳定版、JDK 17 和 Android SDK 36。
2. 用 Android Studio 打开仓库根目录。
3. 等待 Gradle 同步完成。
4. 选择 `app`，点击 Run；或在装有 Gradle 8.11.1 的终端执行：

   ```bash
   gradle :app:testDebugUnitTest
   gradle :app:assembleDebug
   ```

5. Debug APK 位于 `app/build/outputs/apk/debug/app-debug.apk`。

GitHub Actions 的 `Build Android APK` 会自动执行单元测试、构建 Debug APK 并上传 artifact；也可在 Actions 页面手动运行。

## Release 签名

不要把 keystore 或密码提交到 Git。创建自己的签名文件：

```bash
keytool -genkeypair -v -keystore lr-release.jks -alias lr-audiobook \
  -keyalg RSA -keysize 4096 -validity 10000
```

在本地 `keystore.properties` 或 GitHub Actions Secrets 中保存路径、别名和密码，再在 `app/build.gradle.kts` 添加 release signingConfig。首次发布后必须长期保管同一签名证书，否则无法覆盖安装升级。

## 使用附件中的小说

商业小说不会被放入 APK 或仓库。安装后在书架点“选择文件”，主动选择自己的 TXT 或 ZIP 即可；一个 ZIP 中的多个 TXT 会逐本导入。如果你拥有内置授权，可把授权文本放进自己的私有构建流程，并保留授权证明。

## 许可与合规

代码采用 Apache-2.0 许可证。隐私规则见 [PRIVACY.md](PRIVACY.md)，声音和文本版权提示见 [COPYRIGHT_NOTICE.md](COPYRIGHT_NOTICE.md)。
