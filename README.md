# Markdown

一个基于 Android 原生技术栈开发的 Markdown 文件管理与阅读实验项目。

> 该项目目前仍处于早期开发阶段。现有代码已经包含 Android 工程结构、Markdown/HTML 文件识别与本地文件浏览等基础能力，但部分界面与业务流程尚未完整实现。

## 项目简介

Markdown 是一个 Android 应用原型，目标是帮助用户在移动设备上浏览和管理本地 Markdown 文件。

从当前代码可以看到，项目主要围绕以下能力展开：

- 浏览应用内部或外部存储目录
- 识别 `.md` 与 `.html` 文件
- 获取文件名称、路径、大小及最后修改时间等信息
- 创建 Markdown 文件和目录
- 删除文件或目录
- 复制文件
- 使用 WebView/HTML 资源进行 Markdown 内容展示的探索

## 技术栈

项目采用传统 Android View 开发方式，主要技术包括：

- **Kotlin**
- **Android Support Library 26.1.0**
- **Gradle**
- **RecyclerView**
- **CardView**
- **ConstraintLayout**
- **Glide 3.7.0**
- 本地文件系统 API
- 内置 `marked.js`，用于 Markdown 解析/渲染相关尝试

当前工程配置的 `minSdkVersion` 为 19，`targetSdkVersion` 为 26。

## 项目结构

```text
Markdown/
├── app/
│   ├── src/
│   │   ├── main/
│   │   │   ├── assets/          # Markdown 渲染相关静态资源
│   │   │   ├── java/
│   │   │   │   └── com/nzf/markdown/
│   │   │   │       ├── app/     # Application 初始化
│   │   │   │       ├── bean/    # 数据模型
│   │   │   │       ├── adapter/ # RecyclerView 基础组件
│   │   │   │       ├── utils/   # 文件及工具类
│   │   │   │       └── ui/      # 页面与界面逻辑
│   │   │   └── res/             # Android 资源文件
│   │   ├── test/                # 单元测试
│   │   └── androidTest/         # Android 仪器测试
│   └── build.gradle
├── build.gradle
└── settings.gradle
```

## 核心模块

### 文件管理

`FilesUtils` 是目前较为核心的基础类，负责：

- 获取内部和外部应用文件目录
- 创建 `.md` 文件
- 创建目录
- 删除文件和目录
- 复制文件
- 枚举目录内容
- 识别 Markdown、HTML 和目录类型

### 首页

`HomeActivity` 使用 `RecyclerView` 和 `LinearLayoutManager` 初始化文件列表界面，并通过 `FilesUtils` 获取外部存储目录中的 Markdown 相关文件。

目前首页的数据展示逻辑仍有进一步完善空间。

### Markdown 渲染

项目的 `assets` 目录中包含：

- `marked.js`
- HTML 渲染页面
- Markdown 示例内容

这些资源表明项目正在尝试通过 Web 技术完成 Markdown 内容的解析与展示。

## 开发环境

由于项目使用了较早版本的 Android Gradle 配置和 Support Library，建议优先使用能够兼容现有工程配置的 Android Studio 环境打开。

### 克隆项目

```bash
git clone https://github.com/peiyiming/Markdown.git
cd Markdown
```

然后使用 Android Studio 打开项目根目录，并等待 Gradle 依赖同步完成。

## 当前状态

- [x] Android 项目基础结构
- [x] Kotlin 基础代码
- [x] 本地文件目录访问
- [x] Markdown/HTML 文件识别
- [x] 文件创建与删除等基础能力
- [x] Markdown 渲染资源集成
- [ ] 完整的文件列表展示流程
- [ ] 完整的 Markdown 编辑功能
- [ ] 完整的文件预览体验
- [ ] 现代 Android 技术栈升级

## 后续改进建议

如果继续维护该项目，建议考虑：

1. 将 Support Library 升级至 **AndroidX**。
2. 升级 Gradle、Android Gradle Plugin 和 Kotlin 版本。
3. 使用 ViewBinding 替代已废弃的 Kotlin Android Extensions。
4. 修复和完善文件复制等边界条件处理。
5. 补充 Markdown 编辑、预览和文件列表的完整交互流程。
6. 增加单元测试与 UI 测试。
7. 明确项目采用的架构模式，例如 MVVM。

## License

当前仓库尚未明确提供许可证文件。如计划公开分发或接受第三方贡献，建议补充适当的开源许可证。
