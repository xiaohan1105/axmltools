# XML 到 SQL 生成

本包包含从 XML 结构生成 SQL DDL 和配置文件的工具。

## 主要组件

### SQL 生成
- `XMLToMySQLGenerator.java` - 从 XML 结构生成 MySQL DDL
- `XMLToConf.java` - 从 XML 生成配置文件
- `SqlFieldReorderTool.java` - SQL 字段重新排序工具

### XML 处理
- `XmlAllNode.java` - XML 节点处理工具
- `XmlFieldLen.java` - XML 字段长度分析
- `XmlFiledValNum.java` - XML 字段值处理
- `XmlProcess.java` - 通用 XML 处理工具

### 菜单生成
- `CreateLeftMenuJson.java` - 菜单结构生成

## 关键功能

- 从 XML 模式自动生成 DDL
- 配置文件生成
- XML 字段分析和验证
- 动态菜单结构生成
- SQL 字段排序和优化