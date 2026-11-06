#!/bin/bash
set -e

export CI=true DEBIAN_FRONTEND=noninteractive GIT_TERMINAL_PROMPT=0 GCM_INTERACTIVE=never HOMEBREW_NO_AUTO_UPDATE=1 GIT_EDITOR=: EDITOR=: VISUAL='' GIT_SEQUENCE_EDITOR=: GIT_MERGE_AUTOEDIT=no GIT_PAGER=cat PAGER=cat npm_config_yes=true PIP_NO_INPUT=1 YARN_ENABLE_IMMUTABLE_INSTALLS=false; export CI=true GIT_TERMINAL_PROMPT=0 GCM_INTERACTIVE=never GIT_PAGER=cat PAGER=cat
export JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64
export ANDROID_HOME=/opt/android-sdk
export GRADLE_USER_HOME=/root/.gradle

echo "Building APK v1.0.1..."
cd /workspace
/opt/gradle-8.13/bin/gradle :app:assembleDebug --no-daemon --console=plain 2>&1 | tail -20

echo "Verifying APK exists..."
ls -lh app/build/outputs/apk/debug/app-debug.apk

echo "Committing version bump..."
git add -A
git commit -m "chore: bump version to 1.0.1"

echo "Pushing to GitHub..."
git push origin main

echo "Creating release v1.0.1..."
gh release create v1.0.1 app/build/outputs/apk/debug/app-debug.apk --title "MockLoc v1.0.1" --notes "修复闪退 + 搜索列表显示 + 地图性能优化

修复内容：
- 修复 SessionSnapshot 缺少 @Serializable 导致的闪退
- 搜索结果现在显示完整列表
- 优化地图滚动性能，减少卡顿

安装说明：
1. 下载 app-debug.apk
2. 安装到 Android 设备（需 Android 10+）
3. 开启开发者选项 → 选择模拟位置信息应用 → 选择本应用
4. 打开应用选择位置并开始模拟"

echo "Done! Release available at: https://github.com/qingjiuban/mockloc/releases/tag/v1.0.1"
