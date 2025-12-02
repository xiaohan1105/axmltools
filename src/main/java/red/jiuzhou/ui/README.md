# UI 组件包

本包包含 dbxmlTool 应用程序的所有用户界面组件。

## 主要类

### 核心应用程序
- `Dbxmltool.java` - 主应用程序入口点（JavaFX + Spring Boot）
- `MenuTabPaneExample.java` - 主选项卡界面
- `PaginatedTable.java` - 分页数据显示表格

### 功能模块
- `SQLConverterApp.java` - SQL 转换工具界面
- `SqlQryApp.java` - SQL 查询界面
- `EnumQuery.java` - 枚举查询界面
- `EditorStage.java` - JSON 编辑器窗口
- `DesignerInsightStage.java` - 面向策划的 XML 洞察面板，支持字段覆盖率、重复值与示例记录的快速审阅
  - 通过数据库行数对比、值分布排行、智能建议和一键打开源文件/目录等能力，为策划提供更直观的平衡分析依据

### 对话框组件
- `HelpDialogHelper.java` - 帮助系统
- `InitDialog.java` - 项目初始化对话框
- `DirectoryManagerDialog.java` - 目录管理界面

## 技术栈

- JavaFX 用于用户界面
- Spring Boot 集成
- 选项卡导航系统
- 模态对话框用于用户交互
- 支持分页的数据表格
