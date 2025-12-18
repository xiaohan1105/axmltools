# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Git 工作流规范

### 提交规范（重要）

**每次修改完代码后必须立即提交**，不要积累多个修改后再一次性提交。

```bash
# 每完成一个功能点或修复，立即提交
git add .
git commit -m "feat: 简短描述修改内容"

# 推送到 GitHub
git push axmltools clean-main:main
```

### 提交消息格式

| 前缀 | 用途 |
|------|------|
| `feat:` | 新功能 |
| `fix:` | Bug修复 |
| `refactor:` | 代码重构 |
| `docs:` | 文档更新 |
| `style:` | 代码格式调整 |
| `chore:` | 构建/配置变更 |

### GitHub 仓库

- **远程仓库**: https://github.com/xiaohan1105/axmltools
- **远程名称**: `axmltools`
- **工作分支**: `clean-main`（无敏感历史记录）
- **推送命令**: `git push axmltools clean-main:main`

### 敏感信息处理

- **禁止**将 API Key、密码等敏感信息提交到代码中
- 使用环境变量占位符：`${ENV_VAR:default-value}`
- 示例：`apikey: ${AI_QWEN_APIKEY:your-api-key}`

---

## 项目概述

dbxmlTool 是一个游戏配置数据管理工具，用于 MySQL 数据库与 XML 文件之间的双向转换。基于 JavaFX 构建桌面 GUI，集成多个 AI 服务用于数据智能处理和翻译。

**主要功能**：
- 数据库 ↔ XML 双向转换
- Aion游戏机制可视化浏览器（27个机制分类）
- AI驱动的数据分析和洞察
- 主题系统和批量转换

## 构建和运行命令

```bash
# 编译项目
mvnd clean compile

# 运行应用（JavaFX 应用）
mvnd exec:java

# 打包（包含依赖的 fat jar）
mvnd clean package

# 运行测试
mvnd test

# 运行单个测试类
mvnd test -Dtest=YourTestClassName

# 运行单个测试方法
mvnd test -Dtest=YourTestClassName#testMethodName
```

主类入口：`red.jiuzhou.ui.Dbxmltool`

## 技术栈

| 层级 | 技术 |
|-----|------|
| 应用框架 | Spring Boot 2.7.18 |
| GUI框架 | JavaFX (JFoenix 8.0.10, ControlsFX 8.40.12) |
| 数据库 | MySQL 8.0 + Spring JDBC |
| XML处理 | Dom4j 2.1.3 |
| 配置管理 | YAML (SnakeYAML, Jackson) |
| JSON处理 | Fastjson 1.2.83 |
| 日志 | SLF4j + Logback |
| 工具库 | Hutool 5.3.9 |
| AI服务 | DashScope SDK 2.21.0, 火山引擎 SDK |
| 翻译 | 阿里云翻译API |
| 构建工具 | Maven (推荐 mvnd) |
| Java版本 | Java 8 (1.8) |

## 核心架构

### 包结构概览

```
red.jiuzhou
├── ai/               # AI模型集成（4个服务商）
├── analysis/         # 数据分析引擎
│   ├── enhanced/     # AI增强分析
│   └── aion/         # Aion游戏专用分析
│       ├── AionMechanismCategory.java   # 27个机制分类枚举
│       ├── AionMechanismDetector.java   # 机制检测器
│       ├── XmlFieldParser.java          # XML字段解析器
│       ├── DetectionResult.java         # 检测结果
│       └── AionMechanismView.java       # 视图模型
├── api/              # REST API接口
│   └── common/       # 通用模型
├── dbxml/            # 数据库与XML双向转换（核心）
├── relationship/     # 关系分析
├── tabmapping/       # 表映射管理
├── theme/            # 主题管理系统
│   └── rules/        # 转换规则
├── ui/               # JavaFX用户界面
│   ├── features/     # 特性注册系统
│   └── mapping/      # 表映射UI
├── util/             # 工具类库
└── xmltosql/         # XML到SQL/DDL转换
```

### Aion机制浏览器 (`red.jiuzhou.analysis.aion`)

专为Aion游戏设计的机制分类和可视化工具。

**核心类**：
- `AionMechanismCategory.java` - 27个机制分类枚举（定义正则匹配模式、优先级、颜色和图标）
- `AionMechanismDetector.java` - 机制检测器（包含文件夹级别映射 `folderMappings`）
- `XmlFieldParser.java` - XML字段解析器
- `IdNameResolver.java` - ID到NAME转换缓存服务
- `MechanismRelationshipService.java` - 机制间依赖关系分析

**三层级导航**：机制层（27个系统卡片）→ 文件层 → 字段层

**字段引用检测**：自动识别 `item_id`、`npc_id`、`skill_id`、`quest_id` 等字段的跨表引用关系

### 数据转换层 (`red.jiuzhou.dbxml`)

核心模块，处理数据库与XML的双向转换。

| 类名 | 职责 |
|-----|------|
| `DbToXmlGenerator` | 数据库导出为XML，多线程分页处理 |
| `XmlToDbGenerator` | XML导入到数据库，支持事务和批量操作 |
| `WorldDbToXmlGenerator` | World类型数据的特殊导出处理 |
| `WorldXmlToDbGenerator` | World类型数据的特殊导入处理 |
| `TableConf` / `TabConfLoad` | 表配置定义和加载 |
| `TableForestBuilder` | 构建表的父子层级关系树 |

### UI层 (`red.jiuzhou.ui`)

基于JavaFX的桌面应用界面。

| 类名 | 职责 |
|-----|------|
| `Dbxmltool` | 主应用入口（Spring Boot + JavaFX） |
| `MenuTabPaneExample` | 左侧目录树和Tab页管理 |
| `AionMechanismExplorerStage` | Aion机制浏览器窗口 |
| `DesignerInsightStage` | 设计洞察窗口 |
| `ThemeStudioStage` | 主题工作室窗口 |

**工具栏按钮**：
- `🎮 机制浏览器` - 打开Aion机制浏览器
- `📊 设计洞察` - 打开设计洞察分析

**特性系统 (`ui.features`)**：
- `FeatureRegistry.defaultRegistry()` - 特性注册中心，注册所有可启动的功能模块
- `FeatureDescriptor` - 特性描述符（id、名称、描述、分类、启动器）
- `FeatureCategory` - 特性分类枚举
- `StageFeatureLauncher` - Stage窗口启动器实现

### AI服务层 (`red.jiuzhou.ai`)

集成多个AI服务提供商。

| 类名 | 职责 |
|-----|------|
| `AiModelFactory` | AI模型工厂（工厂模式） |
| `TongYiClient` | 通义千问客户端 |
| `DoubaoClient` | 豆包AI客户端 |
| `KimiClient` | Kimi AI客户端 |
| `DeepSeekClient` | DeepSeek AI客户端 |

## 配置文件

### application.yml 关键配置

```yaml
server:
  port: 8081

spring:
  datasource:
    url: jdbc:mysql://localhost:3306/xmldb_suiyue?...
    username: root
    password: "****"

# Aion XML路径配置
aion:
  xmlPath: D:\AionReal58\AionMap\XML
  localizedPath: D:\AionReal58\AionMap\XML\China

# AI服务配置（使用环境变量）
ai:
  qwen:
    apikey: ${AI_QWEN_APIKEY:your-api-key}
    model: qwen-plus
  doubao:
    apikey: ${AI_DOUBAO_APIKEY:your-api-key}
    model: doubao-seed-1-6-250615
  kimi:
    apikey: ${AI_KIMI_APIKEY:your-api-key}
    model: Moonshot-Kimi-K2-Instruct
  deepseek:
    apikey: ${AI_DEEPSEEK_APIKEY:your-api-key}
    model: deepseek-r1
```

## 数据流

```
XML文件 ←→ XmlToDbGenerator/DbToXmlGenerator ←→ MySQL数据库
                     ↓
           Analysis Engine（统计分析 + AI增强）
                     ↓
           Aion Mechanism Explorer（机制可视化）
                     ↓
           Designer Insights（策划洞察）
```

## 编码规范

- 所有代码文件使用 **UTF-8** 编码
- 使用中文注释和日志
- 遵循 Spring Boot 和 JavaFX 最佳实践
- 敏感配置使用环境变量注入
- **Java 8兼容**：不使用Java 9+特性（如String.repeat()）

## 常见开发场景

### 添加新的游戏机制分类

1. 在 `AionMechanismCategory.java` 枚举中添加新分类
2. 配置正则匹配模式、优先级、颜色和图标
3. 如需文件夹级别匹配，在 `AionMechanismDetector.java` 的 `folderMappings` 中添加

### 添加新的特性模块

1. 在 `FeatureRegistry.defaultRegistry()` 中注册新特性
2. 创建对应的 Stage 类
3. 实现 `FeatureLauncher` 接口

### 添加新的AI模型

1. 在 `red.jiuzhou.ai` 包下创建新的 Client 类
2. 在 `AiModelFactory.getClient()` 中添加创建逻辑
3. 在 `application.yml` 中添加配置项（使用环境变量）

## 关键配置文件

| 文件 | 用途 |
|------|------|
| `src/main/resources/application.yml` | 主配置文件（数据库连接、AI服务、路径配置） |
| `src/main/resources/application.yml.example` | 配置模板（无敏感信息） |
| `src/main/resources/CONF/` | 表映射配置目录 |
| `src/main/resources/LeftMenu.json` | 左侧目录树结构配置 |

## 文档

- `docs/MECHANISM_EXPLORER_GUIDE.md` - 机制浏览器使用指南
