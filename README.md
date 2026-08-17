# 水课雷达（Android）

一个面向课堂个人学习记录的本地优先 Android 应用。录音、语音活动检测、流式转写、终稿校正和提问检测均在设备内完成；应用不声明网络权限。

## 已实现

- Jetpack Compose 听课首页、首次课堂录音使用声明和运行时权限申请；
- Android 10+ 麦克风前台服务，16kHz 单声道 `AudioRecord`、息屏 `PARTIAL_WAKE_LOCK` 与计时常驻通知；
- sherpa-onnx v1.13.4 Android JNI 运行时，仅打包 arm64-v8a；按产品约束不兼容 32 位与 x86 设备，以避免携带重复的原生识别库；
- 双引擎离线识别：Zipformer CTC int8 输出流式文本，Silero VAD 分段后由 Paraformer int8 输出终稿；
- 全本地的触发词、疑问句、30 句上下文回溯、15 秒去抖检测器；
- Room 本地记录库：课次、转写、提问三张表，支持历史详情与提问时间轴；
- 系统文件选择器导出 TXT / Markdown，无需存储权限；
- 内置与自定义触发词管理、提醒震动/提示音、可选私有 WAV 录音保存；
- 离线桌面小组件：已授予麦克风权限后可一键开始听课；
- 主页、时间轴、历史详情、触发词、设置五个页面；
- 规则检测与 WAV 文件写入的 JVM 单元测试；
- 不声明 `android.permission.INTERNET`，不调用系统在线语音识别 API。

## 运行

需要 Android SDK Platform 36 和 JDK 17：

```powershell
$env:JAVA_HOME = "C:\path\to\jdk17"
.\gradlew.bat test
.\gradlew.bat assembleDebug
```

首次使用时同意用途说明、授予麦克风权限后，点击“开始聆听”。系统会显示“水课雷达正在聆听”的前台服务通知：流式文字会直接显示；一段语音结束后，Paraformer 终稿会进入记录和提问检测。

## 模型与发布

模型与原生库已放入 `app/src/main/assets/` 和 `app/src/main/jniLibs/`，便于离线安装使用。实际公开发布前，必须按 [第三方声明](docs/THIRD_PARTY_NOTICES.md) 完成每个模型权重的再分发许可核验。

所有课堂记录默认仅保存转写与提问事件。原始音频默认关闭；如在设置中开启，会写入应用私有目录，并会随对应记录删除或卸载应用一并清除。

录音期间，安卓常驻通知会实时显示计时和最新转写；展开通知可查看最近定稿内容和提问回溯，锁屏则通过高优先级提问通知提醒。

主要模型校验值（SHA-256）：

- Zipformer CTC：`68C9C943840F7D9CF3E8A4970BA50F404FEB5277F611FA82B7E72267786FA84A`
- Paraformer：`3EF6C19369B912F7CAF3CEF8E545C5CCD1A33D9D7EC792A46668DC41C4B229EC`
- Silero VAD：`9E2449E1087496D8D4CABA907F23E0BD3F78D91FA552479BB9C23AC09CBB1FD6`
