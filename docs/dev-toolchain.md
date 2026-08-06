# 开发工具链安装记录

本机原本没有任何 Android 开发环境，本次从零安装的命令行工具链（足以完成
`assembleRelease` 构建与 adb 安装，**无需安装 Android Studio IDE**；如需 IDE
体验可在 Android Studio 中打开工程，SDK 路径指向 `C:\Android\sdk` 即可）。

## 已安装组件

| 组件 | 版本 | 安装位置 |
|---|---|---|
| JDK（Temurin OpenJDK） | 17.0.20+8 | `C:\dev\jdk-17.0.20+8` |
| Gradle | 8.11.1 | `C:\dev\gradle-8.11.1` |
| Android SDK cmdline-tools | 11076708 | `C:\Android\cmdline-tools\latest` |
| Android SDK Platform | android-36（Android 16） | `C:\Android\sdk\platforms\android-36` |
| Android SDK Build-Tools | 36.0.0 | `C:\Android\sdk\build-tools\36.0.0` |
| Android SDK Platform-Tools | 最新 | `C:\Android\sdk\platform-tools`（含 adb） |

## 环境变量（已写入用户级，重启终端永久生效）

```
JAVA_HOME       = C:\dev\jdk-17.0.20+8
ANDROID_HOME    = C:\Android\sdk
ANDROID_SDK_ROOT= C:\Android\sdk
PATH            += %JAVA_HOME%\bin ; C:\dev\gradle-8.11.1\bin ; C:\Android\sdk\platform-tools
```

## 常用命令

```powershell
# 版本验证
java -version
gradle --version

# 构建 FeedLite
cd C:\Users\Administrator\Desktop\UI-All\android-rss
.\gradlew.bat assembleRelease
#   签名口令：环境变量 FEEDLITE_STORE_PASS / FEEDLITE_KEY_PASS，
#   或本地 keystore/keystore.properties（该目录 gitignored）。

# 安装到已连接设备
adb install -r app\build\outputs\apk\release\app-release.apk

# 跑 JVM 单测 + 动效 token 防漂移校验
.\gradlew.bat testDebugUnitTest verifyMotionTokens
```

## 安装过程中踩过的坑（备忘）

1. **sdkmanager 接受 license 失败**：交互式 `--licenses` 在 Windows 批处理下
   stdin 失效。解决：把 license 哈希直接写入
   `C:\Android\sdk\licenses\android-sdk-license`（多行），再运行安装命令。
2. **首次构建 SocketTimeout**：Gradle 下载依赖超时。解决：`gradle.properties`
   中加大 `systemProp.org.gradle.internal.http.socketTimeout` 至 180s。
3. **Kotlin DSL 顺序**：`signingConfigs` 块必须定义在引用它的 `buildTypes`
   之前，否则报 `SigningConfig with name 'release' not found`。
4. **PowerShell 5.1 下 `java -version` 的 stderr 被包装成 ErrorRecord**：
   属正常现象，版本信息实际正常输出。

## 可选：安装 Android Studio（IDE）

本次交付不需要；若需要图形化 IDE：
1. 下载 https://developer.android.com/studio（约 1.3GB）；
2. 安装时 SDK 路径填 `C:\Android\sdk`（避免重复下载）；
3. 用 Android Studio 打开 `android-rss` 工程即可。
