# AutoFlow - 自动化任务执行器

## 快速获取APK

1. 将本项目所有文件上传到 GitHub
2. 手动创建 workflow 文件（步骤如下）
3. 等待自动构建完成（约3-5分钟）
4. 在 Actions 中下载 app-debug

## 上传步骤

### 第一步：上传文件

1. 访问 https://github.com
2. 创建新仓库 (New repository)，仓库名填写 AutoFlow
3. 创建好后，点击 "uploading an existing file"
4. 将下面所有文件/文件夹拖入上传：
   - app/
   - gradle/
   - github-workflows/    （重点！这个文件夹前面没有点）
   - build.gradle
   - settings.gradle
   - gradlew
   - gradlew.bat
   - gradle.properties
   - .gitignore
   - README.md
5. 点击 Commit changes

### 第二步：创建自动构建文件

上传完文件后，继续以下操作：

1. 在仓库页面，点击 "Add file" 按钮
2. 选择 "Create new file"
3. 在文件名处填写：`.github/workflows/android-build.yml`
4. 复制下面的代码粘贴进去：

```yaml
name: Build Android APK

on:
  push:
    branches: [ main ]
  workflow_dispatch:

jobs:
  build:
    name: Build Debug APK
    runs-on: ubuntu-latest

    steps:
      - name: Checkout repository
        uses: actions/checkout@v4

      - name: Set up JDK 17
        uses: actions/setup-java@v4
        with:
          java-version: '17'
          distribution: 'temurin'

      - name: Setup Android SDK
        uses: android-actions/setup-android@v2

      - name: Build Debug APK
        run: ./gradlew assembleDebug

      - name: Upload APK
        uses: actions/upload-artifact@v4
        with:
          name: app-debug
          path: app/build/outputs/apk/debug/app-debug.apk
```

5. 滑到底部，点击 "Commit changes"

### 第三步：等待并下载

1. 点击顶部的 "Actions" 标签
2. 等待构建完成（显示绿色勾）
3. 点击那个构建记录
4. 页面底部有 "app-debug"，点击下载 APK
