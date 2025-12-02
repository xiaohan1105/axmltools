# AI 集成

本包包含用于内容处理和翻译的 AI 服务集成。

## AI 模型客户端
- `TongYiClient.java` - TongYi (Qwen) AI 集成
- `DoubaoClient.java` - 豆包 AI 集成
- `KimiClient.java` - Kimi AI 集成
- `DeepSeekClient.java` - DeepSeek AI 集成

## 核心组件
- `AiModelFactory.java` - AI 模型工厂模式实现
- `AiModelClient.java` - AI 模型客户端接口
- `DashScopeBatchHelper.java` - 批处理助手
- `AiTest.java` - AI 测试工具

## 关键功能

- 多 AI 服务支持
- AI 模型创建的工厂模式
- 批处理能力
- 内容翻译和处理
- 测试工具

## 用途

这些组件为应用程序提供 AI 驱动的内容处理、翻译和数据分析功能。