# CLAUDE.md

本文档为 Claude Code 提供项目指导信息。

## 项目概述

dbxmlTool 是一个游戏配置数据管理工具，用于 MySQL 数据库与 XML 文件之间的双向转换。基于 JavaFX 构建桌面 GUI，集成多个 AI 服务（通义千问、豆包、Kimi、DeepSeek）用于数据智能处理和翻译。

## 构建和运行命令

```bash
# 编译项目
mvnd clean compile

# 运行应用（JavaFX 应用）
mvnd javafx:run

# 打包（包含依赖的 fat jar）
mvnd clean package

# 运行测试
mvnd test
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

## 核心架构

### 包结构概览（11个一级包）

```
red.jiuzhou
├── ai/           # AI模型集成（4个服务商）
├── analysis/     # 数据分析引擎
│   └── enhanced/ # AI增强分析
├── api/          # REST API接口
│   └── common/   # 通用模型
├── dbxml/        # 数据库与XML双向转换（核心）
├── relationship/ # 关系分析
├── tabmapping/   # 表映射管理
├── theme/        # 主题管理系统
│   └── rules/    # 转换规则
├── ui/           # JavaFX用户界面
│   ├── features/ # 特性注册系统
│   └── mapping/  # 表映射UI
├── util/         # 工具类库
└── xmltosql/     # XML到SQL/DDL转换
```

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
| `SubTablePreloader` | 子表预加载，避免N+1查询 |
| `ColumnMapping` | 列映射配置 |

**技术特点**：
- 多线程分页处理（PAGE_SIZE=1000）
- 属性字段处理（_attr_前缀）
- 临时文件并发合并策略
- UTF-16编码XML输出

### SQL生成层 (`red.jiuzhou.xmltosql`)

从XML结构自动生成MySQL DDL语句。

| 类名 | 职责 |
|-----|------|
| `XMLToMySQLGenerator` | 核心类，XML→MySQL DDL转换 |
| `XmlProcess` | XML文件处理和解析 |
| `XmlFieldLen` | 字段长度分析 |
| `CreateLeftMenuJson` | 生成左侧菜单配置JSON |

**特点**：
- 层级表自动生成（使用`__`分隔符）
- 智能类型推断（基于长度统计）
- 集成阿里云翻译生成中文注释

### AI服务层 (`red.jiuzhou.ai`)

集成多个AI服务提供商。

| 类名 | 职责 |
|-----|------|
| `AiModelFactory` | AI模型工厂（工厂模式） |
| `AiModelClient` | AI客户端接口 |
| `TongYiClient` | 通义千问客户端 |
| `DoubaoClient` | 豆包AI客户端 |
| `KimiClient` | Kimi AI客户端 |
| `DeepSeekClient` | DeepSeek AI客户端 |
| `DashScopeBatchHelper` | 阿里云DashScope批量处理 |

### 分析引擎 (`red.jiuzhou.analysis`)

为游戏策划提供数据洞察。

| 类名 | 职责 |
|-----|------|
| `XmlDesignerInsightService` | XML设计洞察服务 |
| `DataCorrelationAnalyzer` | 数据关联分析器 |
| `SmartInsightEngine` | 智能洞察引擎（AI增强） |
| `EnumerationAnalysisEngine` | 枚举值分析 |
| `GameSystemDetector` | 游戏系统检测 |

### UI层 (`red.jiuzhou.ui`)

基于JavaFX的桌面应用界面。

| 类名 | 职责 |
|-----|------|
| `Dbxmltool` | 主应用入口（Spring Boot + JavaFX） |
| `MenuTabPaneExample` | 左侧目录树和Tab页管理 |
| `PaginatedTable` | 分页数据表格组件 |
| `SqlQryApp` | SQL查询编辑器 |
| `SQLConverterApp` | 数据转换工具 |
| `DdlApp` | DDL生成器 |
| `DesignerInsightStage` | 设计洞察窗口 |
| `ThemeStudioStage` | 主题工作室窗口 |

**特性系统 (`ui.features`)**：
- `FeatureRegistry` - 特性注册中心
- `FeatureDescriptor` - 特性描述符
- `FeatureCategory` - 特性分类（策划洞察、主题设计、AI助手、领域编辑）

### 主题系统 (`red.jiuzhou.theme`)

统一管理UI主题和资源转换。

| 类名 | 职责 |
|-----|------|
| `Theme` | 主题定义（Builder模式） |
| `ThemeManager` | 主题管理器 |
| `AITransformService` | AI驱动的内容转换 |
| `BatchTransformEngine` | 批量转换引擎 |
| `TransformRule` | 转换规则抽象类 |

**子包 `rules`**：MappingRule、RegexRule、TextStyleRule

### API接口层 (`red.jiuzhou.api`)

REST API接口。

| 类名 | 职责 |
|-----|------|
| `FileController` | 文件管理API |
| `TabController` | Tab页管理API |
| `CommonResult<T>` | 通用返回结果包装器 |
| `ErrorCode` | 错误码定义 |

### 工具类库 (`red.jiuzhou.util`)

| 类名 | 职责 |
|-----|------|
| `AIAssistant` | AI助手（多模型支持、故障转移） |
| `DatabaseUtil` | 数据库操作工具 |
| `XmlUtil` | XML解析工具 |
| `YamlUtils` / `YmlConfigUtil` | YAML配置管理 |
| `AliyunTranslateUtil` | 阿里云翻译 |
| `PathUtil` | 路径处理 |
| `SqlGeneratorUtil` | SQL生成工具 |

## 配置文件

### application.yml 关键配置

```yaml
server:
  port: 8081

spring:
  datasource:
    url: jdbc:mysql://localhost:3306/xmldb?...
    username: root
    password: ****

file:
  homePath: 资源根目录
  confPath: 配置文件目录
  cltDataPath: 客户端数据路径
  svrDataPath: 服务端数据路径

world:
  specialTabName: World类型特殊表名列表（逗号分隔）

xmlPath:
  数据库名: 对应的XML文件路径（逗号分隔多路径）

ai:
  qwen:
    apikey: ****
    model: qwen-plus
  doubao:
    apikey: ****
    model: doubao-seed-1-6-250615
  kimi:
    apikey: ****
    model: Moonshot-Kimi-K2-Instruct
  deepseek:
    apikey: ****
    model: deepseek-chat
  promptKey:
    表名@字段名: 提示词内容
```

### 其他配置文件

| 文件 | 职责 |
|-----|------|
| `logback-spring.xml` | 日志配置（30天归档） |
| `tabMapping.json` | 客户端/服务端表字段映射 |
| `styles.css` | JavaFX样式表 |
| `CONF/` | 游戏配置数据目录 |

## 数据流

```
XML文件 ←→ XmlToDbGenerator/DbToXmlGenerator ←→ MySQL数据库
                     ↓
           Analysis Engine（统计分析 + AI增强）
                     ↓
           Designer Insights（策划可视化）
                     ↓
           Theme System（多种转换规则）
                     ↓
           输出优化后的配置
```

## 关键设计模式

| 模式 | 使用场景 |
|-----|---------|
| 工厂模式 | AiModelFactory |
| Builder模式 | Theme、XmlDesignerInsight |
| 策略模式 | TransformRule多种实现 |
| 单例模式 | ThemeManager、数据库连接 |
| 适配器模式 | InsightIntegrationAdapter |
| 门面模式 | AIAssistant |

## 编码规范

- 所有代码文件使用 UTF-8 编码
- 使用中文注释和日志
- 遵循 Spring Boot 和 JavaFX 最佳实践
- 敏感配置使用环境变量注入（如 `${ALIYUN_ACCESS_KEY_ID}`）

## 常见开发场景

### 添加新的AI模型

1. 在 `red.jiuzhou.ai` 包下创建新的 Client 类，实现 `AiModelClient` 接口
2. 在 `AiModelFactory.getClient()` 中添加新模型的创建逻辑
3. 在 `application.yml` 中添加对应的配置项

### 添加新的转换规则

1. 在 `red.jiuzhou.theme.rules` 包下创建新规则类，继承 `TransformRule`
2. 实现 `apply()` 方法定义转换逻辑

### 添加新的特性模块

1. 在 `FeatureRegistry.defaultRegistry()` 中注册新特性
2. 创建对应的 Stage 类或功能类
3. 实现 `FeatureLauncher` 接口

## 文档

- `docs/DEVELOPER_GUIDE.md` - 开发者指南
- `docs/API_REFERENCE.md` - API参考文档
- `docs/ARCHITECTURE.md` - 架构设计文档
- `docs/CHANGELOG.md` - 更新日志
