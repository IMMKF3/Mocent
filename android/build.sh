# 摸薪 Mocent Android 构建脚本（自带工具链，不依赖系统环境）
#
# 本机没有 Android Studio / Gradle / Android SDK，构建链全部放在 .tools/ 下（已 gitignore）。
# 首次使用若 .tools 缺失，见脚本末尾的“工具链准备”注释。
#
# 用法: bash android/build.sh          → app/build/outputs/apk/debug/app-debug.apk
#       bash android/build.sh release  → 追加 release（需 android/keystore.properties）

set -e
# 脚本在 android/ 下，先切到仓库根目录
cd "$(dirname "$0")/.."
ROOT="$(pwd)"
JDK_WIN='D:\!GitHubProjects\Mocent\.tools\jdk21\jdk-21.0.12.1+1'
JDK="$ROOT/.tools/jdk21/jdk-21.0.12.1+1"
GRADLE="$ROOT/.tools/gradle/gradle-8.7/bin/gradle.bat"
SDK_WIN='D:\!GitHubProjects\Mocent\.tools\android\sdk'

[ -x "$JDK/bin/java.exe" ]      || { echo "!! 缺 JDK21: $JDK"; exit 1; }
[ -f "$GRADLE" ]                || { echo "!! 缺 Gradle: $GRADLE"; exit 1; }
[ -d "$ROOT/.tools/android/sdk/platforms" ] || { echo "!! Android SDK 未安装 platform，先跑 sdkmanager"; exit 1; }

# Gradle 是 .bat，JAVA_HOME / ANDROID_HOME 必须是 Windows 风格路径，不能用 /d/... 的 MSYS 形式
export JAVA_HOME="$JDK_WIN"
export ANDROID_HOME="$SDK_WIN"
export ANDROID_SDK_ROOT="$SDK_WIN"
# Gradle 的缓存目录（含下载的 AGP / Kotlin 插件）也放在 .tools 下，避免污染用户目录
export GRADLE_USER_HOME="$ROOT/.tools/gradle-home"

cd android
TASK="assembleDebug"
[ "$1" = "release" ] && TASK="assembleRelease"
echo "== gradle $TASK（JDK21 + Gradle 8.7 + AGP 8.5.2）=="
"$GRADLE" "$TASK" --no-daemon -q

echo
echo "== 产物 =="
ls -1 app/build/outputs/apk/*/*.apk 2>/dev/null || echo "(未找到 apk)"

# ---------------------------------------------------------------------------
# 工具链准备（一次性，已完成的机器可忽略）
#
# 1) JDK 21（AGP 8.5.2 不支持 JDK 24，必须 ≤22）
#    node .tools/dl.js "https://api.adoptium.net/v3/binary/latest/21/ga/windows/x64/jdk/hotspot/normal/eclipse" ".tools/jdk21/jdk21.zip"
#    node .tools/unzip.js ".tools/jdk21/jdk21.zip" ".tools/jdk21"
#
# 2) Gradle 8.7（AGP 8.5.2 要求 Gradle 8.7+ 且 <9）
#    node .tools/dl.js "https://services.gradle.org/distributions/gradle-8.7-bin.zip" ".tools/gradle/gradle-8.7-bin.zip"
#    node .tools/unzip.js ".tools/gradle/gradle-8.7-bin.zip" ".tools/gradle"
#
# 3) Android SDK cmdline-tools + platform 34
#    node .tools/dl.js "https://dl.google.com/android/repository/commandlinetools-win-11076708_latest.zip" ".tools/android/cmdline-tools.zip"
#    node .tools/unzip.js ".tools/android/cmdline-tools.zip" ".tools/android/tmp"
#    # 补一层 latest（sdkmanager 要求 cmdline-tools/<ver>/bin）
#    # 再接受许可并装包：
#    #   sdkmanager --licenses
#    #   sdkmanager "platform-tools" "platforms;android-34" "build-tools;34.0.0"
# ---------------------------------------------------------------------------
