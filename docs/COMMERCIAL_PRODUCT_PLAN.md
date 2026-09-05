# Markdown Workspace 商业化落地计划

## 产品定位

移动优先的 Markdown Workspace：让用户可以创建、编辑、管理、预览、导出和分享 Markdown 文档，并在后续版本提供跨设备同步与高级生产力能力。

## 当前 MVP

- 本地 Markdown 文档工作区
- 新建、打开、搜索、重命名、删除文档
- Markdown 编辑
- 自动保存
- Markdown 预览
- 基础快捷输入工具栏

## V1：可发布基础版

### P0

- 修复并验证完整构建流程
- AndroidX / Gradle 现代化迁移
- 文档创建、保存和恢复稳定性
- Undo / Redo
- 标题、列表、引用、代码块、链接、图片等快捷工具
- 深色模式
- 空状态和错误状态
- 文档导入与导出
- 分享 Markdown / HTML / PDF

### P1

- 文件夹与工作区
- 最近文档
- 收藏与标签
- 全文搜索
- 图片本地管理
- 文档模板
- 设置与数据备份

## V2：商业化基础

### Free

- 本地编辑与管理
- 基础预览
- 基础导出

### Pro

- 高级主题
- 高级导出
- 无限工作区
- 文档模板
- 云同步
- AI 写作与总结能力

商业化实现必须通过独立的 entitlement / feature-gate 层控制，避免支付 SDK 直接侵入编辑器和文档业务代码。

## V3：增长与跨端

- 账号系统
- 云同步
- Android 多设备恢复
- 分享链接
- Web / Desktop 客户端
- 团队协作

## 架构目标

```text
app
├── core
│   ├── model
│   ├── storage
│   ├── export
│   └── billing
├── feature
│   ├── workspace
│   ├── editor
│   ├── preview
│   ├── search
│   ├── template
│   └── settings
└── platform
    ├── sync
    └── analytics
```

## 交付原则

1. 每个功能必须形成可使用的用户闭环。
2. 每轮改造必须保持可构建，而不是只堆积代码。
3. 生产版本优先解决数据安全、恢复、导入导出和稳定性。
4. 商业化能力与核心编辑能力解耦。
5. 大版本重构必须通过独立分支和可审查 PR 推进。
