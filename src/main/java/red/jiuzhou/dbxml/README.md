# 数据库-XML 转换核心

本包包含数据库和 XML 格式之间转换的核心功能。

## 主要组件

### 转换引擎
- `WorldDbToXmlGenerator.java` - 将 World 数据库数据转换为 XML 格式
- `WorldXmlToDbGenerator.java` - 将 XML 数据转换回数据库格式
- `DbToXmlGenerator.java` - 通用数据库到 XML 转换器
- `XmlToDbGenerator.java` - 通用 XML 到数据库转换器

### 配置管理
- `TableConf.java` - 表配置管理
- `ColumnMapping.java` - 列映射配置
- `TabConfLoad.java` - 表配置加载器

### 数据处理
- `TableForestBuilder.java` - 构建层次化表结构
- `SubTablePreloader.java` - 预加载子表数据
- `DirectoryManagerDialog.java` - 目录管理界面

## 关键功能

- 数据库和 XML 之间的双向转换
- 支持复杂的层次化数据结构
- 可配置的表和列映射
- 数据关系保持
- 批处理能力